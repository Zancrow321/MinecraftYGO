package io.github.zancrow321.jadm.clan;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.arena.DuelArena;
import io.github.zancrow321.jadm.duel.DuelManager;
import io.github.zancrow321.jadm.duel.MatchSetup;
import io.github.zancrow321.jadm.tournament.TournamentManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arena battles, the other kind of clan war: both clans name their duelists, and once both say they are ready the
 * duelists fight one bout after another on a tournament arena, first against first, second against second. Every
 * bout won is a point; after the last bout the clan with more points wins the war.
 */
public final class ClanBattles {
    private static final String WAITING = "waiting";
    private static final String CALLED = "called";
    private static final String DUELING = "dueling";
    private static final String PAUSE = "pause";
    /** Seconds between two bouts. */
    private static final int PAUSE_SECONDS = 6;
    /** Seconds after a bout before its duelists are sent back where they came from. */
    private static final int RETURN_SECONDS = 4;

    /** What one war's current bout is doing; not saved (after a restart the bout starts over). */
    private static final class Bout {
        String phase = WAITING;
        ServerLevel level;
        BlockPos arena;
        long startsAt;
        long missingSince;
        boolean toldNoArena;
    }

    private record Back(ServerLevel level, Vec3 pos, float yaw, float pitch, long dueAt) {
    }

    private static final Map<UUID, Bout> BOUTS = new HashMap<>();
    private static final Map<UUID, Back> RETURNS = new HashMap<>();

    private ClanBattles() {
    }

    public static void reset() {
        BOUTS.clear();
        RETURNS.clear();
    }

    /** Whether a clan battle has called duelists onto this arena or is dueling on it. */
    public static boolean holds(Level level, BlockPos arena) {
        for (Bout b : BOUTS.values()) {
            if (b.level == level && arena.equals(b.arena) && (b.phase.equals(CALLED) || b.phase.equals(DUELING))) {
                return true;
            }
        }
        return false;
    }

    /** How many duelists each clan sends into an arena battle. */
    public static int size() {
        return JadmServerConfig.CLANS.battleDuelists.get();
    }

    /** Called about once a second. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Clans clans = Clans.get(server);
        for (Clans.War war : List.copyOf(clans.wars())) {
            if (war.running() && war.battle()) {
                step(server, clans, war, now);
            }
        }
        BOUTS.keySet().removeIf(id -> clans.wars().stream().noneMatch(w -> w.id.equals(id)));
        sendBack(server, now);
    }

    private static void step(MinecraftServer server, Clans clans, Clans.War war, long now) {
        Clans.Clan a = clans.byId(war.attacker);
        Clans.Clan d = clans.byId(war.defender);
        if (a == null || d == null) {
            return;
        }
        if (war.bout < 0) {
            if (war.attackerReady && war.defenderReady) {
                war.bout = 0;
                clans.changed();
                MutableComponent text = Component.literal("Arena battle! ").withStyle(ChatFormatting.GOLD,
                        ChatFormatting.BOLD).append(Component.literal("").withStyle(ChatFormatting.RESET)
                        .append(ClanText.named(a)).append(Component.literal(" vs ").withStyle(ChatFormatting.GOLD))
                        .append(ClanText.named(d)).append(Component.literal(": " + war.attackerLineup.size()
                                + " bouts begin now. Right-click a duelist to watch.").withStyle(ChatFormatting.GOLD)));
                if (JadmServerConfig.CLANS.announceWars.get()) {
                    server.getPlayerList().broadcastSystemMessage(text, false);
                } else {
                    ClanText.tell(server, a, text);
                    ClanText.tell(server, d, text);
                }
            }
            return;
        }
        int bouts = Math.min(war.attackerLineup.size(), war.defenderLineup.size());
        if (war.bout >= bouts) {
            over(server, war);
            return;
        }
        Bout bout = BOUTS.computeIfAbsent(war.id, k -> new Bout());
        UUID pa = war.attackerLineup.get(war.bout);
        UUID pd = war.defenderLineup.get(war.bout);
        String na = name(a, pa);
        String nd = name(d, pd);
        switch (bout.phase) {
            case PAUSE -> {
                if (now >= bout.startsAt) {
                    bout.phase = WAITING;
                }
            }
            case WAITING -> {
                if (missing(server, clans, war, bout, a, d, pa, pd, now)) {
                    return;
                }
                Map.Entry<ServerLevel, BlockPos> arena = freeArena(server);
                if (arena == null) {
                    if (!bout.toldNoArena) {
                        bout.toldNoArena = true;
                        tellBoth(server, a, d, Component.literal(TournamentManager.get(server).hasArenas()
                                ? "Bout " + (war.bout + 1) + " waits for a free tournament arena."
                                : "Arena battles are fought on tournament arenas, and this server has none yet: an "
                                + "operator adds one with /jadm tournament arena add.").withStyle(ChatFormatting.YELLOW));
                    }
                    return;
                }
                bout.toldNoArena = false;
                bout.level = arena.getKey();
                bout.arena = arena.getValue();
                bout.phase = CALLED;
                int call = JadmServerConfig.CLANS.battleCallSeconds.get();
                bout.startsAt = now + call * 1000L;
                toPodiums(server, bout, pa, pd, true);
                tellBoth(server, a, d, Component.literal("Bout " + (war.bout + 1) + " of " + bouts + ": " + na
                        + " [" + a.tag + "] vs " + nd + " [" + d.tag + "], starting in " + call + " seconds.")
                        .withStyle(ChatFormatting.GOLD));
            }
            case CALLED -> {
                if (missing(server, clans, war, bout, a, d, pa, pd, now)) {
                    return;
                }
                if (now < bout.startsAt) {
                    return;
                }
                if (!DuelArena.available(bout.level, bout.arena)) {
                    bout.phase = WAITING;
                    return;
                }
                toPodiums(server, bout, pa, pd, false);
                UUID warId = war.id;
                int index = war.bout;
                String error = DuelManager.get(server).startMatch(new MatchSetup(
                        List.of(new MatchSetup.Seat(pa, na, null), new MatchSetup.Seat(pd, nd, null)),
                        MatchSetup.Rules.SERVER, -1, null, false,
                        winner -> boutOver(server, warId, index, winner)));
                if (error != null) {
                    tellBoth(server, a, d, Component.literal("The bout couldn't start: " + error
                            + ". Trying again shortly.").withStyle(ChatFormatting.RED));
                    bout.startsAt = now + 5000;
                    return;
                }
                bout.phase = DUELING;
            }
            default -> {
                // DUELING: the duel reports back when it is over.
            }
        }
    }

    /**
     * Whether a duelist of the bout is offline or busy. After {@code battleNoShowMinutes} the bout goes to whoever is
     * there (to nobody if neither is).
     */
    private static boolean missing(MinecraftServer server, Clans clans, Clans.War war, Bout bout, Clans.Clan a,
                                   Clans.Clan d, UUID pa, UUID pd, long now) {
        boolean goneA = absent(server, pa);
        boolean goneD = absent(server, pd);
        if (!goneA && !goneD) {
            bout.missingSince = 0;
            return false;
        }
        if (bout.phase.equals(CALLED)) {
            bout.phase = WAITING;
        }
        int minutes = JadmServerConfig.CLANS.battleNoShowMinutes.get();
        if (bout.missingSince == 0) {
            bout.missingSince = now;
            String who = goneA && goneD ? name(a, pa) + " and " + name(d, pd) : goneA ? name(a, pa) : name(d, pd);
            tellBoth(server, a, d, Component.literal("Bout " + (war.bout + 1) + " waits for " + who
                    + " (offline or in another duel). After " + minutes + " minutes the bout is lost.")
                    .withStyle(ChatFormatting.YELLOW));
            return true;
        }
        if (now - bout.missingSince >= minutes * 60_000L) {
            tellBoth(server, a, d, Component.literal((goneA && goneD ? "Neither duelist" : goneA ? name(a, pa)
                    : name(d, pd)) + " didn't show up for bout " + (war.bout + 1) + ".").withStyle(ChatFormatting.GRAY));
            score(server, clans, war, goneA && goneD ? -1 : goneA ? 1 : 0, pa, pd);
        }
        return true;
    }

    private static boolean absent(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p == null || !p.isAlive() || DuelManager.get(server).inDuel(p);
    }

    /** A bout's duel ended: 0 the attacker's duelist won, 1 the defender's, 2 a draw, -1 it broke off. */
    private static void boutOver(MinecraftServer server, UUID warId, int index, int winner) {
        Clans clans = Clans.get(server);
        Clans.War war = clans.wars().stream().filter(w -> w.id.equals(warId)).findFirst().orElse(null);
        Bout bout = BOUTS.get(warId);
        if (war == null || war.bout != index || bout == null) {
            return;
        }
        UUID pa = war.attackerLineup.get(index);
        UUID pd = war.defenderLineup.get(index);
        long now = System.currentTimeMillis();
        for (UUID id : List.of(pa, pd)) {
            Back back = RETURNS.get(id);
            if (back != null) {
                RETURNS.put(id, new Back(back.level(), back.pos(), back.yaw(), back.pitch(),
                        now + RETURN_SECONDS * 1000L));
            }
        }
        if (winner < 0) {
            ClanText.tell(server, clans.byId(war.attacker), Component.literal("The bout broke off; it will be fought "
                    + "again.").withStyle(ChatFormatting.RED));
            ClanText.tell(server, clans.byId(war.defender), Component.literal("The bout broke off; it will be fought "
                    + "again.").withStyle(ChatFormatting.RED));
            bout.phase = PAUSE;
            bout.startsAt = now + PAUSE_SECONDS * 1000L;
            return;
        }
        score(server, clans, war, winner == 0 ? 0 : winner == 1 ? 1 : -1, pa, pd);
    }

    /** Counts a bout ({@code side} 0 for the attacker, 1 the defender, -1 nobody) and moves on to the next. */
    private static void score(MinecraftServer server, Clans clans, Clans.War war, int side, UUID pa, UUID pd) {
        Clans.Clan a = clans.byId(war.attacker);
        Clans.Clan d = clans.byId(war.defender);
        if (side == 0) {
            war.attackerScore++;
        } else if (side == 1) {
            war.defenderScore++;
        }
        if (side >= 0) {
            UUID won = side == 0 ? pa : pd;
            UUID lost = side == 0 ? pd : pa;
            war.wins.merge(won, 1, Integer::sum);
            Clans.Member mw = (side == 0 ? a : d).members.get(won);
            Clans.Member ml = (side == 0 ? d : a).members.get(lost);
            if (mw != null) {
                mw.warWins++;
            }
            if (ml != null) {
                ml.warLosses++;
            }
        }
        war.bout++;
        clans.changed();
        int bouts = Math.min(war.attackerLineup.size(), war.defenderLineup.size());
        String what = side < 0 ? "Bout " + war.bout + ": no point. " : "Bout " + war.bout + " goes to "
                + (side == 0 ? name(a, pa) : name(d, pd)) + ". ";
        tellBoth(server, a, d, Component.literal(what).withStyle(ChatFormatting.GOLD)
                .append(ClanText.score(a, war.attackerScore, d, war.defenderScore)));
        Bout bout = BOUTS.computeIfAbsent(war.id, k -> new Bout());
        bout.phase = PAUSE;
        bout.missingSince = 0;
        bout.startsAt = System.currentTimeMillis() + PAUSE_SECONDS * 1000L;
        // Once one clan can't be caught any more, the rest of the bouts aren't fought.
        int left = bouts - war.bout;
        if (war.bout >= bouts || Math.abs(war.attackerScore - war.defenderScore) > left) {
            over(server, war);
        }
        ClanCommands.refresh(server, a);
        ClanCommands.refresh(server, d);
    }

    private static void over(MinecraftServer server, Clans.War war) {
        BOUTS.remove(war.id);
        UUID winner = war.attackerScore > war.defenderScore ? war.attacker
                : war.defenderScore > war.attackerScore ? war.defender : null;
        ClanWars.finish(server, war, winner, "battle");
    }

    /** A free tournament arena that no other clan battle holds, or {@code null}. */
    private static Map.Entry<ServerLevel, BlockPos> freeArena(MinecraftServer server) {
        for (Map.Entry<ServerLevel, BlockPos> arena : TournamentManager.get(server).freeArenas()) {
            if (!holds(arena.getKey(), arena.getValue())) {
                return arena;
            }
        }
        return null;
    }

    /** Puts the two duelists on the arena's podiums, remembering where they came from the first time. */
    private static void toPodiums(MinecraftServer server, Bout bout, UUID pa, UUID pd, boolean remember) {
        int end = 1;
        for (UUID id : List.of(pa, pd)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            DuelArena.Spot spot = DuelArena.podium(bout.level, bout.arena, end);
            end = -1;
            if (p == null) {
                continue;
            }
            if (remember && !RETURNS.containsKey(id)) {
                RETURNS.put(id, new Back(p.serverLevel(), p.position(), p.getYRot(), p.getXRot(), Long.MAX_VALUE));
            }
            p.teleportTo(bout.level, spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), 10);
            p.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Sends duelists whose bout is over back where they were called from, once they are out of the duel. */
    private static void sendBack(MinecraftServer server, long now) {
        for (Iterator<Map.Entry<UUID, Back>> it = RETURNS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Back> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Back back = e.getValue();
            if (p == null || now < back.dueAt() || DuelManager.get(server).inDuel(p)) {
                continue;
            }
            p.teleportTo(back.level(), back.pos().x, back.pos().y, back.pos().z, back.yaw(), back.pitch());
            it.remove();
        }
    }

    private static void tellBoth(MinecraftServer server, Clans.Clan a, Clans.Clan d, Component text) {
        ClanText.tell(server, a, text);
        ClanText.tell(server, d, text);
    }

    private static String name(Clans.Clan clan, UUID id) {
        Clans.Member m = clan.members.get(id);
        if (m != null) {
            return m.name;
        }
        return "?";
    }

    /** Where the battle stands, for the clan window: "Bout 2 of 3: Yugi vs Kaiba" or "". */
    public static String status(Clans clans, Clans.War war) {
        if (!war.battle() || war.bout < 0) {
            return "";
        }
        int bouts = Math.min(war.attackerLineup.size(), war.defenderLineup.size());
        if (war.bout >= bouts) {
            return "";
        }
        Clans.Clan a = clans.byId(war.attacker);
        Clans.Clan d = clans.byId(war.defender);
        Bout bout = BOUTS.get(war.id);
        String who = (a == null ? "?" : name(a, war.attackerLineup.get(war.bout))) + " vs "
                + (d == null ? "?" : name(d, war.defenderLineup.get(war.bout)));
        return "Bout " + (war.bout + 1) + " of " + bouts + ": " + who
                + (bout != null && bout.phase.equals(DUELING) ? " (dueling)" : "");
    }
}
