package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.Ruleset;
import io.github.zancrow321.jadm.engine.ai.RandomResponder;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.text.DuelText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * While the winner of the coin toss chooses, the duel shows its decks and waits; going second is the same duel built
 * again with the teams traded, so the other team takes the first turn.
 */
class CoinTossChoiceTest {
    private static final CardDatabase CARDS = CardDatabase.loadBundled();
    private static final DuelText TEXT = DuelText.loadBundled(CARDS);

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @Test
    void waitingShowsTheDecksAndNothingDrawn() {
        try (DuelTable table = table(0)) {
            DuelView view = table.waiting().get(0);
            assertNotNull(view);
            assertEquals(0, view.you());
            assertNull(view.prompt());
            assertTrue(view.board().side(0).hand().isEmpty());
            assertEquals(Deck.bundled("starter_yugi").main().size(), view.board().side(0).deckCount());
            assertEquals(-1, table.waitingFor(), "nobody answers before the duel starts");
        }
    }

    @Test
    void goingFirstStartsWithYourTurn() {
        try (DuelTable table = table(0)) {
            table.waiting();
            DuelView view = table.start().get(0);
            assertEquals(0, view.you());
            assertEquals(0, view.board().turnPlayer());
            assertNotNull(view.prompt(), "you have your first turn's prompt");
        }
    }

    @Test
    void goingSecondGivesTheOpponentTheFirstTurn() {
        try (DuelTable table = table(1)) {
            DuelView view = table.start().get(0);
            assertEquals(1, view.you());
            assertTrue(view.board().turn() >= 2, "the bot played turn 1, now it's " + view.board().turn());
            assertEquals(1, view.board().turnPlayer());
            assertEquals(6, view.board().side(1).hand().size(), "you drew for your first turn");
        }
    }

    /** You (a person, seat 0) on team {@code yourTeam} against a bot. */
    private static DuelTable table(int yourTeam) {
        List<DuelTable.Seat> seats = List.of(new DuelTable.Seat(yourTeam, "Steve", null),
                new DuelTable.Seat(1 - yourTeam, "Rex", new RandomResponder(5, CARDS)));
        return new DuelTable(TEXT, new BundledScripts(),
                DuelSettings.standard(new long[]{4, 3, 2, 1}, Ruleset.MODERN.flags()), seats,
                List.of(Deck.bundled("starter_yugi"), Deck.bundled("starter_kaiba")), (t, m) -> { });
    }
}
