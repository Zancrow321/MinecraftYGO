package io.github.zancrow321.minecraftygo;

import io.github.zancrow321.minecraftygo.engine.Ruleset;
import io.github.zancrow321.minecraftygo.engine.data.Banlist;
import io.github.zancrow321.minecraftygo.engine.data.Banlists;
import io.github.zancrow321.minecraftygo.engine.data.BoosterSets;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.engine.data.PoolMode;
import io.github.zancrow321.minecraftygo.engine.data.Products;
import io.github.zancrow321.minecraftygo.engine.data.Progression;
import io.github.zancrow321.minecraftygo.engine.text.DuelText;
import io.github.zancrow321.minecraftygo.network.PoolModePayload;
import io.github.zancrow321.minecraftygo.progression.Progress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * The bundled card data, loaded once on first use (client and server alike), and what of it the server plays with:
 * the pool of its {@code pool.mode}, and in a progression world the step a player or the world has reached. A
 * client on another server learns these when it joins.
 */
public final class YgoData {
    private static final Path CONFIG = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("minecraftygo");
    /** A server's own list for the modeled pool, from before {@code banlist} was a setting. */
    private static final Path BANLIST_OVERRIDE = CONFIG.resolve("banlist.json");

    private static final Lazy<DuelText> TEXT = new Lazy<>(() -> DuelText.loadBundled(CardDatabase.loadBundled()));
    private static final Lazy<CardPool> MODELED = new Lazy<>(CardPool::loadBundled);
    private static final Lazy<CardPool> EVERYTHING =
            new Lazy<>(() -> CardPool.everything(cards(), modeled().models()));
    private static final Lazy<Products> PRODUCTS = new Lazy<>(Products::loadBundled);
    private static final Lazy<Progression> PROGRESSION = new Lazy<>(() -> new Progression(products(), cards(),
            everything(), banlists()));
    private static final Lazy<Banlists> BANLISTS = new Lazy<>(Banlists::loadBundled);
    private static final Lazy<Banlist> ERA_BANLIST = new Lazy<>(YgoData::loadEraBanlist);
    private static final Lazy<BoosterSets> MODELED_SETS = new Lazy<>(BoosterSets::loadBundled);
    private static final Lazy<BoosterSets> ALL_SETS =
            new Lazy<>(() -> BoosterSets.fromProducts(products(), everything()));
    private static final Map<String, Banlist> CUSTOM_BANLISTS = new HashMap<>();
    /** The booster sets of the last progression step asked for. */
    private static volatile int setsStep = -1;
    private static volatile BoosterSets stepSets;

    /** What the server a client joined said, see {@link #joined}; unused while this JVM runs the server. */
    private static volatile PoolMode joinedMode;
    private static volatile int joinedStep = -1;
    private static volatile boolean joinedLockedInDeck = true;
    private static volatile String joinedBanlist = "auto";

    private YgoData() {
    }

    public static DuelText text() {
        return TEXT.get();
    }

    public static CardDatabase cards() {
        return text().cards();
    }

    /** The pool mode in the server config, or on a client on another server the one that server sent. */
    public static PoolMode poolMode() {
        if (!YgoServerConfig.SPEC.isLoaded()) {
            return joinedMode == null ? PoolMode.PROGRESSION : joinedMode;
        }
        PoolMode configured = PoolMode.parse(YgoServerConfig.POOL_MODE.get());
        return configured == null ? PoolMode.PROGRESSION : configured;
    }

    /** Called on a client when the server it joined says what it plays with. */
    public static void joined(PoolModePayload payload) {
        joinedMode = PoolMode.parse(payload.mode());
        joinedStep = progression().indexOf(payload.product());
        joinedLockedInDeck = payload.lockedInDeck();
        joinedBanlist = payload.banlist();
    }

    // ---- progression

    public static Progression progression() {
        return PROGRESSION.get();
    }

    /**
     * The progression step a player has reached: on the server the world's or (per player) their own, on a client
     * the one the server last sent. {@code null} means the world's.
     */
    public static int step(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return Progress.step(serverPlayer);
        }
        if ((player != null || !Progress.running()) && joinedStep >= 0) {
            return joinedStep;
        }
        return Progress.worldStep();
    }

    /** Whether locked cards stay out of decks in a progression world. */
    public static boolean lockedInDeck() {
        return YgoServerConfig.SPEC.isLoaded() ? YgoServerConfig.LOCKED_IN_DECK.get() : joinedLockedInDeck;
    }

    // ---- pools

    /** The modeled pool, whatever the mode: the monsters with a model. */
    public static CardPool modeled() {
        return MODELED.get();
    }

    /** Every playable card. */
    public static CardPool everything() {
        return EVERYTHING.get();
    }

    /** The cards in play for the world: packs, loot and NPCs without anyone in particular draw from these. */
    public static CardPool pool() {
        return pool(null);
    }

    /** The cards in play for a player ({@code null}: the world). */
    public static CardPool pool(Player player) {
        return switch (poolMode()) {
            case MODELED -> modeled();
            case ALL -> everything();
            case PROGRESSION -> progression().pool(step(player));
        };
    }

    /** The cards a player's deck may hold: their pool, or every card when locked cards are allowed in decks. */
    public static IntPredicate playable(Player player) {
        CardPool pool = poolMode() == PoolMode.PROGRESSION && !lockedInDeck() ? everything() : pool(player);
        return pool::contains;
    }

    /** Whether a card is still locked for a player in a progression world. */
    public static boolean locked(Player player, int code) {
        return poolMode() == PoolMode.PROGRESSION && !pool(player).contains(code);
    }

    // ---- booster sets

    /** The booster sets packs, loot and shops draw from. */
    public static BoosterSets sets() {
        return switch (poolMode()) {
            case MODELED -> MODELED_SETS.get();
            case ALL -> ALL_SETS.get();
            case PROGRESSION -> {
                int step = step(null);
                if (setsStep != step || stepSets == null) {
                    stepSets = progression().sets(step);
                    setsStep = step;
                }
                yield stepSets;
            }
        };
    }

    /** A booster set by id, whether or not it is unlocked, so that every pack opens to its own cards. */
    public static BoosterSets.BoosterSet set(String id) {
        BoosterSets.BoosterSet set = sets().get(id);
        return set != null ? set : ALL_SETS.get().get(id);
    }

    /** Every TCG booster, for the creative tab. */
    public static BoosterSets allSets() {
        return ALL_SETS.get();
    }

    /**
     * What card traders can sell, oldest first: the booster sets (not tins' own packs), structure decks and tins
     * that are out.
     */
    public static java.util.List<Products.Product> shopProducts() {
        java.util.List<Products.Product> candidates = switch (poolMode()) {
            case MODELED -> MODELED_SETS.get().sets().keySet().stream().map(id -> products().get(id))
                    .filter(java.util.Objects::nonNull).toList();
            case ALL -> products().products();
            case PROGRESSION -> products().products().subList(0, step(null) + 1);
        };
        return candidates.stream().filter(p -> p.kind() != Products.Kind.TIN && set(p.id()) != null
                || poolMode() != PoolMode.MODELED && io.github.zancrow321.minecraftygo.item.SealedProductItem.sealed(p))
                .toList();
    }

    /** Every TCG product in release order. */
    public static Products products() {
        return PRODUCTS.get();
    }

    // ---- models

    /** Models from resource packs, which win over the bundled ones; only ever filled on a client. */
    private static volatile Map<Integer, CardPool.Model> packModels = Map.of();

    /** @return the model a monster is drawn with: a resource pack's, else the bundled one, else {@code null} */
    public static CardPool.Model model(int code) {
        CardPool.Model model = packModels.get(code);
        return model != null ? model : modeled().model(code);
    }

    /** Called on a client after resource packs (re)load, with every model they add or replace. */
    public static void packModels(Map<Integer, CardPool.Model> models) {
        packModels = Map.copyOf(models);
    }

    // ---- rules and banlists

    /** The rules at a progression step (any step outside a progression world). */
    public static Ruleset ruleset(int step) {
        String setting = YgoServerConfig.SPEC.isLoaded() ? YgoServerConfig.RULESET.get() : "auto";
        if (!setting.strip().equalsIgnoreCase("auto")) {
            return Ruleset.parse(setting);
        }
        return switch (poolMode()) {
            case MODELED -> Ruleset.MR1;
            case ALL -> Ruleset.MODERN;
            case PROGRESSION -> progression().ruleset(step);
        };
    }

    /** The banlist for the world. */
    public static Banlist banlist() {
        return banlist(step(null));
    }

    /** The banlist for a player: in a progression world the one of their step. */
    public static Banlist banlist(Player player) {
        return banlist(step(player));
    }

    /** The banlist at a progression step (any step outside a progression world). */
    public static Banlist banlist(int step) {
        String setting = (YgoServerConfig.SPEC.isLoaded() ? YgoServerConfig.BANLIST.get() : joinedBanlist).strip();
        if (setting.equalsIgnoreCase("none")) {
            return new Banlist("none", Map.of());
        }
        if (!setting.equalsIgnoreCase("auto")) {
            Banlist custom = customBanlist(setting);
            if (custom != null) {
                return custom;
            }
        }
        return switch (poolMode()) {
            case MODELED -> ERA_BANLIST.get();
            case ALL -> banlists().latest();
            case PROGRESSION -> progression().banlist(step);
        };
    }

    public static Banlists banlists() {
        return BANLISTS.get();
    }

    /** A banlist from {@code config/minecraftygo/banlists/<name>.json}, or {@code null} (logged) if unreadable. */
    private static Banlist customBanlist(String name) {
        synchronized (CUSTOM_BANLISTS) {
            if (CUSTOM_BANLISTS.containsKey(name)) {
                return CUSTOM_BANLISTS.get(name);
            }
            Path file = CONFIG.resolve("banlists").resolve(name + ".json");
            Banlist banlist = null;
            if (!name.matches("[A-Za-z0-9_.-]+") || !Files.isRegularFile(file)) {
                MinecraftYgo.LOGGER.warn("No banlist {}; using the automatic one", file);
            } else {
                try (var reader = Files.newBufferedReader(file)) {
                    banlist = Banlist.load(reader);
                    MinecraftYgo.LOGGER.info("Using the banlist {} from {}", banlist.name(), file);
                } catch (Exception e) {
                    MinecraftYgo.LOGGER.error("Could not read {}; using the automatic banlist", file, e);
                }
            }
            CUSTOM_BANLISTS.put(name, banlist);
            return banlist;
        }
    }

    /** The bundled May 2000 list, or a server's own {@code config/minecraftygo/banlist.json}. */
    private static Banlist loadEraBanlist() {
        if (Files.isRegularFile(BANLIST_OVERRIDE)) {
            try (var reader = Files.newBufferedReader(BANLIST_OVERRIDE)) {
                Banlist custom = Banlist.load(reader);
                MinecraftYgo.LOGGER.info("Using the banlist {} from {}", custom.name(), BANLIST_OVERRIDE);
                return custom;
            } catch (Exception e) {
                MinecraftYgo.LOGGER.error("Could not read {}; using the bundled banlist", BANLIST_OVERRIDE, e);
            }
        }
        return Banlist.loadBundled();
    }

    /** A value built on first use, once. */
    private static final class Lazy<T> {
        private final Supplier<T> supplier;
        private volatile T value;

        Lazy(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        T get() {
            T v = value;
            if (v == null) {
                synchronized (this) {
                    v = value;
                    if (v == null) {
                        v = value = supplier.get();
                    }
                }
            }
            return v;
        }
    }
}
