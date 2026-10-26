package io.github.zancrow321.jadm.engine.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossMonstersTest {
    private static final int DARK_MAGICIAN = 46986414;
    private static final int BLUE_EYES = 89631139;
    private static final int POT_OF_GREED = 55144522;

    private static final CardDatabase CARDS = CardDatabase.loadBundled();

    @Test
    void everyDefaultBossIsACard() {
        assertEquals(List.of(), BossMonsters.unknown(BossMonsters.DEFAULT, CARDS));
    }

    @Test
    void matchesNamesWhateverTheCapitals() {
        List<String> list = List.of("  dark MAGICIAN ");
        assertTrue(BossMonsters.contains(list, CARDS, DARK_MAGICIAN));
        assertFalse(BossMonsters.contains(list, CARDS, BLUE_EYES));
        assertFalse(BossMonsters.contains(List.of(), CARDS, DARK_MAGICIAN));
    }

    @Test
    void matchesPasscodes() {
        assertTrue(BossMonsters.contains(List.of(Integer.toString(BLUE_EYES)), CARDS, BLUE_EYES));
        assertFalse(BossMonsters.contains(List.of(Integer.toString(BLUE_EYES)), CARDS, POT_OF_GREED));
    }

    @Test
    void altArtsCountAsTheirCard() {
        for (int alt : AltArts.loadBundled().of(CARDS, DARK_MAGICIAN)) {
            assertTrue(BossMonsters.contains(List.of("Dark Magician"), CARDS, alt), "alt " + alt);
            assertTrue(BossMonsters.contains(List.of(Integer.toString(DARK_MAGICIAN)), CARDS, alt), "alt " + alt);
        }
    }

    @Test
    void reportsTypos() {
        assertEquals(List.of("Dark Magican"), BossMonsters.unknown(List.of("Dark Magician", "Dark Magican", ""), CARDS));
    }
}
