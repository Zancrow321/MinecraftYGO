package io.github.zancrow321.jadm.duel;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.zancrow321.jadm.JadmServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /jadm stats}: your own duel record, {@code /jadm stats <player>} someone else's, {@code /jadm stats top} the
 * players with the most wins and {@code /jadm stats reset <player>} (operators) clears one.
 */
public final class StatsCommands {
    private static final int TOP = 10;

    private StatsCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("stats")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return show(ctx, player.getScoreboardName(), records(ctx).of(player.getUUID()).orElse(null));
                })
                .then(Commands.literal("top").executes(StatsCommands::top))
                .then(Commands.literal("reset").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(StatsCommands::names)
                                .executes(StatsCommands::reset)))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(StatsCommands::names)
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "player");
                            return show(ctx, name, find(ctx, name).map(Map.Entry::getValue).orElse(null));
                        }));
    }

    private static int show(CommandContext<CommandSourceStack> ctx, String name, DuelRecords.Record r) {
        CommandSourceStack source = ctx.getSource();
        if (!JadmServerConfig.TRACK_RECORD.get()) {
            source.sendFailure(Component.literal("This server doesn't keep duel records ([results] trackRecord)."));
            return 0;
        }
        if (r == null || r.duels() == 0) {
            source.sendSuccess(() -> Component.literal(name + " hasn't finished a duel yet."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(r.name() + "'s duel record").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(r.summary() + " (" + r.winRate() + "% won)"), false);
        source.sendSuccess(() -> Component.literal("Against players: " + r.playerWins() + "-" + r.playerLosses()
                + "-" + r.playerDraws() + ", against NPCs and bots: " + r.npcWins() + "-" + r.npcLosses() + "-"
                + r.npcDraws()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Wins in a row: " + r.streak() + " now, " + r.bestStreak()
                + " at best").withStyle(ChatFormatting.GRAY), false);
        return r.duels();
    }

    private static int top(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!JadmServerConfig.TRACK_RECORD.get()) {
            source.sendFailure(Component.literal("This server doesn't keep duel records ([results] trackRecord)."));
            return 0;
        }
        List<DuelRecords.Record> top = records(ctx).top(TOP);
        if (top.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nobody has finished a duel yet."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Top duelists").withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < top.size(); i++) {
            DuelRecords.Record r = top.get(i);
            String line = (i + 1) + ". " + r.name() + ": " + r.summary() + " (" + r.winRate() + "%)";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return top.size();
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        Optional<Map.Entry<UUID, DuelRecords.Record>> found = find(ctx, name);
        if (found.isEmpty() || !records(ctx).reset(found.get().getKey())) {
            ctx.getSource().sendFailure(Component.literal(name + " has no duel record."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + found.get().getValue().name()
                + "'s duel record."), true);
        return 1;
    }

    /** An online player by name first (they may have been renamed), then the name a record was last kept under. */
    private static Optional<Map.Entry<UUID, DuelRecords.Record>> find(CommandContext<CommandSourceStack> ctx,
                                                                      String name) {
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return records(ctx).of(online.getUUID()).map(r -> Map.entry(online.getUUID(), r));
        }
        return records(ctx).byName(name);
    }

    private static CompletableFuture<Suggestions> names(CommandContext<CommandSourceStack> ctx,
                                                        SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(records(ctx).names(), builder);
    }

    private static DuelRecords records(CommandContext<CommandSourceStack> ctx) {
        return DuelRecords.get(ctx.getSource().getServer());
    }
}
