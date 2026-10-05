package io.github.zancrow321.minecraftygo.engine.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The obtainable cards ({@code minecraftygo/data/pool.json}): every monster with a YGOMCModels figure plus the spells
 * and traps of the same era. The pool restricts what can be collected and put into decks; the engine itself can
 * still run any card.
 */
public final class CardPool {
	/** {@code kind} is monster, spell or trap; {@code model} is the YGOMCModels folder (null for spells/traps). */
	public record Entry(int code, String name, String kind, boolean extraDeck, String firstRelease, String model) {
		public boolean hasModel() {
			return model != null;
		}
	}

	private final String eraCutoff;
	private final Map<Integer, Entry> entries;

	private CardPool(String eraCutoff, Map<Integer, Entry> entries) {
		this.eraCutoff = eraCutoff;
		this.entries = Collections.unmodifiableMap(entries);
	}

	public static CardPool read(Reader reader) {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		Map<Integer, Entry> entries = new LinkedHashMap<>();
		for (JsonElement element : root.getAsJsonArray("cards")) {
			JsonObject o = element.getAsJsonObject();
			int code = o.get("code").getAsInt();
			entries.put(code, new Entry(code, o.get("name").getAsString(), o.get("kind").getAsString(),
					o.has("extraDeck") && o.get("extraDeck").getAsBoolean(),
					o.has("firstRelease") ? o.get("firstRelease").getAsString() : null,
					o.has("model") ? o.get("model").getAsString() : null));
		}
		return new CardPool(root.get("eraCutoff").getAsString(), entries);
	}

	public String eraCutoff() {
		return eraCutoff;
	}

	public boolean contains(int code) {
		return entries.containsKey(code);
	}

	public Optional<Entry> get(int code) {
		return Optional.ofNullable(entries.get(code));
	}

	public List<Entry> entries() {
		return new ArrayList<>(entries.values());
	}

	public int size() {
		return entries.size();
	}
}
