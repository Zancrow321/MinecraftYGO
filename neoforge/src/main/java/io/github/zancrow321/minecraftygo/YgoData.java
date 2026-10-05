package io.github.zancrow321.minecraftygo;

import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.engine.text.DuelText;

/**
 * The bundled card pool and text, loaded once on first use (client and server alike).
 */
public final class YgoData {
    private static volatile DuelText text;
    private static volatile CardPool pool;

    private YgoData() {
    }

    public static DuelText text() {
        DuelText t = text;
        if (t == null) {
            synchronized (YgoData.class) {
                t = text;
                if (t == null) {
                    t = text = DuelText.loadBundled(CardDatabase.loadBundled());
                }
            }
        }
        return t;
    }

    public static CardPool pool() {
        CardPool p = pool;
        if (p == null) {
            synchronized (YgoData.class) {
                p = pool;
                if (p == null) {
                    p = pool = CardPool.loadBundled();
                }
            }
        }
        return p;
    }

    public static CardDatabase cards() {
        return text().cards();
    }
}
