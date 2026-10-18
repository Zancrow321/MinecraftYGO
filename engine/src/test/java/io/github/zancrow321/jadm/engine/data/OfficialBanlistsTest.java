package io.github.zancrow321.jadm.engine.data;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The official lists besides the TCG history, the Genesys deck rules and reading the formats the update downloads.
 */
class OfficialBanlistsTest {
    private static final CardDatabase CARDS = CardDatabase.loadBundled();
    private static final int DECODE_TALKER = 1861629;
    private static final int ODD_EYES = 16178681;
    private static final int POT_OF_GREED = 55144522;

    @Test
    void bundlesTheOfficialListsAndTheirKonamiIds() {
        OfficialBanlists lists = OfficialBanlists.loadBundled();
        for (String id : List.of("ocg", "traditional", "worlds", "master-duel", "genesys", "genesys-ocg")) {
            assertNotNull(lists.get(id), id);
            assertTrue(lists.get(id).size() > 100, id + " has " + lists.get(id).size());
        }
        assertTrue(lists.get("genesys").genesys());
        assertFalse(lists.get("ocg").genesys());
        assertTrue(OfficialBanlists.loadKonamiIds().size() > 10_000);
        // Round trip, as the downloaded lists are saved.
        assertEquals(lists, OfficialBanlists.parse(lists.toJson()));
    }

    @Test
    void readsProjectIgnisLists() {
        OfficialBanlists.Entry e = OfficialBanlists.parseLflist("tcg", "TCG", """
                #[2026.09 TCG]
                !2026.09 TCG
                #Forbidden
                55144522 0 --Pot of Greed
                #Limited
                83764718 1 --Monster Reborn
                !2026.04 TCG
                1 2 --an older list
                """);
        assertEquals(LocalDate.of(2026, 9, 1), e.date());
        assertEquals("TCG, September 2026", e.name());
        assertEquals(Map.of(POT_OF_GREED, 0, 83764718, 1), e.limits());
        assertThrows(IllegalArgumentException.class, () -> OfficialBanlists.parseLflist("x", "X", "#nothing"));
    }

    @Test
    void readsKonamiIdListsWithAlternateArtworks() {
        Map<Integer, List<Integer>> ids = Map.of(4007, List.of(89631139, 89631140), 5000, List.of(POT_OF_GREED));
        OfficialBanlists.Entry genesys = OfficialBanlists.parseVector("genesys", "Genesys", true,
                "{\"date\": \"2026-10-18\", \"regulation\": {\"4007\": 33, \"5000\": 100, \"99999\": 5}}", ids);
        assertEquals("Genesys, October 18, 2026", genesys.name());
        assertEquals(Map.of(89631139, 33, 89631140, 33, POT_OF_GREED, 100), genesys.points());
        OfficialBanlists.Entry md = OfficialBanlists.parseVector("master-duel", "Master Duel", false,
                "{\"date\": \"2026-10-06\", \"regulation\": {\"5000\": 0}}", ids);
        assertEquals("Master Duel, October 2026", md.name());
        assertEquals(Map.of(POT_OF_GREED, 0), md.limits());
    }

    @Test
    void newerListsReplaceOlderOnes() {
        OfficialBanlists lists = OfficialBanlists.loadBundled();
        OfficialBanlists.Entry newer = new OfficialBanlists.Entry("ocg", "OCG, January 2099", LocalDate.of(2099, 1, 1),
                Map.of(POT_OF_GREED, 0), null);
        OfficialBanlists merged = lists.with(new OfficialBanlists(List.of(newer)));
        assertEquals(newer, merged.get("ocg"));
        assertEquals(lists.lists().size(), merged.lists().size());

        Banlists history = Banlists.loadBundled();
        Banlist latest = new Banlist("TCG, January 2099", Map.of());
        assertSame(latest, history.withNewer(new Banlists.Dated(LocalDate.of(2099, 1, 1), latest)).latest());
        assertSame(history, history.withNewer(new Banlists.Dated(LocalDate.of(2000, 1, 1), latest)));
    }

    @Test
    void genesysKeepsLinkAndPendulumMonstersOutAndCountsPoints() {
        Banlist genesys = OfficialBanlists.loadBundled().get("genesys").banlist(CARDS, Banlist.GENESYS_POINT_CAP);
        assertTrue(genesys.genesys());
        assertEquals(0, genesys.limit(DECODE_TALKER));
        assertEquals(0, genesys.limit(ODD_EYES));
        assertEquals(Banlist.MAX_COPIES, genesys.limit(89631139));

        Deck yugi = Deck.bundled("starter_yugi");
        assertTrue(DeckRules.points(yugi, genesys) > 0, "Yugi's deck has pointed classics");
        Deck base = new Deck("t", yugi.main().stream().filter(c -> genesys.points(c) == 0).toList(), yugi.extra(),
                List.of());
        assertTrue(DeckRules.problems(base, CARDS, genesys).isEmpty(), DeckRules.problems(base, CARDS, genesys)
                .toString());
        List<Integer> extra = new ArrayList<>(base.extra());
        extra.add(DECODE_TALKER);
        List<String> problems = DeckRules.problems(new Deck("t", base.main(), extra, List.of()), CARDS, genesys);
        assertEquals(List.of("Decode Talker: no Link or Pendulum monsters in Genesys."), problems);

        // The costliest pointed cards in the side deck go over the cap.
        List<Integer> pointed = genesys.points().keySet().stream().filter(c -> CARDS.card(c) != null)
                .sorted(Comparator.comparingInt((Integer c) -> genesys.points(c)).reversed()).limit(2).toList();
        Deck expensive = new Deck("t", base.main(), base.extra(), pointed);
        int points = DeckRules.points(expensive, genesys);
        assertTrue(points > Banlist.GENESYS_POINT_CAP, "points " + points);
        assertEquals(List.of("The deck costs " + points + " Genesys points (at most 100)."),
                DeckRules.problems(expensive, CARDS, genesys));
        assertTrue(DeckRules.problems(expensive, CARDS, genesys.withPointCap(points)).isEmpty());
        // A Forbidden & Limited List has no points.
        assertEquals(0, DeckRules.points(expensive, Banlists.loadBundled().latest()));
    }

    @Test
    void builtAndTournamentDecksAreLegalUnderGenesys() {
        Banlist genesys = OfficialBanlists.loadBundled().get("genesys").banlist(CARDS, Banlist.GENESYS_POINT_CAP);
        CardPool everything = CardPool.everything(CARDS, CardPool.loadBundled().models());
        for (long seed = 1; seed <= 20; seed++) {
            Deck deck = DeckBuilder.random("npc", seed, CARDS, everything, genesys);
            assertTrue(DeckRules.problems(deck, CARDS, genesys).isEmpty(),
                    seed + ": " + DeckRules.problems(deck, CARDS, genesys));
        }
        int legal = 0;
        for (TournamentDecks.Entry entry : TournamentDecks.loadBundled(CARDS).decks()) {
            Deck deck = entry.legal(genesys, CARDS);
            if (deck != null) {
                legal++;
                assertTrue(DeckRules.problems(deck, CARDS, genesys).isEmpty(), entry.name());
            }
        }
        assertTrue(legal > 0, "no tournament deck works under Genesys");
    }
}
