package io.github.zancrow321.minecraftygo.engine.query;

import java.util.List;

/** Snapshot of the whole duel: both players (index = core player 0/1) and the current chain. */
public record FieldState(List<PlayerField> players, List<ChainEntry> chain) {
	public FieldState {
		if (players.size() != 2) {
			throw new IllegalArgumentException("Two players required");
		}
		players = List.copyOf(players);
		chain = List.copyOf(chain);
	}

	public PlayerField player(int player) {
		return players.get(player);
	}

	/** A chain link: the activated card and the effect description. */
	public record ChainEntry(int code, int controller, int location, int sequence, long description) {}
}
