package io.github.zancrow321.minecraftygo.engine.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * Builds random but playable 40-card decks from the pool for NPC duelists: mostly low-level monsters with a few
 * tribute monsters, spells and traps, and a handful of Fusions. Every deck it builds is legal.
 */
public final class DeckBuilder {
    private static final int LOW_LEVEL = 16;
    private static final int MID_LEVEL = 4;
    private static final int HIGH_LEVEL = 2;
    private static final int EXTRA = 5;

    private DeckBuilder() {
    }

    public static Deck random(String name, long seed, CardDatabase cards, CardPool pool, Banlist banlist) {
        Random random = new Random(seed);
        List<Integer> low = new ArrayList<>();
        List<Integer> mid = new ArrayList<>();
        List<Integer> high = new ArrayList<>();
        List<Integer> fusions = new ArrayList<>();
        for (int code : pool.monsters()) {
            CardInfo card = cards.card(code);
            if (card == null || card.is(TYPE_RITUAL) || card.is(TYPE_TOKEN)) {
                continue;
            }
            if (DeckRules.isExtra(card)) {
                // Only fusions so far; synchro, xyz and link summons come with the extra deck support for NPCs.
                if (card.is(TYPE_FUSION)) {
                    fusions.add(code);
                }
            } else if (card.data().level() <= 4) {
                low.add(code);
            } else if (card.data().level() <= 6) {
                mid.add(code);
            } else {
                high.add(code);
            }
        }
        Map<Integer, Integer> copies = new HashMap<>();
        List<Integer> main = new ArrayList<>();
        draw(low, LOW_LEVEL, random, banlist, copies, main);
        draw(mid, MID_LEVEL, random, banlist, copies, main);
        draw(high, HIGH_LEVEL, random, banlist, copies, main);
        draw(new ArrayList<>(pool.spellsTraps()), DeckRules.MAIN_MIN - main.size(), random, banlist, copies, main);
        List<Integer> extra = new ArrayList<>();
        draw(fusions, EXTRA, random, banlist, copies, extra);
        Collections.shuffle(main, random);
        return new Deck(name, List.copyOf(main), List.copyOf(extra), List.of());
    }

    /** Adds {@code count} cards from {@code from}, one or two copies each, within the banlist. */
    private static void draw(List<Integer> from, int count, Random random, Banlist banlist, Map<Integer, Integer> copies,
                             List<Integer> into) {
        Collections.shuffle(from, random);
        int target = into.size() + count;
        for (int pass = 0; pass < 3 && into.size() < target; pass++) {
            for (int code : from) {
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
