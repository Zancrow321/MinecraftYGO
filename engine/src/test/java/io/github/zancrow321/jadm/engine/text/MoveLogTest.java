package io.github.zancrow321.jadm.engine.text;

import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.Move;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.Summoning;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import io.github.zancrow321.jadm.engine.protocol.MessageType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Cards Set from the Deck, and cards added to the hand from the GY or banishment, are named where that's allowed. */
class MoveLogTest {
    private static final int MIRROR_FORCE = 44095762;
    private static final int MONSTER_REBORN = 83764718;
    private static DuelText text;

    @BeforeAll
    static void load() {
        text = DuelText.loadBundled(CardDatabase.loadBundled());
    }

    private static String line(int viewer, DuelMessage message, Move lastMove) {
        return new DuelLog(text, List.of("Alex", "Steve"), viewer, loc -> null, c -> false, lastMove)
                .describe(message);
    }

    private static final Loc SET_ZONE = new Loc(0, LOCATION_SZONE, 1, POS_FACEDOWN_DEFENSE);

    @Test
    void cardSetFromDeckIsNamedForItsOwnerOnly() {
        Move move = new Move(MIRROR_FORCE, new Loc(0, LOCATION_DECK, 12, POS_FACEDOWN_DEFENSE), SET_ZONE, 0x40);
        DuelMessage.Set set = new DuelMessage.Set(MIRROR_FORCE, SET_ZONE);
        assertEquals("You Set Mirror Force from your Deck in the Spell & Trap Zone", line(0, set, move));
        assertEquals("Alex Set a card from their Deck in the Spell & Trap Zone", line(1, set, move));
        assertEquals("Alex Set a card from their Deck in the Spell & Trap Zone", line(-1, set, move));
    }

    @Test
    void cardSetFromGraveyardIsNamedForEveryone() {
        Move move = new Move(MONSTER_REBORN, new Loc(0, LOCATION_GRAVE, 3, POS_FACEUP_ATTACK), SET_ZONE, 0x40);
        DuelMessage.Set set = new DuelMessage.Set(MONSTER_REBORN, SET_ZONE);
        assertEquals("Alex Set Monster Reborn from their GY in the Spell & Trap Zone", line(1, set, move));
    }

    @Test
    void cardSetFromHandIsNamedForItsOwner() {
        Move move = new Move(MIRROR_FORCE, new Loc(0, LOCATION_HAND, 2, POS_FACEDOWN_DEFENSE), SET_ZONE, 0x40);
        DuelMessage.Set set = new DuelMessage.Set(MIRROR_FORCE, SET_ZONE);
        assertEquals("You Set Mirror Force in the Spell & Trap Zone", line(0, set, move));
        assertEquals("Alex Set a card in the Spell & Trap Zone", line(1, set, move));
    }

    @Test
    void cardAddedFromGraveyardIsNamedForEveryone() {
        Move add = new Move(MONSTER_REBORN, new Loc(0, LOCATION_GRAVE, 3, POS_FACEUP_ATTACK),
                new Loc(0, LOCATION_HAND, 4, POS_FACEDOWN_DEFENSE), 0x40);
        assertEquals("You added Monster Reborn from your GY to your hand", line(0, add, null));
        assertEquals("Alex added Monster Reborn from their GY to their hand", line(1, add, null));
        assertEquals("Alex added Monster Reborn from their GY to their hand", line(-1, add, null));
    }

    @Test
    void cardAddedFromBanishmentIsNamedIfItWasFaceUp() {
        Loc hand = new Loc(1, LOCATION_HAND, 4, POS_FACEDOWN_DEFENSE);
        Move faceUp = new Move(MONSTER_REBORN, new Loc(1, LOCATION_REMOVED, 0, POS_FACEUP_ATTACK), hand, 0x40);
        assertEquals("Steve added Monster Reborn from banishment to their hand", line(0, faceUp, null));
        Move faceDown = new Move(MONSTER_REBORN, new Loc(1, LOCATION_REMOVED, 0, POS_FACEDOWN_DEFENSE), hand, 0x40);
        assertEquals("Steve added a card from banishment to their hand", line(0, faceDown, null));
        assertEquals("You added Monster Reborn from banishment to your hand", line(1, faceDown, null));
    }

    @Test
    void faceDownSummonStaysSecretFromTheOpponent() {
        Summoning summon = new Summoning(MessageType.SPSUMMONING, MONSTER_REBORN,
                new Loc(0, LOCATION_MZONE, 2, POS_FACEDOWN_DEFENSE));
        assertEquals("You Special Summoned Monster Reborn face-down", line(0, summon, null));
        assertEquals("Alex Special Summoned a card face-down", line(1, summon, null));
    }
}
