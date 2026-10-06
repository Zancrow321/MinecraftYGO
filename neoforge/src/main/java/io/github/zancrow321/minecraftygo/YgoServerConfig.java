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

    public static final ModConfigSpec.ConfigValue<String> RULESET = BUILDER
            .comment("The rules duels are played under: \"mr1\" (original, as the cards were printed), \"goat\" "
                    + "(the 2005 TCG format) or \"modern\" (today's Master Rule).")
            .define("ruleset", "mr1");

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
            .comment("Which cards are in play: \"modeled\" (monsters with a 3D model and the spells and traps of "
                    + "their era) or \"all\" (every official card; packs come from every TCG booster). Takes effect "
                    + "after a restart.")
            .define("mode", "modeled", v -> v instanceof String s
                    && io.github.zancrow321.minecraftygo.engine.data.PoolMode.parse(s) != null);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private YgoServerConfig() {
    }
}
