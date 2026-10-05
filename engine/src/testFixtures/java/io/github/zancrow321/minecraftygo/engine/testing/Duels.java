package io.github.zancrow321.minecraftygo.engine.testing;

import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.deck.Deck;
import io.github.zancrow321.minecraftygo.engine.duel.DuelSession;
import io.github.zancrow321.minecraftygo.engine.duel.DuelSetup;
import io.github.zancrow321.minecraftygo.engine.duel.EngineDataSource;
import io.github.zancrow321.minecraftygo.engine.ffi.DuelOptions;

import java.util.List;

/** Helpers to create test duels. */
public final class Duels {
	private Duels() {}

	public static long[] seed(long seed) {
		return new long[] {seed, seed * 31 + 7, seed ^ 0x5DEECE66DL, 42};
	}

	public static DuelSetup single(long seed) {
		return DuelSetup.single(seed(seed), SampleDecks.dragons(), SampleDecks.magicians());
	}

	public static DuelSetup tag(long seed) {
		return new DuelSetup(seed(seed), OcgConstants.DUEL_MODE_GOAT, DuelOptions.Team.STANDARD, DuelOptions.Team.STANDARD,
				List.of(List.of(SampleDecks.dragons(), SampleDecks.magicians()),
						List.of(SampleDecks.magicians(), SampleDecks.dragons())));
	}

	public static DuelSetup single(long seed, Deck first, Deck second) {
		return DuelSetup.single(seed(seed), first, second);
	}

	public static DuelSession start(DuelSetup setup, EngineDataSource data) {
		return DuelSession.start(TestEnvironment.core(), setup, data, TestEnvironment.scripts());
	}
}
