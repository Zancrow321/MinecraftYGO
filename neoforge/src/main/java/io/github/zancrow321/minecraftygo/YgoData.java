package io.github.zancrow321.minecraftygo;

import io.github.zancrow321.minecraftygo.engine.data.Banlist;
import io.github.zancrow321.minecraftygo.engine.data.BoosterSets;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.engine.text.DuelText;

/**
 * The bundled card pool and text, loaded once on first use (client and server alike).
 */
public final class YgoData {
    private static volatile DuelText text;
    private static volatile CardPool pool;
    private static volatile BoosterSets sets;
    private static volatile Banlist banlist;
    /** A server's own banlist replaces the bundled one: {@code config/minecraftygo/banlist.json}. */
    private static final java.nio.file.Path BANLIST_OVERRIDE =
            net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("minecraftygo/banlist.json");

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

    public static BoosterSets sets() {
        BoosterSets s = sets;
        if (s == null) {
            synchronized (YgoData.class) {
                s = sets;
                if (s == null) {
                    s = sets = BoosterSets.loadBundled();
                }
            }
        }
        return s;
    }

    public static Banlist banlist() {
        Banlist b = banlist;
        if (b == null) {
            synchronized (YgoData.class) {
                b = banlist;
                if (b == null) {
                    b = banlist = loadBanlist();
                }
            }
        }
        return b;
    }

    private static Banlist loadBanlist() {
        if (java.nio.file.Files.isRegularFile(BANLIST_OVERRIDE)) {
            try (var reader = java.nio.file.Files.newBufferedReader(BANLIST_OVERRIDE)) {
                Banlist custom = Banlist.load(reader);
                MinecraftYgo.LOGGER.info("Using the banlist {} from {}", custom.name(), BANLIST_OVERRIDE);
                return custom;
            } catch (Exception e) {
                MinecraftYgo.LOGGER.error("Could not read {}; using the bundled banlist", BANLIST_OVERRIDE, e);
            }
        }
        return Banlist.loadBundled();
    }

    public static CardDatabase cards() {
        return text().cards();
    }
}
