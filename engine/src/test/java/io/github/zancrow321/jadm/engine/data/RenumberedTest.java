package io.github.zancrow321.jadm.engine.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RenumberedTest {
    private static final CardDatabase CARDS = CardDatabase.loadBundled();

    @Test
    void oldPasscodesLeadToBundledCards() {
        Renumbered.bundled().forEach((old, now) -> {
            assertNull(CARDS.card(old), old + " is renumbered but still a card");
            assertNotNull(CARDS.card(now), old + " is renumbered to " + now + ", which isn't a card");
        });
    }

    @Test
    void beyondTheBraveTemporaryPasscodesMoveToTheRealOnes() {
        // Tigress in Silent Repose came as 101402082 before YGOPRODeck listed its real passcode.
        assertEquals(99630221, Renumbered.current(101402082));
        assertEquals("Tigress in Silent Repose", CARDS.name(Renumbered.current(101402082)));
        assertEquals(89631139, Renumbered.current(89631139));
    }
}
