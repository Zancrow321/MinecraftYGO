package io.github.zancrow321.jadm.ranking;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.api.event.RankChangeEvent;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a ranked duel does once it is over: moves both players' ratings, pays promotion bonuses, tells the server
 * about new ranks and puts the rank in the player list.
 */
public final class RankedDuels {
    private RankedDuels() {
    }

    /**
     * What a ranked duel did to one player, for the result screen.
     *
     * @param line  e.g. "Gold, 1216 rating (+16), 3rd place"
     * @param notes promotions and bonuses, one line each, shown with the rewards
     */
    public record Outcome(String line, int color, List<String> notes) {
    }

    /** Why two people can't start a ranked duel, or {@code null} if they can. */
    public static String whyNot(MinecraftServer server, UUID a, UUID b) {
        if (!JadmServerConfig.RANKING.enabled.get()) {
            return "Ranked duels are turned off on this server.";
        }
        if (!Ranking.get(server).pairMayCount(a, b)) {
            return "You two have played " + JadmServerConfig.RANKING.maxPerPairPerDay.get()
                    + " ranked duels today, the most that count. Play unranked or come back tomorrow.";
        }
        return null;
    }

    /**
     * Counts a finished ranked 1v1 duel.
     *
     * @param scoreA 1 if {@code a} won, 0 if {@code b} won, 0.5 for a draw
     * @return what it did to each of the two, or nothing if it didn't count
     */
    public static Map<UUID, Outcome> finish(MinecraftServer server, UUID a, String nameA, UUID b, String nameB,
                                            double scoreA) {
        Map<UUID, Outcome> out = new HashMap<>();
        String why = whyNot(server, a, b);
        if (why != null) {
            Outcome unranked = new Outcome("", 0, List.of("Not ranked: " + why));
            out.put(a, unranked);
            out.put(b, unranked);
            return out;
        }
        Ranking ranking = Ranking.get(server);
        for (Ranking.Change change : ranking.record(a, nameA, b, nameB, scoreA)) {
            Ranking.Entry after = change.after();
            int tier = after.tier();
            int place = ranking.place(change.player());
            String line = Tiers.name(tier) + ", " + after.rating() + " rating (" + signed(change.delta()) + "), "
                    + ordinal(place) + " place";
            List<String> notes = new ArrayList<>();
            if (change.promotedTo() >= 0) {
                notes.add("Promoted to " + Tiers.name(change.promotedTo()) + "!");
                int bonus = JadmServerConfig.RANKING.promotionPoints.get();
                if (bonus > 0 && Points.active()) {
                    Points.get(server).add(server, change.player(), bonus);
                    notes.add("Promotion bonus: " + Points.format(bonus));
                }
                if (JadmServerConfig.RANKING.announcePromotions.get()) {
                    server.getPlayerList().broadcastSystemMessage(Component.literal(after.name() + " reached ")
                            .append(Component.literal(Tiers.name(change.promotedTo()))
                                    .withStyle(s -> s.withColor(Tiers.color(change.promotedTo())).withBold(true)))
                            .append(" in the ranking!").withStyle(ChatFormatting.GRAY), false);
                }
            }
            out.put(change.player(), new Outcome(line, Tiers.color(tier), notes));
            NeoForge.EVENT_BUS.post(new RankChangeEvent(server, change.player(), after.name(),
                    change.before().rating(), after.rating(), change.before().tier(), tier, change.promotedTo() >= 0));
            ServerPlayer online = server.getPlayerList().getPlayer(change.player());
            if (online != null) {
                online.refreshTabListName();
            }
        }
        return out;
    }

    /** Puts a ranked player's rank in front of their name in the player list. */
    public static void tabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !JadmServerConfig.RANKING.showInTabList.get()) {
            return;
        }
        Ranking.get(player.server).of(player.getUUID()).filter(e -> e.games() > 0).ifPresent(e -> {
            Component name = event.getDisplayName() != null ? event.getDisplayName() : player.getDisplayName();
            event.setDisplayName(badge(e.tier()).append(" ").append(name));
        });
    }

    /** "[Gold]" in the rank's color. */
    public static MutableComponent badge(int tier) {
        return Component.literal("[" + Tiers.name(tier) + "]")
                .withStyle(s -> s.withColor(TextColor.fromRgb(Tiers.color(tier))));
    }

    static String signed(int n) {
        return n >= 0 ? "+" + n : String.valueOf(n);
    }

    static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th"
                : switch (n % 10) {
                    case 1 -> "st";
                    case 2 -> "nd";
                    case 3 -> "rd";
                    default -> "th";
                };
        return n + suffix;
    }
}
