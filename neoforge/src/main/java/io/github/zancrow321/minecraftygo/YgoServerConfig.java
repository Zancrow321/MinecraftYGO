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

    public static final ModConfigSpec SPEC = BUILDER.build();

    private YgoServerConfig() {
    }
}
