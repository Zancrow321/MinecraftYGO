package io.github.zancrow321.minecraftygo.engine.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * Builds random but playable 40-card decks from the pool for NPC duelists: mostly low-level monsters with a few
 * tribute monsters, spells and traps, and an Extra Deck of whatever the pool offers: Fusions, Synchros (with a few
 * Tuners in the main deck to make them), Xyz monsters of the ranks the main deck can build, and small Links. Cards
 * with a monster model are picked more often, so NPC duels show more models. Every deck it builds is legal.
 */
public final class DeckBuilder {
    private static final int LOW_LEVEL = 16;
    private static final int MID_LEVEL = 4;
    private static final int HIGH_LEVEL = 2;
    /** Tuners among the low-level monsters, when the pool has Synchros to make. */
    private static final int TUNERS = 4;
    private static final int FUSIONS = 3;
    private static final int SYNCHROS = 4;
    private static final int XYZ = 4;
    private static final int LINKS = 4;
    /** Links above this rating need more monsters than an NPC usually has out. */
    private static final int MAX_LINK_RATING = 3;
    /** How much likelier a card with a monster model is picked. */
    private static final double MODEL_WEIGHT = 3;

    private DeckBuilder() {
    }

    public static Deck random(String name, long seed, CardDatabase cards, CardPool pool, Banlist banlist) {
        Random random = new Random(seed);
        List<Integer> low = new ArrayList<>();
        List<Integer> tuners = new ArrayList<>();
        List<Integer> mid = new ArrayList<>();
        List<Integer> high = new ArrayList<>();
        List<Integer> fusions = new ArrayList<>();
        List<Integer> synchros = new ArrayList<>();
        List<Integer> xyz = new ArrayList<>();
        List<Integer> links = new ArrayList<>();
        for (int code : pool.monsters()) {
            CardInfo card = cards.card(code);
            if (card == null || card.is(TYPE_RITUAL) || card.is(TYPE_TOKEN)) {
                continue;
            }
            if (DeckRules.isExtra(card)) {
                if (card.is(TYPE_FUSION)) {
                    fusions.add(code);
                } else if (card.is(TYPE_SYNCHRO)) {
                    synchros.add(code);
                } else if (card.is(TYPE_XYZ)) {
                    xyz.add(code);
                } else if (card.is(TYPE_LINK) && card.data().level() <= MAX_LINK_RATING) {
                    links.add(code);
                }
            } else if (card.data().level() <= 4) {
                (card.is(TYPE_TUNER) ? tuners : low).add(code);
            } else if (card.data().level() <= 6) {
                mid.add(code);
            } else {
                high.add(code);
            }
        }
        Map<Integer, Integer> copies = new HashMap<>();
        List<Integer> main = new ArrayList<>();
        int tunerCount = synchros.isEmpty() ? 0 : TUNERS;
        draw(tuners, tunerCount, random, pool, banlist, copies, main);
        if (synchros.isEmpty()) {
            low.addAll(tuners);
        }
        draw(low, LOW_LEVEL - Math.min(tunerCount, main.size()), random, pool, banlist, copies, main);
        draw(mid, MID_LEVEL, random, pool, banlist, copies, main);
        draw(high, HIGH_LEVEL, random, pool, banlist, copies, main);
        draw(new ArrayList<>(pool.spellsTraps()), DeckRules.MAIN_MIN - main.size(), random, pool, banlist, copies,
                main);

        List<Integer> extra = new ArrayList<>();
        draw(fusions, FUSIONS, random, pool, banlist, copies, extra);
        draw(synchros, Math.min(tunerCount, SYNCHROS), random, pool, banlist, copies, extra);
        draw(buildableXyz(xyz, main, cards), XYZ, random, pool, banlist, copies, extra);
        draw(links, LINKS, random, pool, banlist, copies, extra);
        // Whatever room is left goes to more Fusions, so pools without the newer summons keep their five.
        draw(fusions, Math.min(DeckRules.EXTRA_MAX - extra.size(), 5 - Math.min(5, extra.size())), random, pool,
                banlist, copies, extra);
        Collections.shuffle(main, random);
        return new Deck(name, List.copyOf(main), List.copyOf(extra), List.of());
    }

    /** The Xyz monsters whose rank matches a level at least four of the main deck's monsters share. */
    private static List<Integer> buildableXyz(List<Integer> xyz, List<Integer> main, CardDatabase cards) {
        Map<Integer, Integer> levels = new HashMap<>();
        for (int code : main) {
            CardInfo card = cards.card(code);
            if (card != null && card.is(TYPE_MONSTER)) {
                levels.merge(card.data().level(), 1, Integer::sum);
            }
        }
        List<Integer> buildable = new ArrayList<>();
        for (int code : xyz) {
            if (levels.getOrDefault(cards.card(code).data().level(), 0) >= 4) {
                buildable.add(code);
            }
        }
        return buildable;
    }

    /**
     * Adds {@code count} cards from {@code from}, one copy each at first and more on later passes, within the
     * banlist. Cards with a model come first more often.
     */
    private static void draw(List<Integer> from, int count, Random random, CardPool pool, Banlist banlist,
                             Map<Integer, Integer> copies, List<Integer> into) {
        Map<Integer, Double> order = new HashMap<>();
        for (int code : from) {
            double roll = random.nextDouble();
            order.put(code, pool.models().containsKey(code) ? roll / MODEL_WEIGHT : roll);
        }
        List<Integer> shuffled = new ArrayList<>(from);
        shuffled.sort(Comparator.comparingDouble(order::get));
        int target = into.size() + Math.max(0, count);
        for (int pass = 0; pass < 3 && into.size() < target; pass++) {
            for (int code : shuffled) {
                if (into.size() >= target) {
                    break;
                }
                int limit = Math.min(banlist.limit(code), pass == 0 ? 1 : pass + 1);
                if (copies.getOrDefault(code, 0) < limit) {
                    copies.merge(code, 1, Integer::sum);
                    into.add(code);
                }
            }
        }
    }
}
