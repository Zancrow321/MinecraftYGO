package io.github.zancrow321.jadm;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Per-world settings, in {@code serverconfig/jadm-server.toml}.
 */
public final class JadmServerConfig {
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
                    + "config/jadm/banlists/ without .json, e.g. \"goat\" for goat.json: "
                    + "{\"name\": \"...\", \"limits\": {\"<passcode>\": <copies>}}.")
            .define("banlist", "auto");

    public static final ModConfigSpec.IntValue STARTING_LIFE_POINTS = BUILDER
            .comment("Life points each duelist starts with in a 1v1 duel; tag duels use tagStartingLifePoints.")
            .defineInRange("startingLifePoints", 8000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue TAG_STARTING_LIFE_POINTS = BUILDER
            .comment("Life points each team starts with in tag and Battle City duels (2v2), where partners share them.")
            .defineInRange("tagStartingLifePoints", 16000, 100, 1_000_000);

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
                    + "TCG release order with /jadm progression), \"modeled\" (monsters with a 3D model and the spells "
                    + "and traps of their era) or \"all\" (every official card; packs come from every TCG booster).")
            .define("mode", "progression", v -> v instanceof String s
                    && io.github.zancrow321.jadm.engine.data.PoolMode.parse(s) != null);

    public static final ModConfigSpec.ConfigValue<String> PROGRESSION_SCOPE = BUILDER.pop().push("progression")
            .comment("\"server\": one progress for everyone. \"player\": each player unlocks on their own "
                    + "(/jadm progression next <player>).")
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
            .comment("What the shops take: \"points\" for Duel Points, a balance each player has (see "
                    + "[shop.points]), or an item id such as \"minecraft:emerald\" or \"minecraft:diamond\" for "
                    + "the villager trade window paid in that item. Changes apply to traders right away.")
            .define("currency", "points", v -> v instanceof String s
                    && net.minecraft.resources.ResourceLocation.tryParse(s) != null);

    public static final ModConfigSpec.DoubleValue SHOP_PRICE_MULTIPLIER = BUILDER
            .comment("Every price in [shop.prices] is multiplied by this and rounded, e.g. 0.5 for half price or 2 "
                    + "for double; a price is at least 1 and at most 64.")
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
            .comment("What card traders ask, in the [shop] currency; with points, each 1 here is "
                    + "[shop.points] pricePoints Duel Points. 0 means traders don't sell it (and stop selling it if "
                    + "they did).").push("prices"));

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

    /** Duel Points, the currency of {@code currency = "points"}, in {@code [shop.points]}. */
    public static final Points POINTS = new Points(BUILDER.pop()
            .comment("Duel Points: with currency = \"points\", every player has a balance the shops take and pay "
                    + "out. /jadm dp shows it.").push("points"));

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec.IntValue SHOP_ROTATION_DAYS = BUILDER
            .comment("Every this many days the older products change.")
            .defineInRange("rotationDays", 7, 1, 1000);

    public static final ModConfigSpec.BooleanValue TRACK_RECORD = BUILDER.pop().push("results")
            .comment("Count each player's wins, losses and draws, against players and against NPCs and bots "
                    + "(/jadm stats, /jadm stats top and the result screen).")
            .define("trackRecord", true);

    public static final ModConfigSpec.BooleanValue ANNOUNCE_RESULTS = BUILDER
            .comment("Tell everyone on the server in chat who won each duel.")
            .define("announce", false);

    public static final ModConfigSpec.BooleanValue REWARD_BOT_DUELS = BUILDER
            .comment("Duels against the bot (/jadm duel bot, bot seats in tag duels) give the [results.npcs] rewards "
                    + "too. Off, they only count for the record.")
            .define("rewardBotDuels", false);

    /** What a duel against other people brings, in {@code [results.players]}. */
    public static final Rewards PLAYER_REWARDS = new Rewards(BUILDER
            .comment("Rewards for duels against other players. A draw brings nothing.").push("players"), 0);

    /** What a duel against an NPC duelist brings, in {@code [results.npcs]}. */
    public static final Rewards NPC_REWARDS = new Rewards(BUILDER.pop()
            .comment("Rewards for duels against NPC duelists. A draw brings nothing.").push("npcs"), 1);

    public static final ModConfigSpec.IntValue NPC_REMATCH_MINUTES = BUILDER
            .comment("After you beat an NPC duelist it won't duel you again for this many minutes (20 is one "
                    + "Minecraft day); 0 means right away.")
            .defineInRange("rematchMinutes", 20, 0, 100_000);

    static {
        // The defaults every new tournament starts from; its host can change them for that tournament.
        BUILDER.pop(2).push("tournament");
        io.github.zancrow321.jadm.tournament.TournamentOptions.define(BUILDER);
        BUILDER.pop().push("guide");
    }

    public static final ModConfigSpec.BooleanValue GUIDE_ON_FIRST_JOIN = BUILDER
            .comment("Give every player the Duelist's Handbook (all the mod's info, commands and settings) on their "
                    + "first join. /jadm guide opens it any time.")
            .define("giveOnFirstJoin", true);

    public static final ModConfigSpec.BooleanValue GUIDE_CRAFTABLE = BUILDER
            .comment("The handbook can be crafted from a book and a sheet of paper.")
            .define("craftable", true);

    public static final ModConfigSpec.BooleanValue ARENA_AUTO_START = BUILDER.pop().push("arena")
            .comment("A duel starts by itself when one player stands on each podium of a Duel Arena, after a "
                    + "countdown. Arenas added for tournaments wait for the tournament while one is open or running.")
            .define("autoStart", true);

    public static final ModConfigSpec.IntValue ARENA_COUNTDOWN = BUILDER
            .comment("Seconds counted down before that duel starts; stepping off a podium calls it off.")
            .defineInRange("countdownSeconds", 5, 1, 60);

    public static final ModConfigSpec.DoubleValue ARENA_MAX_FIELD_SIZE = BUILDER
            .comment("Player-built arenas (Arena Core and Duelist Podiums) fit the duel field to the room they have. "
                    + "This is the biggest it gets, 1 being the usual size.")
            .defineInRange("maxFieldSize", 1.5, 0.5, 3.0);

    public static final ModConfigSpec.DoubleValue ARENA_MIN_FIELD_SIZE = BUILDER
            .comment("A player-built arena with room for less than this field size can't be used.")
            .defineInRange("minFieldSize", 0.35, 0.2, 1.0);

    /** The ranking: Elo ratings and ranks from ranked duels, in {@code [ranking]}. */
    public static final Ranking RANKING = new Ranking(BUILDER.pop()
            .comment("The ranking: ranked duels (/jadm duel <player> ranked) win and lose rating points, which put "
                    + "players into ranks from Bronze to Duel King. /jadm rank and Ranking Boards show it.")
            .push("ranking"));

    /** Trading between players, in {@code [trade]}. */
    public static final Trade TRADE = new Trade(BUILDER.pop()
            .comment("Trading: two players swap cards (and Duel Points) in a trade window that both have to "
                    + "confirm. /jadm trade <player> asks someone.").push("trade"));

    public static final ModConfigSpec.IntValue SET_POINTS_PER_CARD = BUILDER.pop().push("collection")
            .comment("The Set Collection Book shows how much of each set a player owns (cards in binders, deck boxes "
                    + "and loose in the inventory or ender chest). Completing a set brings the rewards below, once per "
                    + "player and set. Duel Points per card in the set, when the [shop] currency is points.")
            .defineInRange("pointsPerCard", 10, 0, 1_000_000);

    public static final ModConfigSpec.IntValue SET_PACKS = BUILDER
            .comment("Booster packs (a random set that is out) for each completed set.")
            .defineInRange("packs", 3, 0, 64);

    public static final ModConfigSpec.IntValue SET_EMERALDS = BUILDER
            .comment("Emeralds for each completed set.")
            .defineInRange("emeralds", 0, 0, 640);

    public static final ModConfigSpec.IntValue SET_XP = BUILDER
            .comment("Experience points for each completed set.")
            .defineInRange("xp", 0, 0, 100_000);

    public static final ModConfigSpec.BooleanValue SET_ANNOUNCE = BUILDER
            .comment("Tell everyone on the server in chat when a player completes a set.")
            .define("announce", true);

    public static final StarChips STAR_CHIPS = new StarChips(BUILDER.pop()
            .comment("Star Chip events (Duelist Kingdom): everyone who joins gets Star Chips, puts them up in duels and "
                    + "whoever collects enough goes to the finals, a tournament on the tournament arenas. An operator "
                    + "starts one with /jadm starchips start.").push("starchips"));

    /** Duelist clans, their crests, the clan ranking and clan wars, in {@code [clans]}. */
    public static final Clans CLANS = new Clans(BUILDER.pop()
            .comment("Duelist clans: players found clans with a tag and a crest (a banner from the loom), fill a clan "
                    + "treasury with Duel Points and fight clan wars, which move the clans' ratings in the clan "
                    + "ranking. /jadm clan opens the clan window.").push("clans"));

    static {
        BUILDER.pop();
    }

    /** Booster packs, Duel Points, emeralds and experience points for the winners and the losers of a duel. */
    public static final class Rewards {
        public final ModConfigSpec.IntValue winPacks;
        public final ModConfigSpec.IntValue winPoints;
        public final ModConfigSpec.IntValue winEmeralds;
        public final ModConfigSpec.IntValue winXp;
        public final ModConfigSpec.IntValue lossPacks;
        public final ModConfigSpec.IntValue lossPoints;
        public final ModConfigSpec.IntValue lossEmeralds;
        public final ModConfigSpec.IntValue lossXp;

        /** Defines the settings in the section {@code builder} just entered. */
        private Rewards(ModConfigSpec.Builder builder, int defaultWinPacks) {
            winPacks = builder.comment("Booster packs each winner gets (a random set that is out).")
                    .defineInRange("winPacks", defaultWinPacks, 0, 64);
            winPoints = builder.comment("Duel Points each winner gets, when the [shop] currency is points.")
                    .defineInRange("winPoints", 100, 0, 1_000_000);
            winEmeralds = builder.comment("Emeralds each winner gets.")
                    .defineInRange("winEmeralds", 0, 0, 640);
            winXp = builder.comment("Experience points each winner gets.")
                    .defineInRange("winXp", 0, 0, 100_000);
            lossPacks = builder.comment("Booster packs each loser gets as a consolation.")
                    .defineInRange("lossPacks", 0, 0, 64);
            lossPoints = builder.comment("Duel Points each loser gets as a consolation, when the [shop] currency is "
                    + "points.").defineInRange("lossPoints", 20, 0, 1_000_000);
            lossEmeralds = builder.comment("Emeralds each loser gets as a consolation.")
                    .defineInRange("lossEmeralds", 0, 0, 640);
            lossXp = builder.comment("Experience points each loser gets as a consolation.")
                    .defineInRange("lossXp", 0, 0, 100_000);
        }
    }

    /** The ranking's settings. */
    public static final class Ranking {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.IntValue startRating;
        public final ModConfigSpec.IntValue kFactor;
        public final ModConfigSpec.IntValue placementGames;
        public final ModConfigSpec.ConfigValue<List<? extends Integer>> tiers;
        public final ModConfigSpec.IntValue promotionPoints;
        public final ModConfigSpec.IntValue maxPerPairPerDay;
        public final ModConfigSpec.BooleanValue arenaDuels;
        public final ModConfigSpec.BooleanValue showInTabList;
        public final ModConfigSpec.BooleanValue announcePromotions;

        private Ranking(ModConfigSpec.Builder builder) {
            enabled = builder.comment("Ranked duels can be played. Off, the ranking stays as it is but nothing changes "
                    + "it.").define("enabled", true);
            startRating = builder.comment("The rating every player starts with (and gets back when a new season "
                    + "starts).").defineInRange("startRating", 1000, 0, 100_000);
            kFactor = builder.comment("How many rating points a ranked duel moves at most: an even match moves half "
                    + "this, an upset almost all of it.").defineInRange("kFactor", 32, 1, 400);
            placementGames = builder.comment("A player's first this many ranked duels move twice as many points, so "
                    + "new players find their rank quickly.").defineInRange("placementGames", 5, 0, 100);
            tiers = builder.comment("The rating each rank starts at, lowest first: Silver, Gold, Platinum, Diamond, "
                    + "Duel King. Below the first is Bronze.")
                    .defineListAllowEmpty("tiers", io.github.zancrow321.jadm.ranking.Tiers.DEFAULT_STARTS,
                            () -> 0, o -> o instanceof Number);
            promotionPoints = builder.comment("Duel Points a player gets the first time they reach each rank (per "
                    + "season), when the [shop] currency is points; 0 for none.")
                    .defineInRange("promotionPoints", 200, 0, 1_000_000);
            maxPerPairPerDay = builder.comment("Ranked duels between the same two players count this many times a "
                    + "day at most (more are played unranked), so two friends can't farm points; 0 for no limit.")
                    .defineInRange("maxPerPairPerDay", 5, 0, 1000);
            arenaDuels = builder.comment("Duels that start by themselves on a Duel Arena are ranked.")
                    .define("arenaDuels", false);
            showInTabList = builder.comment("Show each ranked player's rank in front of their name in the player "
                    + "list (Tab).").define("showInTabList", true);
            announcePromotions = builder.comment("Tell everyone in chat when a player reaches a new rank.")
                    .define("announcePromotions", true);
        }

        /** Where each rank above Bronze starts, lowest first. */
        public List<Integer> tierStarts() {
            List<Integer> starts = new java.util.ArrayList<>();
            for (Object o : tiers.get()) {
                // The config file may hand back longs.
                if (o instanceof Number n) {
                    starts.add(n.intValue());
                }
            }
            starts.sort(null);
            return starts.isEmpty() ? io.github.zancrow321.jadm.ranking.Tiers.DEFAULT_STARTS : starts;
        }
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private JadmServerConfig() {
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
            command = builder.comment("/jadm shop opens the machine's shop anywhere, without a machine.")
                    .define("command", false);
            operatorsOnly = builder.comment("Only operators can place and break machines, for server-run shops.")
                    .define("operatorsOnly", false);
            name = builder.comment("The shop's name at the top of its window; empty for \"Card Vending Machine\".")
                    .define("name", "");
        }
    }

    /** The settings of Duel Points. */
    public static final class Points {
        public final ModConfigSpec.ConfigValue<String> symbol;
        public final ModConfigSpec.IntValue startBalance;
        public final ModConfigSpec.IntValue pricePoints;
        public final ModConfigSpec.IntValue dailyBonus;
        public final ModConfigSpec.BooleanValue transfers;
        public final ModConfigSpec.IntValue sellCommon;
        public final ModConfigSpec.IntValue sellRare;
        public final ModConfigSpec.IntValue sellSuper;
        public final ModConfigSpec.IntValue sellUltra;
        public final ModConfigSpec.IntValue sellSecret;
        public final ModConfigSpec.IntValue emeraldExchange;

        /** Defines the settings in the section {@code builder} just entered. */
        private Points(ModConfigSpec.Builder builder) {
            symbol = builder.comment("What the points are called after a number, e.g. \"500 DP\".")
                    .define("symbol", "DP");
            startBalance = builder.comment("What a player has when they first join.")
                    .defineInRange("startBalance", 500, 0, 1_000_000_000);
            pricePoints = builder.comment("How many points each 1 of a [shop.prices] price costs, so a core pack of "
                    + "4 is 100 DP.").defineInRange("pricePoints", 25, 1, 1_000_000);
            dailyBonus = builder.comment("Points a player gets once a day (a real day) when they play; 0 for none.")
                    .defineInRange("dailyBonus", 50, 0, 1_000_000_000);
            transfers = builder.comment("Players can give each other points with /jadm dp pay.")
                    .define("transfers", true);
            sellCommon = builder.comment("Card Vending Machines buy loose cards for this many points, by rarity; "
                    + "0 means they don't buy that rarity.").defineInRange("sellCommon", 5, 0, 1_000_000);
            sellRare = builder.defineInRange("sellRare", 15, 0, 1_000_000);
            sellSuper = builder.defineInRange("sellSuper", 30, 0, 1_000_000);
            sellUltra = builder.defineInRange("sellUltra", 60, 0, 1_000_000);
            sellSecret = builder.defineInRange("sellSecret", 120, 0, 1_000_000);
            emeraldExchange = builder.comment("Card Vending Machines take emeralds for this many points each; 0 for "
                    + "no exchange.").defineInRange("emeraldExchange", 10, 0, 1_000_000);
        }
    }

    /** The settings of trading between players. */
    public static final class Trade {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.BooleanValue onlyModItems;
        public final ModConfigSpec.BooleanValue points;
        public final ModConfigSpec.IntValue maxDistance;
        public final ModConfigSpec.IntValue requestSeconds;
        public final ModConfigSpec.BooleanValue rightClick;

        /** Defines the settings in the section {@code builder} just entered. */
        private Trade(ModConfigSpec.Builder builder) {
            enabled = builder.comment("Players can trade with each other.").define("enabled", true);
            onlyModItems = builder.comment("Only this mod's items can be traded: cards, packs, decks, tins, binders, "
                    + "deck boxes, duel disks... Off, any item can.").define("onlyModItems", true);
            points = builder.comment("Duel Points can be put into a trade, when the [shop] currency is points and "
                    + "[shop.points] transfers is on.").define("points", true);
            maxDistance = builder.comment("How close (in blocks) the two have to be to start and keep trading; 0 "
                    + "means anywhere, even in another dimension.").defineInRange("maxDistance", 0, 0, 10_000);
            requestSeconds = builder.comment("Seconds a trade request waits to be accepted.")
                    .defineInRange("requestSeconds", 60, 5, 3600);
            rightClick = builder.comment("Sneaking and right-clicking another player with a card, binder or deck box "
                    + "in your hand asks them to trade.").define("rightClick", true);
        }
    }

    /** The settings of Star Chip events. */
    public static final class StarChips {
        public final ModConfigSpec.IntValue startChips;
        public final ModConfigSpec.IntValue goal;
        public final ModConfigSpec.IntValue finalists;
        public final ModConfigSpec.IntValue defaultWager;
        public final ModConfigSpec.IntValue maxWager;
        public final ModConfigSpec.BooleanValue npcDuels;
        public final ModConfigSpec.IntValue npcWager;
        public final ModConfigSpec.IntValue durationMinutes;
        public final ModConfigSpec.BooleanValue fillFinals;
        public final ModConfigSpec.BooleanValue lateJoin;
        public final ModConfigSpec.IntValue entryFee;
        public final ModConfigSpec.BooleanValue playersCanHost;
        public final ModConfigSpec.BooleanValue announce;
        public final ModConfigSpec.ConfigValue<java.util.List<? extends String>> finalsSettings;

        /** Defines the settings in the section {@code builder} just entered. */
        private StarChips(ModConfigSpec.Builder builder) {
            startChips = builder.comment("Star Chips each duelist gets when they join an event.")
                    .defineInRange("startChips", 2, 1, 100);
            goal = builder.comment("Star Chips needed to qualify for the finals. Qualified duelists keep their "
                    + "chips and duel no more until the finals.").defineInRange("goal", 10, 2, 1000);
            finalists = builder.comment("The finals begin as soon as this many duelists have qualified.")
                    .defineInRange("finalists", 4, 2, 64);
            defaultWager = builder.comment("Star Chips each side puts up in a duel between two duelists of the event, "
                    + "unless the challenger asks for more with /jadm starchips duel <player> <chips>. Nobody puts "
                    + "up more than they have.").defineInRange("defaultWager", 1, 1, 1000);
            maxWager = builder.comment("The most Star Chips one duel can be for; 0 for no limit.")
                    .defineInRange("maxWager", 0, 0, 1000);
            npcDuels = builder.comment("Duels against NPC duelists are for Star Chips too (the NPC puts up npcWager), "
                    + "so players can reach the finals on their own. Off: only duels between players, as on Duelist "
                    + "Kingdom.").define("npcDuels", false);
            npcWager = builder.comment("Star Chips a duel against an NPC duelist is for.")
                    .defineInRange("npcWager", 1, 1, 1000);
            durationMinutes = builder.comment("The finals begin this many minutes after the event started, even "
                    + "if fewer have qualified; 0 waits until enough have (or an operator runs /jadm starchips "
                    + "finals).").defineInRange("durationMinutes", 0, 0, 100_000);
            fillFinals = builder.comment("When the finals begin before enough have qualified, the empty seats go "
                    + "to the duelists with the most Star Chips.").define("fillFinals", true);
            lateJoin = builder.comment("Players can still join once the event is under way.")
                    .define("lateJoin", true);
            entryFee = builder.comment("Duel Points it costs to join; 0 for free.")
                    .defineInRange("entryFee", 0, 0, 1_000_000);
            playersCanHost = builder.comment("Players without operator rights may start and run events.")
                    .define("playersCanHost", false);
            announce = builder.comment("Tell the whole server who qualified, who is out and how the finals went; "
                    + "off tells only the duelists of the event.").define("announce", true);
            finalsSettings = builder.comment("Tournament settings for the finals, as \"setting=value\" (the same "
                    + "settings as /jadm tournament set; the rest come from [tournament]). Who plays, the entry "
                    + "fee and NPCs are fixed by the event.")
                    .defineListAllowEmpty("finalsSettings", java.util.List.of("format=single", "bestOf=1",
                            "thirdPlaceMatch=false", "prizesFirst=pack 10; points 1000",
                            "prizesSecond=pack 5; points 300", "prizesThird=pack 2", "prizesFourth=pack 1",
                            "prizesTop8=pack 1"), () -> "", v -> v instanceof String s && s.contains("="));
        }
    }

    /** The settings of clans and clan wars. */
    public static final class Clans {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.IntValue createPrice;
        public final ModConfigSpec.IntValue maxMembers;
        public final ModConfigSpec.IntValue bannerPrice;
        public final ModConfigSpec.BooleanValue showTag;
        public final ModConfigSpec.BooleanValue ftbTeams;
        public final ModConfigSpec.IntValue startRating;
        public final ModConfigSpec.IntValue kFactor;
        public final ModConfigSpec.IntValue warHours;
        public final ModConfigSpec.IntValue warTarget;
        public final ModConfigSpec.IntValue warMaxPerPair;
        public final ModConfigSpec.BooleanValue warOnlyRanked;
        public final ModConfigSpec.IntValue warAcceptHours;
        public final ModConfigSpec.IntValue warCooldownHours;
        public final ModConfigSpec.IntValue warMinMembers;
        public final ModConfigSpec.IntValue maxStake;
        public final ModConfigSpec.IntValue warWinPoints;
        public final ModConfigSpec.BooleanValue announceWars;
        public final ModConfigSpec.IntValue battleDuelists;
        public final ModConfigSpec.IntValue battleCallSeconds;
        public final ModConfigSpec.IntValue battleNoShowMinutes;

        /** Defines the settings in the section {@code builder} just entered. */
        private Clans(ModConfigSpec.Builder builder) {
            enabled = builder.comment("Players can found and join clans. Off, the clans stay as they are but nobody "
                    + "can found one, join one or declare war.").define("enabled", true);
            createPrice = builder.comment("Duel Points it costs to found a clan, when the [shop] currency is points; "
                    + "0 for free.").defineInRange("createPrice", 500, 0, 1_000_000);
            maxMembers = builder.comment("The most members a clan can have, its leader included; 0 for no limit.")
                    .defineInRange("maxMembers", 20, 0, 1000);
            bannerPrice = builder.comment("Duel Points a copy of the clan's crest as a banner costs "
                    + "(/jadm clan banner), when the [shop] currency is points; 0 for free.")
                    .defineInRange("bannerPrice", 25, 0, 1_000_000);
            showTag = builder.comment("Show each member's clan tag, in the clan's color, in front of their name in "
                    + "chat, in the player list (Tab) and above their head. Each clan gets a vanilla scoreboard team "
                    + "for this (named jadm_clan_...), so chat mods that show teams show the tag too. Off removes "
                    + "those teams.").define("showTag", true);
            ftbTeams = builder.comment("When FTB Teams is installed, every clan is also an FTB Teams party with the "
                    + "same members, leader, officers, name, color and motto, so FTB mods (chunk claims, quests, "
                    + "team chat) treat the whole clan as one team. Changes on either side carry over: joining or "
                    + "leaving the party joins or leaves the clan. Founding a clan turns the founder's own party into "
                    + "the clan; disbanding a clan keeps its party. Off leaves FTB Teams alone.")
                    .define("ftbTeams", true);
            startRating = builder.comment("The clan rating every new clan starts with (and gets back when an operator "
                    + "starts a new clan season).").defineInRange("startRating", 1000, 0, 100_000);
            kFactor = builder.comment("How many rating points a clan war moves at most: an even war moves half this, "
                    + "an upset almost all of it.").defineInRange("kFactor", 40, 1, 400);
            warHours = builder.comment("How long a clan war lasts once it is accepted, in hours. In a race for "
                    + "points the clan that is ahead when the time is up wins (an even score is a draw); an arena "
                    + "battle has to begin within this time or ends in a draw.").defineInRange("warHours", 72, 1, 2160);
            warTarget = builder.comment("A race for points ends early when a clan reaches this many points (one per "
                    + "duel won); 0 to always play the full time.").defineInRange("warTarget", 10, 0, 10_000);
            warMaxPerPair = builder.comment("Duels between the same two duelists count this many times per war at "
                    + "most, so a clan has to beat many of the other clan's duelists; 0 for no limit.")
                    .defineInRange("warMaxPerPair", 3, 0, 1000);
            warOnlyRanked = builder.comment("Only ranked duels (/jadm duel <player> ranked) count in clan wars. Off, "
                    + "every duel between members of the two clans counts, tag duels too.")
                    .define("warOnlyRanked", false);
            warAcceptHours = builder.comment("Hours a declared war waits for the other clan's leader or an officer "
                    + "to accept it.").defineInRange("warAcceptHours", 24, 1, 720);
            warCooldownHours = builder.comment("Hours after a war before the same two clans can fight again; 0 for "
                    + "none.").defineInRange("warCooldownHours", 24, 0, 8760);
            warMinMembers = builder.comment("Members a clan needs to declare or accept a war.")
                    .defineInRange("warMinMembers", 1, 1, 1000);
            maxStake = builder.comment("The most Duel Points from the clan treasury each clan can put up in a war "
                    + "(the winner takes both); 0 turns stakes off.").defineInRange("maxStake", 10_000, 0,
                    100_000_000);
            warWinPoints = builder.comment("Duel Points the server adds to the winning clan's treasury, when the "
                    + "[shop] currency is points; 0 for none.").defineInRange("warWinPoints", 500, 0, 1_000_000);
            announceWars = builder.comment("Tell everyone on the server when a clan war begins and how it ended; "
                    + "off tells only the two clans.").define("announceWars", true);
            battleDuelists = builder.comment("Duelists each clan sends into an arena battle; they fight one bout "
                    + "after another on a tournament arena (/jadm tournament arena add), first against first. An odd "
                    + "number avoids even scores.").defineInRange("battleDuelists", 3, 1, 15);
            battleCallSeconds = builder.comment("Seconds the two duelists of a bout stand on the podiums before it "
                    + "starts.").defineInRange("battleCallSeconds", 10, 3, 120);
            battleNoShowMinutes = builder.comment("Minutes a bout waits for a duelist who is offline or busy before "
                    + "the other one wins it.").defineInRange("battleNoShowMinutes", 3, 1, 60);
        }
    }

    /** The settings of Shop Stands. */
    public static final class PlayerShops {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.IntValue maxPerPlayer;
        public final ModConfigSpec.BooleanValue currencyOnly;
        public final ModConfigSpec.IntValue taxPercent;
        public final ModConfigSpec.BooleanValue onlyModItems;
        public final ModConfigSpec.BooleanValue operatorsManage;
        public final ModConfigSpec.BooleanValue notifyOwner;

        /** Defines the settings in the section {@code builder} just entered. */
        private PlayerShops(ModConfigSpec.Builder builder) {
            enabled = builder.comment("Shop Stands sell. Off, they say the shop is closed (their owners can still "
                    + "take their things out).").define("enabled", true);
            maxPerPlayer = builder.comment("How many Shop Stands each player can set up; 0 means any number.")
                    .defineInRange("maxPerPlayer", 3, 0, 1000);
            currencyOnly = builder.comment("With an item currency, prices must be in it. Off, owners can ask for any "
                    + "item (diamonds, a rare card...). With points, stands always ask points.")
                    .define("currencyOnly", false);
            taxPercent = builder.comment("This share of every price is kept back from the owner, in percent, "
                    + "rounded down (so a price of 1 is never taxed).").defineInRange("taxPercent", 0, 0, 100);
            onlyModItems = builder.comment("Stands only sell this mod's items: cards, packs, decks, tins, binders, "
                    + "deck boxes, duel disks...").define("onlyModItems", false);
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
