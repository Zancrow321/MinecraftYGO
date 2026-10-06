package io.github.zancrow321.jadm.engine.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            foils += pack.stream().filter(c -> c.rarity() != BoosterSets.Rarity.COMMON).count();
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

        BoosterSets.BoosterSet drl = SETS.get("dragons_of_legend");
        assertEquals("premium", drl.profile().name());
        assertEquals(5000, foils(drl, 1000), "all-foil packs");

        assertEquals("mini", SETS.get("hidden_arsenal").profile().name());
        assertTrue(SETS.sets().containsKey("2014_mega_tin_mega_pack"), "a Mega-Tin's own pack is a set");
    }
}
