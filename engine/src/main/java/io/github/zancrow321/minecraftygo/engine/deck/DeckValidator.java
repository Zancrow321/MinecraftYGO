package io.github.zancrow321.minecraftygo.engine.deck;

import io.github.zancrow321.minecraftygo.engine.data.CardData;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * Official deck construction rules: 40-60 Main Deck, at most 15 Extra and 15 Side Deck cards, at most 3 copies of a
 * card across all three (alternate artworks count as the same card), banlist limits and an optional card pool.
 */
public final class DeckValidator {
	public static final int MIN_MAIN = 40;
	public static final int MAX_MAIN = 60;
	public static final int MAX_EXTRA = 15;
	public static final int MAX_SIDE = 15;
	public static final int MAX_COPIES = 3;

	private final CardDatabase database;
	private final Banlist banlist;
	private final IntPredicate pool;

	public DeckValidator(CardDatabase database, Banlist banlist, IntPredicate pool) {
		this.database = database;
		this.banlist = banlist;
		this.pool = pool;
	}

	public DeckValidator(CardDatabase database) {
		this(database, Banlist.NONE, _ -> true);
	}

	public List<DeckProblem> validate(Deck deck) {
		List<DeckProblem> problems = new ArrayList<>();
		if (deck.main().size() < MIN_MAIN) {
			problems.add(new DeckProblem(DeckProblem.Kind.MAIN_TOO_SMALL, 0, deck.main().size(), MIN_MAIN));
		}
		if (deck.main().size() > MAX_MAIN) {
			problems.add(new DeckProblem(DeckProblem.Kind.MAIN_TOO_LARGE, 0, deck.main().size(), MAX_MAIN));
		}
		if (deck.extra().size() > MAX_EXTRA) {
			problems.add(new DeckProblem(DeckProblem.Kind.EXTRA_TOO_LARGE, 0, deck.extra().size(), MAX_EXTRA));
		}
		if (deck.side().size() > MAX_SIDE) {
			problems.add(new DeckProblem(DeckProblem.Kind.SIDE_TOO_LARGE, 0, deck.side().size(), MAX_SIDE));
		}
		Map<Integer, Integer> copies = new TreeMap<>();
		Map<Integer, Boolean> reported = new HashMap<>();
		checkSection(deck.main(), false, problems, copies, reported);
		checkSection(deck.extra(), true, problems, copies, reported);
		checkSection(deck.side(), null, problems, copies, reported);
		copies.forEach((canonical, count) -> {
			int limit = Math.min(MAX_COPIES, banlist.limit(canonical));
			if (count > limit) {
				problems.add(new DeckProblem(DeckProblem.Kind.TOO_MANY_COPIES, canonical, count, limit));
			}
		});
		return problems;
	}

	/** @param extraSection true for the Extra Deck, false for the Main Deck, null for the Side Deck (both allowed). */
	private void checkSection(List<Integer> codes, Boolean extraSection, List<DeckProblem> problems,
			Map<Integer, Integer> copies, Map<Integer, Boolean> reported) {
		for (int code : codes) {
			Optional<CardData> card = database.find(code);
			if (card.isEmpty()) {
				if (reported.putIfAbsent(code, true) == null) {
					problems.add(new DeckProblem(DeckProblem.Kind.UNKNOWN_CARD, code, 1, 0));
				}
				continue;
			}
			CardData data = card.get();
			copies.merge(data.canonicalCode(), 1, Integer::sum);
			if (reported.containsKey(code)) {
				continue;
			}
			DeckProblem.Kind kind = null;
			if (data.isToken()) {
				kind = DeckProblem.Kind.TOKEN;
			} else if (!pool.test(data.canonicalCode())) {
				kind = DeckProblem.Kind.NOT_IN_POOL;
			} else if (Boolean.FALSE.equals(extraSection) && data.isExtraDeckCard()) {
				kind = DeckProblem.Kind.EXTRA_DECK_CARD_IN_MAIN;
			} else if (Boolean.TRUE.equals(extraSection) && !data.isExtraDeckCard()) {
				kind = DeckProblem.Kind.MAIN_DECK_CARD_IN_EXTRA;
			}
			if (kind != null) {
				reported.put(code, true);
				problems.add(new DeckProblem(kind, code, 1, 0));
			}
		}
	}
}
