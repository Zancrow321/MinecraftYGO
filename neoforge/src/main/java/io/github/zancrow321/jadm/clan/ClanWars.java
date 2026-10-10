package io.github.zancrow321.jadm.clan;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Clan wars as a race for points: once one clan's war declaration is accepted, every duel won against a member of
 * the other clan brings a point, until one clan reaches the target or the time is up. The winner takes both stakes
 * and rating from the loser.
 */
public final class ClanWars {
    private ClanWars() {
    }

    /** Why {@code clan} can't fight {@code other} now, or {@code null} if it can. */
    static String whyNot(Clans clans, Clans.Clan clan, Clans.Clan other, long now) {
        var config = JadmServerConfig.CLANS;
        if (!config.enabled.get()) {
            return "Clans are turned off on this server.";
        }
        if (clan == other) {
            return "A clan can't go to war with itself.";
        }
        int min = config.warMinMembers.get();
        if (clan.members.size() < min || other.members.size() < min) {
            return "Both clans need at least " + min + " members for a war.";
        }
        for (Clans.War w : clans.wars()) {
            if (w.running() && (w.involves(clan.id) || w.involves(other.id))) {
                Clans.Clan busy = w.involves(clan.id) ? clan : other;
                return "[" + busy.tag + "] is already at war.";
            }
        }
        long cooldown = config.warCooldownHours.get() * 3_600_000L;
        long since = now - clans.lastWar(clan.id, other.id);
        if (cooldown > 0 && since < cooldown) {
            return "[" + clan.tag + "] and [" + other.tag + "] fought a war only recently. Try again in "
                    + ClanText.duration(cooldown - since) + ".";
        }
        return null;
    }

    /** {@code clan} declares war on {@code other}, putting up {@code stake} from its treasury. */
    static String declare(MinecraftServer server, ServerPlayer by, Clans.Clan clan, Clans.Clan other, long stake,
                          String format) {
        Clans clans = Clans.get(server);
        long now = System.currentTimeMillis();
        String why = whyNot(clans, clan, other, now);
        if (why == null && Clans.War.BATTLE.equals(format)) {
            why = battleSize(clan, other);
        }
        if (why != null) {
            return why;
        }
        for (Clans.War w : clans.wars()) {
            if (!w.running() && w.attacker.equals(clan.id)) {
                Clans.Clan target = clans.byId(w.defender);
                return "[" + clan.tag + "] has already declared war on [" + (target == null ? "?" : target.tag)
                        + "]. Take that back first with /jadm clan war cancel.";
            }
            if (!w.running() && w.involves(clan.id) && w.involves(other.id)) {
                return "[" + other.tag + "] has already declared war on you: /jadm clan war accept " + other.tag;
            }
        }
        long max = JadmServerConfig.CLANS.maxStake.get();
        if (stake > 0 && (!Points.active() || max == 0)) {
            return "Clan wars are fought without stakes on this server.";
        }
        if (stake > max) {
            return "The most a clan can put up is " + Points.format(max) + ".";
        }
        if (stake > clan.treasury) {
            return "The clan treasury holds only " + Points.format(clan.treasury) + ".";
        }
        clan.treasury -= stake;
        Clans.War war = clans.declare(clan.id, other.id, stake, format);
        long hours = JadmServerConfig.CLANS.warAcceptHours.get();
        String kind = war.battle() ? " an arena battle (" + ClanText.count(ClanBattles.size(), "duelist") + " a side)"
                : " a race for points";
        String stakeText = stake > 0 ? " Each clan puts up " + Points.format(stake) + "; the winner takes both."
                : "";
        ClanText.tell(server, clan, Component.literal(by.getScoreboardName() + " declared war on ")
                .append(ClanText.named(other)).append(":" + kind + "!" + stakeText + " They have " + hours
                        + " hours to accept.").withStyle(ChatFormatting.GOLD));
        ClanText.tell(server, other, Component.literal("").append(ClanText.named(clan))
                .append(Component.literal(" declared war on your clan:" + kind + "!" + stakeText)
                        .withStyle(ChatFormatting.GOLD)));
        ClanText.tellManagers(server, other, Component.literal("Your leader or an officer answers within " + hours
                + " hours: ").withStyle(ChatFormatting.GRAY)
                .append(ClanText.button("Accept", ChatFormatting.GREEN, "/jadm clan war accept " + clan.tag))
                .append(" ")
                .append(ClanText.button("Deny", ChatFormatting.RED, "/jadm clan war deny " + clan.tag)));
        return null;
    }

    /** {@code clan} accepts the war {@code war} declared on it. */
    static String accept(MinecraftServer server, Clans.Clan clan, Clans.War war) {
        Clans clans = Clans.get(server);
        Clans.Clan attacker = clans.byId(war.attacker);
        if (attacker == null) {
            clans.removeWar(war);
            return "That clan no longer exists.";
        }
        long now = System.currentTimeMillis();
        String why = whyNot(clans, attacker, clan, now);
        if (why == null && war.battle()) {
            why = battleSize(attacker, clan);
        }
        if (why != null) {
            return why;
        }
        if (war.stake > clan.treasury) {
            return "Accepting means putting up " + Points.format(war.stake) + ", but the clan treasury holds only "
                    + Points.format(clan.treasury) + ". Members can pay in with /jadm clan deposit <amount>.";
        }
        clan.treasury -= war.stake;
        war.startedAt = now;
        war.endsAt = now + JadmServerConfig.CLANS.warHours.get() * 3_600_000L;
        war.target = JadmServerConfig.CLANS.warTarget.get();
        clans.changed();
        MutableComponent text = Component.literal("Clan war! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("").withStyle(ChatFormatting.RESET).append(ClanText.named(attacker))
                        .append(Component.literal(" vs ").withStyle(ChatFormatting.GOLD))
                        .append(ClanText.named(clan)));
        MutableComponent rules = Component.literal(rules(war)).withStyle(ChatFormatting.GRAY);
        announce(server, attacker, clan, text);
        ClanText.tell(server, attacker, rules);
        ClanText.tell(server, clan, rules);
        return null;
    }

    private static String battleSize(Clans.Clan a, Clans.Clan b) {
        int size = ClanBattles.size();
        return a.members.size() < size || b.members.size() < size
                ? "An arena battle needs " + size + " duelists from each clan." : null;
    }

    /** "Every duel won against the other clan brings a point. First to 10 or ahead in 3 days wins." */
    static String rules(Clans.War war) {
        if (war.battle()) {
            return "Arena battle: each clan names " + ClanText.count(ClanBattles.size(), "duelist") + " (/jadm clan war lineup or the "
                    + "clan window); once both are ready, they fight one bout after another on a tournament arena. "
                    + "The battle has to begin within " + ClanText.duration(war.endsAt - war.startedAt) + "."
                    + (war.stake > 0 ? " The winner takes both stakes, " + Points.format(2 * war.stake) + "." : "");
        }
        String counts = JadmServerConfig.CLANS.warOnlyRanked.get() ? "Every ranked duel" : "Every duel";
        int perPair = JadmServerConfig.CLANS.warMaxPerPair.get();
        return counts + " won against the other clan brings a point"
                + (perPair > 0 ? " (the same two duelists count " + perPair + " times at most)" : "") + ". "
                + (war.target > 0 ? "First to " + war.target + " points, or ahead after " : "Ahead after ")
                + ClanText.duration(war.endsAt - war.startedAt) + ", wins"
                + (war.stake > 0 ? " both stakes, " + Points.format(2 * war.stake) : "") + ".";
    }

    /** Takes back or turns down a war that was never accepted: the attacker gets its stake back. */
    static void callOff(MinecraftServer server, Clans.War war, String why) {
        Clans clans = Clans.get(server);
        Clans.Clan attacker = clans.byId(war.attacker);
        Clans.Clan defender = clans.byId(war.defender);
        if (attacker != null) {
            attacker.treasury += war.stake;
        }
        clans.removeWar(war);
        Component text = Component.literal("The war between " + (attacker == null ? "?" : "[" + attacker.tag + "]")
                + " and " + (defender == null ? "?" : "[" + defender.tag + "]") + " is off: " + why)
                .withStyle(ChatFormatting.GRAY);
        ClanText.tell(server, attacker, text);
        ClanText.tell(server, defender, text);
    }

    /**
     * Ends a running war.
     *
     * @param winner the winning clan, or {@code null} for a draw
     * @param how    "target", "time", "surrender" or "disband"
     */
    static void finish(MinecraftServer server, Clans.War war, UUID winner, String how) {
        Clans clans = Clans.get(server);
        Clans.Clan a = clans.byId(war.attacker);
        Clans.Clan d = clans.byId(war.defender);
        int k = JadmServerConfig.CLANS.kFactor.get();
        int ra = a != null ? a.rating : JadmServerConfig.CLANS.startRating.get();
        int rd = d != null ? d.rating : JadmServerConfig.CLANS.startRating.get();
        double expectedA = 1 / (1 + Math.pow(10, (rd - ra) / 400.0));
        double scoreA = winner == null ? 0.5 : winner.equals(war.attacker) ? 1 : 0;
        int deltaA = (int) Math.round(k * (scoreA - expectedA));
        int deltaD = (int) Math.round(k * ((1 - scoreA) - (1 - expectedA)));
        long pot = 2 * war.stake;
        long bonus = Points.active() ? JadmServerConfig.CLANS.warWinPoints.get() : 0;
        for (Clans.Clan c : new Clans.Clan[]{a, d}) {
            if (c == null) {
                continue;
            }
            int delta = c == a ? deltaA : deltaD;
            c.rating = Math.max(0, c.rating + delta);
            c.peak = Math.max(c.peak, c.rating);
            if (winner == null) {
                c.warsDrawn++;
                c.treasury += war.stake;
            } else if (winner.equals(c.id)) {
                c.warsWon++;
                c.treasury += pot + bonus;
            } else {
                c.warsLost++;
            }
        }
        String mvp = mvp(clans, war);
        int winnerIndex = winner == null ? -1 : winner.equals(war.attacker) ? 0 : 1;
        clans.finished(war, new Clans.Result(war.attacker, a != null ? a.tag : "?", a != null ? a.name : "?",
                a != null ? a.color : "gray", war.defender, d != null ? d.tag : "?", d != null ? d.name : "?",
                d != null ? d.color : "gray", war.attackerScore, war.defenderScore, winnerIndex, how,
                System.currentTimeMillis(), war.stake, deltaA, mvp));
        Clans.Clan won = winner == null ? null : winner.equals(war.attacker) ? a : d;
        Clans.Clan lost = winner == null ? null : won == a ? d : a;
        MutableComponent text;
        if (won == null) {
            text = Component.literal("The clan war ended in a draw: ").withStyle(ChatFormatting.GOLD)
                    .append(scoreLine(a, war.attackerScore, d, war.defenderScore));
        } else {
            String why = switch (how) {
                case "surrender" -> " (" + (lost == null ? "?" : "[" + lost.tag + "]") + " surrendered)";
                case "disband" -> " (" + (lost == null ? "the other clan" : "[" + lost.tag + "]") + " disbanded)";
                default -> "";
            };
            text = Component.literal("").append(ClanText.named(won))
                    .append(Component.literal(" won the clan war" + why + ": ").withStyle(ChatFormatting.GOLD))
                    .append(scoreLine(a, war.attackerScore, d, war.defenderScore));
        }
        announce(server, a, d, text);
        MutableComponent details = Component.literal((mvp.isEmpty() ? "" : "Best duelist: " + mvp + ". ")
                + "Clan rating " + (a == null ? "" : "[" + a.tag + "] " + signed(deltaA) + ", ")
                + (d == null ? "" : "[" + d.tag + "] " + signed(deltaD)) + "."
                + (won != null && pot + bonus > 0 ? " [" + won.tag + "]'s treasury gets " + Points.format(pot + bonus)
                + "." : winner == null && war.stake > 0 ? " Both stakes go back." : ""))
                .withStyle(ChatFormatting.GRAY);
        ClanText.tell(server, a, details);
        ClanText.tell(server, d, details);
    }

    private static MutableComponent scoreLine(Clans.Clan a, int scoreA, Clans.Clan d, int scoreD) {
        if (a == null || d == null) {
            return Component.literal(scoreA + " : " + scoreD).withStyle(ChatFormatting.WHITE);
        }
        return ClanText.score(a, scoreA, d, scoreD);
    }

    /** "Yugi (4 wins)", the duelist who won the most duels in the war, or "". */
    private static String mvp(Clans clans, Clans.War war) {
        UUID best = null;
        int most = 0;
        for (Map.Entry<UUID, Integer> e : war.wins.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }
        if (best == null) {
            return "";
        }
        String name = "?";
        for (UUID clan : List.of(war.attacker, war.defender)) {
            Clans.Clan c = clans.byId(clan);
            if (c != null && c.members.containsKey(best)) {
                name = c.members.get(best).name;
            }
        }
        return name + " (" + most + (most == 1 ? " win)" : " wins)");
    }

    /** Tells the whole server, or only the two clans if {@code [clans] announceWars} is off. */
    private static void announce(MinecraftServer server, Clans.Clan a, Clans.Clan b, Component text) {
        if (JadmServerConfig.CLANS.announceWars.get()) {
            server.getPlayerList().broadcastSystemMessage(text, false);
        } else {
            ClanText.tell(server, a, text);
            ClanText.tell(server, b, text);
        }
    }

    /** Called about once a second: ends wars whose time is up and drops declarations nobody answered. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        Clans clans = Clans.get(server);
        long now = System.currentTimeMillis();
        long acceptMillis = JadmServerConfig.CLANS.warAcceptHours.get() * 3_600_000L;
        for (Clans.War war : List.copyOf(clans.wars())) {
            if (!war.running() && now - war.declaredAt > acceptMillis) {
                callOff(server, war, "nobody accepted it in time.");
            } else if (war.running() && now >= war.endsAt && !(war.battle() && war.bout >= 0)) {
                UUID winner = war.attackerScore > war.defenderScore ? war.attacker
                        : war.defenderScore > war.attackerScore ? war.defender : null;
                finish(server, war, winner, "time");
            }
        }
    }

    /** The war {@code people} on the two teams fight for, if a duel between them would count, or {@code null}. */
    private static Clans.War warBetween(Clans clans, List<UUID> team0, List<UUID> team1) {
        if (team0.isEmpty() || team1.isEmpty() || team0.contains(null) || team1.contains(null)) {
            return null;
        }
        Clans.Clan c0 = sameClan(clans, team0);
        Clans.Clan c1 = sameClan(clans, team1);
        if (c0 == null || c1 == null || c0 == c1) {
            return null;
        }
        return clans.runningBetween(c0.id, c1.id);
    }

    private static Clans.Clan sameClan(Clans clans, List<UUID> people) {
        Clans.Clan clan = clans.of(people.get(0));
        for (UUID id : people) {
            if (clans.of(id) != clan) {
                return null;
            }
        }
        return clan;
    }

    private static String pairKey(List<UUID> team0, List<UUID> team1) {
        List<String> ids = new ArrayList<>();
        team0.forEach(id -> ids.add(id.toString()));
        team1.forEach(id -> ids.add(id.toString()));
        ids.sort(null);
        return String.join("|", ids);
    }

    private static boolean pairMayCount(Clans.War war, List<UUID> team0, List<UUID> team1) {
        int max = JadmServerConfig.CLANS.warMaxPerPair.get();
        return max <= 0 || war.pairs.getOrDefault(pairKey(team0, team1), 0) < max;
    }

    /**
     * The line the duelists see when a duel starts that counts in a clan war (or would, if it were ranked), or
     * {@code null}.
     */
    public static Component startNote(MinecraftServer server, List<UUID> team0, List<UUID> team1, boolean ranked) {
        Clans clans = Clans.get(server);
        Clans.War war = warBetween(clans, team0, team1);
        if (war == null || war.battle()) {
            return null;
        }
        Clans.Clan a = clans.byId(war.attacker);
        Clans.Clan d = clans.byId(war.defender);
        MutableComponent score = ClanText.score(a, war.attackerScore, d, war.defenderScore);
        if (JadmServerConfig.CLANS.warOnlyRanked.get() && !ranked) {
            return Component.literal("Clan war ").withStyle(ChatFormatting.GRAY).append(score)
                    .append(Component.literal(": only ranked duels count, so this one doesn't.")
                            .withStyle(ChatFormatting.GRAY));
        }
        if (!pairMayCount(war, team0, team1)) {
            return Component.literal("Clan war ").withStyle(ChatFormatting.GRAY).append(score)
                    .append(Component.literal(": you already counted " + JadmServerConfig.CLANS.warMaxPerPair.get()
                            + " times against each other in this war, so this duel doesn't.")
                            .withStyle(ChatFormatting.GRAY));
        }
        return Component.literal("Clan war! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("This duel counts: ").withStyle(ChatFormatting.RESET, ChatFormatting.GOLD))
                .append(score);
    }

    /**
     * A duel between people ended: if it was between two clans at war, the winners' clan scores a point.
     *
     * @param team0  the people on team 0 ({@code null} for a bot seat)
     * @param winner 0 or 1, anything else for a draw
     * @return a line for each duelist's result screen, if the duel was part of a war
     */
    public static Map<UUID, String> duelEnded(MinecraftServer server, List<UUID> team0, List<UUID> team1, int winner,
                                              boolean ranked) {
        Map<UUID, String> notes = new HashMap<>();
        Clans clans = Clans.get(server);
        Clans.War war = warBetween(clans, team0, team1);
        if (war == null || war.battle() || winner != 0 && winner != 1) {
            return notes;
        }
        if (JadmServerConfig.CLANS.warOnlyRanked.get() && !ranked) {
            return notes;
        }
        Clans.Clan a = clans.byId(war.attacker);
        Clans.Clan d = clans.byId(war.defender);
        if (!pairMayCount(war, team0, team1)) {
            String line = "Clan war: you already counted " + JadmServerConfig.CLANS.warMaxPerPair.get()
                    + " times against each other.";
            team0.forEach(id -> notes.put(id, line));
            team1.forEach(id -> notes.put(id, line));
            return notes;
        }
        war.pairs.merge(pairKey(team0, team1), 1, Integer::sum);
        List<UUID> winners = winner == 0 ? team0 : team1;
        List<UUID> losers = winner == 0 ? team1 : team0;
        Clans.Clan winning = clans.of(winners.get(0));
        if (winning.id.equals(war.attacker)) {
            war.attackerScore++;
        } else {
            war.defenderScore++;
        }
        for (UUID id : winners) {
            war.wins.merge(id, 1, Integer::sum);
            Clans.Member m = winning.members.get(id);
            if (m != null) {
                m.warWins++;
            }
        }
        Clans.Clan losing = clans.of(losers.get(0));
        for (UUID id : losers) {
            Clans.Member m = losing.members.get(id);
            if (m != null) {
                m.warLosses++;
            }
        }
        clans.changed();
        String line = "Clan war: [" + a.tag + "] " + war.attackerScore + " : " + war.defenderScore + " [" + d.tag
                + "]";
        winners.forEach(id -> notes.put(id, line + ", a point for your clan!"));
        losers.forEach(id -> notes.put(id, line));
        String winnerNames = String.join(" & ", winners.stream().map(id -> nameIn(winning, id)).toList());
        String loserNames = String.join(" & ", losers.stream().map(id -> nameIn(losing, id)).toList());
        MutableComponent text = Component.literal(winnerNames + " beat " + loserNames + ": ")
                .withStyle(ChatFormatting.GRAY).append(ClanText.score(a, war.attackerScore, d, war.defenderScore));
        ClanText.tell(server, a, text);
        ClanText.tell(server, d, text);
        if (war.target > 0 && war.score(winning.id) >= war.target) {
            finish(server, war, winning.id, "target");
        }
        return notes;
    }

    /** Takes someone who left a clan out of its arena battle lineups that haven't begun. */
    static void dropFromLineups(Clans clans, Clans.Clan clan, UUID player) {
        for (Clans.War w : clans.warsOf(clan.id)) {
            if (w.battle() && w.bout < 0 && w.lineup(clan.id).remove(player)) {
                w.setReady(clan.id, false);
                clans.changed();
            }
        }
    }

    private static String nameIn(Clans.Clan clan, UUID id) {
        Clans.Member m = clan.members.get(id);
        return m == null ? "?" : m.name;
    }

    private static String signed(int n) {
        return n >= 0 ? "+" + n : String.valueOf(n);
    }
}
