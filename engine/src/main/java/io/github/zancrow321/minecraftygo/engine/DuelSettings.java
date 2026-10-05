package io.github.zancrow321.minecraftygo.engine;

/**
 * Options for a new duel.
 *
 * @param seed  four 64-bit words seeding the core's RNG; must not all be zero
 * @param flags a combination of {@link OcgConstants} {@code DUEL_*} flags, e.g. {@link OcgConstants#DUEL_MODE_MR1}
 */
public record DuelSettings(long[] seed, long flags, Team team1, Team team2) {
    public DuelSettings {
        if (seed.length != 4) {
            throw new IllegalArgumentException("seed must have 4 words");
        }
    }

    /**
     * Per-team starting values. In a tag duel both partners share these.
     */
    public record Team(int startingLp, int startingDrawCount, int drawCountPerTurn) {
        public static final Team STANDARD = new Team(8000, 5, 1);
    }

    public static DuelSettings standard(long[] seed, long flags) {
        return new DuelSettings(seed, flags, Team.STANDARD, Team.STANDARD);
    }
}
