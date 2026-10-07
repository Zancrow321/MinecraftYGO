package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.DuelLogHandler;
import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.Ruleset;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The deck is shuffled before the opening hands are drawn, so the hand depends on the duel's seed. */
class OpeningHandTest {
    private static final CardDatabase CARDS = CardDatabase.loadBundled();

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @Test
    void openingHandChangesWithTheSeed() {
        Set<List<Integer>> hands = new HashSet<>();
        for (long seed = 1; seed <= 10; seed++) {
            hands.add(openingHand(seed));
        }
        assertTrue(hands.size() >= 8, "only " + hands.size() + " different opening hands in 10 duels: " + hands);
    }

    @Test
    void sameSeedGivesTheSameHand() {
        assertEquals(openingHand(42), openingHand(42));
    }

    /** Player 0's opening hand, in draw order. */
    private static List<Integer> openingHand(long seed) {
        DuelSettings settings = DuelSettings.standard(new long[]{seed, seed * 31, seed * 17, 99}, Ruleset.MODERN.flags());
        DuelLogHandler log = (type, message) -> { };
        try (DuelController duel = new DuelController(CARDS, new BundledScripts(), settings,
                Deck.bundled("starter_yugi"), Deck.bundled("starter_kaiba"), log)) {
            List<Integer> hand = new ArrayList<>();
            for (DuelMessage message : duel.advance().messages()) {
                if (message instanceof DuelMessage.Draw draw && draw.player() == 0) {
                    hand.addAll(draw.codes());
                }
            }
            assertEquals(5, hand.size(), "opening hand size");
            return hand;
        }
    }
}
