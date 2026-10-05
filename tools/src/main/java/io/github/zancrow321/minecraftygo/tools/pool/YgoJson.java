package io.github.zancrow321.minecraftygo.tools.pool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zancrow321.minecraftygo.tools.util.Json;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The parts of YGOJSON (iconmaster5326/YGOJSON, MIT) the pool generator needs. */
public final class YgoJson {
	private static final java.util.regex.Pattern LEADING_DIGITS = java.util.regex.Pattern.compile("^\\d{1,9}");

	private YgoJson() {}

	/** {@code type} is YGOJSON's cardType: monster, spell, trap, token or skill. */
	public record Card(String id, String type, String name, List<Integer> passwords, Set<String> setIds,
			boolean illegal, List<String> classifications) {
		public Optional<Integer> password() {
			return passwords.isEmpty() ? Optional.empty() : Optional.of(passwords.getFirst());
		}
	}

	public record Locale(String language, String prefix, String date, List<String> formats) {}

	public record Printing(String cardId, String rarity, String suffix) {}

	public record Content(List<String> locales, List<String> formats, List<Printing> cards) {}

	public record CardSet(String id, String name, Map<String, Locale> locales, List<Content> contents) {
		/** Earliest dated locale, if any. */
		public Optional<Locale> firstLocale() {
			return locales.values().stream().filter(l -> l.date() != null).min((a, b) -> a.date().compareTo(b.date()));
		}
	}

	public static List<Card> loadCards(Path cardsJson) {
		List<Card> cards = new ArrayList<>();
		Json.forEachObject(cardsJson, o -> {
			JsonObject en = o.getAsJsonObject("text").getAsJsonObject("en");
			String name = en == null ? null : Json.string(en, "name");
			List<Integer> passwords = new ArrayList<>();
			for (JsonElement password : o.getAsJsonArray("passwords")) {
				// Some entries carry wiki markup after the digits, e.g. "39399168<!--...-->".
				java.util.regex.Matcher digits = LEADING_DIGITS.matcher(password.getAsString());
				if (digits.find()) {
					passwords.add(Integer.parseInt(digits.group()));
				}
			}
			cards.add(new Card(Json.string(o, "id"), Json.string(o, "cardType"), name, passwords,
					strings(o.getAsJsonArray("sets"), new LinkedHashSet<>()),
					o.has("illegal") && o.get("illegal").getAsBoolean(),
					o.has("classifications") ? new ArrayList<>(strings(o.getAsJsonArray("classifications"), new LinkedHashSet<>())) : List.of()));
		});
		return cards;
	}

	public static List<CardSet> loadSets(Path setsJson) {
		List<CardSet> sets = new ArrayList<>();
		Json.forEachObject(setsJson, o -> {
			Map<String, Locale> locales = new LinkedHashMap<>();
			JsonObject localeObject = o.getAsJsonObject("locales");
			if (localeObject != null) {
				for (var entry : localeObject.entrySet()) {
					JsonObject l = entry.getValue().getAsJsonObject();
					locales.put(entry.getKey(), new Locale(entry.getKey(), Json.string(l, "prefix"), Json.string(l, "date"),
							l.has("formats") ? List.copyOf(strings(l.getAsJsonArray("formats"), new LinkedHashSet<>())) : List.of()));
				}
			}
			List<Content> contents = new ArrayList<>();
			JsonArray contentArray = o.getAsJsonArray("contents");
			if (contentArray != null) {
				for (JsonElement element : contentArray) {
					JsonObject c = element.getAsJsonObject();
					List<Printing> printings = new ArrayList<>();
					for (JsonElement p : c.getAsJsonArray("cards")) {
						JsonObject printing = p.getAsJsonObject();
						printings.add(new Printing(Json.string(printing, "card"), Json.string(printing, "rarity"),
								Json.string(printing, "suffix")));
					}
					contents.add(new Content(
							c.has("locales") ? List.copyOf(strings(c.getAsJsonArray("locales"), new LinkedHashSet<>())) : List.of(),
							c.has("formats") ? List.copyOf(strings(c.getAsJsonArray("formats"), new LinkedHashSet<>())) : List.of(),
							printings));
				}
			}
			JsonObject names = o.getAsJsonObject("name");
			String name = names == null ? null : Json.string(names, "en");
			sets.add(new CardSet(Json.string(o, "id"), name, locales, contents));
		});
		return sets;
	}

	private static <C extends java.util.Collection<String>> C strings(JsonArray array, C target) {
		if (array != null) {
			for (JsonElement element : array) {
				target.add(element.getAsString());
			}
		}
		return target;
	}
}
