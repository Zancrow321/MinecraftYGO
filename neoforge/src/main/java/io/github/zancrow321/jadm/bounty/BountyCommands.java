package io.github.zancrow321.jadm.bounty;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.duel.DuelManager;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * {@code /jadm bounty}: the biggest bounties; {@code <player>} the bounty on someone; {@code place <player> <amount>}
 * puts Duel Points on their head; {@code withdraw <player>} takes yours back; operators call one off with
 * {@code clear <player>}.
 */
public final class BountyCommands {
    private static final int TOP = 10;

    private BountyCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("bounty")
                .executes(BountyCommands::board)
                .then(Commands.literal("place").then(Commands.argument("player", StringArgumentType.word())
                        .suggests(BountyCommands::online)
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(BountyCommands::place))))
                .then(Commands.literal("withdraw").then(Commands.argument("player", StringArgumentType.word())
                        .suggests(BountyCommands::wanted).executes(BountyCommands::withdraw)))
                .then(Commands.literal("clear").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(BountyCommands::wanted).executes(BountyCommands::clear)))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(BountyCommands::wanted)
                        .executes(BountyCommands::show));
    }

    private static boolean disabled(CommandSourceStack source) {
        if (JadmServerConfig.BOUNTY.enabled.get()) {
            return false;
        }
        source.sendFailure(Component.literal("Bounties are turned off on this server ([bounty] enabled)."));
        return true;
    }

    private static int board(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (disabled(source)) {
            return 0;
        }
        Bounties bounties = Bounties.get(source.getServer());
        List<Map.Entry<UUID, Bounties.Bounty>> top = bounties.top(TOP);
        if (top.isEmpty()) {
            source.sendSuccess(() -> Component.literal("There are no bounties. Put one up with /jadm bounty place "
                    + "<player> <amount>."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Wanted").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                false);
        for (int i = 0; i < top.size(); i++) {
            Bounties.Bounty bounty = top.get(i).getValue();
            String line = (i + 1) + ". " + bounty.name() + ": " + Points.format(bounty.total())
                    + (bounty.backers() > 1 ? " from " + bounty.backers() + " players" : "");
            source.sendSuccess(() -> Component.literal(line), false);
        }
        ServerPlayer self = source.getPlayer();
        if (self != null) {
            long mine = bounties.total(self.getUUID());
            if (mine > 0) {
                source.sendSuccess(() -> Component.literal("The bounty on you: " + Points.format(mine))
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
        return top.size();
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (disabled(source)) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "player");
        Optional<Bounties.Bounty> found = find(source.getServer(), name)
                .flatMap(p -> Bounties.get(source.getServer()).on(p.getId()));
        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.literal("There is no bounty on " + name + "."), false);
            return 0;
        }
        Bounties.Bounty bounty = found.get();
        source.sendSuccess(() -> Component.literal("Bounty on " + bounty.name() + ": " + Points.format(bounty.total()))
                .withStyle(ChatFormatting.RED), false);
        bounty.amounts().forEach((backer, amount) -> source.sendSuccess(() -> Component.literal("- "
                + bounty.backerName(backer) + ": " + Points.format(amount)).withStyle(ChatFormatting.GRAY), false));
        return (int) Math.min(bounty.total(), Integer.MAX_VALUE);
    }

    private static int place(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer backer = source.getPlayerOrException();
        if (disabled(source)) {
            return 0;
        }
        if (!Points.active()) {
            source.sendFailure(Component.literal("Bounties are paid in Duel Points, and this server's shops use "
                    + JadmServerConfig.SHOP_CURRENCY.get() + " instead."));
            return 0;
        }
        MinecraftServer server = source.getServer();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<GameProfile> target = find(server, name);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Nobody named " + name + " has played on this server."));
            return 0;
        }
        UUID id = target.get().getId();
        String targetName = target.get().getName();
        if (id.equals(backer.getUUID())) {
            source.sendFailure(Component.literal("You can't put a bounty on yourself."));
            return 0;
        }
        long amount = IntegerArgumentType.getInteger(ctx, "amount");
        int min = JadmServerConfig.BOUNTY.minAmount.get();
        if (amount < min) {
            source.sendFailure(Component.literal("A bounty is at least " + Points.format(min) + "."));
            return 0;
        }
        long fee = Bounties.fee(amount);
        Points points = Points.get(server);
        if (!points.take(server, backer.getUUID(), amount + fee)) {
            source.sendFailure(Component.literal("That costs " + Points.format(amount + fee)
                    + (fee > 0 ? " (" + Points.format(fee) + " of it the fee)" : "") + ", but you have "
                    + Points.format(points.balance(backer.getUUID())) + "."));
            return 0;
        }
        Bounties bounties = Bounties.get(server);
        bounties.raise(server, id, targetName, backer, amount);
        long total = bounties.total(id);
        source.sendSuccess(() -> Component.literal("You put " + Points.format(amount) + " on " + targetName + "'s head"
                + (fee > 0 ? " (plus a fee of " + Points.format(fee) + ")" : "") + ". The bounty is now "
                + Points.format(total) + ".").withStyle(ChatFormatting.GOLD), false);
        if (JadmServerConfig.BOUNTY.announce.get()) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(backer.getScoreboardName()
                    + " raised the bounty on " + targetName + " to " + Points.format(total)
                    + ". Beat them in a duel to collect it!").withStyle(ChatFormatting.RED), false);
        } else {
            ServerPlayer wanted = server.getPlayerList().getPlayer(id);
            if (wanted != null) {
                wanted.sendSystemMessage(Component.literal("The bounty on you is now " + Points.format(total)
                        + ". Whoever beats you in a duel collects it.").withStyle(ChatFormatting.RED));
            }
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    private static int withdraw(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer backer = source.getPlayerOrException();
        if (disabled(source)) {
            return 0;
        }
        if (!JadmServerConfig.BOUNTY.withdraw.get()) {
            source.sendFailure(Component.literal("Bounties can't be taken back on this server."));
            return 0;
        }
        MinecraftServer server = source.getServer();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<GameProfile> target = find(server, name);
        Bounties bounties = Bounties.get(server);
        if (target.isEmpty() || bounties.on(target.get().getId()).map(b -> b.from(backer.getUUID())).orElse(0L) == 0) {
            source.sendFailure(Component.literal("You have no bounty on " + name + "."));
            return 0;
        }
        if (DuelManager.get(server).inDuel(target.get().getId())) {
            source.sendFailure(Component.literal(target.get().getName() + " is in a duel right now; take your bounty "
                    + "back once it is over."));
            return 0;
        }
        long amount = bounties.withdraw(server, target.get().getId(), backer.getUUID());
        source.sendSuccess(() -> Component.literal("You took your " + Points.format(amount) + " off "
                + target.get().getName() + "'s head" + (Bounties.fee(amount) > 0 ? " (the fee stays paid)" : "")
                + ".").withStyle(ChatFormatting.GOLD), false);
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<GameProfile> target = find(server, name);
        long total = target.map(p -> Bounties.get(server).clear(server, p.getId())).orElse(0L);
        if (total == 0) {
            source.sendFailure(Component.literal("There is no bounty on " + name + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Called off the bounty on " + target.get().getName() + " and paid "
                + Points.format(total) + " back to whoever put it up."), true);
        return 1;
    }

    /** An online player by name first, then someone with a bounty on them, then anyone who has played here. */
    private static Optional<GameProfile> find(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(online.getGameProfile());
        }
        Bounties bounties = Bounties.get(server);
        Optional<UUID> wanted = bounties.byName(name);
        if (wanted.isPresent()) {
            return Optional.of(new GameProfile(wanted.get(), bounties.on(wanted.get()).orElseThrow().name()));
        }
        return server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(name);
    }

    private static CompletableFuture<Suggestions> online(CommandContext<CommandSourceStack> ctx,
                                                         SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder);
    }

    private static CompletableFuture<Suggestions> wanted(CommandContext<CommandSourceStack> ctx,
                                                         SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Stream.concat(Bounties.get(ctx.getSource().getServer()).names()
                .stream(), Stream.of(ctx.getSource().getServer().getPlayerNames())).distinct(), builder);
    }
}
