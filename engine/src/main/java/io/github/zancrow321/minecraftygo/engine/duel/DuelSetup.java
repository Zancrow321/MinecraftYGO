package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.deck.Deck;
import io.github.zancrow321.minecraftygo.engine.ffi.DuelOptions;

import java.util.List;

/**
 * Everything needed to start a duel. {@code teams.get(0)} moves first. Each team has one deck per duelist (two for a
 * tag duel); duelist 0 starts. {@code seed} drives both the core RNG and the host-side deck shuffle.
 */
public record DuelSetup(long[] seed, long flags, DuelOptions.Team team1, DuelOptions.Team team2, List<List<Deck>> teams) {
	public DuelSetup {
		seed = seed.clone();
		if (teams.size() != 2 || teams.stream().anyMatch(t -> t.isEmpty() || t.size() > 2)) {
			throw new IllegalArgumentException("Two teams with one or two duelists each are required");
		}
		teams = teams.stream().map(List::copyOf).toList();
	}

	@Override
	public long[] seed() {
		return seed.clone();
	}

	public static DuelSetup single(long[] seed, Deck first, Deck second) {
		return new DuelSetup(seed, OcgConstants.DUEL_MODE_GOAT, DuelOptions.Team.STANDARD, DuelOptions.Team.STANDARD,
				List.of(List.of(first), List.of(second)));
	}

	public DuelOptions toOptions() {
		return new DuelOptions(seed, flags, team1, team2);
	}
}
