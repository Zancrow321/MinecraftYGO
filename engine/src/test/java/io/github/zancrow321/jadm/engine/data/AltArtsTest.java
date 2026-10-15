package io.github.zancrow321.jadm.engine.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltArtsTest {
    private static final int DARK_MAGICIAN = 46986414;
    private static final int POT_OF_GREED = 55144522;

    private static final CardDatabase CARDS = CardDatabase.loadBundled();
    private static final AltArts ALT_ARTS = AltArts.loadBundled();

    @Test
    void everyAltArtBelongsToItsOriginal() {
        assertFalse(ALT_ARTS.byOriginal().isEmpty());
        ALT_ARTS.byOriginal().forEach((original, alts) -> {
            assertEquals(original, CARDS.original(original), "original " + original);
            for (int alt : alts) {
                assertEquals(original, CARDS.original(alt), "alt " + alt);
                assertEquals(CARDS.name(original), CARDS.name(alt));
            }
        });
        assertTrue(ALT_ARTS.of(CARDS, DARK_MAGICIAN).size() >= 2);
    }

    @Test
    void productsListOriginalsOnly() {
        for (Products.Product product : Products.loadBundled().products()) {
            for (Products.Printing printing : product.cards()) {
                assertEquals(CARDS.original(printing.code()), printing.code(), product.id());
            }
        }
    }

    @Test
    void rollsAnAltArtAtTheGivenRate() {
        Random random = new Random(3);
        Set<Integer> seen = new HashSet<>();
        int alts = 0;
        for (int i = 0; i < 10_000; i++) {
            int code = ALT_ARTS.roll(CARDS, DARK_MAGICIAN, 0.2, random);
            seen.add(code);
            if (code != DARK_MAGICIAN) {
                alts++;
                assertEquals(DARK_MAGICIAN, CARDS.original(code));
            }
        }
        assertTrue(alts > 1700 && alts < 2300, "alts " + alts);
        assertEquals(1 + ALT_ARTS.of(CARDS, DARK_MAGICIAN).size(), seen.size());
        assertEquals(DARK_MAGICIAN, ALT_ARTS.roll(CARDS, DARK_MAGICIAN, 0, random));
        // A card without alt arts never changes.
        assertEquals(POT_OF_GREED, ALT_ARTS.roll(CARDS, POT_OF_GREED, 1, random));
    }

    @Test
    void numbersTheArtworks() {
        List<Integer> alts = ALT_ARTS.of(CARDS, DARK_MAGICIAN);
        assertEquals(1, ALT_ARTS.number(CARDS, DARK_MAGICIAN));
        assertEquals(2, ALT_ARTS.number(CARDS, alts.get(0)));
        assertEquals(alts.size() + 1, ALT_ARTS.count(CARDS, alts.get(0)));
    }

    @Test
    void altArtsShareTheirOriginalsLimit() {
        int alt = ALT_ARTS.of(CARDS, DARK_MAGICIAN).get(0);
        List<Integer> main = new ArrayList<>(List.of(DARK_MAGICIAN, DARK_MAGICIAN, alt, alt));
        for (CardInfo card : CARDS.all()) {
            if (main.size() > DeckRules.MAIN_MIN) {
                break;
            }
            if (card.is(io.github.zancrow321.jadm.engine.OcgConstants.TYPE_NORMAL) && card.data().alias() == 0
                    && !DeckRules.isExtra(card) && !card.name().equals("Dark Magician")) {
                main.add(card.code());
            }
        }
        Banlist free = new Banlist("none", Map.of());
        List<String> problems = DeckRules.problems(new Deck("test", main, List.of(), List.of()), CARDS, free);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("at most 3"), problems.get(0));
        main.remove((Integer) DARK_MAGICIAN);
        assertTrue(DeckRules.problems(new Deck("test", main, List.of(), List.of()), CARDS, free).isEmpty());
        // A limited card stays limited as an alt art.
        Banlist limited = new Banlist("limited", Map.of(DARK_MAGICIAN, 1));
        assertFalse(DeckRules.problems(new Deck("test", main, List.of(), List.of()), CARDS, limited).isEmpty());
    }
}
