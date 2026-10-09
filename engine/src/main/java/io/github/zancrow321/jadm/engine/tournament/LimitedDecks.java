package io.github.zancrow321.jadm.engine.tournament;

import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.data.DeckRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * Decks for Sealed and Draft tournaments, built from the cards someone opened or picked: at least a minimum of main
 * deck cards (20 by default) and at most {@value DeckRules#MAIN_MAX}, up to {@value DeckRules#EXTRA_MAX} in the
 * extra deck, and only as many copies of a card as the pool holds. There is no banlist and no three-copy limit.
 */
public final class LimitedDecks {
    /** The size of the decks {@link #build} aims for, when the pool has enough cards. */
    public static final int TARGET = 30;
    private static final int MAX_MID_LEVEL = 4;
    private static final int MAX_HIGH_LEVEL = 3;

    private LimitedDecks() {
    }

    /**
     * How good a card is for a deck put together from a few packs: beatsticks that can be Normal Summoned first,
     * then spells and traps (often removal), tribute monsters by how hard they hit, extra deck monsters last.
     */
    public static double value(CardInfo card) {
        if (card == null) {
            return 0;
        }
        if (card.is(TYPE_SPELL)) {
            return 15;
        }
        if (card.is(TYPE_TRAP)) {
            return 14;
        }
        double atk = Math.max(0, card.data().attack()) / 100.0;
        double effect = card.is(TYPE_NORMAL) ? 0 : 3;
        if (DeckRules.isExtra(card)) {
            return atk / 2 + effect - 4;
        }
        if (card.is(TYPE_RITUAL)) {
            return atk / 2;
        }
        int level = card.data().level();
        return level <= 4 ? atk + effect : level <= 6 ? atk + effect - 7 : atk + effect - 13;
    }

    /**
     * A deck from a pool, as for someone who runs out of time or an NPC: the best cards by {@link #value}, about
     * {@value #TARGET} of them (all of them if that's fewer than {@code minimum}), with only a few tribute monsters.
     * Every extra deck monster in the pool goes in the extra deck, up to its limit.
     */
    public static Deck build(String name, List<Integer> pool, CardDatabase cards, int minimum) {
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> candidates = new ArrayList<>();
        for (int code : pool) {
            CardInfo card = cards.card(code);
            if (card == null) {
                continue;
            }
            if (DeckRules.isExtra(card)) {
                extra.add(code);
            } else {
                candidates.add(code);
            }
        }
        candidates.sort(Comparator.comparingDouble((Integer code) -> -value(cards.card(code))).thenComparing(c -> c));
        extra.sort(Comparator.comparingDouble((Integer code) -> -value(cards.card(code))).thenComparing(c -> c));
        int target = Math.min(DeckRules.MAIN_MAX, Math.max(minimum, TARGET));
        int mid = 0;
        int high = 0;
        List<Integer> skipped = new ArrayList<>();
        for (int code : candidates) {
            if (main.size() >= target) {
                break;
            }
            CardInfo card = cards.card(code);
            int level = card.is(TYPE_MONSTER) ? card.data().level() : 0;
            if (level >= 7 && high >= MAX_HIGH_LEVEL || level >= 5 && level <= 6 && mid >= MAX_MID_LEVEL) {
                skipped.add(code);
                continue;
            }
            if (level >= 7) {
                high++;
            } else if (level >= 5) {
                mid++;
            }
            main.add(code);
        }
        // Too few cards without them: the tribute monsters go in after all.
        for (int code : skipped) {
            if (main.size() >= minimum) {
                break;
            }
            main.add(code);
        }
        return new Deck(name, main, extra.subList(0, Math.min(extra.size(), DeckRules.EXTRA_MAX)), List.of());
    }

    /** @return what keeps the deck from being played, empty if it is legal */
    public static List<String> problems(List<Integer> main, List<Integer> extra, List<Integer> pool,
                                        CardDatabase cards, int minimum) {
        List<String> problems = new ArrayList<>();
        if (main.size() < minimum) {
            problems.add("The main deck needs at least " + minimum + " cards.");
        }
        if (main.size() > DeckRules.MAIN_MAX) {
            problems.add("The main deck holds at most " + DeckRules.MAIN_MAX + " cards.");
        }
        if (extra.size() > DeckRules.EXTRA_MAX) {
            problems.add("The extra deck holds at most " + DeckRules.EXTRA_MAX + " cards.");
        }
        Map<Integer, Integer> left = counts(pool);
        for (List<Integer> part : List.of(main, extra)) {
            for (int code : part) {
                if (left.merge(code, -1, Integer::sum) < 0) {
                    problems.add(cards.name(code) + " isn't in your pool often enough.");
                    break;
                }
            }
        }
        return problems;
    }

    public static Map<Integer, Integer> counts(List<Integer> codes) {
        Map<Integer, Integer> counts = new HashMap<>();
        codes.forEach(code -> counts.merge(code, 1, Integer::sum));
        return counts;
    }
}
