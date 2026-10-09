package io.github.zancrow321.jadm.collection;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.engine.data.CardPool;
import io.github.zancrow321.jadm.engine.data.PoolMode;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.item.SealedProductItem;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the Set Collection Book lists, the same on the server and the client: every set that is out (in a
 * progression world, the products a player has unlocked) and comes as booster packs or a sealed deck, with the
 * different cards in it. A card counts once whatever its rarity, and an alternate artwork counts as the original.
 */
public final class SetBook {
    /** The last sets worked out, for the pool mode and step they were worked out for. */
    private static volatile Cache cache;

    private SetBook() {
    }

    /**
     * A set in the book.
     *
     * @param cards the different cards in it, as original passcodes, in the product's order
     */
    public record Entry(Products.Product product, int[] cards) {
        public int size() {
            return cards.length;
        }

        /** How many of the set's cards are in {@code owned}. */
        public int owned(Set<Integer> owned) {
            int n = 0;
            for (int code : cards) {
                if (owned.contains(code)) {
                    n++;
                }
            }
            return n;
        }
    }

    private record Cache(PoolMode mode, int step, List<Entry> sets) {
    }

    /** Every set a player can collect, oldest first. */
    public static List<Entry> sets(Player player) {
        PoolMode mode = JadmData.poolMode();
        int step = mode == PoolMode.PROGRESSION ? JadmData.step(player) : -1;
        Cache c = cache;
        if (c == null || c.mode() != mode || c.step() != step) {
            c = new Cache(mode, step, build(mode, step, JadmData.pool(player)));
            cache = c;
        }
        return c.sets();
    }

    /** @return the set with this product id, or {@code null} if the player can't collect it */
    public static Entry set(Player player, String id) {
        for (Entry e : sets(player)) {
            if (e.product().id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    private static List<Entry> build(PoolMode mode, int step, CardPool pool) {
        List<Products.Product> products = JadmData.products().products();
        int last = mode == PoolMode.PROGRESSION ? Math.min(step, products.size() - 1) : products.size() - 1;
        List<Entry> sets = new ArrayList<>();
        for (int i = 0; i <= last; i++) {
            Products.Product product = products.get(i);
            if (!collectable(product)) {
                continue;
            }
            Set<Integer> cards = new LinkedHashSet<>();
            for (Products.Printing printing : product.cards()) {
                if (pool.contains(printing.code())) {
                    cards.add(original(printing.code()));
                }
            }
            // A set mostly made of cards the modeled pool leaves out can't really be collected.
            if (cards.size() >= Math.min(10, product.cards().size())) {
                sets.add(new Entry(product, cards.stream().mapToInt(Integer::intValue).toArray()));
            }
        }
        return List.copyOf(sets);
    }

    /** Whether a product is a set of its own: one that comes in packs, or a sealed structure deck or tin. */
    public static boolean collectable(Products.Product product) {
        return BoosterSets.isBooster(product)
                || SealedProductItem.sealed(product) && product.kind() == Products.Kind.DECK;
    }

    /** The passcode a card counts as: the original's for an alternate artwork, else its own. */
    public static int original(int code) {
        CardInfo card = JadmData.cards().card(code);
        return card == null ? code : CardPool.alternateArtOf(card);
    }
}
