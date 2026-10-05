package io.github.zancrow321.minecraftygo.engine.deck;

import java.util.List;

/** A deck as card codes. The order of {@code main} is irrelevant; the duel shuffles it. */
public record Deck(List<Integer> main, List<Integer> extra, List<Integer> side) {
	public Deck {
		main = List.copyOf(main);
		extra = List.copyOf(extra);
		side = List.copyOf(side);
	}

	public static Deck of(List<Integer> main, List<Integer> extra) {
		return new Deck(main, extra, List.of());
	}
}
