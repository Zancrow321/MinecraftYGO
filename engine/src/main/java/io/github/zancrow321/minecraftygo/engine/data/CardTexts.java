package io.github.zancrow321.minecraftygo.engine.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Card names, descriptions and effect strings of the card pool ({@code minecraftygo/data/texts_en.json}). Effect
 * descriptions in prompts are {@code (code << 20) | index}; {@link #effectString} resolves them.
 */
public final class CardTexts {
	public record Text(String name, String description, List<String> strings) {
		public Text {
			strings = List.copyOf(strings);
		}
	}

	private final Map<Integer, Text> texts;

	private CardTexts(Map<Integer, Text> texts) {
		this.texts = Map.copyOf(texts);
	}

	public static CardTexts read(Reader reader) {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		Map<Integer, Text> texts = new HashMap<>();
		for (var entry : root.entrySet()) {
			JsonObject o = entry.getValue().getAsJsonObject();
			List<String> strings = new ArrayList<>();
			if (o.has("strings")) {
				for (JsonElement s : o.getAsJsonArray("strings")) {
					strings.add(s.getAsString());
				}
			}
			texts.put(Integer.parseInt(entry.getKey()), new Text(o.get("name").getAsString(), o.get("desc").getAsString(), strings));
		}
		return new CardTexts(texts);
	}

	public Optional<Text> get(int code) {
		return Optional.ofNullable(texts.get(code));
	}

	public Optional<String> name(int code) {
		return get(code).map(Text::name);
	}

	/** Resolves a core effect description; empty for system strings ({@code code == 0}) or unknown cards. */
	public Optional<String> effectString(long description) {
		int code = (int) (description >>> 20);
		int index = (int) (description & 0xFFFFF);
		if (code == 0) {
			return Optional.empty();
		}
		return get(code).flatMap(text -> index < text.strings().size() && !text.strings().get(index).isEmpty()
				? Optional.of(text.strings().get(index)) : Optional.empty());
	}

	public int size() {
		return texts.size();
	}
}
