package io.github.zancrow321.minecraftygo.engine.data;

import io.github.zancrow321.minecraftygo.engine.deck.Deck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GOAT format uses separate card entries ({@code 5047xxxxx}, alias = the original card) whose scripts implement the
 * 2005 rulings. In GOAT duels the host replaces deck codes by these variants; display, images and models keep using
 * the original code ({@link #original}).
 */
public final class GoatVariantResolver {
	public static final int FIRST_GOAT_CODE = 504_700_000;
	public static final int LAST_GOAT_CODE = 504_799_999;

	private final Map<Integer, Integer> originalToGoat;
	private final Map<Integer, Integer> goatToOriginal;

	public GoatVariantResolver(Map<Integer, Integer> originalToGoat) {
		this.originalToGoat = Map.copyOf(originalToGoat);
		Map<Integer, Integer> reverse = new HashMap<>();
		originalToGoat.forEach((original, goat) -> reverse.put(goat, original));
		this.goatToOriginal = Map.copyOf(reverse);
	}

	public static GoatVariantResolver fromDatabase(CardDatabase database) {
		Map<Integer, Integer> mapping = new HashMap<>();
		for (int code : database.codes()) {
			if (code >= FIRST_GOAT_CODE && code <= LAST_GOAT_CODE) {
				database.find(code).filter(card -> card.alias() != 0).ifPresent(card -> mapping.put(card.alias(), code));
			}
		}
		return new GoatVariantResolver(mapping);
	}

	public int toGoat(int code) {
		return originalToGoat.getOrDefault(code, code);
	}

	public int original(int code) {
		return goatToOriginal.getOrDefault(code, code);
	}

	public Deck toGoat(Deck deck) {
		return new Deck(map(deck.main()), map(deck.extra()), map(deck.side()));
	}

	private List<Integer> map(List<Integer> codes) {
		return codes.stream().map(this::toGoat).toList();
	}

	public int size() {
		return originalToGoat.size();
	}
}
