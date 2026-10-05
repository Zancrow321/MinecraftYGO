package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;

/**
 * Tracks which duelist of each team is active. The core swaps a team to its next duelist at the start of that team's
 * turns (except the very first turn) and announces it with {@code MSG_TAG_SWAP}; prompts for the team go to the
 * active duelist.
 */
public final class TagRotation {
	private final int[] duelists;
	private final int[] active = new int[2];

	public TagRotation(int team0Duelists, int team1Duelists) {
		this.duelists = new int[] {team0Duelists, team1Duelists};
	}

	public void observe(CoreMessage message) {
		if (message instanceof Event.TagSwap swap) {
			int team = swap.player();
			active[team] = (active[team] + 1) % duelists[team];
		}
	}

	public int active(int team) {
		return active[team];
	}

	public int duelists(int team) {
		return duelists[team];
	}
}
