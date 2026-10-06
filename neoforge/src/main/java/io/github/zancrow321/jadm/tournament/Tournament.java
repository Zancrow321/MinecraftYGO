package io.github.zancrow321.jadm.tournament;

import io.github.zancrow321.jadm.engine.tournament.Bracket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One tournament as it is saved with the world (as JSON, so plain fields). */
final class Tournament {
    static final String OPEN = "open";
    static final String RUNNING = "running";
    static final String DONE = "done";
    static final String CANCELLED = "cancelled";

    String name;
    /** {@code null} for a scheduled tournament, which the server hosts */
    UUID host;
    String hostName;
    Map<String, String> settings = new LinkedHashMap<>();
    List<Entrant> entrants = new ArrayList<>();
    String state = OPEN;
    /** Epoch milliseconds; {@code closesAt} 0 waits for the host. */
    long openedAt;
    long closesAt;
    long finishedAt;
    Bracket bracket;
    /** The progression step whose rules and banlist the tournament is played under. */
    int step;
    /** match id -> how far it got */
    Map<Integer, Live> live = new HashMap<>();
    /** The latest happenings, newest last, for the window. */
    List<String> news = new ArrayList<>();
    /** Entry fees taken, to share out or refund. */
    int pot;

    /** A duelist: a person, or an NPC when {@code player} is {@code null}. */
    static final class Entrant {
        UUID player;
        String name;
        /** An NPC's look and deck */
        long seed;
        /** The deck locked in at joining, or {@code null} to use the deck box of the moment. */
        List<Integer> main;
        List<Integer> extra;
        String deckName;
        int feePaid;
        int place;

        boolean npc() {
            return player == null;
        }
    }

    /** A match that can be played: waiting for an arena and its duelists, called to the arena, or in a game. */
    static final class Live {
        static final String WAITING = "waiting";
        static final String CALLED = "called";
        static final String DUELING = "dueling";
        static final String PAUSE = "pause";

        String phase = WAITING;
        /** When this phase began (epoch ms) and, when called or paused, when the next game starts. */
        long since;
        long startsAt;
        /** Index into the registered arenas, or -1 */
        int arena = -1;
        Set<UUID> ready = new HashSet<>();
        /** Where each person was before they were brought to the arena. */
        Map<UUID, Back> back = new HashMap<>();
        /** The side (0 or 1) that goes first in the next game: the loser of the last one; -1 for a coin toss. */
        int first = -1;
        /** The NPC standing in on the arena, if any. */
        UUID npcEntity;
        /** Since when a duelist has been missing (offline or busy), epoch ms, or 0. */
        long missingSince;
    }

    static final class Back {
        String dim;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
    }

    String setting(String key) {
        String v = settings.get(key);
        return v != null ? v : TournamentOptions.configured(TournamentOptions.option(key));
    }

    int integer(String key) {
        try {
            return Integer.parseInt(setting(key).strip());
        } catch (NumberFormatException e) {
            return (int) TournamentOptions.option(key).fallback();
        }
    }

    boolean bool(String key) {
        return Boolean.parseBoolean(setting(key).strip());
    }

    List<String> list(String key) {
        return TournamentOptions.splitList(setting(key));
    }

    Bracket.Format format() {
        Bracket.Format f = Bracket.Format.parse(setting("format"));
        return f != null ? f : Bracket.Format.SINGLE;
    }

    boolean active() {
        return state.equals(OPEN) || state.equals(RUNNING);
    }

    Entrant entrant(UUID player) {
        for (Entrant e : entrants) {
            if (player.equals(e.player)) {
                return e;
            }
        }
        return null;
    }

    int indexOf(UUID player) {
        for (int i = 0; i < entrants.size(); i++) {
            if (player.equals(entrants.get(i).player)) {
                return i;
            }
        }
        return -1;
    }

    void news(String line) {
        news.add(line);
        while (news.size() > 30) {
            news.remove(0);
        }
    }
}
