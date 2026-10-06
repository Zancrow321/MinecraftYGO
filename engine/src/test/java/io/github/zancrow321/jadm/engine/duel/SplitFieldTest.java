package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.Ruleset;
import io.github.zancrow321.jadm.engine.ai.DuelistAi;
import io.github.zancrow321.jadm.engine.ai.Responder;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.text.DuelText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Battle City style tag duels: each partner places cards on their own half of the team's zones. */
class SplitFieldTest {
    /** Bits of a duelist's own zones (monster and spell/trap columns) in a SelectPlace mask. */
    private static int zones(int duelist) {
        int mask = 0;
        for (int seq = 0; seq < 5; seq++) {
            if (DuelTable.zoneOwner(seq) == duelist) {
                mask |= 1 << seq | 1 << (8 + seq);
            }
        }
        return mask;
    }

    @Test
    void placementOffersOnlyYourHalfAndTheMiddle() {
        var open = new DuelMessage.SelectPlace(0, 1, 0, false);
        assertEquals(zones(1), DuelTable.restrict(open, 0).blockedZones() & zones(1));
        assertEquals(0, DuelTable.restrict(open, 0).blockedZones() & zones(0));
        assertEquals(zones(0), DuelTable.restrict(open, 1).blockedZones() & zones(0));
        // Only the partner's zones are free: the prompt stays as it is rather than offering nothing.
        int allButPartner = ~zones(1);
        var cornered = new DuelMessage.SelectPlace(0, 1, allButPartner, false);
        assertEquals(allButPartner, DuelTable.restrict(cornered, 0).blockedZones());
    }

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @Test
    void fourBotsFinishASplitFieldDuel() {
        CardDatabase cards = CardDatabase.loadBundled();
        DuelText text = DuelText.loadBundled(cards);
        int[] restricted = new int[1];
        List<DuelTable.Seat> seats = List.of(
                new DuelTable.Seat(0, "Yugi", checking(0, new DuelistAi(1, cards), restricted)),
                new DuelTable.Seat(0, "Kaiba", checking(1, new DuelistAi(2, cards), restricted)),
                new DuelTable.Seat(1, "Marik", checking(0, new DuelistAi(3, cards), restricted)),
                new DuelTable.Seat(1, "Odion", checking(1, new DuelistAi(4, cards), restricted)));
        List<Deck> decks = List.of(Deck.bundled("starter_yugi"), Deck.bundled("starter_kaiba"),
                Deck.bundled("starter_kaiba"), Deck.bundled("starter_yugi"));
        try (DuelTable table = new DuelTable(text, new BundledScripts(),
                DuelSettings.standard(new long[]{4, 3, 2, 1}, Ruleset.MR1.flags()), seats, decks, (t, m) -> { })
                .splitField(true)) {
            table.start();
            for (int calls = 0; !table.finished(); calls++) {
                if (calls > 2_000) {
                    fail("split field duel did not finish");
                }
                table.pump();
            }
        }
        assertTrue(restricted[0] > 0, "some placements were restricted to a half");
    }

    /** Fails if a duelist is offered their partner's zones while their own half or the middle is free. */
    private static Responder checking(int duelist, Responder inner, int[] restricted) {
        return (prompt, board, attempt) -> {
            if (prompt instanceof DuelMessage.SelectPlace place && !place.disableField()) {
                int partner = zones(1 - duelist);
                int ownFree = Integer.bitCount(~place.blockedZones() & 0x1F7F1F7F & ~partner);
                if (ownFree >= place.count()) {
                    assertEquals(partner, place.blockedZones() & partner, "partner zones offered");
                    restricted[0]++;
                }
            }
            return inner.respond(prompt, board, attempt);
        };
    }
}
