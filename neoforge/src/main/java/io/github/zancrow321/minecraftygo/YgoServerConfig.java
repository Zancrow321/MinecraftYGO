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

    public static final ModConfigSpec.IntValue SHOP_NEWEST = BUILDER.pop().push("shop")
            .comment("Card traders always sell the newest this many products (packs, structure decks, tins) out.")
            .defineInRange("newestAlways", 3, 0, 50);

    public static final ModConfigSpec.IntValue SHOP_ROTATING = BUILDER
            .comment("...and this many older ones, a different pick for each trader.")
            .defineInRange("rotatingOlder", 4, 0, 50);

    public static final ModConfigSpec.IntValue SHOP_ROTATION_DAYS = BUILDER
            .comment("Every this many days the older products change.")
            .defineInRange("rotationDays", 7, 1, 1000);

    public static final ModConfigSpec.BooleanValue TRACK_RECORD = BUILDER.pop().push("results")
            .comment("Count each player's wins, losses and draws, against players and against NPCs and bots "
                    + "(/ygo stats, /ygo stats top and the result screen).")
            .define("trackRecord", true);

    public static final ModConfigSpec.BooleanValue ANNOUNCE_RESULTS = BUILDER
            .comment("Tell everyone on the server in chat who won each duel.")
            .define("announce", false);

    public static final ModConfigSpec.BooleanValue REWARD_BOT_DUELS = BUILDER
            .comment("Duels against the bot (/ygo duel bot, bot seats in tag duels) give the [results.npcs] rewards "
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
        BUILDER.pop(2);
    }

    /** Booster packs, emeralds and experience points for the winners and the losers of a duel. */
    public static final class Rewards {
        public final ModConfigSpec.IntValue winPacks;
        public final ModConfigSpec.IntValue winEmeralds;
        public final ModConfigSpec.IntValue winXp;
        public final ModConfigSpec.IntValue lossPacks;
        public final ModConfigSpec.IntValue lossEmeralds;
        public final ModConfigSpec.IntValue lossXp;

        /** Defines the settings in the section {@code builder} just entered. */
        private Rewards(ModConfigSpec.Builder builder, int defaultWinPacks) {
            winPacks = builder.comment("Booster packs each winner gets (a random set that is out).")
                    .defineInRange("winPacks", defaultWinPacks, 0, 64);
            winEmeralds = builder.comment("Emeralds each winner gets.")
                    .defineInRange("winEmeralds", 0, 0, 640);
            winXp = builder.comment("Experience points each winner gets.")
                    .defineInRange("winXp", 0, 0, 100_000);
            lossPacks = builder.comment("Booster packs each loser gets as a consolation.")
                    .defineInRange("lossPacks", 0, 0, 64);
            lossEmeralds = builder.comment("Emeralds each loser gets as a consolation.")
                    .defineInRange("lossEmeralds", 0, 0, 640);
            lossXp = builder.comment("Experience points each loser gets as a consolation.")
                    .defineInRange("lossXp", 0, 0, 100_000);
        }
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
}
