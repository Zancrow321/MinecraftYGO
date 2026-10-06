package io.github.zancrow321.minecraftygo.engine.data;

import java.util.Locale;

/** Which of the bundled cards a server plays with. */
public enum PoolMode {
    /** Every official card, unlocked product by product in TCG release order (see {@link Progression}). */
    PROGRESSION,
    /** Monsters with a 3D model and the spells and traps of their era: the pool before all cards were bundled. */
    MODELED,
    /** Every official card. */
    ALL;

    /** @return the mode named by a config value such as {@code "all"}, or {@code null} if there is none */
    public static PoolMode parse(String id) {
        for (PoolMode mode : values()) {
            if (mode.id().equals(id.strip().toLowerCase(Locale.ROOT))) {
                return mode;
            }
        }
        return null;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
