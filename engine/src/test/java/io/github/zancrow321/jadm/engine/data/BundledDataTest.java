package io.github.zancrow321.jadm.engine.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledDataTest {
    @Test
    void everyBundledDeckCardIsInTheDatabase() {
        CardDatabase db = CardDatabase.loadBundled();
        for (String id : List.of("starter_yugi", "starter_kaiba")) {
            Deck deck = Deck.bundled(id);
            assertEquals(50, deck.main().size(), id);
            for (int code : deck.main()) {
                assertNotNull(db.card(code), id + " card " + code);
            }
        }
        assertEquals("Blue-Eyes White Dragon", db.name(89631139));
    }

    @Test
    void everyPoolCardIsInTheDatabase() {
        CardDatabase db = CardDatabase.loadBundled();
        CardPool pool = CardPool.loadBundled();
        assertTrue(pool.monsters().size() > 500, "monsters " + pool.monsters().size());
        assertTrue(pool.spellsTraps().size() > 100, "spells/traps " + pool.spellsTraps().size());
        for (int code : pool.monsters()) {
            assertNotNull(db.card(code), "monster " + code);
            assertNotNull(pool.model(code), "model for " + code);
        }
        for (int code : pool.spellsTraps()) {
            assertNotNull(db.card(code), "spell/trap " + code);
        }
        assertEquals("blue_eyes_white_dragon", pool.model(89631139).id());
        assertTrue(pool.model(89631139).animations().contains("idle"));
    }

    @Test
    void servesScriptsByFileName() {
        BundledScripts scripts = new BundledScripts();
        assertNotNull(scripts.read("constant.lua"));
        assertNotNull(scripts.read("./script/c83764718.lua")); // Monster Reborn
        assertNull(scripts.read("c1.lua"));
        assertNull(scripts.read("../cards.json"));
    }

    @Test
    void parsesYdkSections() {
        Deck deck = Deck.parseYdk("t", "#created by x\n#main\n1\n2\n#extra\n3\n!side\n4\n");
        assertEquals(List.of(1, 2), deck.main());
        assertEquals(List.of(3), deck.extra());
        assertEquals(List.of(4), deck.side());
    }
}
