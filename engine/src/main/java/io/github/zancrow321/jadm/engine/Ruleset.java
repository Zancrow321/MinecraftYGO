package io.github.zancrow321.jadm.engine;

import java.util.Locale;

/**
 * The rule sets a server can pick. They change how the engine resolves turns, chains and summons, and which extra
 * deck monsters can be summoned at all (Master Rule 1 has no Xyz, 2 no Pendulum, 3 no Link).
 */
public enum Ruleset {
    /** Master Rule 1, as the cards were printed: the first player draws, ignition effects work the old way. */
    MR1("Original (Master Rule 1)", OcgConstants.DUEL_MODE_MR1),
    /** The 2005 TCG "Goat" format: MR1 plus the TCG rulings of that time. */
    GOAT("Goat format", OcgConstants.DUEL_MODE_GOAT),
    /** Master Rule 2 (2011): Xyz monsters. */
    MR2("Master Rule 2", OcgConstants.DUEL_MODE_MR2),
    /** Master Rule 3 (2014): Pendulum Zones. */
    MR3("Master Rule 3", OcgConstants.DUEL_MODE_MR3),
    /** Master Rule 4 (2017): Link monsters and the Extra Monster Zones. */
    MR4("Master Rule 4", OcgConstants.DUEL_MODE_MR4),
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

    /** @return the ruleset named {@code id} (case-insensitive, {@code mr5} is {@link #MODERN}), or MR1 for an unknown name */
    public static Ruleset parse(String id) {
        if (id.trim().equalsIgnoreCase("mr5")) {
            return MODERN;
        }
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MR1;
        }
    }
}
