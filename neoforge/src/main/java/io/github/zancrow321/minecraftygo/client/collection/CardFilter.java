package io.github.zancrow321.minecraftygo.client.collection;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.data.CardInfo;
import io.github.zancrow321.minecraftygo.engine.data.DeckRules;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.function.IntPredicate;

/**
 * The deck box's filters for the cards you own (card type, attribute, level) and how they are sorted. Each is a
 * button that steps through its choices.
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
        return Component.translatable("screen.minecraftygo.deck_box.kind." + kind.name().toLowerCase());
    }

    Component attributeLabel() {
        return Component.translatable("screen.minecraftygo.deck_box.attribute." + ATTRIBUTE_KEYS[attribute]);
    }

    Component levelLabel() {
        return level == 0 ? Component.translatable("screen.minecraftygo.deck_box.level.any")
                : Component.translatable("screen.minecraftygo.deck_box.level", level);
    }

    Component sortLabel() {
        return Component.translatable("screen.minecraftygo.deck_box.sort." + sort.name().toLowerCase());
    }

    @Override
    public boolean test(int code) {
        CardInfo card = YgoData.cards().card(code);
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
        CardInfo card = YgoData.cards().card(code);
        if (card == null) {
            return 3;
        }
        return card.is(OcgConstants.TYPE_MONSTER) ? 0 : card.is(OcgConstants.TYPE_SPELL) ? 1
                : card.is(OcgConstants.TYPE_TRAP) ? 2 : 3;
    }

    private static int stat(int code, Sort sort) {
        CardInfo card = YgoData.cards().card(code);
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

    /** The level, rank or Link rating; pendulum scales share the field. */
    static int level(CardInfo card) {
        return card.data().level() & 0xFF;
    }
}
