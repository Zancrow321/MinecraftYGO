package io.github.zancrow321.jadm.engine.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A list of boss monsters, for the ace shot's list mode: English card names (capitals and spaces around them don't
 * matter) or passcodes. An alternate artwork counts as its original card.
 */
public final class BossMonsters {
    /** Classic aces of the anime and the bundled decks, a start for a server's own list. */
    public static final List<String> DEFAULT = List.of(
            "Blue-Eyes White Dragon", "Blue-Eyes Ultimate Dragon", "Dark Magician", "Dark Magician Girl",
            "Red-Eyes Black Dragon", "Summoned Skull", "Black Luster Soldier", "Gaia The Fierce Knight",
            "Buster Blader", "Jinzo", "Dark Paladin", "Cyber End Dragon", "Elemental HERO Flame Wingman",
            "Elemental HERO Neos", "Stardust Dragon", "Red Dragon Archfiend", "Number 39: Utopia",
            "Slifer the Sky Dragon", "Obelisk the Tormentor", "The Winged Dragon of Ra");

    private BossMonsters() {
    }

    /** Whether {@code code} (or the card it is an alternate artwork of) is on the list. */
    public static boolean contains(List<? extends String> entries, CardDatabase cards, int code) {
        if (entries.isEmpty()) {
            return false;
        }
        int original = cards.original(code);
        String name = key(cards.name(original));
        for (String entry : entries) {
            String key = key(entry);
            if (key.equals(name) || key.equals(Integer.toString(original)) || key.equals(Integer.toString(code))) {
                return true;
            }
        }
        return false;
    }

    /** The entries that name no card, to warn about typos. */
    public static List<String> unknown(List<? extends String> entries, CardDatabase cards) {
        Set<String> known = new HashSet<>();
        for (CardInfo card : cards.all()) {
            known.add(key(card.name()));
            known.add(Integer.toString(card.code()));
        }
        List<String> unknown = new ArrayList<>();
        for (String entry : entries) {
            if (!key(entry).isEmpty() && !known.contains(key(entry))) {
                unknown.add(entry);
            }
        }
        return unknown;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
