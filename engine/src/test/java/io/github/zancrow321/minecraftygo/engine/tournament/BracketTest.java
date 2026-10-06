package io.github.zancrow321.minecraftygo.engine.tournament;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BracketTest {
    private static final Gson GSON = new Gson();

    @Test
    void seedOrderKeepsTopSeedsApart() {
        assertArrayEquals(new int[]{0, 7, 3, 4, 1, 6, 2, 5}, Bracket.seedOrder(8));
    }

    /** Every format finishes for every field size, with one champion and each duelist in one place. */
    @ParameterizedTest
    @EnumSource(Bracket.Format.class)
    void everyFormatFinishes(Bracket.Format format) {
        for (int n = 2; n <= 33; n++) {
            for (int bestOf : new int[]{1, 3}) {
                Random random = new Random(n * 31L + bestOf);
                Bracket bracket = Bracket.create(format, n, bestOf, 0, format == Bracket.Format.SWISS ? 4 : 0,
                        n % 2 == 0, true);
                int steps = play(bracket, random, 0.1);
                assertTrue(bracket.finished(), format + " with " + n);
                assertTrue(steps < 10_000);
                List<Bracket.Placement> places = bracket.placements();
                assertEquals(n, places.size());
                assertEquals(1, places.get(0).place());
                assertEquals(1, places.stream().filter(p -> p.place() == 1).count(), format + " with " + n);
                Set<Integer> seen = new HashSet<>();
                places.forEach(p -> assertTrue(seen.add(p.entrant())));
            }
        }
    }

    /** Plays random results; nobody is ever in two playable matches at once. */
    private static int play(Bracket bracket, Random random, double drawChance) {
        int steps = 0;
        while (!bracket.finished()) {
            List<Bracket.Match> open = bracket.playable();
            assertFalse(open.isEmpty(), "stuck");
            Set<Integer> busy = new HashSet<>();
            for (Bracket.Match m : open) {
                assertTrue(busy.add(m.a) && busy.add(m.b), "someone plays twice at once");
            }
            Bracket.Match m = open.get(random.nextInt(open.size()));
            bracket.reportGame(m.id, random.nextDouble() < drawChance ? 2 : random.nextInt(2));
            steps++;
            // Saving and loading at any point keeps the tournament as it was.
            if (steps % 7 == 0) {
                Bracket copy = GSON.fromJson(GSON.toJson(bracket), Bracket.class);
                assertEquals(GSON.toJson(bracket), GSON.toJson(copy));
            }
        }
        return steps;
    }

    @Test
    void roundRobinPairsEveryoneOnce() {
        for (int n = 2; n <= 12; n++) {
            Bracket bracket = Bracket.create(Bracket.Format.ROUND_ROBIN, n, 1, 0, 0, false, true);
            play(bracket, new Random(n), 0);
            Map<Long, Integer> met = new HashMap<>();
            for (Bracket.Match m : bracket.matches) {
                if (m.a >= 0 && m.b >= 0) {
                    met.merge(Math.min(m.a, m.b) * 100L + Math.max(m.a, m.b), 1, Integer::sum);
                }
            }
            assertEquals(n * (n - 1) / 2, met.size());
            assertTrue(met.values().stream().allMatch(c -> c == 1));
        }
    }

    @Test
    void swissAvoidsRematches() {
        Bracket bracket = Bracket.create(Bracket.Format.SWISS, 16, 1, 0, 0, false, true);
        play(bracket, new Random(3), 0);
        assertEquals(4, bracket.round);
        Set<Long> met = new HashSet<>();
        for (Bracket.Match m : bracket.matches) {
            assertTrue(met.add(Math.min(m.a, m.b) * 100L + Math.max(m.a, m.b)), "rematch");
        }
        // Four rounds for sixteen: exactly one duelist wins them all.
        assertEquals(1, bracket.standings().stream().filter(s -> s.wins == 4).count());
    }

    @Test
    void swissTopCutPlacesTheCutFirst() {
        Bracket bracket = Bracket.create(Bracket.Format.SWISS, 10, 1, 3, 4, false, true);
        play(bracket, new Random(9), 0);
        List<Bracket.Placement> places = bracket.placements();
        Bracket.Match fin = bracket.matches.get(bracket.matches.size() - 1);
        assertEquals("T", fin.stage);
        assertEquals(fin.winner, places.get(0).entrant());
        assertEquals(5, places.get(4).place());
    }

    @Test
    void singleEliminationGivesTopSeedsByes() {
        Bracket bracket = Bracket.create(Bracket.Format.SINGLE, 5, 1, 0, 0, false, true);
        // 8-bracket: seeds 1-3 get byes and 4 plays 5 in the first round; seeds 2 and 3 meet straight away.
        List<Bracket.Match> open = bracket.playable();
        assertEquals(2, open.size());
        assertEquals(Set.of(3, 4), Set.of(open.get(0).a, open.get(0).b));
        assertEquals(1, open.get(0).round);
        assertEquals(Set.of(1, 2), Set.of(open.get(1).a, open.get(1).b));
        assertEquals(2, open.get(1).round);
    }

    @Test
    void doubleEliminationChampionLosesAtMostOnceBeforeTheReset() {
        for (int n = 3; n <= 20; n++) {
            Bracket bracket = Bracket.create(Bracket.Format.DOUBLE, n, 1, 0, 0, false, true);
            play(bracket, new Random(n), 0);
            Map<Integer, Integer> losses = new HashMap<>();
            for (Bracket.Match m : bracket.matches) {
                if (!m.walkover && m.loser >= 0) {
                    losses.merge(m.loser, 1, Integer::sum);
                }
            }
            int champion = bracket.placements().get(0).entrant();
            assertTrue(losses.getOrDefault(champion, 0) <= 1);
            for (int e = 0; e < n; e++) {
                assertTrue(losses.getOrDefault(e, 0) <= 2, "lost three times");
            }
        }
    }

    @Test
    void droppingOutHandsTheMatchOver() {
        Bracket bracket = Bracket.create(Bracket.Format.SINGLE, 4, 3, 0, 0, false, true);
        Bracket.Match first = bracket.playable().get(0);
        bracket.reportGame(first.id, 0);
        bracket.drop(first.a);
        assertEquals(first.b, bracket.match(first.id).winner);
        assertTrue(bracket.match(first.id).walkover);
    }

    @Test
    void bestOfThreeNeedsTwoWins() {
        Bracket bracket = Bracket.create(Bracket.Format.SINGLE, 2, 3, 0, 0, false, true);
        Bracket.Match m = bracket.playable().get(0);
        bracket.reportGame(m.id, 0);
        assertFalse(m.decided());
        bracket.reportGame(m.id, 1);
        bracket.reportGame(m.id, 2);
        assertFalse(m.decided(), "a drawn game doesn't decide an elimination match");
        bracket.reportGame(m.id, 1);
        assertEquals(m.b, m.winner);
        assertTrue(bracket.finished());
        List<Bracket.Placement> places = new ArrayList<>(bracket.placements());
        assertEquals(m.b, places.get(0).entrant());
    }
}
