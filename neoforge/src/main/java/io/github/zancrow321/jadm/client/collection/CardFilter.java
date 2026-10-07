package io.github.zancrow321.jadm.client.collection;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.engine.data.DeckRules;
import io.github.zancrow321.jadm.item.CardItem;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The filters of the binder and the deck box (card type, attribute, level) and how they are sorted. Each is a button
 * that steps through its choices. The search box looks through the card text too, see {@link #matches}.
 */
final class CardFilter implements IntPredicate {
    enum Kind { ALL, MONSTER, SPELL, TRAP, EXTRA }

    enum Sort { NAME, ATK, DEF, LEVEL }

    /** OCG attribute bits, in the order the attribute button steps through them (index 0 is any attribute). */
    static final int[] ATTRIBUTES = {0, 0x10, 0x20, 0x01, 0x02, 0x04, 0x08, 0x40};
    private static final String[] ATTRIBUTE_KEYS = {"any", "light", "dark", "earth", "water", "fire", "wind", "divine"};
    static final int MAX_LEVEL = 12;

    Kind kind = Kind.ALL;
    int attribute;
    /** 0 for any level; Xyz ranks and Link ratings count as levels. */
    int level;
    Sort sort = Sort.NAME;

    void nextKind(int step) {
        kind = Kind.values()[Math.floorMod(kind.ordinal() + step, Kind.values().length)];
    }

    void nextAttribute(int step) {
        attribute = Math.floorMod(attribute + step, ATTRIBUTES.length);
    }

    void nextLevel(int step) {
        level = Math.floorMod(level + step, MAX_LEVEL + 1);
    }

    void nextSort(int step) {
        sort = Sort.values()[Math.floorMod(sort.ordinal() + step, Sort.values().length)];
    }

    Component kindLabel() {
        return Component.translatable("screen.jadm.deck_box.kind." + kind.name().toLowerCase());
    }

    Component attributeLabel() {
        return Component.translatable("screen.jadm.deck_box.attribute." + ATTRIBUTE_KEYS[attribute]);
    }

    Component levelLabel() {
        return level == 0 ? Component.translatable("screen.jadm.deck_box.level.any")
                : Component.translatable("screen.jadm.deck_box.level", level);
    }

    Component sortLabel() {
        return Component.translatable("screen.jadm.deck_box.sort." + sort.name().toLowerCase());
    }

    @Override
    public boolean test(int code) {
        CardInfo card = JadmData.cards().card(code);
        if (card == null) {
            return kind == Kind.ALL && attribute == 0 && level == 0;
        }
        boolean kindOk = switch (kind) {
            case ALL -> true;
            case MONSTER -> card.is(OcgConstants.TYPE_MONSTER) && !DeckRules.isExtra(card);
            case SPELL -> card.is(OcgConstants.TYPE_SPELL);
            case TRAP -> card.is(OcgConstants.TYPE_TRAP);
            case EXTRA -> DeckRules.isExtra(card);
        };
        return kindOk
                && (attribute == 0 || card.is(OcgConstants.TYPE_MONSTER)
                        && (card.data().attribute() & ATTRIBUTES[attribute]) != 0)
                && (level == 0 || card.is(OcgConstants.TYPE_MONSTER) && level(card) == level);
    }

    /** Highest first for ATK, DEF and level; spells and traps after the monsters; then by name. */
    Comparator<Integer> order() {
        Comparator<Integer> byName = Comparator.comparing(CardGrid::name, String.CASE_INSENSITIVE_ORDER);
        return switch (sort) {
            case NAME -> byName;
            case ATK -> Comparator.<Integer>comparingInt(c -> -stat(c, Sort.ATK)).thenComparing(byName);
            case DEF -> Comparator.<Integer>comparingInt(c -> -stat(c, Sort.DEF)).thenComparing(byName);
            case LEVEL -> Comparator.<Integer>comparingInt(c -> -stat(c, Sort.LEVEL)).thenComparing(byName);
        };
    }

    /** The order of a deck list: monsters, then spells, then traps, each by name. */
    static Comparator<Integer> deckOrder() {
        return Comparator.<Integer>comparingInt(CardFilter::group)
                .thenComparing(CardGrid::name, String.CASE_INSENSITIVE_ORDER);
    }

    /** 0 for monsters, 1 for spells, 2 for traps, 3 for anything unknown. */
    static int group(int code) {
        CardInfo card = JadmData.cards().card(code);
        if (card == null) {
            return 3;
        }
        return card.is(OcgConstants.TYPE_MONSTER) ? 0 : card.is(OcgConstants.TYPE_SPELL) ? 1
                : card.is(OcgConstants.TYPE_TRAP) ? 2 : 3;
    }

    private static int stat(int code, Sort sort) {
        CardInfo card = JadmData.cards().card(code);
        if (card == null || !card.is(OcgConstants.TYPE_MONSTER)) {
            return Integer.MIN_VALUE + 1;
        }
        return switch (sort) {
            case ATK -> card.data().attack();
            // Link monsters have no DEF.
            case DEF -> card.is(OcgConstants.TYPE_LINK) ? -1 : card.data().defense();
            default -> level(card);
        };
    }

    /**
     * Whether every word of {@code query} is somewhere in the card: its name, passcode, card text, attribute, type
     * ("dragon", "quick-play", "tuner") or level ("level 4", "rank 4", "link-2" in quotes). Words in quotes count as
     * one phrase ("destroy all"). Attributes and types also go by their German names ("drache", "finsternis",
     * "schnellzauber"), since the card data is English.
     */
    static boolean matches(int code, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return true;
        }
        String text = SEARCH_TEXT.computeIfAbsent(code, CardFilter::searchText);
        Matcher term = TERM.matcher(q);
        while (term.find()) {
            String word = term.group(1) != null ? term.group(1) : term.group(2);
            if (!text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** A word of a search, or a phrase in quotes. */
    private static final Pattern TERM = Pattern.compile("\"([^\"]*)\"?|(\\S+)");

    /** What {@link #matches} looks through, lower case, one per card. */
    private static final Map<Integer, String> SEARCH_TEXT = new HashMap<>();

    /** German names of the attributes, by bit (EARTH, WATER, FIRE, WIND, LIGHT, DARK, DIVINE). */
    private static final String[] ATTRIBUTES_DE = {"Erde", "Wasser", "Feuer", "Wind", "Licht", "Finsternis",
            "Göttlich"};
    /** German names of the monster types, by bit, as on German cards. */
    private static final String[] RACES_DE = {"Krieger", "Hexer", "Fee", "Unterweltler", "Zombie", "Maschine", "Aqua",
            "Pyro", "Fels", "Geflügeltes Ungeheuer", "Pflanze", "Insekt", "Donner", "Drache", "Ungeheuer",
            "Ungeheuer-Krieger", "Dinosaurier", "Fisch", "Seeschlange", "Reptil", "Psi", "Göttliches Ungeheuer",
            "Schöpfergott", "Wyrm", "Cyberse", "Illusion"};
    /** German names of the card type bits (monster, spell, trap, ..., link), as on German cards. */
    private static final String[] TYPES_DE = {"Monster", "Zauber", "Falle", "", "Normal", "Effekt", "Fusion", "Ritual",
            "Fallenmonster", "Spirit", "Union", "Zwilling", "Empfänger", "Synchro", "Spielmarke", "", "Schnellzauber",
            "Permanent", "Ausrüstung", "Spielfeld", "Konter", "Flipp", "Toon", "Xyz", "Pendel", "", "Link"};

    private static String searchText(int code) {
        CardInfo card = JadmData.cards().card(code);
        if (card == null) {
            return String.valueOf(code);
        }
        var data = card.data();
        var text = JadmData.text();
        StringBuilder s = new StringBuilder(card.name()).append('\n').append(code);
        for (int bit = 0; bit < 32; bit++) {
            if ((data.type() & (1L << bit)) != 0) {
                s.append(' ').append(text.system(1050 + bit));
                if (bit < TYPES_DE.length) {
                    s.append(' ').append(TYPES_DE[bit]);
                }
            }
        }
        if (card.is(OcgConstants.TYPE_MONSTER)) {
            s.append(' ').append(CardItem.typeLine(card));
            int attribute = Integer.numberOfTrailingZeros(Math.max(1, data.attribute()));
            if (attribute < ATTRIBUTES_DE.length) {
                s.append(' ').append(ATTRIBUTES_DE[attribute]);
            }
            int race = Long.numberOfTrailingZeros(Math.max(1, data.race()));
            if (race < RACES_DE.length) {
                s.append(' ').append(RACES_DE[race]);
            }
            s.append(card.is(OcgConstants.TYPE_XYZ) ? " Rang " : " Stufe ").append(level(card));
        }
        return s.append('\n').append(card.description()).toString().toLowerCase(Locale.ROOT);
    }

    /** The level, rank or Link rating; pendulum scales share the field. */
    static int level(CardInfo card) {
        return card.data().level() & 0xFF;
    }
}
