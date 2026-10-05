package io.github.zancrow321.minecraftygo.engine;

import java.util.Locale;

/**
 * The rule sets a server can pick. The card pool is from the early era either way; these change how the engine
 * resolves turns, chains and summons.
 */
public enum Ruleset {
    /** Master Rule 1, as the cards were printed: the first player draws, ignition effects work the old way. */
    MR1("Original (Master Rule 1)", OcgConstants.DUEL_MODE_MR1),
    /** The 2005 TCG "Goat" format: MR1 plus the TCG rulings of that time. */
    GOAT("Goat format", OcgConstants.DUEL_MODE_GOAT),
    /** Today's Master Rule. */
    MODERN("Modern (Master Rule 2020)", OcgConstants.DUEL_MODE_MR5);

    private final String displayName;
    private final long flags;

    Ruleset(String displayName, long flags) {
        this.displayName = displayName;
        this.flags = flags;
    }

    public String displayName() {
        return displayName;
    }

    public long flags() {
        return flags;
    }

    /** @return the ruleset named {@code id} (case-insensitive), or MR1 for an unknown name */
    public static Ruleset parse(String id) {
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MR1;
        }
    }
}
