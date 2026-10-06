package io.github.zancrow321.minecraftygo;

import io.github.zancrow321.minecraftygo.engine.data.Banlist;
import io.github.zancrow321.minecraftygo.engine.data.BoosterSets;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.engine.data.PoolMode;
import io.github.zancrow321.minecraftygo.engine.data.Products;
import io.github.zancrow321.minecraftygo.engine.text.DuelText;

/**
 * The bundled card data, loaded once on first use (client and server alike), and the pool the server's
 * {@code pool.mode} picks from it. A client on another server learns the mode when it joins.
 */
public final class YgoData {
    private static volatile DuelText text;
    private static volatile CardPool modeled;
    private static volatile Products products;
    /** The pool and booster sets of {@link #mode}; both are rebuilt when the mode changes (another world). */
    private static volatile PoolMode mode;
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

    /** The pool mode a client was told by the server it joined, see {@link #serverPoolMode}. */
    private static volatile PoolMode joinedMode;

    /** The pool mode in the server config, or on a client on another server the one that server sent. */
    public static PoolMode poolMode() {
        if (!YgoServerConfig.SPEC.isLoaded()) {
            return joinedMode == null ? PoolMode.MODELED : joinedMode;
        }
        PoolMode configured = PoolMode.parse(YgoServerConfig.POOL_MODE.get());
        return configured == null ? PoolMode.MODELED : configured;
    }

    /** Called on a client when the server it joined says which pool it plays with. */
    public static void serverPoolMode(PoolMode mode) {
        joinedMode = mode;
    }

    /** The cards this server plays with, and the monster models. */
    public static CardPool pool() {
        refresh();
        return pool;
    }

    /** The booster sets packs, loot and shops draw from. */
    public static BoosterSets sets() {
        refresh();
        return sets;
    }

    private static void refresh() {
        PoolMode wanted = poolMode();
        if (mode == wanted && pool != null) {
            return;
        }
        synchronized (YgoData.class) {
            if (mode == wanted && pool != null) {
                return;
            }
            CardPool p = wanted == PoolMode.ALL ? CardPool.everything(cards(), modeled().models()) : modeled();
            sets = wanted == PoolMode.ALL ? BoosterSets.fromProducts(products(), p) : BoosterSets.loadBundled();
            pool = p;
            mode = wanted;
            MinecraftYgo.LOGGER.info("Card pool {}: {} monsters, {} spells and traps, {} booster sets", wanted.id(),
                    p.monsters().size(), p.spellsTraps().size(), sets.sets().size());
        }
    }

    /** The modeled pool, whatever the mode: the monsters with a model. */
    public static CardPool modeled() {
        CardPool p = modeled;
        if (p == null) {
            synchronized (YgoData.class) {
                p = modeled;
                if (p == null) {
                    p = modeled = CardPool.loadBundled();
                }
            }
        }
        return p;
    }

    /** Models from resource packs, which win over the bundled ones; only ever filled on a client. */
    private static volatile java.util.Map<Integer, CardPool.Model> packModels = java.util.Map.of();

    /** @return the model a monster is drawn with: a resource pack's, else the bundled one, else {@code null} */
    public static CardPool.Model model(int code) {
        CardPool.Model model = packModels.get(code);
        return model != null ? model : modeled().model(code);
    }

    /** Called on a client after resource packs (re)load, with every model they add or replace. */
    public static void packModels(java.util.Map<Integer, CardPool.Model> models) {
        packModels = java.util.Map.copyOf(models);
    }

    /** Every TCG product in release order. */
    public static Products products() {
        Products p = products;
        if (p == null) {
            synchronized (YgoData.class) {
                p = products;
                if (p == null) {
                    p = products = Products.loadBundled();
                }
            }
        }
        return p;
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
