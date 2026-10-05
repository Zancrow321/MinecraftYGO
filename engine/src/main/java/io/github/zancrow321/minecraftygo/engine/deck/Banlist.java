package io.github.zancrow321.minecraftygo.engine.deck;

import java.util.Map;

/** Copy limits per card code (0 = forbidden, 1 = limited, 2 = semi-limited); unlisted cards allow 3. */
public record Banlist(String name, Map<Integer, Integer> limits) {
	public static final Banlist NONE = new Banlist("none", Map.of());

	public Banlist {
		limits = Map.copyOf(limits);
	}

	public int limit(int code) {
		return limits.getOrDefault(code, DeckValidator.MAX_COPIES);
	}
}
