package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.Ruleset;
import io.github.zancrow321.jadm.engine.ai.RandomResponder;
import io.github.zancrow321.jadm.engine.data.AltArts;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.text.DuelText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The boss monster each duelist marked in their deck reaches every view, by team, for the ace shot. */
class DeckBossTest {
    private static final int DARK_MAGICIAN = 46986414;
    private static final int BLUE_EYES = 89631139;
    private static final int POT_OF_GREED = 55144522;

    private static final CardDatabase CARDS = CardDatabase.loadBundled();
    private static final DuelText TEXT = DuelText.loadBundled(CARDS);

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    private static Deck withBoss(String id, int boss) {
        Deck deck = Deck.bundled(id);
        return new Deck(deck.name(), deck.main(), deck.extra(), deck.side(), boss);
    }

    private static DuelTable table(Deck yugi, Deck kaiba) {
        List<DuelTable.Seat> seats = List.of(new DuelTable.Seat(0, "Steve", null),
                new DuelTable.Seat(1, "Rex", new RandomResponder(5, CARDS)));
        return new DuelTable(TEXT, new BundledScripts(),
                DuelSettings.standard(new long[]{4, 3, 2, 1}, Ruleset.MODERN.flags()), seats, List.of(yugi, kaiba),
                (t, m) -> { });
    }

    /** A monster of Yugi's starter deck that is its own original printing. */
    private static int yugiMonster() {
        return Deck.bundled("starter_yugi").main().stream()
                .filter(code -> CARDS.card(code).is(OcgConstants.TYPE_MONSTER) && CARDS.original(code) == code)
                .findFirst().orElseThrow();
    }

    @Test
    void eachTeamGetsItsDecksBoss() {
        int yugiBoss = yugiMonster();
        try (DuelTable table = table(withBoss("starter_yugi", yugiBoss), withBoss("starter_kaiba", BLUE_EYES))) {
            assertEquals(List.of(List.of(yugiBoss), List.of(BLUE_EYES)), table.bosses());
            DuelView view = table.watchingNow();
            assertEquals(List.of(yugiBoss), view.bossesOf(0));
            assertEquals(List.of(BLUE_EYES), view.bossesOf(1));
            // It survives the trip to the client.
            assertEquals(view.bosses(), ViewCodec.decode(ViewCodec.encode(view)).bosses());
        }
    }

    @Test
    void noBossOrOneTheDeckDoesntHoldCountsAsNone() {
        try (DuelTable table = table(Deck.bundled("starter_yugi"), withBoss("starter_kaiba", POT_OF_GREED + 1))) {
            assertEquals(List.of(List.of(), List.of()), table.bosses());
            assertEquals(List.of(), table.watchingNow().bossesOf(1));
        }
    }

    @Test
    void anAltArtInTheDeckCountsAsItsBoss() {
        List<Integer> alts = AltArts.loadBundled().of(CARDS, DARK_MAGICIAN);
        assertFalse(alts.isEmpty());
        Deck yugi = Deck.bundled("starter_yugi");
        List<Integer> main = new ArrayList<>(yugi.main().subList(0, yugi.main().size() - 1));
        main.add(alts.get(0));
        // Marked with the alternate artwork, kept as the original.
        Deck deck = new Deck(yugi.name(), main, yugi.extra(), List.of(), alts.get(0));
        try (DuelTable table = table(deck, Deck.bundled("starter_kaiba"))) {
            assertEquals(List.of(DARK_MAGICIAN), table.bosses().get(0));
        }
    }

    @Test
    void aViewWithoutBossesHasNone() {
        assertEquals(List.of(), new DuelView(0, List.of("A", "B"), null, List.of(), List.of(), null, 0, null, null)
                .bossesOf(1));
    }
}
