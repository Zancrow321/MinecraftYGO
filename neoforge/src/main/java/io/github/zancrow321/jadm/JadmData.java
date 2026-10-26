package io.github.zancrow321.jadm;

import io.github.zancrow321.jadm.engine.Ruleset;
import io.github.zancrow321.jadm.engine.data.Banlist;
import io.github.zancrow321.jadm.engine.data.Banlists;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.CardPool;
import io.github.zancrow321.jadm.engine.data.OfficialBanlists;
import io.github.zancrow321.jadm.engine.data.PoolMode;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.engine.data.Progression;
import io.github.zancrow321.jadm.engine.text.DuelText;
import io.github.zancrow321.jadm.network.PoolModePayload;
import io.github.zancrow321.jadm.progression.Progress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * The bundled card data, loaded once on first use (client and server alike), and what of it the server plays with:
 * the pool of its {@code pool.mode}, and in a progression world the step a player or the world has reached. A
 * client on another server learns these when it joins.
 */
public final class JadmData {
    private static final Path CONFIG = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("jadm");
    /** A server's own list for the modeled pool, from before {@code banlist} was a setting. */
    private static final Path BANLIST_OVERRIDE = CONFIG.resolve("banlist.json");
    /** The official lists {@code /jadm banlist update} downloaded; they replace the bundled ones with the same id. */
    public static final Path DOWNLOADED_BANLISTS = CONFIG.resolve("official_banlists.json");

    private static final Lazy<DuelText> TEXT = new Lazy<>(() -> DuelText.loadBundled(CardDatabase.loadBundled()));
    private static final Lazy<CardPool> MODELED = new Lazy<>(CardPool::loadBundled);
    private static final Lazy<CardPool> EVERYTHING =
            new Lazy<>(() -> CardPool.everything(cards(), modeled().models()));
    private static final Lazy<Products> BUNDLED_PRODUCTS = new Lazy<>(Products::loadBundled);
    /** The products in the order the server releases them, and the progression through them; see {@link #order}. */
    private static volatile Order order;
    private static final Lazy<Banlists> BUNDLED_BANLISTS = new Lazy<>(Banlists::loadBundled);
    private static final Lazy<OfficialBanlists> BUNDLED_OFFICIAL = new Lazy<>(OfficialBanlists::loadBundled);
    private static final Lazy<io.github.zancrow321.jadm.engine.data.TournamentDecks> TOURNAMENT_DECKS =
            new Lazy<>(() -> io.github.zancrow321.jadm.engine.data.TournamentDecks.loadBundled(cards()));
    private static final Lazy<Banlist> ERA_BANLIST = new Lazy<>(JadmData::loadEraBanlist);
    private static final Lazy<io.github.zancrow321.jadm.engine.data.AltArts> ALT_ARTS =
            new Lazy<>(io.github.zancrow321.jadm.engine.data.AltArts::loadBundled);
    private static final Lazy<BoosterSets> MODELED_SETS = new Lazy<>(BoosterSets::loadBundled);
    private static final Lazy<BoosterSets> ALL_SETS =
            new Lazy<>(() -> BoosterSets.fromProducts(products(), everything()));
    private static final Map<String, Banlist> CUSTOM_BANLISTS = new HashMap<>();
    /** Official lists as banlists, by id and point cap. */
    private static final Map<String, Banlist> OFFICIAL_BANLISTS = new HashMap<>();
    /**
     * The downloaded official lists in use: on the server those in {@link #DOWNLOADED_BANLISTS}, on a client on
     * another server what that server sent. {@code null} until first needed.
     */
    private static volatile OfficialBanlists downloaded;
    /** The bundled lists with the downloaded ones in place, and the TCG history with a newer downloaded list. */
    private static volatile OfficialBanlists official;
    private static volatile Banlists banlists;
    /** The booster sets of the last progression step asked for. */
    private static volatile int setsStep = -1;
    private static volatile BoosterSets stepSets;

    /** What the server a client joined said, see {@link #joined}; unused while this JVM runs the server. */
    private static volatile PoolMode joinedMode;
    private static volatile int joinedStep = -1;
    private static volatile boolean joinedLockedInDeck = true;
    private static volatile String joinedBanlist = "auto";
    private static volatile int joinedPointCap = Banlist.GENESYS_POINT_CAP;

    private JadmData() {
    }

    public static DuelText text() {
        return TEXT.get();
    }

    public static CardDatabase cards() {
        return text().cards();
    }

    /** The alternate artworks that can be pulled. */
    public static io.github.zancrow321.jadm.engine.data.AltArts altArts() {
        return ALT_ARTS.get();
    }

    /** The original card's passcode for an alternate artwork, else the code itself. */
    public static int original(int code) {
        return cards().original(code);
    }

    /** Whether a card is an alternate artwork of another. */
    public static boolean isAltArt(int code) {
        return code > 0 && original(code) != code;
    }

    /**
     * A card pulled from a pack or tin: now and then (the {@code [cards] altArtChance} setting) one of its alternate
     * artworks instead, if it has any.
     */
    public static int pullArtwork(int code, java.util.random.RandomGenerator random) {
        double chance = JadmServerConfig.SPEC.isLoaded() ? JadmServerConfig.ALT_ART_CHANCE.get()
                : io.github.zancrow321.jadm.engine.data.AltArts.DEFAULT_CHANCE;
        return altArts().roll(cards(), code, chance, random);
    }

    /** The pool mode in the server config, or on a client on another server the one that server sent. */
    public static PoolMode poolMode() {
        if (!JadmServerConfig.SPEC.isLoaded()) {
            return joinedMode == null ? PoolMode.PROGRESSION : joinedMode;
        }
        PoolMode configured = PoolMode.parse(JadmServerConfig.POOL_MODE.get());
        return configured == null ? PoolMode.PROGRESSION : configured;
    }

    /** Called on a client when the server it joined says what it plays with. */
    public static void joined(PoolModePayload payload) {
        if (!Progress.running()) {
            releaseDates(io.github.zancrow321.jadm.progression.ReleaseDates.decode(payload.releaseDates()));
        }
        joinedMode = PoolMode.parse(payload.mode());
        joinedStep = progression().indexOf(payload.product());
        joinedLockedInDeck = payload.lockedInDeck();
        joinedBanlist = payload.banlist();
        joinedPointCap = payload.genesysPointCap();
    }

    // ---- progression

    public static Progression progression() {
        return order().progression().get();
    }

    /**
     * The products with the server's release dates ({@code [progression] releaseDates}), product id to date, empty
     * for the real ones.
     */
    public static Map<String, java.time.LocalDate> releaseDates() {
        return order().dates();
    }

    /**
     * Sets the server's release dates: on the server from its config, on a client from the server it joined.
     *
     * @return whether the order changed
     */
    public static synchronized boolean releaseDates(Map<String, java.time.LocalDate> dates) {
        if (order().dates().equals(dates)) {
            return false;
        }
        order = Order.of(Map.copyOf(dates));
        setsStep = -1;
        stepSets = null;
        return true;
    }

    private static Order order() {
        Order o = order;
        if (o == null) {
            synchronized (JadmData.class) {
                o = order;
                if (o == null) {
                    o = order = Order.of(Map.of());
                }
            }
        }
        return o;
    }

    /** The products in release order with some dates moved, and the progression through them, built on first use. */
    private record Order(Map<String, java.time.LocalDate> dates, Products products, Lazy<Progression> progression) {
        static Order of(Map<String, java.time.LocalDate> dates) {
            Products products = BUNDLED_PRODUCTS.get().withReleaseDates(dates);
            return new Order(dates, products, new Lazy<>(() -> new Progression(products, cards(), everything(),
                    BUNDLED_BANLISTS.get())));
        }
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
        return JadmServerConfig.SPEC.isLoaded() ? JadmServerConfig.LOCKED_IN_DECK.get() : joinedLockedInDeck;
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
        // An alternate artwork may go wherever its original may (and the other way round).
        return code -> pool.contains(code) || pool.contains(original(code)) || altArts().of(cards(), code).stream()
                .anyMatch(pool::contains);
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
                || poolMode() != PoolMode.MODELED && io.github.zancrow321.jadm.item.SealedProductItem.sealed(p))
                .toList();
    }

    /** The hand-kept tournament decks NPCs play. */
    public static io.github.zancrow321.jadm.engine.data.TournamentDecks tournamentDecks() {
        return TOURNAMENT_DECKS.get();
    }

    /** Every TCG product in release order, with the server's {@link #releaseDates}. */
    public static Products products() {
        return order().products();
    }

    /** Every TCG product in real release order. */
    public static Products bundledProducts() {
        return BUNDLED_PRODUCTS.get();
    }

    // ---- models

    /** Models from resource packs, which win over the bundled ones; only ever filled on a client. */
    private static volatile Map<Integer, CardPool.Model> packModels = Map.of();

    /** @return the model a monster is drawn with: a resource pack's, else the bundled one, else {@code null} */
    public static CardPool.Model model(int code) {
        CardPool.Model model = ownModel(code);
        if (model != null || code <= 0) {
            return model;
        }
        // An alternate artwork looks like its original, and the other way round.
        int original = original(code);
        model = original == code ? null : ownModel(original);
        for (int alt : altArts().of(cards(), code)) {
            if (model == null && alt != code) {
                model = ownModel(alt);
            }
        }
        return model;
    }

    private static CardPool.Model ownModel(int code) {
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
        return ruleset(step, rulesetSetting(), banlist(step));
    }

    /** The server's {@code ruleset} setting ("auto" on a client on another server). */
    public static String rulesetSetting() {
        return JadmServerConfig.SPEC.isLoaded() ? JadmServerConfig.RULESET.get() : "auto";
    }

    /** The rules for a {@code ruleset} setting, where "auto" is Genesys under a Genesys list. */
    public static Ruleset ruleset(int step, String setting, Banlist banlist) {
        if (setting.strip().equalsIgnoreCase("auto") && banlist.genesys()) {
            return Ruleset.GENESYS;
        }
        return ruleset(step, setting);
    }

    /** The rules for a {@code ruleset} setting such as "auto" or "goat", at a progression step. */
    public static Ruleset ruleset(int step, String setting) {
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

    /**
     * The banlist for a player: their own list if an operator gave them one, otherwise the server's (in a progression
     * world the one of their step).
     */
    public static Banlist banlist(Player player) {
        return banlist(step(player), banlistSetting(player));
    }

    /** The banlist at a progression step (any step outside a progression world). */
    public static Banlist banlist(int step) {
        return banlist(step, banlistSetting());
    }

    /** The server's {@code banlist} setting, or on a client on another server the one that server sent. */
    public static String banlistSetting() {
        return JadmServerConfig.SPEC.isLoaded() ? JadmServerConfig.BANLIST.get() : joinedBanlist;
    }

    /**
     * The {@code banlist} setting a player plays with: on the server their own ({@code /jadm banlist use <list>
     * <player>}) or else the server's, on a client the one the server sent for them.
     */
    public static String banlistSetting(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            String own = io.github.zancrow321.jadm.banlist.PlayerBanlists.get(serverPlayer.server)
                    .setting(serverPlayer.getUUID());
            return own != null ? own : banlistSetting();
        }
        return player != null && joinedMode != null ? joinedBanlist : banlistSetting();
    }

    /** On a client, the {@code banlist} setting the server sent for the local player. */
    public static String localBanlistSetting() {
        return joinedMode != null ? joinedBanlist : banlistSetting();
    }

    /**
     * The banlist for a {@code banlist} setting such as "auto", "none", an official list ("genesys", "ocg", ...) or a
     * file name, at a progression step. A file in {@code config/jadm/banlists/} wins over an official list of its name.
     */
    public static Banlist banlist(int step, String setting) {
        setting = setting.strip();
        if (setting.equalsIgnoreCase("none")) {
            return new Banlist("none", Map.of());
        }
        if (!setting.equalsIgnoreCase("auto")) {
            Banlist chosen = !hasCustomBanlist(setting) && isOfficial(setting) ? officialBanlist(setting)
                    : customBanlist(setting);
            if (chosen != null) {
                return chosen;
            }
        }
        return switch (poolMode()) {
            case MODELED -> ERA_BANLIST.get();
            case ALL -> banlists().latest();
            case PROGRESSION -> banlists().at(progression().date(step));
        };
    }

    /** Every TCG Forbidden & Limited List, with the newest downloaded one if it is newer than the bundled ones. */
    public static Banlists banlists() {
        official();
        return banlists;
    }

    /** The official lists a {@code banlist} setting can name, the downloaded ones in place of the bundled. */
    public static OfficialBanlists official() {
        OfficialBanlists o = official;
        if (o == null) {
            synchronized (OFFICIAL_BANLISTS) {
                if (official == null) {
                    if (downloaded == null) {
                        downloaded = readDownloaded();
                    }
                    apply(downloaded);
                }
                o = official;
            }
        }
        return o;
    }

    /** The downloaded official lists in use (empty if none were). */
    public static OfficialBanlists downloaded() {
        official();
        return downloaded;
    }

    /** Whether {@code id} names an official list: "tcg" or one of {@link #official()}. */
    public static boolean isOfficial(String id) {
        return id.equalsIgnoreCase(OfficialBanlists.TCG) || official().get(id) != null;
    }

    /** Puts newly downloaded lists to use (on a client: the server's), so every banlist is looked up again. */
    public static void useDownloaded(OfficialBanlists lists) {
        synchronized (OFFICIAL_BANLISTS) {
            downloaded = lists;
            apply(lists);
        }
    }

    /** Reads {@link #DOWNLOADED_BANLISTS} again, as when a server starts. */
    public static void reloadDownloaded() {
        useDownloaded(readDownloaded());
    }

    /** Called on a client when the server it joined says which lists it downloaded (empty JSON for none). */
    public static void joinedOfficial(String json) {
        try {
            useDownloaded(json.isEmpty() ? new OfficialBanlists(List.of()) : OfficialBanlists.parse(json));
        } catch (RuntimeException e) {
            Jadm.LOGGER.warn("The server sent official banlists that couldn't be read", e);
        }
    }

    private static void apply(OfficialBanlists lists) {
        OfficialBanlists bundled = BUNDLED_OFFICIAL.get();
        OfficialBanlists merged = bundled;
        Banlists history = BUNDLED_BANLISTS.get();
        for (OfficialBanlists.Entry e : lists.lists()) {
            if (e.id().equalsIgnoreCase(OfficialBanlists.TCG)) {
                history = history.withNewer(new Banlists.Dated(e.date(), new Banlist(e.name(), e.limits())));
            } else if (bundled.get(e.id()) == null || !e.date().isBefore(bundled.get(e.id()).date())) {
                merged = merged.with(e);
            }
        }
        OFFICIAL_BANLISTS.clear();
        banlists = history;
        official = merged;
    }

    private static OfficialBanlists readDownloaded() {
        if (Files.isRegularFile(DOWNLOADED_BANLISTS)) {
            try (var reader = Files.newBufferedReader(DOWNLOADED_BANLISTS)) {
                return OfficialBanlists.load(reader);
            } catch (Exception e) {
                Jadm.LOGGER.error("Could not read {}; using the bundled official banlists", DOWNLOADED_BANLISTS, e);
            }
        }
        return new OfficialBanlists(List.of());
    }

    /** The Genesys point cap of the server (on a client on another server, the one it sent). */
    public static int genesysPointCap() {
        return JadmServerConfig.SPEC.isLoaded() ? JadmServerConfig.GENESYS_POINT_CAP.get() : joinedPointCap;
    }

    /** An official list by id ("tcg" is the newest TCG list), Genesys ones with the server's point cap. */
    public static Banlist officialBanlist(String id) {
        if (id.equalsIgnoreCase(OfficialBanlists.TCG)) {
            return banlists().latest();
        }
        OfficialBanlists.Entry entry = official().get(id);
        if (entry == null) {
            return null;
        }
        int cap = genesysPointCap();
        String key = entry.id() + "@" + cap;
        synchronized (OFFICIAL_BANLISTS) {
            return OFFICIAL_BANLISTS.computeIfAbsent(key, k -> entry.banlist(cards(), cap));
        }
    }

    private static boolean hasCustomBanlist(String name) {
        return name.matches("[A-Za-z0-9_.-]+") && Files.isRegularFile(CONFIG.resolve("banlists").resolve(name + ".json"));
    }

    /** A banlist from {@code config/jadm/banlists/<name>.json}, or {@code null} (logged) if unreadable. */
    private static Banlist customBanlist(String name) {
        synchronized (CUSTOM_BANLISTS) {
            if (CUSTOM_BANLISTS.containsKey(name)) {
                return CUSTOM_BANLISTS.get(name);
            }
            Path file = CONFIG.resolve("banlists").resolve(name + ".json");
            Banlist banlist = null;
            if (!name.matches("[A-Za-z0-9_.-]+") || !Files.isRegularFile(file)) {
                Jadm.LOGGER.warn("No banlist {}; using the automatic one", file);
            } else {
                try (var reader = Files.newBufferedReader(file)) {
                    banlist = Banlist.load(reader);
                    if (banlist.genesys()) {
                        banlist = Banlist.genesys(banlist.name(), banlist.limits(), banlist.points(),
                                banlist.pointCap(), cards());
                    }
                    Jadm.LOGGER.info("Using the banlist {} from {}", banlist.name(), file);
                } catch (Exception e) {
                    Jadm.LOGGER.error("Could not read {}; using the automatic banlist", file, e);
                }
            }
            CUSTOM_BANLISTS.put(name, banlist);
            return banlist;
        }
    }

    /** The bundled May 2000 list, or a server's own {@code config/jadm/banlist.json}. */
    private static Banlist loadEraBanlist() {
        if (Files.isRegularFile(BANLIST_OVERRIDE)) {
            try (var reader = Files.newBufferedReader(BANLIST_OVERRIDE)) {
                Banlist custom = Banlist.load(reader);
                Jadm.LOGGER.info("Using the banlist {} from {}", custom.name(), BANLIST_OVERRIDE);
                return custom;
            } catch (Exception e) {
                Jadm.LOGGER.error("Could not read {}; using the bundled banlist", BANLIST_OVERRIDE, e);
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
