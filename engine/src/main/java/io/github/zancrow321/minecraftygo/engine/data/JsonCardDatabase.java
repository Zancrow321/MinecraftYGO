package io.github.zancrow321.minecraftygo.engine.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The bundled numeric card data ({@code minecraftygo/data/cards.json}, generated from BabelCDB): one row per card
 * {@code [code, alias, [setcodes], type, level, attribute, race, atk, def, lscale, rscale, linkMarker]}.
 */
public final class JsonCardDatabase implements CardDatabase {
	private final Map<Integer, CardData> cards;

	private JsonCardDatabase(Map<Integer, CardData> cards) {
		this.cards = Map.copyOf(cards);
	}

	public static JsonCardDatabase read(Reader reader) {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		if (root.get("format").getAsInt() != 1) {
			throw new IllegalArgumentException("Unsupported cards.json format " + root.get("format"));
		}
		Map<Integer, CardData> cards = new HashMap<>();
		for (JsonElement element : root.getAsJsonArray("cards")) {
			JsonArray row = element.getAsJsonArray();
			JsonArray setcodeArray = row.get(2).getAsJsonArray();
			int[] setcodes = new int[setcodeArray.size()];
			for (int i = 0; i < setcodes.length; i++) {
				setcodes[i] = setcodeArray.get(i).getAsInt();
			}
			CardData card = new CardData(row.get(0).getAsInt(), row.get(1).getAsInt(), setcodes,
					(int) row.get(3).getAsLong(), row.get(4).getAsInt(), row.get(5).getAsInt(), row.get(6).getAsLong(),
					row.get(7).getAsInt(), row.get(8).getAsInt(), row.get(9).getAsInt(), row.get(10).getAsInt(),
					row.get(11).getAsInt());
			cards.put(card.code(), card);
		}
		return new JsonCardDatabase(cards);
	}

	@Override
	public Optional<CardData> find(int code) {
		return Optional.ofNullable(cards.get(code));
	}

	@Override
	public Collection<Integer> codes() {
		return Collections.unmodifiableSet(cards.keySet());
	}

	public int size() {
		return cards.size();
	}
}
