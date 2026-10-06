package io.github.zancrow321.minecraftygo.engine.data;

import io.github.zancrow321.minecraftygo.engine.Ruleset;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * A world that unlocks the TCG products one after another, in release order. A step is the index of the newest
 * unlocked product in {@link Products#products()}; every product up to it is unlocked, reprint-only ones included.
 *
 * <p>A card is unlocked with the first TCG product it was printed in. Cards never printed in the TCG are unlocked
 * once the step's date reaches their OCG release, and the few cards no product lists only with the last step. The
 * rules follow the extra deck monsters that are out (Master Rule 1 until the first Xyz, 2 until the first Pendulum,
 * 3 until the first Link, 4 until April 2020, then 5), and the banlist is the TCG list in force on the step's date.
 */
public final class Progression {
    /** The day Master Rule 5 took effect in the TCG. */
    public static final LocalDate MASTER_RULE_5 = LocalDate.of(2020, 4, 1);

    private final Products products;
    private final CardDatabase cards;
    private final CardPool everything;
    private final Banlists banlists;
    /** original passcode (see {@link CardPool#alternateArtOf}) to the index of the first product it is new in */
    private final Map<Integer, Integer> firstProduct = new HashMap<>();
    private final Set<Integer> starterCards = new HashSet<>();
    private final int firstXyz;
    private final int firstPendulum;
    private final int firstLink;
    /** The pools of the last few steps asked for: building one walks every card. */
    private final Map<Integer, CardPool> pools = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, CardPool> eldest) {
            return size() > 8;
        }
    };

    public Progression(Products products, CardDatabase cards, CardPool everything, Banlists banlists) {
        this.products = products;
        this.cards = cards;
        this.everything = everything;
        this.banlists = banlists;
        int xyz = Integer.MAX_VALUE;
        int pendulum = Integer.MAX_VALUE;
        int link = Integer.MAX_VALUE;
        List<Products.Product> list = products.products();
        for (int i = 0; i < list.size(); i++) {
            for (int code : list.get(i).newCards()) {
                CardInfo card = cards.card(code);
                if (card == null) {
                    firstProduct.putIfAbsent(code, i);
                    continue;
                }
                // All artworks of a card unlock together: YGOPRODeck sometimes lists a set under another artwork
                // (Dark Magician's LOB print is 46986420).
                firstProduct.putIfAbsent(CardPool.alternateArtOf(card), i);
                xyz = card.is(TYPE_XYZ) ? Math.min(xyz, i) : xyz;
                pendulum = card.is(TYPE_PENDULUM) ? Math.min(pendulum, i) : pendulum;
                link = card.is(TYPE_LINK) ? Math.min(link, i) : link;
            }
        }
        firstXyz = xyz;
        firstPendulum = pendulum;
        firstLink = link;
        for (String deck : CardPool.STARTER_DECKS) {
            Deck starter = Deck.bundled(deck);
            starterCards.addAll(starter.main());
            starterCards.addAll(starter.extra());
        }
    }

    public static Progression loadBundled(CardDatabase cards, CardPool modeled) {
        return new Progression(Products.loadBundled(), cards, CardPool.everything(cards, modeled.models()),
                Banlists.loadBundled());
    }

    public Products products() {
        return products;
    }

    /** The number of steps, one per product. */
    public int size() {
        return products.products().size();
    }

    public int last() {
        return size() - 1;
    }

    /** The newest unlocked product at a step. */
    public Products.Product product(int step) {
        return products.products().get(clamp(step));
    }

    public LocalDate date(int step) {
        return product(step).date();
    }

    private int clamp(int step) {
        return Math.max(0, Math.min(last(), step));
    }

    /** @return the step of the product with this id, or -1 */
    public int indexOf(String id) {
        List<Products.Product> list = products.products();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds a step by what a person types: a set code ({@code MRD}), a product id or a date ({@code 2005-03-01},
     * the newest product released by then).
     *
     * @return the step, or -1 if nothing matches
     */
    public int find(String target) {
        String t = target.strip();
        try {
            LocalDate date = LocalDate.parse(t);
            int found = -1;
            for (int i = 0; i < size(); i++) {
                if (!products.products().get(i).date().isAfter(date)) {
                    found = i;
                }
            }
            return found;
        } catch (DateTimeParseException notADate) {
            // a code or an id
        }
        int byId = indexOf(t.toLowerCase(Locale.ROOT));
        if (byId >= 0) {
            return byId;
        }
        // Promos share their code; the first product with new cards under it is the one people mean.
        int fallback = -1;
        for (int i = 0; i < size(); i++) {
            Products.Product p = products.products().get(i);
            if (p.code().equalsIgnoreCase(t)) {
                if (!p.newCards().isEmpty()) {
                    return i;
                }
                fallback = fallback < 0 ? i : fallback;
            }
        }
        return fallback;
    }

    /**
     * @return the step after unlocking {@code count} more products with new cards; the reprint-only products in
     *         between come along. Stops at the last product.
     */
    public int next(int step, int count) {
        int s = clamp(step);
        for (int n = 0; n < count && s < last(); n++) {
            s++;
            while (s < last() && products.products().get(s).newCards().isEmpty()) {
                s++;
            }
        }
        return s;
    }

    /** The products a step unlocks with new cards next, at most {@code count}. */
    public List<Products.Product> upcoming(int step, int count) {
        List<Products.Product> out = new ArrayList<>();
        int s = clamp(step);
        while (out.size() < count && s < last()) {
            s = next(s, 1);
            out.add(product(s));
        }
        return out;
    }

    /**
     * @return the product that unlocks a card (for an alternate artwork, the original's), or {@code null} for a card
     *         no TCG product lists
     */
    public Products.Product unlockedBy(int code) {
        Integer first = first(code);
        return first == null ? null : products.products().get(first);
    }

    /** The step that unlocks a card's artworks, or {@code null}. */
    private Integer first(int code) {
        CardInfo card = cards.card(code);
        if (card == null) {
            return firstProduct.get(code);
        }
        Integer first = firstProduct.get(CardPool.alternateArtOf(card));
        return first != null || card.data().alias() == 0 ? first : firstProduct.get(card.data().alias());
    }

    /** Whether a card is unlocked at a step (its own printing or, for an alternate artwork, the original's). */
    public boolean unlocked(int code, int step) {
        if (step >= last() || starterCards.contains(code)) {
            return true;
        }
        Integer first = first(code);
        CardInfo card = cards.card(code);
        int original = card == null ? code : card.data().alias() != 0 ? card.data().alias() : code;
        if (first != null) {
            return first <= step;
        }
        LocalDate ocg = products.ocgOnly().getOrDefault(code, products.ocgOnly().get(original));
        return ocg != null && !ocg.isAfter(date(step));
    }

    /** The cards in play at a step: every unlocked card, and the starter decks. */
    public CardPool pool(int step) {
        int s = clamp(step);
        synchronized (pools) {
            CardPool cached = pools.get(s);
            if (cached != null) {
                return cached;
            }
        }
        List<Integer> monsters = everything.monsters().stream().filter(c -> unlocked(c, s)).toList();
        List<Integer> spellsTraps = everything.spellsTraps().stream().filter(c -> unlocked(c, s)).toList();
        Set<Integer> playable = new HashSet<>(starterCards);
        everything.playable().stream().filter(c -> unlocked(c, s)).forEach(playable::add);
        CardPool pool = new CardPool(date(s).toString(), monsters, spellsTraps, everything.models(), playable);
        synchronized (pools) {
            pools.put(s, pool);
        }
        return pool;
    }

    /** The booster products unlocked at a step, with their cards. */
    public BoosterSets sets(int step) {
        int s = clamp(step);
        return BoosterSets.fromProducts(new Products(products.products().subList(0, s + 1), Map.of()), everything);
    }

    /** The rules at a step. */
    public Ruleset ruleset(int step) {
        int s = clamp(step);
        if (s < firstXyz) {
            return Ruleset.MR1;
        }
        if (s < firstPendulum) {
            return Ruleset.MR2;
        }
        if (s < firstLink) {
            return Ruleset.MR3;
        }
        return date(s).isBefore(MASTER_RULE_5) ? Ruleset.MR4 : Ruleset.MODERN;
    }

    /** The TCG banlist in force at a step. */
    public Banlist banlist(int step) {
        return banlists.at(date(step));
    }
}
