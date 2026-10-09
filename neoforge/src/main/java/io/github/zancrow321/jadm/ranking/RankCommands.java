package io.github.zancrow321.jadm.ranking;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.zancrow321.jadm.network.RankingPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /jadm rank}: the ranking window; {@code /jadm rank top} and {@code /jadm rank <player>} in chat; for
 * operators {@code set}, {@code reset} and {@code season}.
 */
public final class RankCommands {
    private static final Gson GSON = new Gson();
    private static final int TOP = 10;

    private RankCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("rank")
                .executes(ctx -> open(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("top").executes(RankCommands::top))
                .then(Commands.literal("set").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(RankCommands::names)
                                .then(Commands.argument("rating", IntegerArgumentType.integer(0, 100_000))
                                        .executes(RankCommands::set))))
                .then(Commands.literal("reset").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(RankCommands::names)
                                .executes(RankCommands::reset)))
                .then(Commands.literal("season").requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("This puts every player back to "
                                    + "the start rating. To start a new season, run ")
                                    .append(Component.literal("/jadm rank season confirm")
                                            .withStyle(ChatFormatting.YELLOW)), false);
                            return 0;
                        })
                        .then(Commands.literal("confirm").executes(RankCommands::season)))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(RankCommands::names)
                        .executes(RankCommands::show));
    }

    /** Opens the ranking window for {@code player}. */
    public static int open(ServerPlayer player) {
        RankingView view = RankingView.of(Ranking.get(player.server), player.getUUID(), player.getScoreboardName());
        PacketDistributor.sendToPlayer(player, new RankingPayload(GSON.toJson(view)));
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        Optional<Map.Entry<UUID, Ranking.Entry>> found = find(ctx, name);
        if (found.isEmpty() || found.get().getValue().games() == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(name + " hasn't played a ranked duel this season."),
                    false);
            return 0;
        }
        Ranking.Entry e = found.get().getValue();
        int place = ranking(ctx).place(found.get().getKey());
        ctx.getSource().sendSuccess(() -> Component.literal(e.name() + ": ").withStyle(ChatFormatting.GOLD)
                .append(RankedDuels.badge(e.tier())).append(Component.literal(" " + e.rating() + " rating, "
                        + RankedDuels.ordinal(place) + " place").withStyle(ChatFormatting.WHITE)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Ranked: " + e.wins() + " won, " + e.losses() + " lost, "
                + e.draws() + " drawn; best rating this season " + e.peak()).withStyle(ChatFormatting.GRAY), false);
        return e.rating();
    }

    private static int top(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<Map.Entry<UUID, Ranking.Entry>> standings = ranking(ctx).standings();
        if (standings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nobody has played a ranked duel this season."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Ranking, season " + ranking(ctx).season())
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < Math.min(TOP, standings.size()); i++) {
            Ranking.Entry e = standings.get(i).getValue();
            int place = i + 1;
            source.sendSuccess(() -> Component.literal(place + ". ").append(RankedDuels.badge(e.tier()))
                    .append(" " + e.name() + ": " + e.rating() + " (" + e.wins() + "-" + e.losses() + "-"
                            + e.draws() + ")"), false);
        }
        return Math.min(TOP, standings.size());
    }

    private static int set(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        int rating = IntegerArgumentType.getInteger(ctx, "rating");
        UUID id = idOf(ctx, name);
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal(name + " isn't online and has no ranking."));
            return 0;
        }
        Ranking.Entry e = ranking(ctx).set(id, found(ctx, name, id), rating);
        refresh(ctx, id);
        ctx.getSource().sendSuccess(() -> Component.literal("Set " + e.name() + "'s rating to " + e.rating() + " (")
                .append(RankedDuels.badge(e.tier())).append(")."), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        UUID id = idOf(ctx, name);
        if (id == null || !ranking(ctx).reset(id)) {
            ctx.getSource().sendFailure(Component.literal(name + " has no ranking."));
            return 0;
        }
        refresh(ctx, id);
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + name + "'s ranking."), true);
        return 1;
    }

    private static int season(CommandContext<CommandSourceStack> ctx) {
        int season = ranking(ctx).newSeason();
        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(ServerPlayer::refreshTabListName);
        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.literal("Ranking season "
                + season + " has begun! Everyone starts again at " + io.github.zancrow321.jadm.JadmServerConfig
                .RANKING.startRating.get() + ".").withStyle(ChatFormatting.GOLD), false);
        return season;
    }

    private static void refresh(CommandContext<CommandSourceStack> ctx, UUID id) {
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(id);
        if (online != null) {
            online.refreshTabListName();
        }
    }

    /** An online player by name first (they may have been renamed), then the name a ranking was last kept under. */
    private static Optional<Map.Entry<UUID, Ranking.Entry>> find(CommandContext<CommandSourceStack> ctx,
                                                                 String name) {
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return ranking(ctx).of(online.getUUID()).map(e -> Map.entry(online.getUUID(), e));
        }
        return ranking(ctx).byName(name);
    }

    private static UUID idOf(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        return ranking(ctx).byName(name).map(Map.Entry::getKey).orElse(null);
    }

    /** The name to keep for {@code id}: their online name, else the one on record. */
    private static String found(CommandContext<CommandSourceStack> ctx, String typed, UUID id) {
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(id);
        return online != null ? online.getScoreboardName()
                : ranking(ctx).of(id).map(Ranking.Entry::name).orElse(typed);
    }

    private static CompletableFuture<Suggestions> names(CommandContext<CommandSourceStack> ctx,
                                                        SuggestionsBuilder builder) {
        java.util.Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(ranking(ctx).names());
        names.addAll(List.of(ctx.getSource().getServer().getPlayerNames()));
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private static Ranking ranking(CommandContext<CommandSourceStack> ctx) {
        return Ranking.get(ctx.getSource().getServer());
    }
}
