package io.github.zancrow321.jadm.arena;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.duel.DuelManager;
import io.github.zancrow321.jadm.tournament.TournamentManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Starts a duel by itself when one person stands on each podium of a free Duel Arena (or each side of a
 * player-built arena; with two podiums a side, two pairs make a tag duel). Whoever waits alone is told
 * they are waiting for an opponent (and the arena shows it over the podiums); once both podiums are taken a short
 * countdown runs, and stepping off calls it off. After a duel, people have to step off and back on to go again.
 */
public final class ArenaLobby {
    /** Looks at the podiums every this many ticks. */
    private static final int EVERY = 5;

    /** A waiting duel: who would play it, and when it starts (game time), or 0 while someone is missing. */
    private static final class Lobby {
        List<UUID> who = List.of();
        long startsAt;
        /** The second last counted out loud. */
        int shownSecond;
        int waiting;
    }

    private record Key(ServerLevel level, BlockPos arena) {
    }

    private static final Map<Key, Lobby> LOBBIES = new HashMap<>();
    /**
     * People who were in a duel and are still on its podium, or logged in standing on one: they count again once they
     * step off.
     */
    private static final Set<UUID> STEP_OFF = new HashSet<>();
    /** Everyone online at the last look, to tell who just logged in. */
    private static Set<UUID> online = Set.of();

    private ArenaLobby() {
    }

    public static void reset() {
        LOBBIES.clear();
        STEP_OFF.clear();
        online = Set.of();
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % EVERY != 0) {
            return;
        }
        DuelManager duels = DuelManager.get(server);
        boolean on = JadmServerConfig.ARENA_AUTO_START.get();
        // Who stands on which podium: index 0 for the podium at end +1, 1 for end -1.
        Map<Key, List<List<ServerPlayer>>> found = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            seen.add(id);
            if (duels.inDuel(player)) {
                STEP_OFF.add(id);
                continue;
            }
            DuelArena.Podium podium = player.isSpectator() || !player.isAlive() ? null : DuelArena.podiumOf(player);
            if (podium == null) {
                // Riding a podium back down after a duel isn't stepping off.
                if (!DuelArena.riding(id)) {
                    STEP_OFF.remove(id);
                }
                continue;
            }
            if (!online.contains(id)) {
                // Logged in up here, maybe where a duel or a restart left them: no duel before they step off.
                STEP_OFF.add(id);
            }
            if (on && !STEP_OFF.contains(id)) {
                found.computeIfAbsent(new Key(player.serverLevel(), podium.arena()),
                                k -> List.of(new ArrayList<>(), new ArrayList<>()))
                        .get(podium.end() > 0 ? 0 : 1).add(player);
            }
        }
        online = seen;
        for (Key key : List.copyOf(LOBBIES.keySet())) {
            if (!found.containsKey(key)) {
                close(key);
            }
        }
        found.forEach((key, ends) -> update(server, duels, key, ends));
    }

    private static void update(MinecraftServer server, DuelManager duels, Key key, List<List<ServerPlayer>> ends) {
        ServerLevel level = key.level();
        if (!DuelArena.available(level, key.arena())) {
            close(key);
            return;
        }
        if (TournamentManager.get(server).holds(level, key.arena())) {
            close(key);
            ends.forEach(players -> players.forEach(p -> bar(p, Component.translatable(
                    "message.jadm.arena.tournament").withStyle(ChatFormatting.GRAY))));
            return;
        }
        // A side holds one duelist per podium: the Duelist Kingdom arena has one a side, a built one up to two.
        int seats = level.getBlockState(key.arena()).is(DuelDome.CORE.get()) ? 2 : 1;
        List<List<ServerPlayer>> ready = List.of(new ArrayList<>(), new ArrayList<>());
        int waiting = 0;
        for (int i = 0; i < 2; i++) {
            List<ServerPlayer> here = ends.get(i);
            if (here.size() > seats || seats > 1 && crowded(here)) {
                here.forEach(p -> bar(p, Component.translatable("message.jadm.arena.crowded")
                        .withStyle(ChatFormatting.RED)));
                continue;
            }
            for (ServerPlayer player : here) {
                String problem = DuelManager.deckProblem(player);
                if (problem != null) {
                    bar(player, Component.literal(problem).withStyle(ChatFormatting.RED));
                } else {
                    ready.get(i).add(player);
                    waiting |= 1 << i;
                }
            }
        }
        Lobby lobby = LOBBIES.computeIfAbsent(key, k -> new Lobby());
        long now = level.getGameTime();
        int team = ready.get(0).size();
        if (team > 0 && team == ready.get(1).size() && team == ends.get(0).size() && team == ends.get(1).size()) {
            List<UUID> who = new ArrayList<>();
            ready.forEach(side -> side.forEach(p -> who.add(p.getUUID())));
            if (lobby.startsAt == 0 || !who.equals(lobby.who)) {
                lobby.who = List.copyOf(who);
                lobby.startsAt = now + JadmServerConfig.ARENA_COUNTDOWN.get() * 20L;
                lobby.shownSecond = -1;
            }
            long left = lobby.startsAt - now;
            if (left <= 0) {
                close(key);
                // Whatever happens next, they step off before this arena counts them again.
                STEP_OFF.addAll(lobby.who);
                if (team == 1) {
                    duels.arenaDuel(ready.get(0).get(0), ready.get(1).get(0));
                } else {
                    duels.arenaDuel(ready.get(0), ready.get(1));
                }
                return;
            }
            int seconds = (int) ((left + 19) / 20);
            if (seconds != lobby.shownSecond) {
                lobby.shownSecond = seconds;
                sound(level, key.arena(), SoundEvents.NOTE_BLOCK_HAT.value(), 1.2f);
            }
            for (int i = 0; i < 2; i++) {
                String opponents = String.join(" & ", ready.get(1 - i).stream()
                        .map(ServerPlayer::getScoreboardName).toList());
                for (ServerPlayer player : ready.get(i)) {
                    bar(player, Component.translatable("message.jadm.arena.countdown",
                            Component.literal(opponents).withStyle(ChatFormatting.WHITE),
                            Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.GOLD));
                }
            }
        } else {
            lobby.who = List.of();
            lobby.startsAt = 0;
            if (waiting != 0 && (lobby.waiting & waiting) != waiting) {
                // Someone just stepped up.
                sound(level, key.arena(), SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f);
            }
            String dots = ".".repeat(1 + (int) (now / 10 % 3));
            boolean tag = ready.get(0).size() + ready.get(1).size() > 2;
            for (List<ServerPlayer> side : ready) {
                for (ServerPlayer player : side) {
                    bar(player, Component.translatable(tag ? "message.jadm.arena.waiting_tag"
                            : "message.jadm.arena.waiting", dots).withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        lobby.waiting = waiting;
        show(key, waiting, lobby.startsAt);
    }

    private static void close(Key key) {
        LOBBIES.remove(key);
        show(key, 0, 0);
    }

    private static void show(Key key, int waiting, long startsAt) {
        if (!key.level().isLoaded(key.arena())) {
            return;
        }
        if (key.level().getBlockEntity(key.arena()) instanceof ArenaBlockEntity arena) {
            arena.lobby(waiting, startsAt);
        } else if (key.level().getBlockEntity(key.arena()) instanceof ArenaCoreBlockEntity core) {
            core.lobby(waiting, startsAt);
        }
    }

    /** Whether two of these people share one podium. */
    private static boolean crowded(List<ServerPlayer> players) {
        Set<BlockPos> podiums = new HashSet<>();
        for (ServerPlayer player : players) {
            BlockPos podium = BuiltArena.podiumUnder(player);
            if (podium != null && !podiums.add(podium)) {
                return true;
            }
        }
        return false;
    }

    private static void bar(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }

    private static void sound(ServerLevel level, BlockPos arena, SoundEvent sound, float pitch) {
        level.playSound(null, arena.above(DuelArena.HEIGHT), sound, SoundSource.BLOCKS, 2.0f, pitch);
    }
}
