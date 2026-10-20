package io.github.zancrow321.jadm.quest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /jadm quests}: the quest window; for operators {@code reload}, {@code list}, {@code reset <player>} and
 * {@code complete <player> <quest>}.
 */
public final class QuestCommands {
    private QuestCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("quests")
                .executes(ctx -> Quests.send(ctx.getSource().getPlayerOrException(), true))
                .then(Commands.literal("reload").requires(source -> source.hasPermission(2))
                        .executes(QuestCommands::reload))
                .then(Commands.literal("list").requires(source -> source.hasPermission(2))
                        .executes(QuestCommands::list))
                .then(Commands.literal("reset").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                            Quests.reset(player);
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.jadm.quests.reset",
                                    player.getDisplayName()), true);
                            return 1;
                        })))
                .then(Commands.literal("complete").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest", StringArgumentType.word())
                                        .suggests(QuestCommands::held)
                                        .executes(QuestCommands::complete))));
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        List<String> problems = QuestPool.load();
        int count = QuestPool.get().size();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.jadm.quests.reloaded", count), true);
        problems.forEach(p -> ctx.getSource().sendFailure(Component.literal(p)));
        return problems.isEmpty() ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        for (QuestDef def : QuestPool.get().values()) {
            String title = Quests.title(def, "en_us");
            ctx.getSource().sendSuccess(() -> Component.literal(def.id).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" " + def.period + ", " + def.type + " x" + def.goal + ", weight "
                            + def.weight + (title == null ? "" : ": " + title)).withStyle(ChatFormatting.GRAY)),
                    false);
        }
        return QuestPool.get().size();
    }

    private static int complete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String id = StringArgumentType.getString(ctx, "quest");
        if (!Quests.complete(player, id)) {
            ctx.getSource().sendFailure(Component.translatable("commands.jadm.quests.not_held",
                    player.getDisplayName(), id));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.jadm.quests.completed", id,
                player.getDisplayName()), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> held(CommandContext<CommandSourceStack> ctx,
                                                       SuggestionsBuilder builder) {
        try {
            return SharedSuggestionProvider.suggest(Quests.current(EntityArgument.getPlayer(ctx, "player")), builder);
        } catch (CommandSyntaxException e) {
            return SharedSuggestionProvider.suggest(QuestPool.get().keySet(), builder);
        }
    }
}
