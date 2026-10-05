package io.github.zancrow321.minecraftygo.engine.deck;

/** One reason a deck is not legal; {@code code} is 0 for deck-wide problems. */
public record DeckProblem(Kind kind, int code, int count, int limit) {
	public enum Kind {
		MAIN_TOO_SMALL, MAIN_TOO_LARGE, EXTRA_TOO_LARGE, SIDE_TOO_LARGE, TOO_MANY_COPIES, UNKNOWN_CARD,
		NOT_IN_POOL, EXTRA_DECK_CARD_IN_MAIN, MAIN_DECK_CARD_IN_EXTRA, TOKEN
	}
}
