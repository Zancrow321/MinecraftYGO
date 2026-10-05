package io.github.zancrow321.minecraftygo.engine.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionDataTest {
    @Test
    void boosterSetsOnlyHoldPoolCards() {
        CardPool pool = CardPool.loadBundled();
        Set<Integer> codes = new HashSet<>(pool.monsters());
        codes.addAll(pool.spellsTraps());
        BoosterSets sets = BoosterSets.loadBundled();
        assertNotNull(sets.get("legend_of_blue_eyes_white_dragon"));
        for (BoosterSets.BoosterSet set : sets.sets().values()) {
            assertFalse(set.of(BoosterSets.Rarity.COMMON).isEmpty(), set.id());
            for (BoosterSets.Card card : set.cards()) {
                assertTrue(codes.contains(card.code()), set.id() + " card " + card.code());
            }
        }
    }

    @Test
    void packsHaveEightCommonsAndOneFoil() {
        BoosterSets.BoosterSet lob = BoosterSets.loadBundled().get("legend_of_blue_eyes_white_dragon");
        Random random = new Random(7);
        int ultras = 0;
        for (int i = 0; i < 1200; i++) {
            List<BoosterSets.Card> pack = lob.open(random);
            assertEquals(BoosterSets.PACK_SIZE, pack.size());
            for (int k = 0; k < 8; k++) {
                assertEquals(BoosterSets.Rarity.COMMON, pack.get(k).rarity());
            }
            assertTrue(pack.get(8).rarity() != BoosterSets.Rarity.COMMON);
            if (pack.get(8).rarity() == BoosterSets.Rarity.ULTRA) {
                ultras++;
            }
        }
        assertTrue(ultras > 60 && ultras < 140, "about 1 in 12 ultras, got " + ultras);
    }

    @Test
    void starterDecksAreLegalAndLimitsAreEnforced() {
        CardDatabase cards = CardDatabase.loadBundled();
        Banlist banlist = Banlist.loadBundled();
        for (String id : List.of("starter_yugi", "starter_kaiba")) {
            assertEquals(List.of(), DeckRules.problems(Deck.bundled(id), cards, banlist), id);
        }
        int raigeki = 12580477;
        assertEquals(1, banlist.limit(raigeki));
        List<Integer> main = new ArrayList<>(Deck.bundled("starter_yugi").main().subList(0, 38));
        main.add(raigeki);
        main.add(raigeki);
        List<String> problems = DeckRules.problems(new Deck("x", main, List.of(), List.of()), cards, banlist);
        assertTrue(problems.stream().anyMatch(p -> p.contains("Raigeki")), problems.toString());
        problems = DeckRules.problems(new Deck("x", main.subList(0, 20), List.of(), List.of()), cards, banlist);
        assertTrue(problems.get(0).contains("40 to 60"), problems.toString());
    }
}
