package io.github.zancrow321.jadm.engine.data;

import io.github.zancrow321.jadm.engine.Ruleset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionTest {
    private static CardDatabase cards;
    private static Progression progression;

    @BeforeAll
    static void load() {
        cards = CardDatabase.loadBundled();
        progression = Progression.loadBundled(cards, CardPool.loadBundled());
    }

    @Test
    void startsWithLegendOfBlueEyes() {
        int lob = progression.find("LOB");
        assertEquals("legend_of_blue_eyes_white_dragon", progression.product(lob).id());
        CardPool pool = progression.pool(lob);
        assertTrue(pool.contains(89631139), "Blue-Eyes White Dragon");
        assertTrue(pool.contains(46986414), "Dark Magician, whose LOB print is listed under another artwork");
        assertTrue(pool.contains(46986420), "that artwork");
        assertFalse(pool.contains(77585513), "Jinzo (Pharaoh's Servant)");
        assertFalse(pool.contains(44508094), "Stardust Dragon");
        assertTrue(Deck.bundled("starter_kaiba").main().stream().allMatch(pool::contains), "starter decks");
        assertEquals(Ruleset.MR1, progression.ruleset(lob));
        assertEquals("TCG, May 2001", progression.banlist(lob).name());
    }

    @Test
    void nextSkipsReprintsAndUntilTakesCodesAndDates() {
        int mrl = progression.find("MRL");
        assertEquals("magic_ruler", progression.product(mrl).id());
        int next = progression.next(mrl, 1);
        assertFalse(progression.product(next).newCards().isEmpty());
        for (int i = mrl + 1; i < next; i++) {
            assertTrue(progression.product(i).newCards().isEmpty(), progression.product(i).id());
        }
        assertEquals(progression.next(next, 2), progression.next(mrl, 3));
        int byDate = progression.find("2002-09-16");
        assertFalse(progression.date(byDate).isAfter(LocalDate.of(2002, 9, 16)));
        assertTrue(byDate >= mrl);
        assertEquals(-1, progression.find("NOPE"));
        assertEquals(progression.last(), progression.next(progression.last(), 5));
    }

    @Test
    void cardsUnlockWithTheirFirstProduct() {
        int pgd = progression.find("PGD");
        assertTrue(progression.unlocked(77585513, pgd), "Jinzo");
        assertFalse(progression.unlocked(77585513, progression.find("MRL")));
        // A card never printed in the TCG unlocks on its OCG date.
        Map.Entry<Integer, LocalDate> ocgOnly = progression.products().ocgOnly().entrySet().stream()
                .filter(e -> e.getValue().isAfter(LocalDate.of(2005, 1, 1))).findFirst().orElseThrow();
        int before = progression.find(ocgOnly.getValue().minusDays(40).toString());
        int after = progression.find(ocgOnly.getValue().plusDays(40).toString());
        assertFalse(progression.unlocked(ocgOnly.getKey(), before), "#" + ocgOnly.getKey());
        assertTrue(progression.unlocked(ocgOnly.getKey(), after), "#" + ocgOnly.getKey());
        CardPool last = progression.pool(progression.last());
        assertEquals(CardPool.of(PoolMode.ALL, cards).playable(), last.playable());
    }

    @Test
    void rulesAndBanlistsFollowTheDate() {
        assertEquals(Ruleset.MR1, progression.ruleset(progression.find("2009-01-01")));
        assertEquals(Ruleset.MR2, progression.ruleset(progression.find("2013-01-01")));
        assertEquals(Ruleset.MR3, progression.ruleset(progression.find("2016-01-01")));
        assertEquals(Ruleset.MR4, progression.ruleset(progression.find("2019-01-01")));
        assertEquals(Ruleset.MODERN, progression.ruleset(progression.find("2021-01-01")));
        assertEquals("TCG, March 2005", progression.banlist(progression.find("2005-06-01")).name());
        // Change of Heart, forbidden in the TCG since 2004 (and again later)
        assertEquals(0, progression.banlist(progression.find("2005-06-01")).limit(4031928));
    }

    @Test
    void stepsHaveBoostersAndLegalNpcDecks() {
        int mrd = progression.find("MRD");
        BoosterSets sets = progression.sets(mrd);
        assertTrue(sets.sets().containsKey("legend_of_blue_eyes_white_dragon"));
        assertTrue(sets.sets().containsKey("metal_raiders"));
        assertFalse(sets.sets().containsKey("magic_ruler"));
        for (int step : List.of(mrd, progression.find("2010-01-01"), progression.find("2018-01-01"))) {
            CardPool pool = progression.pool(step);
            Banlist banlist = progression.banlist(step);
            for (long seed = 0; seed < 10; seed++) {
                Deck deck = DeckBuilder.random("npc", seed, cards, pool, banlist);
                assertEquals(List.of(), DeckRules.problems(deck, cards, banlist, pool::contains),
                        progression.product(step).code() + " seed " + seed);
            }
        }
        BoosterSets.BoosterSet lob = sets.get("legend_of_blue_eyes_white_dragon");
        assertFalse(lob.open(new Random(1)).isEmpty());
    }
}
