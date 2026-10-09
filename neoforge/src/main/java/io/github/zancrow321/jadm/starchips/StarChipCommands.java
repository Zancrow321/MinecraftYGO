package io.github.zancrow321.jadm.starchips;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /jadm starchips ...}: anyone can see the standings, join, leave and challenge for more chips; hosts
 * (operators, or anyone with {@code playersCanHost}) start, end and call the finals; operators change chips.
 */
public final class StarChipCommands {
    private StarChipCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("starchips")
                .executes(StarChipCommands::status)
                .then(Commands.literal("join").executes(ctx -> run(ctx, false, m -> m.join(player(ctx)))))
                .then(Commands.literal("leave").executes(ctx -> run(ctx, false, m -> m.leave(player(ctx)))))
                .then(Commands.literal("duel").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> run(ctx, false, m -> m.challenge(player(ctx),
                                EntityArgument.getPlayer(ctx, "player"), 1)))
                        .then(Commands.argument("chips", IntegerArgumentType.integer(1, 1000))
                                .executes(ctx -> run(ctx, false, m -> m.challenge(player(ctx),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "chips")))))))
                .then(Commands.literal("start")
                        .executes(ctx -> run(ctx, true, m -> m.start(ctx.getSource().getPlayer(), null)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, true, m -> m.start(ctx.getSource().getPlayer(),
                                        StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("finals").executes(ctx -> run(ctx, true, StarChips::finals)))
                .then(Commands.literal("cancel").executes(ctx -> run(ctx, true, StarChips::cancel)))
                .then(Commands.literal("kick").then(Commands.argument("name", StringArgumentType.word())
                        .suggests(StarChipCommands::duelists)
                        .executes(ctx -> run(ctx, true, m -> m.kick(StringArgumentType.getString(ctx, "name"))))))
                .then(adjust("give"))
                .then(adjust("take"))
                .then(adjust("set"));
    }

    /** {@code give|take|set <name> <amount>}, for operators. */
    private static LiteralArgumentBuilder<CommandSourceStack> adjust(String how) {
        return Commands.literal(how).requires(source -> source.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(StarChipCommands::duelists)
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100_000))
                                .executes(ctx -> run(ctx, false, m -> m.adjust(
                                        StringArgumentType.getString(ctx, "name"), how,
                                        IntegerArgumentType.getInteger(ctx, "amount"))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> duelists(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        StarChipData.Event e = manager(ctx).event();
        return SharedSuggestionProvider.suggest(e == null ? List.of()
                : e.duelists.values().stream().map(d -> d.name).toList(), builder);
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        manager(ctx).status(ctx.getSource().getPlayer()).forEach(line ->
                ctx.getSource().sendSuccess(() -> line, false));
        return 1;
    }

    /**
     * Runs a subcommand that answers with what went wrong, or {@code null}.
     *
     * @param host whether only hosts may run it
     */
    private static int run(CommandContext<CommandSourceStack> ctx, boolean host, Action action) {
        if (host && !StarChips.canHost(ctx.getSource().getPlayer())) {
            ctx.getSource().sendFailure(Component.literal("Only operators can run Star Chip events on this server."));
            return 0;
        }
        String error;
        try {
            error = action.apply(manager(ctx));
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal(e.getRawMessage().getString()));
            return 0;
        }
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static StarChips manager(CommandContext<CommandSourceStack> ctx) {
        return StarChips.get(ctx.getSource().getServer());
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    @FunctionalInterface
    private interface Action {
        String apply(StarChips manager) throws CommandSyntaxException;
    }
}
