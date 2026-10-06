package io.github.zancrow321.minecraftygo;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-world settings, in {@code serverconfig/minecraftygo-server.toml}.
 */
public final class YgoServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue STARTER_DECKS = BUILDER
            .comment("Players without a legal deck box duel with a starter deck instead of being turned away.")
            .define("starterDecksWithoutDeckBox", true);

    /** Bumped when a release changes defaults that older config files should pick up, see {@link #migrate}. */
    private static final int VERSION = 2;

    public static final ModConfigSpec.IntValue CONFIG_VERSION = BUILDER
            .comment("Used by the mod to update older settings; leave it alone.")
            .defineInRange("configVersion", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> RULESET = BUILDER
            .comment("The rules duels are played under: \"auto\" (the rules of the newest unlocked cards in a "
                    + "progression world, Master Rule 1 for the modeled pool, today's for all cards), \"mr1\" "
                    + "(original, as the cards were printed), \"goat\" (the 2005 TCG format), \"mr2\", \"mr3\", "
                    + "\"mr4\" or \"modern\" (today's Master Rule).")
            .define("ruleset", "auto");

    public static final ModConfigSpec.ConfigValue<String> BANLIST = BUILDER
            .comment("The Forbidden & Limited List: \"auto\" (the TCG list of the time in a progression world, the "
                    + "May 2000 list for the modeled pool, today's for all cards), \"none\", or the name of a file in "
                    + "config/minecraftygo/banlists/ without .json, e.g. \"goat\" for goat.json: "
                    + "{\"name\": \"...\", \"limits\": {\"<passcode>\": <copies>}}.")
            .define("banlist", "auto");

    public static final ModConfigSpec.IntValue STARTING_LIFE_POINTS = BUILDER
            .comment("Life points each duelist (or tag team) starts with.")
            .defineInRange("startingLifePoints", 8000, 100, 1_000_000);

    public static final ModConfigSpec.BooleanValue ALLOW_ANTE = BUILDER
            .comment("Allow ante duels, where each duelist puts up a random card from their deck box and the "
                    + "winner takes both.")
            .define("allowAnte", true);

    public static final ModConfigSpec.IntValue TURN_TIME_LIMIT = BUILDER
            .comment("Seconds each person has for their choices in each turn; 0 means no limit. Once it runs out, "
                    + "the bot makes their choices until the turn ends.")
            .defineInRange("turnTimeLimit", 0, 0, 3600);

    public static final ModConfigSpec.ConfigValue<String> POOL_MODE = BUILDER.push("pool")
            .comment("Which cards are in play: \"progression\" (every official card, unlocked product by product in "
                    + "TCG release order with /ygo progression), \"modeled\" (monsters with a 3D model and the spells "
                    + "and traps of their era) or \"all\" (every official card; packs come from every TCG booster).")
            .define("mode", "progression", v -> v instanceof String s
                    && io.github.zancrow321.minecraftygo.engine.data.PoolMode.parse(s) != null);

    public static final ModConfigSpec.ConfigValue<String> PROGRESSION_SCOPE = BUILDER.pop().push("progression")
            .comment("\"server\": one progress for everyone. \"player\": each player unlocks on their own "
                    + "(/ygo progression next <player>).")
            .define("scope", "server", v -> "server".equals(v) || "player".equals(v));

    public static final ModConfigSpec.ConfigValue<String> START_PRODUCT = BUILDER
            .comment("Where a new world (or, per player, a new player) starts: a set code such as \"LOB\" or a date "
                    + "such as \"2005-03-01\".")
            .define("startProduct", "LOB");

    public static final ModConfigSpec.BooleanValue ANNOUNCE = BUILDER
            .comment("Say in chat when new sets, rules or banlists are unlocked.")
            .define("announce", true);

    public static final ModConfigSpec.BooleanValue LOCKED_IN_DECK = BUILDER.pop().push("cards")
            .comment("In a progression world, cards from sets that are still locked can't go in a deck (they still "
                    + "count in the binder, with a lock).")
            .define("lockedInDeck", true);

    public static final ModConfigSpec.ConfigValue<String> SHOP_CURRENCY = BUILDER.pop().push("shop")
            .comment("The item card traders take for their goods and pay for paper, as an item id, e.g. "
                    + "\"minecraft:diamond\" or \"minecraft:gold_ingot\". Changes apply to traders right away.")
            .define("currency", "minecraft:emerald", v -> v instanceof String s
                    && net.minecraft.resources.ResourceLocation.tryParse(s) != null);

    public static final ModConfigSpec.DoubleValue SHOP_PRICE_MULTIPLIER = BUILDER
            .comment("Every price in [shop.prices] is multiplied by this and rounded, e.g. 0.5 for half price or 2 "
                    + "for double; a price is at least 1 and at most a stack of the currency.")
            .defineInRange("priceMultiplier", 1.0, 0.01, 100.0);

    public static final ModConfigSpec.BooleanValue SHOP_DYNAMIC_PRICES = BUILDER
            .comment("Prices go up for a while when a trade sells out often and down for players the village likes, "
                    + "as with other villagers. Off, card traders always ask exactly the [shop.prices].")
            .define("dynamicPrices", true);

    public static final ModConfigSpec.BooleanValue SHOP_WANDERING_TRADER = BUILDER
            .comment("Wandering traders sometimes sell a booster pack and, rarely, a duel disk.")
            .define("wanderingTrader", true);

    public static final ModConfigSpec.IntValue SHOP_NEWEST = BUILDER
            .comment("Card traders always sell the newest this many products (packs, structure decks, tins) out.")
            .defineInRange("newestAlways", 3, 0, 50);

    public static final ModConfigSpec.IntValue SHOP_ROTATING = BUILDER
            .comment("...and this many older ones, a different pick for each trader.")
            .defineInRange("rotatingOlder", 4, 0, 50);

    /** What card traders ask, in {@code [shop.prices]}. */
    public static final Prices PRICES = new Prices(BUILDER
            .comment("What card traders ask, in the [shop] currency (emeralds unless changed). 0 means traders "
                    + "don't sell it (and stop selling it if they did).").push("prices"));

    /** How many of each a card trader has, in {@code [shop.stock]}. */
    public static final Stock STOCK = new Stock(BUILDER.pop()
            .comment("How many of each a card trader can sell before it runs out; like other villagers it restocks "
                    + "at its counter up to twice a day.").push("stock"));

    /** The card vending machine, in {@code [shop.machine]}. */
    public static final Machine MACHINE = new Machine(BUILDER.pop()
            .comment("The Card Vending Machine: a block that sells at the [shop] prices and currency, without a "
                    + "villager.").push("machine"));

    /** Shop Stands, the shops players run, in {@code [shop.players]}. */
    public static final PlayerShops PLAYER_SHOPS = new PlayerShops(BUILDER.pop()
            .comment("Shop Stands: shops that players set up to sell their own cards, packs and anything else.")
            .push("players"));

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec.IntValue SHOP_ROTATION_DAYS = BUILDER
            .comment("Every this many days the older products change.")
            .defineInRange("rotationDays", 7, 1, 1000);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private YgoServerConfig() {
    }

    /**
     * Brings an older config file up to date: from 0.2.0 on, worlds start with progression and pick their rules and
     * banlist automatically, also when the old file named the previous defaults.
     */
    public static void migrate() {
        if (CONFIG_VERSION.get() >= VERSION) {
            return;
        }
        POOL_MODE.set("progression");
        RULESET.set("auto");
        CONFIG_VERSION.set(VERSION);
        SPEC.save();
    }

    /** The prices of the card shop. */
    public static final class Prices {
        public final ModConfigSpec.IntValue corePack;
        public final ModConfigSpec.IntValue premiumPack;
        public final ModConfigSpec.IntValue smallPack;
        public final ModConfigSpec.IntValue structureDeck;
        public final ModConfigSpec.IntValue tin;
        public final ModConfigSpec.IntValue randomPackNovice;
        public final ModConfigSpec.IntValue randomPackJourneyman;
        public final ModConfigSpec.IntValue randomPackMaster;
        public final ModConfigSpec.IntValue binder;
        public final ModConfigSpec.IntValue deckBox;
        public final ModConfigSpec.IntValue starterDeck;
        public final ModConfigSpec.IntValue duelDisk;
        public final ModConfigSpec.IntValue paper;
        public final ModConfigSpec.IntValue wanderingPack;
        public final ModConfigSpec.IntValue wanderingDuelDisk;

        /** Defines the settings in the section {@code builder} just entered. */
        private Prices(ModConfigSpec.Builder builder) {
            corePack = builder.comment("A 9-card core booster pack of a set.")
                    .defineInRange("corePack", 4, 0, 64);
            premiumPack = builder.comment("A pack of an all-foil set (Dragons of Legend, Premium Gold...).")
                    .defineInRange("premiumPack", 6, 0, 64);
            smallPack = builder.comment("A smaller pack: tournament packs (3 cards), mini boosters such as Duelist "
                    + "Packs and battle packs (5 cards).").defineInRange("smallPack", 3, 0, 64);
            structureDeck = builder.comment("A structure or starter deck of a set.")
                    .defineInRange("structureDeck", 10, 0, 64);
            tin = builder.comment("A collector's tin (its promo card and three packs).")
                    .defineInRange("tin", 14, 0, 64);
            randomPackNovice = builder.comment("A pack of a random set that is out, from novice traders.")
                    .defineInRange("randomPackNovice", 3, 0, 64);
            randomPackJourneyman = builder.comment("...from journeyman traders.")
                    .defineInRange("randomPackJourneyman", 4, 0, 64);
            randomPackMaster = builder.comment("...from master traders, a cheap deal for a trader you levelled up.")
                    .defineInRange("randomPackMaster", 2, 0, 64);
            binder = builder.comment("A binder.").defineInRange("binder", 4, 0, 64);
            deckBox = builder.comment("An empty deck box.").defineInRange("deckBox", 2, 0, 64);
            starterDeck = builder.comment("Yugi's or Kaiba's starter deck in a deck box, from apprentice traders.")
                    .defineInRange("starterDeck", 10, 0, 64);
            duelDisk = builder.comment("A duel disk, from expert traders.").defineInRange("duelDisk", 16, 0, 64);
            paper = builder.comment("Paper a trader buys for one currency item; 0 means it buys none. Not changed "
                    + "by priceMultiplier.").defineInRange("paper", 24, 0, 64);
            wanderingPack = builder.comment("A random pack from a wandering trader.")
                    .defineInRange("wanderingPack", 5, 0, 64);
            wanderingDuelDisk = builder.comment("A duel disk from a wandering trader.")
                    .defineInRange("wanderingDuelDisk", 20, 0, 64);
        }
    }

    /** The settings of the card vending machine. */
    public static final class Machine {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.ConfigValue<String> products;
        public final ModConfigSpec.BooleanValue supplies;
        public final ModConfigSpec.BooleanValue limitPerPlayer;
        public final ModConfigSpec.BooleanValue command;
        public final ModConfigSpec.BooleanValue operatorsOnly;
        public final ModConfigSpec.ConfigValue<String> name;

        /** Defines the settings in the section {@code builder} just entered. */
        private Machine(ModConfigSpec.Builder builder) {
            enabled = builder.comment("Machines sell. Off, they say the shop is closed.").define("enabled", true);
            products = builder.comment("The packs, structure decks and tins they sell: \"rotation\" (the newest "
                            + "and a few older ones, like card traders, the same pick on every machine), \"all\" "
                            + "(every product that is out) or \"none\".")
                    .define("products", "rotation", v -> "rotation".equals(v) || "all".equals(v) || "none".equals(v));
            supplies = builder.comment("They also sell random packs, binders, deck boxes, the starter decks and duel "
                    + "disks.").define("supplies", true);
            limitPerPlayer = builder.comment("Each player can buy the [shop.stock] amounts a day from machines (a "
                    + "Minecraft day). Off, machines never run out.").define("limitPerPlayer", true);
            command = builder.comment("/ygo shop opens the machine's shop anywhere, without a machine.")
                    .define("command", false);
            operatorsOnly = builder.comment("Only operators can place and break machines, for server-run shops.")
                    .define("operatorsOnly", false);
            name = builder.comment("The shop's name at the top of its window; empty for \"Card Vending Machine\".")
                    .define("name", "");
        }
    }

    /** The settings of Shop Stands. */
    public static final class PlayerShops {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.IntValue maxPerPlayer;
        public final ModConfigSpec.BooleanValue currencyOnly;
        public final ModConfigSpec.IntValue taxPercent;
        public final ModConfigSpec.BooleanValue onlyYgoItems;
        public final ModConfigSpec.BooleanValue operatorsManage;
        public final ModConfigSpec.BooleanValue notifyOwner;

        /** Defines the settings in the section {@code builder} just entered. */
        private PlayerShops(ModConfigSpec.Builder builder) {
            enabled = builder.comment("Shop Stands sell. Off, they say the shop is closed (their owners can still "
                    + "take their things out).").define("enabled", true);
            maxPerPlayer = builder.comment("How many Shop Stands each player can set up; 0 means any number.")
                    .defineInRange("maxPerPlayer", 3, 0, 1000);
            currencyOnly = builder.comment("Prices must be in the [shop] currency. Off, owners can ask for any item "
                    + "(diamonds, a rare card...).").define("currencyOnly", false);
            taxPercent = builder.comment("This share of every price is kept back from the owner, in percent, "
                    + "rounded down (so a price of 1 is never taxed).").defineInRange("taxPercent", 0, 0, 100);
            onlyYgoItems = builder.comment("Stands only sell this mod's items: cards, packs, decks, tins, binders, "
                    + "deck boxes, duel disks...").define("onlyYgoItems", false);
            operatorsManage = builder.comment("Operators can open, stock and break anyone's stand. Off, only the "
                    + "owner can.").define("operatorsManage", true);
            notifyOwner = builder.comment("Tell the owner in chat when someone buys from their stand.")
                    .define("notifyOwner", true);
        }
    }

    /** The stock of the card shop. */
    public static final class Stock {
        public final ModConfigSpec.IntValue packs;
        public final ModConfigSpec.IntValue masterPacks;
        public final ModConfigSpec.IntValue structureDecks;
        public final ModConfigSpec.IntValue tins;
        public final ModConfigSpec.IntValue binders;
        public final ModConfigSpec.IntValue deckBoxes;
        public final ModConfigSpec.IntValue starterDecks;
        public final ModConfigSpec.IntValue duelDisks;

        /** Defines the settings in the section {@code builder} just entered. */
        private Stock(ModConfigSpec.Builder builder) {
            packs = builder.comment("Packs of each set, and the novice's and journeyman's random packs.")
                    .defineInRange("packs", 16, 1, 999);
            masterPacks = builder.comment("The master's cheap random packs.")
                    .defineInRange("masterPacks", 32, 1, 999);
            structureDecks = builder.comment("Each structure deck.").defineInRange("structureDecks", 4, 1, 999);
            tins = builder.comment("Each tin.").defineInRange("tins", 3, 1, 999);
            binders = builder.comment("Binders.").defineInRange("binders", 4, 1, 999);
            deckBoxes = builder.comment("Empty deck boxes.").defineInRange("deckBoxes", 8, 1, 999);
            starterDecks = builder.comment("Each starter deck.").defineInRange("starterDecks", 2, 1, 999);
            duelDisks = builder.comment("Duel disks.").defineInRange("duelDisks", 3, 1, 999);
        }
    }
}
