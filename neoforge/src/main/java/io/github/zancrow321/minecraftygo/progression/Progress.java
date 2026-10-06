package io.github.zancrow321.minecraftygo.progression;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.YgoServerConfig;
import io.github.zancrow321.minecraftygo.engine.Ruleset;
import io.github.zancrow321.minecraftygo.engine.data.Banlist;
import io.github.zancrow321.minecraftygo.engine.data.PoolMode;
import io.github.zancrow321.minecraftygo.engine.data.Products;
import io.github.zancrow321.minecraftygo.engine.data.Progression;
import io.github.zancrow321.minecraftygo.network.PoolModePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The server side of progression: where the world and its players stand, moving them on, and telling everyone.
 */
public final class Progress {
    /** The running server, while there is one in this JVM. */
    private static volatile MinecraftServer server;

    private Progress() {
    }

    public static void started(MinecraftServer s) {
        server = s;
    }

    public static void stopped() {
        server = null;
    }

    /** Whether a server runs in this JVM (a dedicated server, or the integrated one of a single player world). */
    public static boolean running() {
        return server != null;
    }

    public static boolean perPlayer() {
        return YgoServerConfig.SPEC.isLoaded() && "player".equals(YgoServerConfig.PROGRESSION_SCOPE.get());
    }

    /** The step a new world or player starts at. */
    public static int start() {
        Progression progression = YgoData.progression();
        int start = YgoServerConfig.SPEC.isLoaded() ? progression.find(YgoServerConfig.START_PRODUCT.get()) : -1;
        return start >= 0 ? start : progression.find("LOB");
    }

    private static int resolve(String product) {
        int step = product == null ? -1 : YgoData.progression().indexOf(product);
        return step >= 0 ? step : start();
    }

    /** The world's step (with per-player progress, where commands without a player name leave the world). */
    public static int worldStep() {
        MinecraftServer s = server;
        return s == null ? start() : resolve(ProgressionData.get(s).world());
    }

    public static int step(ServerPlayer player) {
        if (!perPlayer()) {
            return resolve(ProgressionData.get(player.server).world());
        }
        return resolve(ProgressionData.get(player.server).player(player.getUUID()));
    }

    /** Tells a player's client what the server plays with, and where the player stands. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new PoolModePayload(YgoData.poolMode().id(),
                YgoData.progression().product(step(player)).id(), YgoServerConfig.LOCKED_IN_DECK.get(),
                YgoServerConfig.BANLIST.get()));
    }

    /**
     * Moves the world, or one player with per-player progress, to a step, tells whoever it concerns and returns
     * a summary for the person who did it.
     *
     * @param player the player to move, or {@code null} for the world
     */
    public static Component move(MinecraftServer s, ServerPlayer player, int to) {
        Progression progression = YgoData.progression();
        int from = player == null ? worldStep() : step(player);
        String id = progression.product(to).id();
        ProgressionData data = ProgressionData.get(s);
        if (player == null) {
            data.world(id);
        } else {
            data.player(player.getUUID(), id);
        }
        List<ServerPlayer> concerned = player != null ? List.of(player)
                : perPlayer() ? List.of() : s.getPlayerList().getPlayers();
        concerned.forEach(Progress::sync);
        List<Component> news = news(from, to);
        if (YgoServerConfig.ANNOUNCE.get()) {
            for (ServerPlayer p : concerned) {
                news.forEach(p::sendSystemMessage);
            }
        }
        Products.Product product = progression.product(to);
        return Component.literal((player == null ? "The world" : player.getScoreboardName()) + " is now at "
                + describe(product) + ".");
    }

    /** What changes going from one step to another: new sets, rules and banlist. */
    static List<Component> news(int from, int to) {
        Progression progression = YgoData.progression();
        List<Component> lines = new ArrayList<>();
        if (to > from) {
            List<Products.Product> opened = new ArrayList<>();
            for (int i = from + 1; i <= to; i++) {
                if (!progression.product(i).newCards().isEmpty()) {
                    opened.add(progression.product(i));
                }
            }
            if (opened.size() == 1) {
                lines.add(Component.literal("New set: ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(describe(opened.get(0))).withStyle(ChatFormatting.YELLOW)));
            } else if (!opened.isEmpty()) {
                lines.add(Component.literal(opened.size() + " new sets, up to ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(describe(progression.product(to))).withStyle(ChatFormatting.YELLOW)));
            }
        } else if (to < from) {
            lines.add(Component.literal("Card progress set back to ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(describe(progression.product(to))).withStyle(ChatFormatting.YELLOW)));
        }
        if (YgoData.poolMode() != PoolMode.PROGRESSION) {
            return lines;
        }
        Ruleset rulesBefore = YgoData.ruleset(from);
        Ruleset rulesAfter = YgoData.ruleset(to);
        if (rulesBefore != rulesAfter) {
            lines.add(Component.literal("Duels are now played under " + rulesAfter.displayName() + ".")
                    .withStyle(ChatFormatting.AQUA));
        }
        Banlist banlistBefore = YgoData.banlist(from);
        Banlist banlistAfter = YgoData.banlist(to);
        if (!banlistBefore.name().equals(banlistAfter.name())) {
            lines.add(Component.literal("The Forbidden & Limited List is now " + banlistAfter.name() + ".")
                    .withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    /** e.g. {@code Metal Raiders (MRD, 2002-06-26)} */
    static String describe(Products.Product product) {
        return product.name() + " (" + product.code() + ", " + product.date() + ")";
    }

    /** The status lines of {@code /ygo progression status}. */
    static List<Component> status(int step, String who) {
        Progression progression = YgoData.progression();
        Products.Product product = progression.product(step);
        List<Component> lines = new ArrayList<>();
        MutableComponent head = Component.literal(who + ": ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(describe(product)).withStyle(ChatFormatting.YELLOW));
        lines.add(head);
        if (YgoData.poolMode() != PoolMode.PROGRESSION) {
            lines.add(Component.literal("This world plays with the " + YgoData.poolMode().id()
                    + " pool; progression only counts with pool.mode = \"progression\".").withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.literal("Rules: " + YgoData.ruleset(step).displayName() + " · Banlist: "
                + YgoData.banlist(step).name() + " · " + YgoData.progression().pool(step).playable().size()
                + " cards").withStyle(ChatFormatting.GRAY));
        List<Products.Product> next = progression.upcoming(step, 1);
        lines.add(Component.literal(next.isEmpty() ? "Every set is unlocked."
                : "Next: " + describe(next.get(0)) + ", " + next.get(0).newCards().size() + " new cards")
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
