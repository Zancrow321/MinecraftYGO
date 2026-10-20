package io.github.zancrow321.jadm.engine.data;

import io.github.zancrow321.jadm.engine.OcgConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * Deck construction rules: 40 to 60 main deck cards, up to 15 in the extra deck (fusion, synchro, xyz and link
 * monsters), at most three copies of a card, or fewer if the banlist says so, and only cards this server plays with.
 * Under a Genesys list the deck's points stay within the cap, and it holds no Link or Pendulum monsters.
 */
public final class DeckRules {
    public static final int MAIN_MIN = 40;
    public static final int MAIN_MAX = 60;
    public static final int EXTRA_MAX = 15;

    private DeckRules() {
    }

    /** Whether a card belongs in the extra deck. */
    public static boolean isExtra(CardInfo card) {
        return card != null && (card.data().type() & OcgConstants.TYPES_EXTRA_DECK) != 0;
    }

    /** The Genesys points of the main, extra and side deck together (0 under a Forbidden & Limited List). */
    public static int points(Deck deck, Banlist banlist) {
        int points = 0;
        for (List<Integer> part : List.of(deck.main(), deck.extra(), deck.side())) {
            for (int code : part) {
                points += banlist.points(code);
            }
        }
        return points;
    }

    /** @return what is wrong with the deck, empty if it is legal; every card counts as playable */
    public static List<String> problems(Deck deck, CardDatabase cards, Banlist banlist) {
        return problems(deck, cards, banlist, code -> true);
    }

    /**
     * @param playable whether a card may be used on this server (see {@link CardPool#contains})
     * @return what is wrong with the deck, empty if it is legal
     */
    public static List<String> problems(Deck deck, CardDatabase cards, Banlist banlist, IntPredicate playable) {
        List<String> problems = new ArrayList<>();
        if (deck.main().size() < MAIN_MIN || deck.main().size() > MAIN_MAX) {
            problems.add("The main deck needs " + MAIN_MIN + " to " + MAIN_MAX + " cards (it has "
                    + deck.main().size() + ").");
        }
        if (deck.extra().size() > EXTRA_MAX) {
            problems.add("The extra deck holds at most " + EXTRA_MAX + " cards (it has " + deck.extra().size() + ").");
        }
        // Alternate artworks are copies of their original: they share its limit.
        Map<Integer, Integer> copies = new TreeMap<>();
        Map<Integer, Integer> limits = new TreeMap<>();
        for (List<Integer> part : List.of(deck.main(), deck.extra(), deck.side())) {
            for (int code : part) {
                int original = cards.original(code);
                copies.merge(original, 1, Integer::sum);
                limits.merge(original, Math.min(banlist.limit(code), banlist.limit(original)), Math::min);
            }
        }
        java.util.stream.Stream.of(deck.main(), deck.extra(), deck.side()).flatMap(List::stream)
                .filter(code -> !playable.test(code)).findFirst().ifPresent(code ->
                problems.add((cards.card(code) == null ? "#" + code : cards.name(code))
                        + " can't be played on this server."));
        copies.forEach((code, n) -> {
            int limit = limits.get(code);
            if (n > limit) {
                String name = cards.card(code) == null ? "#" + code : cards.name(code);
                problems.add(banlist.genesys() && Banlist.genesysForbidden(cards.card(code))
                        ? name + ": no Link or Pendulum monsters in Genesys."
                        : limit == 0 ? name + " is forbidden."
                        : name + ": at most " + limit + (limit == 1 ? " copy" : " copies") + " (" + n + " in the deck).");
            }
        });
        if (banlist.genesys()) {
            int points = points(deck, banlist);
            if (points > banlist.pointCap()) {
                problems.add("The deck costs " + points + " Genesys points (at most " + banlist.pointCap() + ").");
            }
        }
        for (int code : deck.main()) {
            if (isExtra(cards.card(code))) {
                problems.add(cards.name(code) + " belongs in the extra deck.");
                break;
            }
        }
        for (int code : deck.extra()) {
            if (!isExtra(cards.card(code))) {
                problems.add((cards.card(code) == null ? "#" + code : cards.name(code))
                        + " can't go in the extra deck.");
                break;
            }
        }
        return problems;
    }
}
