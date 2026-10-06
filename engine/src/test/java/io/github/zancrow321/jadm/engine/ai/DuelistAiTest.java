package io.github.zancrow321.jadm.engine.ai;

import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.duel.DuelTable;
import io.github.zancrow321.jadm.engine.text.DuelText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The heuristic duelist should clearly beat the random bot that stood in for it, with either starter deck.
 */
class DuelistAiTest {
    private static final int DUELS = 20;

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @Test
    void beatsTheRandomBotMostOfTheTime() {
        CardDatabase cards = CardDatabase.loadBundled();
        DuelText text = DuelText.loadBundled(cards);
        int wins = 0;
        for (int i = 0; i < DUELS; i++) {
            // Alternate who goes first and which deck the AI plays.
            boolean aiFirst = i % 2 == 0;
            String aiDeck = (i / 2) % 2 == 0 ? "starter_kaiba" : "starter_yugi";
            String otherDeck = aiDeck.equals("starter_kaiba") ? "starter_yugi" : "starter_kaiba";
            Responder ai = new DuelistAi(i, cards);
            Responder random = new RandomResponder(1000 + i, cards);
            Responder[] bots = aiFirst ? new Responder[]{ai, random} : new Responder[]{random, ai};
            Deck[] decks = aiFirst ? new Deck[]{Deck.bundled(aiDeck), Deck.bundled(otherDeck)}
                    : new Deck[]{Deck.bundled(otherDeck), Deck.bundled(aiDeck)};
            try (DuelTable table = new DuelTable(text, new BundledScripts(),
                    DuelSettings.standard(new long[]{i + 1, 3, 5, 7}, OcgConstants.DUEL_MODE_MR1), decks[0], decks[1],
                    List.of("A", "B"), bots, (t, m) -> { })) {
                table.start();
                for (int pumps = 0; !table.finished(); pumps++) {
                    if (pumps > 10_000) {
                        fail("duel " + i + " did not finish");
                    }
                    table.pump();
                }
                if (table.winner() == (aiFirst ? 0 : 1)) {
                    wins++;
                }
            }
        }
        System.out.println("DuelistAi won " + wins + " of " + DUELS + " against the random bot");
        assertTrue(wins >= DUELS * 7 / 10, "won only " + wins + " of " + DUELS);
    }
}
