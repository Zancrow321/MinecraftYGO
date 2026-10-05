package io.github.zancrow321.minecraftygo.engine.ffi;

/**
 * Parameters of {@code OCG_CreateDuel}. {@code seed} must hold four values that are not all zero; {@code flags} is a
 * combination of the {@code DUEL_*} constants (e.g. {@code DUEL_MODE_GOAT}).
 */
public record DuelOptions(long[] seed, long flags, Team team1, Team team2) {
	public DuelOptions {
		if (seed.length != 4) {
			throw new IllegalArgumentException("seed must have 4 elements");
		}
		if (seed[0] == 0 && seed[1] == 0 && seed[2] == 0 && seed[3] == 0) {
			throw new IllegalArgumentException("seed must not be all zero");
		}
		seed = seed.clone();
	}

	@Override
	public long[] seed() {
		return seed.clone();
	}

	/** {@code OCG_Player}: per-team starting values. */
	public record Team(int startingLp, int startingDrawCount, int drawCountPerTurn) {
		public static final Team STANDARD = new Team(8000, 5, 1);
	}
}
