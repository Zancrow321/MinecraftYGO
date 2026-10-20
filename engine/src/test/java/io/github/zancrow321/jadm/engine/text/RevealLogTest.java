package io.github.zancrow321.jadm.engine.text;

import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.protocol.CardRef;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.ConfirmCards;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.Move;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import io.github.zancrow321.jadm.engine.protocol.MessageType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Effects like Lilith or the Enneacraft monsters reveal cards to the opponent; the log names them for both. */
class RevealLogTest {
    private static final int TRAP_HOLE = 4206964;
    private static final int MIRROR_FORCE = 44095762;
    private static final int DIMENSIONAL_PRISON = 70342110;
    private static DuelText text;

    @BeforeAll
    static void load() {
        text = DuelText.loadBundled(CardDatabase.loadBundled());
    }

    private static String line(int viewer, DuelMessage message, Predicate<CardRef> shown) {
        return new DuelLog(text, List.of("Alex", "Steve"), viewer, loc -> null, shown)
                .describe(message);
    }

    private static CardRef inDeck(int code) {
        return new CardRef(code, new Loc(0, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE));
    }

    @Test
    void deckRevealToOpponentNamesTheCardsForBothPlayers() {
        // Duel.ConfirmCards(1-tp, g): Alex shows Steve three cards from the Deck.
        ConfirmCards reveal = new ConfirmCards(MessageType.CONFIRM_CARDS, 1,
                List.of(inDeck(TRAP_HOLE), inDeck(MIRROR_FORCE), inDeck(DIMENSIONAL_PRISON)));
        assertEquals("You revealed Trap Hole, Mirror Force, Dimensional Prison from your Deck to Steve",
                line(0, reveal, c -> false));
        assertEquals("Alex revealed Trap Hole, Mirror Force, Dimensional Prison from their Deck to you",
                line(1, reveal, c -> false));
        assertEquals("Alex revealed 3 cards from their Deck to Steve", line(-1, reveal, c -> false));
    }

    @Test
    void handRevealNamesTheCard() {
        CardRef card = new CardRef(TRAP_HOLE, new Loc(1, LOCATION_HAND, 2, POS_FACEDOWN_DEFENSE));
        ConfirmCards reveal = new ConfirmCards(MessageType.CONFIRM_CARDS, 0, List.of(card));
        assertEquals("Steve revealed Trap Hole from their hand to you", line(0, reveal, c -> false));
        assertEquals("You revealed Trap Hole from your hand to Alex", line(1, reveal, c -> false));
    }

    @Test
    void deckTopIsShownToEveryone() {
        ConfirmCards reveal = new ConfirmCards(MessageType.CONFIRM_DECKTOP, 0, List.of(inDeck(MIRROR_FORCE)));
        assertEquals("You revealed the top card of your Deck: Mirror Force", line(0, reveal, c -> false));
        assertEquals("Alex revealed the top card of their Deck: Mirror Force", line(1, reveal, c -> false));
    }

    @Test
    void cardAddedFromDeckIsNamedOnlyIfItWasShown() {
        Move add = new Move(MIRROR_FORCE, new Loc(0, LOCATION_DECK, 5, POS_FACEDOWN_DEFENSE),
                new Loc(0, LOCATION_HAND, 3, POS_FACEDOWN_DEFENSE), 0x40);
        assertEquals("Alex added Mirror Force to their hand", line(1, add, c -> c.code() == MIRROR_FORCE));
        assertEquals("Alex added a card to their hand", line(1, add, c -> false));
    }
}
