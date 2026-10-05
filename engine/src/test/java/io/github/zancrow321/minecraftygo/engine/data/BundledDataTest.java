package io.github.zancrow321.minecraftygo.engine.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
