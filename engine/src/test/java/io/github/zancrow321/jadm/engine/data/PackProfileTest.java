package io.github.zancrow321.jadm.engine.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackProfileTest {
    private static final BoosterSets SETS = BoosterSets.fromProducts(Products.loadBundled(),
            CardPool.of(PoolMode.ALL, CardDatabase.loadBundled()));

    private static long foils(BoosterSets.BoosterSet set, int packs) {
        Random random = new Random(5);
        long foils = 0;
        for (int i = 0; i < packs; i++) {
            List<BoosterSets.Card> pack = set.open(random);
            assertEquals(set.profile().size(), pack.size(), set.id());
            foils += pack.stream().filter(c -> c.rarity().slot() != BoosterSets.Rarity.COMMON).count();
        }
        return foils;
    }

    @Test
    void packsLookLikeTheOriginals() {
        BoosterSets.BoosterSet lob = SETS.get("legend_of_blue_eyes_white_dragon");
        assertEquals("core", lob.profile().name());
        assertEquals(1000, foils(lob, 1000), "one rare or better per LOB pack");

        BoosterSets.BoosterSet duea = SETS.get("duelist_alliance");
        assertEquals(PackProfile.MODERN_CORE, duea.profile());
        assertEquals(2000, foils(duea, 1000), "a rare and a foil per modern pack");

        assertEquals("tournament", SETS.get("tournament_pack_1st_season").profile().name());
        assertEquals(3, SETS.get("tournament_pack_1st_season").profile().size());
        for (String game : List.of("one", "two", "three", "four", "five", "six", "seven", "eight")) {
            BoosterSets.BoosterSet cp = SETS.get("champion_pack_game_" + game);
            assertEquals("tournament", cp.profile().name(), "Champion Pack: Game " + game);
            assertEquals(20, cp.cards().size());
        }

        BoosterSets.BoosterSet drl = SETS.get("dragons_of_legend");
        assertEquals("premium", drl.profile().name());
        assertEquals(5000, foils(drl, 1000), "all-foil packs");

        assertEquals("mini", SETS.get("hidden_arsenal").profile().name());
        assertTrue(SETS.sets().containsKey("2014_mega_tin_mega_pack"), "a Mega-Tin's own pack is a set");

        BoosterSets.BoosterSet thsf = SETS.get("the_secret_forces");
        assertEquals(PackProfile.SUPER_SECRET, thsf.profile());
        assertEquals(1000, secrets(thsf, 1000), "a secret in every Secret Forces pack");
    }

    /** The Power-Up Packs were 15-card "special" products, too small to come in packs, so they were nowhere. */
    @Test
    void powerUpPacksCanBeOpened() {
        BoosterSets.BoosterSet ys13 = SETS.get("super_starter_power_up_pack");
        assertEquals("power-up", ys13.profile().name());
        Random random = new Random(9);
        for (int i = 0; i < 200; i++) {
            List<BoosterSets.Card> pack = ys13.open(random);
            assertEquals(5, pack.size());
            assertEquals(1, pack.stream().filter(c -> c.rarity().slot() == BoosterSets.Rarity.ULTRA).count());
            assertEquals(2, pack.stream().filter(c -> c.rarity().slot() == BoosterSets.Rarity.SUPER).count());
        }
        BoosterSets.BoosterSet ys14 = SETS.get("space_time_showdown_power_up_pack");
        assertEquals(1, ys14.profile().slots().size(), "no supers in the Space-Time Showdown pack");
        assertEquals(1000, foils(ys14, 1000), "its ultra in every pack");
        assertTrue(SETS.sets().containsKey("noble_knights_of_the_round_table_power_up_pack"));
    }

    /** YGOPRODeck lists only Duelist's Advance's 48 monsters; its spells, traps and Links come from the set list. */
    @Test
    void duelistsAdvanceHasItsSpellsAndTraps() {
        BoosterSets.BoosterSet duad = SETS.get("duelist_s_advance");
        assertEquals(PackProfile.MODERN_CORE, duad.profile());
        CardDatabase db = CardDatabase.loadBundled();
        long spellsTraps = duad.cards().stream().map(BoosterSets.Card::code).distinct()
                .filter(c -> (db.get(c).type() & 0x1) == 0).count();
        assertEquals(34, spellsTraps, "27 spells and 7 traps");
        assertEquals(100, duad.cards().stream().map(BoosterSets.Card::code).distinct().count());
        assertEquals(BoosterSets.Rarity.ULTRA, SETS.rarity("duelist_s_advance", 22377092), "Trap Holic");
    }

    private static long secrets(BoosterSets.BoosterSet set, int packs) {
        Random random = new Random(5);
        long secrets = 0;
        for (int i = 0; i < packs; i++) {
            secrets += set.open(random).stream().filter(c -> c.rarity().slot() == BoosterSets.Rarity.SECRET)
                    .count();
        }
        return secrets;
    }

    /** Sets without commons once filled every pack with seven copies of their one stray common. */
    @Test
    void setsWithoutCommonsHaveRaresInsteadOfTheSameCardSevenTimes() {
        for (String id : List.of("maze_of_millennia", "justice_hunters")) {
            BoosterSets.BoosterSet set = SETS.get(id);
            assertEquals(PackProfile.RARE_ONLY, set.profile(), id);
            assertEquals(BoosterSets.Rarity.RARE, set.base(), id);
            assertTrue(set.of(BoosterSets.Rarity.COMMON).isEmpty(), id + ": the stray common counts as a rare");
            Random random = new Random(7);
            for (int i = 0; i < 200; i++) {
                List<BoosterSets.Card> pack = set.open(random);
                assertEquals(7, pack.size(), id);
                assertTrue(pack.stream().noneMatch(c -> c.rarity().slot() == BoosterSets.Rarity.COMMON), id);
                assertTrue(pack.stream().map(BoosterSets.Card::code).distinct().count() >= 4, id + ": varied packs");
                assertTrue(pack.get(6).rarity().slot().compareTo(BoosterSets.Rarity.RARE) > 0, id + ": a foil last");
            }
        }
    }

    /**
     * Phantom Nightmare and the other core sets list their ultras as Quarter Century Secret Rares too; taking that
     * printing as the card's rarity left them without a single ultra.
     */
    @Test
    void cardsPrintedInSeveralRaritiesAreInEachSlot() {
        BoosterSets.BoosterSet phni = SETS.get("phantom_nightmare");
        assertEquals(14, phni.of(BoosterSets.Rarity.ULTRA).size(), "Phantom Nightmare's ultras");
        assertTrue(phni.of(BoosterSets.Rarity.SECRET).size() > 10, "its secrets and the Quarter Century ones");
        assertEquals(BoosterSets.Rarity.ULTRA, SETS.rarity("phantom_nightmare",
                phni.of(BoosterSets.Rarity.ULTRA).getFirst().code()), "listed as the plainest rarity");
        Random random = new Random(3);
        long ultras = 0;
        for (int i = 0; i < 6000; i++) {
            ultras += phni.open(random).stream().filter(c -> c.rarity().slot() == BoosterSets.Rarity.ULTRA).count();
        }
        assertTrue(ultras > 800 && ultras < 1200, "about one ultra in six packs: " + ultras);

        for (Products.Product product : Products.loadBundled().products()) {
            BoosterSets.BoosterSet set = SETS.get(product.id());
            boolean ultra = product.cards().stream().flatMap(p -> java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(p.rarity()), p.better().stream()))
                    .anyMatch(r -> BoosterSets.Rarity.ofPrinted(r) == BoosterSets.Rarity.ULTRA);
            if (set != null && ultra && set.base().compareTo(BoosterSets.Rarity.ULTRA) <= 0) {
                assertTrue(!set.of(BoosterSets.Rarity.ULTRA).isEmpty(), product.id() + " has no ultras to pull");
            }
        }
    }

    /** No booster set's plain slots come from a single card. */
    @Test
    void everySetsPlainCardsAreVaried() {
        for (BoosterSets.BoosterSet set : SETS.sets().values()) {
            int plainSlots = set.profile().size() - set.profile().slots().size();
            if (plainSlots > 1) {
                assertTrue(set.of(set.base()).size() > 1, set.id() + " has one " + set.base().id() + " card");
            }
        }
    }

    @Test
    void foilSlotsNeverDropToAFewCommons() {
        BoosterSets.BoosterSet ra05 = SETS.get("rarity_collection_5");
        assertEquals("premium", ra05.profile().name());
        assertEquals(5000, foils(ra05, 1000), "Rarity Collection 5 packs are all foils");

        // One common among foils: the foil slots take the foils, not that common.
        List<BoosterSets.Card> cards = List.of(new BoosterSets.Card(1, BoosterSets.Rarity.COMMON),
                new BoosterSets.Card(2, BoosterSets.Rarity.SECRET), new BoosterSets.Card(3, BoosterSets.Rarity.SECRET));
        BoosterSets.BoosterSet set = new BoosterSets.BoosterSet("test", "TST", "Test", cards,
                ra05.profile());
        assertEquals(5000, foils(set, 1000));
    }

    @Test
    void printedRaritiesAreRarities() {
        // YGOPRODeck's set lists carry notes such as "2", "New" or "force-SMW" in the rarity field.
        Pattern real = Pattern.compile("(?i)rare|common|short print");
        for (Products.Product product : Products.loadBundled().products()) {
            assertFalse(product.name().contains("&apos;") || product.name().contains("&amp;"), product.name());
            for (Products.Printing printing : product.cards()) {
                assertTrue(real.matcher(printing.rarity()).find(), product.id() + " " + printing);
            }
        }
    }

    /** Every rarity a set's cards are printed in can be pulled, the rare finishes less often than the plain ones. */
    @Test
    void everyPrintedRarityCanBePulled() {
        assertPulls("phantom_nightmare", BoosterSets.Rarity.COMMON, BoosterSets.Rarity.SUPER,
                BoosterSets.Rarity.ULTRA, BoosterSets.Rarity.SECRET, BoosterSets.Rarity.QUARTER_CENTURY);
        assertPulls("rarity_collection_5", BoosterSets.Rarity.SUPER, BoosterSets.Rarity.ULTRA,
                BoosterSets.Rarity.ULTIMATE, BoosterSets.Rarity.SECRET, BoosterSets.Rarity.COLLECTORS,
                BoosterSets.Rarity.PLATINUM_SECRET, BoosterSets.Rarity.STARLIGHT);
        java.util.Map<BoosterSets.Rarity, Integer> pulled = assertPulls("doom_of_dimensions",
                BoosterSets.Rarity.SECRET, BoosterSets.Rarity.STARLIGHT);
        // Per card, a Starlight Rare comes a fifth as often as a Secret Rare.
        BoosterSets.BoosterSet dood = SETS.get("doom_of_dimensions");
        double starlight = (double) pulled.get(BoosterSets.Rarity.STARLIGHT)
                / dood.cards().stream().filter(c -> c.rarity() == BoosterSets.Rarity.STARLIGHT).count();
        double secret = (double) pulled.get(BoosterSets.Rarity.SECRET)
                / dood.cards().stream().filter(c -> c.rarity() == BoosterSets.Rarity.SECRET).count();
        assertTrue(starlight > secret * 0.12 && starlight < secret * 0.3, starlight + " vs " + secret);

        // Every rarity printed in a booster set is in it, and none is lost to an unknown name.
        for (Products.Product product : Products.loadBundled().products()) {
            BoosterSets.BoosterSet set = SETS.get(product.id());
            if (set == null) {
                continue;
            }
            for (Products.Printing p : product.cards()) {
                if (set.cards().stream().noneMatch(c -> c.code() == p.code())) {
                    continue; // not in the pool
                }
                for (String printed : java.util.stream.Stream.concat(java.util.stream.Stream.of(p.rarity()),
                        p.better().stream()).toList()) {
                    BoosterSets.Rarity r = BoosterSets.Rarity.ofPrinted(printed);
                    assertTrue(set.cards().contains(new BoosterSets.Card(p.code(), r))
                                    || r.slot().compareTo(set.base()) < 0,
                            product.id() + " " + p.code() + " " + printed);
                }
            }
        }
    }

    private static java.util.Map<BoosterSets.Rarity, Integer> assertPulls(String id, BoosterSets.Rarity... rarities) {
        Random random = new Random(11);
        java.util.Map<BoosterSets.Rarity, Integer> pulled = new java.util.EnumMap<>(BoosterSets.Rarity.class);
        for (int i = 0; i < 24_000; i++) {
            SETS.get(id).open(random).forEach(c -> pulled.merge(c.rarity(), 1, Integer::sum));
        }
        for (BoosterSets.Rarity r : rarities) {
            assertTrue(pulled.getOrDefault(r, 0) > 0, id + " gives " + r + ": " + pulled);
        }
        return pulled;
    }

    @Test
    void printedRarityNames() {
        String[][] names = {
                {"Common", "COMMON"}, {"Short Print", "COMMON"}, {"Super Short Print", "COMMON"},
                {"Duel Terminal Normal Parallel Rare", "NORMAL_PARALLEL"}, {"Normal Parallel Rare", "NORMAL_PARALLEL"},
                {"Duel Terminal Normal Rare Parallel Rare", "RARE_PARALLEL"},
                {"Duel Terminal Rare Parallel Rare", "RARE_PARALLEL"}, {"Rare", "RARE"},
                {"Starfoil Rare", "STARFOIL"}, {"Shatterfoil Rare", "SHATTERFOIL"}, {"Mosaic Rare", "MOSAIC"},
                {"Super Rare", "SUPER"}, {"Duel Terminal Super Parallel Rare", "SUPER_PARALLEL"},
                {"Super Parallel Rare", "SUPER_PARALLEL"}, {"Ultra Rare", "ULTRA"},
                {"Ultra Parallel Rare", "ULTRA_PARALLEL"}, {"Duel Terminal Ultra Parallel Rare", "ULTRA_PARALLEL"},
                {"Ultra Rare (Pharaoh's Rare)", "PHARAOH"}, {"Gold Rare", "GOLD"}, {"Premium Gold Rare", "PREMIUM_GOLD"},
                {"Platinum Rare", "PLATINUM"}, {"Ultimate Rare", "ULTIMATE"}, {"Secret Rare", "SECRET"},
                {"Ultra Secret Rare", "ULTRA_SECRET"}, {"Prismatic Secret Rare", "PRISMATIC_SECRET"},
                {"Platinum Secret Rare", "PLATINUM_SECRET"}, {"PLatinum Secret Rare", "PLATINUM_SECRET"},
                {"Gold Secret Rare", "GOLD_SECRET"}, {"Extra Secret Rare", "EXTRA_SECRET"},
                {"Ghost Rare", "GHOST"}, {"Ghost/Gold Rare", "GHOST_GOLD"}, {"Collector's Rare", "COLLECTORS"},
                {"Quarter Century Secret Rare", "QUARTER_CENTURY"}, {"Grand Master Rare", "GRAND_MASTER"},
                {"Starlight Rare", "STARLIGHT"}, {"10000 Secret Rare", "TEN_THOUSAND_SECRET"}};
        for (String[] n : names) {
            assertEquals(BoosterSets.Rarity.valueOf(n[1]), BoosterSets.Rarity.ofPrinted(n[0]), n[0]);
        }
        for (BoosterSets.Rarity r : BoosterSets.Rarity.values()) {
            assertEquals(r, BoosterSets.Rarity.parse(r.id()));
            assertEquals(r, BoosterSets.Rarity.ofPrinted(r.displayName()), r.displayName());
        }
    }
}
