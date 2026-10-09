package io.github.zancrow321.jadm.engine.tournament;

import com.google.gson.Gson;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.data.DeckRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftTest {
    private static final CardDatabase CARDS = CardDatabase.loadBundled();

    /** Packs of distinct made-up codes: seat s, round r, card i is r * 100 + s * 10 + i. */
    private static List<List<Draft.Card>> packs(int seats, int round, int size) {
        List<List<Draft.Card>> out = new ArrayList<>();
        for (int s = 0; s < seats; s++) {
            List<Draft.Card> pack = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                pack.add(new Draft.Card(round * 100 + s * 10 + i, "common"));
            }
            out.add(pack);
        }
        return out;
    }

    @Test
    void everyCardEndsUpInExactlyOnePool() {
        int seats = 3;
        int size = 4;
        Draft draft = new Draft(seats, 2);
        Gson gson = new Gson();
        while (!draft.finished()) {
            draft.open(packs(seats, draft.round, size));
            boolean over = false;
            while (!over) {
                for (int s = 0; s < seats; s++) {
                    assertTrue(draft.waitingFor(s));
                    assertTrue(draft.choose(s, 0));
                    assertFalse(draft.choose(s, 0), "picked twice");
                }
                assertTrue(draft.everyoneChose());
                // A restart in the middle of a draft reads it back from JSON.
                draft = gson.fromJson(gson.toJson(draft), Draft.class);
                over = draft.pass();
            }
        }
        Set<Integer> seen = new HashSet<>();
        for (List<Draft.Card> pool : draft.picked) {
            assertEquals(2 * size, pool.size());
            pool.forEach(c -> assertTrue(seen.add(c.code)));
        }
        assertEquals(2 * seats * size, seen.size());
    }

    @Test
    void packsGoAroundOneWayThenTheOther() {
        Draft draft = new Draft(3, 2);
        draft.open(packs(3, 0, 3));
        for (int s = 0; s < 3; s++) {
            draft.choose(s, 0);
        }
        draft.pass();
        // Seat 1 now holds what is left of seat 0's pack.
        assertEquals(1, draft.packs.get(1).get(0).code);
        assertEquals(0, draft.picked.get(0).get(0).code);
        while (!draft.pass()) {
            for (int s = 0; s < 3; s++) {
                draft.choose(s, 0);
            }
        }
        draft.open(packs(3, 1, 3));
        for (int s = 0; s < 3; s++) {
            draft.choose(s, 0);
        }
        draft.pass();
        // The second round passes back: seat 2 holds seat 0's pack.
        assertEquals(101, draft.packs.get(2).get(0).code);
    }

    @Test
    void botsDraftPlayableDecks() {
        BoosterSets sets = BoosterSets.loadBundled();
        BoosterSets.BoosterSet set = sets.sets().values().iterator().next();
        Random random = new Random(7);
        int seats = 4;
        Draft draft = new Draft(seats, 5);
        while (!draft.finished()) {
            List<List<Draft.Card>> opened = new ArrayList<>();
            for (int s = 0; s < seats; s++) {
                opened.add(set.open(random).stream().map(c -> new Draft.Card(c.code(), c.rarity().id())).toList());
            }
            draft.open(opened);
            do {
                for (int s = 0; s < seats; s++) {
                    draft.choose(s, Draft.botPick(draft.packs.get(s), CARDS, random));
                }
            } while (!draft.pass());
        }
        for (List<Draft.Card> picks : draft.picked) {
            List<Integer> pool = picks.stream().map(c -> c.code).toList();
            assertEquals(5 * set.profile().size(), pool.size());
            Deck deck = LimitedDecks.build("Bot", pool, CARDS, 20);
            assertTrue(deck.main().size() >= 20, "deck of " + deck.main().size());
            assertTrue(deck.extra().size() <= DeckRules.EXTRA_MAX);
            assertEquals(List.of(), LimitedDecks.problems(deck.main(), deck.extra(), pool, CARDS, 20));
        }
    }

    @Test
    void problemsCatchCardsNotInThePool() {
        List<Integer> pool = List.of(89631139, 89631139);
        List<Integer> main = new ArrayList<>(List.of(89631139, 89631139, 89631139));
        assertFalse(LimitedDecks.problems(main, List.of(), pool, CARDS, 3).isEmpty());
        main.remove(0);
        assertEquals(List.of(), LimitedDecks.problems(main, List.of(), pool, CARDS, 2));
        assertEquals(1, LimitedDecks.problems(main, List.of(), pool, CARDS, 20).size());
    }
}
