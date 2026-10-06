package io.github.zancrow321.jadm.engine.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionDataTest {
    @Test
    void boosterSetsOnlyHoldPoolCards() {
        CardPool pool = CardPool.loadBundled();
        Set<Integer> codes = new HashSet<>(pool.monsters());
        codes.addAll(pool.spellsTraps());
        BoosterSets sets = BoosterSets.loadBundled();
        assertNotNull(sets.get("legend_of_blue_eyes_white_dragon"));
        for (BoosterSets.BoosterSet set : sets.sets().values()) {
            assertFalse(set.of(BoosterSets.Rarity.COMMON).isEmpty(), set.id());
            for (BoosterSets.Card card : set.cards()) {
                assertTrue(codes.contains(card.code()), set.id() + " card " + card.code());
            }
        }
    }

    @Test
    void packsHaveEightCommonsAndOneFoil() {
        BoosterSets.BoosterSet lob = BoosterSets.loadBundled().get("legend_of_blue_eyes_white_dragon");
        Random random = new Random(7);
        int ultras = 0;
        for (int i = 0; i < 1200; i++) {
            List<BoosterSets.Card> pack = lob.open(random);
            assertEquals(BoosterSets.PACK_SIZE, pack.size());
            for (int k = 0; k < 8; k++) {
                assertEquals(BoosterSets.Rarity.COMMON, pack.get(k).rarity());
            }
            assertTrue(pack.get(8).rarity() != BoosterSets.Rarity.COMMON);
            if (pack.get(8).rarity() == BoosterSets.Rarity.ULTRA) {
                ultras++;
            }
        }
        assertTrue(ultras > 60 && ultras < 140, "about 1 in 12 ultras, got " + ultras);
    }

    @Test
    void starterDecksAreLegalAndLimitsAreEnforced() {
        CardDatabase cards = CardDatabase.loadBundled();
        Banlist banlist = Banlist.loadBundled();
        for (String id : List.of("starter_yugi", "starter_kaiba")) {
            assertEquals(List.of(), DeckRules.problems(Deck.bundled(id), cards, banlist), id);
        }
        int raigeki = 12580477;
        assertEquals(1, banlist.limit(raigeki));
        List<Integer> main = new ArrayList<>(Deck.bundled("starter_yugi").main().subList(0, 38));
        main.add(raigeki);
        main.add(raigeki);
        List<String> problems = DeckRules.problems(new Deck("x", main, List.of(), List.of()), cards, banlist);
        assertTrue(problems.stream().anyMatch(p -> p.contains("Raigeki")), problems.toString());
        problems = DeckRules.problems(new Deck("x", main.subList(0, 20), List.of(), List.of()), cards, banlist);
        assertTrue(problems.get(0).contains("40 to 60"), problems.toString());
    }

    @Test
    void randomNpcDecksAreLegal() {
        CardDatabase cards = CardDatabase.loadBundled();
        CardPool pool = CardPool.loadBundled();
        Banlist banlist = Banlist.loadBundled();
        for (long seed = 0; seed < 50; seed++) {
            Deck deck = DeckBuilder.random("npc", seed, cards, pool, banlist);
            assertEquals(List.of(), DeckRules.problems(deck, cards, banlist), "seed " + seed);
            assertEquals(DeckRules.MAIN_MIN, deck.main().size(), "seed " + seed);
        }
        assertEquals(DeckBuilder.random("a", 7, cards, pool, banlist).main(),
                DeckBuilder.random("a", 7, cards, pool, banlist).main());
    }

    @Test
    void randomNpcDecksFromAllCardsAreLegal() {
        CardDatabase cards = CardDatabase.loadBundled();
        CardPool pool = CardPool.of(PoolMode.ALL, cards);
        Banlist banlist = Banlist.loadBundled();
        for (long seed = 0; seed < 50; seed++) {
            Deck deck = DeckBuilder.random("npc", seed, cards, pool, banlist);
            assertEquals(List.of(), DeckRules.problems(deck, cards, banlist, pool::contains), "seed " + seed);
        }
    }

    @Test
    void poolModesDecideWhatIsPlayable() {
        CardDatabase cards = CardDatabase.loadBundled();
        CardPool modeled = CardPool.of(PoolMode.MODELED, cards);
        CardPool all = CardPool.of(PoolMode.ALL, cards);
        int blueEyes = 89631139;
        int stardust = 44508094; // Stardust Dragon, a synchro monster without a model
        int decodeTalkerArt = 1861630; // an alternate artwork of Decode Talker
        assertTrue(cards.all().size() > 14000, "all official cards are bundled");
        assertTrue(modeled.contains(blueEyes) && all.contains(blueEyes));
        assertFalse(modeled.contains(stardust));
        assertTrue(all.contains(stardust) && all.contains(decodeTalkerArt));
        assertFalse(all.monsters().contains(decodeTalkerArt), "alternate artworks aren't handed out twice");
        assertTrue(all.monsters().size() > 9000, "monsters " + all.monsters().size());
        assertTrue(all.spellsTraps().size() > 4500, "spells/traps " + all.spellsTraps().size());
        assertTrue(DeckRules.isExtra(cards.card(stardust)));
        for (String id : CardPool.STARTER_DECKS) {
            assertEquals(List.of(), DeckRules.problems(Deck.bundled(id), cards, Banlist.loadBundled(),
                    modeled::contains), id);
        }
        List<Integer> main = new ArrayList<>(Deck.bundled("starter_yugi").main().subList(0, 39));
        main.add(20721928); // Elemental HERO Sparkman, which has no model
        List<String> problems = DeckRules.problems(new Deck("x", main, List.of(), List.of()), cards,
                Banlist.loadBundled(), modeled::contains);
        assertTrue(problems.stream().anyMatch(p -> p.contains("can't be played")), problems.toString());
    }

    @Test
    void productsAreInReleaseOrderAndBringEachCardOnce() {
        Products products = Products.loadBundled();
        assertTrue(products.products().size() > 900, "products " + products.products().size());
        Products.Product lob = products.get("legend_of_blue_eyes_white_dragon");
        assertEquals("LOB", lob.code());
        assertEquals(Products.Kind.BOOSTER, lob.kind());
        assertTrue(lob.newCards().contains(89631139));
        assertTrue(products.get("spell_ruler").newCards().isEmpty(), "a reprint of Magic Ruler");
        Set<Integer> seen = new HashSet<>();
        java.time.LocalDate last = java.time.LocalDate.MIN;
        for (Products.Product product : products.products()) {
            assertFalse(product.date().isBefore(last), product.id());
            last = product.date();
            for (int code : product.newCards()) {
                assertTrue(seen.add(code), product.id() + " brings " + code + " again");
            }
        }
        assertFalse(products.ocgOnly().isEmpty());
        for (int code : products.ocgOnly().keySet()) {
            assertFalse(seen.contains(code), code + " is OCG-only and in a TCG product");
        }
    }

    @Test
    void boostersOfAllCardsHoldOnlyPlayableCards() {
        CardDatabase cards = CardDatabase.loadBundled();
        CardPool all = CardPool.of(PoolMode.ALL, cards);
        BoosterSets sets = BoosterSets.fromProducts(Products.loadBundled(), all);
        assertTrue(sets.sets().size() > 200, "boosters " + sets.sets().size());
        BoosterSets.BoosterSet lob = sets.get("legend_of_blue_eyes_white_dragon");
        assertTrue(lob.cards().size() > 120, "LOB has all its cards, not only the modeled ones");
        Random random = new Random(3);
        for (BoosterSets.BoosterSet set : sets.sets().values()) {
            for (BoosterSets.Card card : set.cards()) {
                assertTrue(all.contains(card.code()), set.id() + " card " + card.code());
            }
            assertEquals(set.profile().size(), set.open(random).size(), set.id());
        }
        assertEquals(BoosterSets.Rarity.SECRET, BoosterSets.Rarity.ofPrinted("Starlight Rare"));
        assertEquals(BoosterSets.Rarity.ULTRA, BoosterSets.Rarity.ofPrinted("Ultimate Rare"));
        assertEquals(BoosterSets.Rarity.COMMON, BoosterSets.Rarity.ofPrinted("Super Short Print"));
        assertEquals(BoosterSets.Rarity.RARE, BoosterSets.Rarity.ofPrinted("Rare"));
    }

    @Test
    void declarableFollowsTheCoresFilter() {
        CardDatabase cards = CardDatabase.loadBundled();
        long isType = 0x4000010200000000L;
        List<Long> monsters = List.of(0x1L, isType);
        List<Long> notMonsters = List.of(0x1L, isType, 0x4000000700000000L);
        assertTrue(Declarable.test(cards.get(89631139), monsters)); // Blue-Eyes White Dragon
        assertFalse(Declarable.test(cards.get(83764718), monsters)); // Monster Reborn
        assertTrue(Declarable.test(cards.get(83764718), notMonsters));
        assertFalse(Declarable.test(cards.get(1861630), monsters), "alternate artworks need ALLOW_ALIASES");
        long tokens = cards.all().stream().filter(c -> c.is(0x4000) && c.is(0x1))
                .filter(c -> Declarable.test(c.data(), monsters)).count();
        assertEquals(0, tokens, "tokens need ALLOW_TOKENS");
    }
}
