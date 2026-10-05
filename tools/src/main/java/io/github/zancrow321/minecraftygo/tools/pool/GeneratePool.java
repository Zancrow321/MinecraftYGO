package io.github.zancrow321.minecraftygo.tools.pool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.data.CardData;
import io.github.zancrow321.minecraftygo.engine.data.GoatVariantResolver;
import io.github.zancrow321.minecraftygo.tools.util.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The card pool pipeline. Inputs: pinned YGOJSON, BabelCDB, CardScripts and YGOMCModels checkouts plus
 * {@code tools/data/*.json}. Outputs (committed): engine data ({@code cards.json}), {@code pool.json},
 * {@code texts_en.json}, {@code sets.json}, {@code scripts.txt} and {@code docs/PoolReport.md}.
 *
 * <p>Rules: every modeled monster is in the pool; spells/traps are added when first released on or before the era
 * cutoff (the latest first release among the modeled monsters unless overridden) and when a script exists; ritual
 * spells of pool ritual monsters are always added. Unmapped model folders fail the build with suggestions.
 */
public final class GeneratePool {
	private static final Pattern LOAD_SCRIPT = Pattern.compile("Duel\\.Load(?:Card)?Script\\(\"([^\"]+\\.lua)\"\\)");
	private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

	private final Path upstream;
	private final Path dataDir;
	private final Path out;
	private final Path report;
	private final List<String> warnings = new ArrayList<>();

	private GeneratePool(Path upstream, Path dataDir, Path out, Path report) {
		this.upstream = upstream;
		this.dataDir = dataDir;
		this.out = out;
		this.report = report;
	}

	public static void main(String[] args) {
		if (args.length != 4) {
			throw new IllegalArgumentException("usage: GeneratePool <upstreamDir> <dataDir> <engineDataOut> <report.md>");
		}
		new GeneratePool(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3])).run();
	}

	/** One pool card. {@code kind}: monster, spell or trap; {@code model}: YGOMCModels folder or null. */
	record PoolCard(int code, String name, String kind, boolean extraDeck, String firstRelease, String model, String reason) {}

	private void run() {
		JsonObject overrides = Json.read(dataDir.resolve("model-overrides.json")).getAsJsonObject().getAsJsonObject("folders");
		JsonObject config = Json.read(dataDir.resolve("pool-config.json")).getAsJsonObject();
		Path ygojson = upstream.resolve("ygojson");
		Path cardScripts = upstream.resolve("cardScripts");
		Path babel = upstream.resolve("babelCdb");

		System.out.println("Loading YGOJSON...");
		List<YgoJson.Card> cards = YgoJson.loadCards(ygojson.resolve("cards.json"));
		List<YgoJson.CardSet> sets = YgoJson.loadSets(ygojson.resolve("sets.json"));
		System.out.println("Loading BabelCDB...");
		Map<Integer, CdbReader.Entry> cdb = CdbReader.read(List.of(babel.resolve("cards.cdb"), babel.resolve("goat-entries.cdb")));
		Set<String> officialScripts = listScripts(cardScripts.resolve("official"));

		Map<String, String> setFirstDate = new HashMap<>();
		for (YgoJson.CardSet set : sets) {
			set.firstLocale().ifPresent(l -> setFirstDate.put(set.id(), l.date()));
		}
		Map<String, YgoJson.Card> cardsById = new HashMap<>();
		Map<String, List<YgoJson.Card>> monstersByName = new HashMap<>();
		Map<Integer, YgoJson.Card> cardsByCode = new HashMap<>();
		for (YgoJson.Card card : cards) {
			cardsById.put(card.id(), card);
			card.passwords().forEach(code -> cardsByCode.putIfAbsent(code, card));
			if ("monster".equals(card.type()) && card.name() != null && card.password().isPresent()) {
				monstersByName.computeIfAbsent(NameNormalizer.normalize(card.name()), _ -> new ArrayList<>()).add(card);
			}
		}

		// ---- 1. model folders -> monsters ------------------------------------------------------------------
		List<ModelFolders> folders = ModelFolders.scan(upstream.resolve("ygomcmodels"));
		Map<Integer, PoolCard> pool = new TreeMap<>();
		List<String> unmapped = new ArrayList<>();
		for (ModelFolders folder : folders) {
			JsonObject override = overrides.has(folder.name()) ? overrides.getAsJsonObject(folder.name()) : new JsonObject();
			if (override.has("ignore") && override.get("ignore").getAsBoolean()) {
				continue;
			}
			YgoJson.Card card;
			if (override.has("code")) {
				card = cardsByCode.get(override.get("code").getAsInt());
			} else {
				String key = NameNormalizer.normalize(override.has("name") ? override.get("name").getAsString() : folder.name());
				List<YgoJson.Card> candidates = monstersByName.getOrDefault(key, List.of());
				if (candidates.size() > 1) {
					throw new IllegalStateException("Ambiguous model folder " + folder.name() + ": " + candidates.stream()
							.map(c -> c.name() + " " + c.passwords()).toList() + " - add a code override");
				}
				card = candidates.isEmpty() ? null : candidates.getFirst();
			}
			if (card == null) {
				unmapped.add(folder.name() + " (suggestions: " + suggestions(folder.name(), monstersByName) + ")");
				continue;
			}
			Optional<Integer> resolved = resolveCode(card, cdb);
			if (resolved.isEmpty()) {
				throw new IllegalStateException("Model " + folder.name() + " maps to " + card.name() + " " + card.passwords()
						+ " which is missing in BabelCDB");
			}
			int code = resolved.get();
			PoolCard previous = pool.put(code, new PoolCard(code, card.name(), "monster", cdb.get(code).data().isExtraDeckCard(),
					firstRelease(card, setFirstDate), folder.name(), "model"));
			if (previous != null) {
				throw new IllegalStateException("Folders " + previous.model() + " and " + folder.name() + " both map to " + code);
			}
		}
		if (!unmapped.isEmpty()) {
			throw new IllegalStateException("Unmapped YGOMCModels folders - add them to tools/data/model-overrides.json:\n  "
					+ String.join("\n  ", unmapped));
		}
		List<PoolCard> undated = pool.values().stream().filter(c -> c.firstRelease() == null).toList();
		if (!undated.isEmpty()) {
			warnings.add("Monsters without release date (ignored for the cutoff): " + undated.stream().map(PoolCard::name).toList());
		}

		// ---- 2. era cutoff ---------------------------------------------------------------------------------------
		String cutoff = config.has("eraCutoff") && !config.get("eraCutoff").isJsonNull()
				? config.get("eraCutoff").getAsString()
				: pool.values().stream().map(PoolCard::firstRelease).filter(d -> d != null).max(String::compareTo).orElseThrow();

		// ---- 3. spells and traps of the era -------------------------------------------------------------------
		Set<Integer> exclude = codes(config.getAsJsonArray("excludeCodes"));
		Set<Integer> include = codes(config.getAsJsonArray("includeCodes"));
		int skippedNoScript = 0;
		for (YgoJson.Card card : cards) {
			if (!("spell".equals(card.type()) || "trap".equals(card.type())) || card.illegal()) {
				continue;
			}
			Optional<Integer> resolved = resolveCode(card, cdb);
			if (resolved.isEmpty()) {
				continue;
			}
			int code = resolved.get();
			String first = firstRelease(card, setFirstDate);
			boolean inEra = first != null && first.compareTo(cutoff) <= 0;
			if ((!inEra && !include.contains(code)) || exclude.contains(code) || !cdb.containsKey(code)) {
				continue;
			}
			if (!officialScripts.contains("c" + code + ".lua")) {
				skippedNoScript++;
				continue;
			}
			pool.putIfAbsent(code, new PoolCard(code, card.name(), card.type(), false, first, null, inEra ? "era" : "include"));
		}
		if (skippedNoScript > 0) {
			warnings.add(skippedNoScript + " era spells/traps skipped because they have no script");
		}

		// ---- 4. ritual spells for pool ritual monsters --------------------------------------------------------
		for (PoolCard monster : List.copyOf(pool.values())) {
			CardData data = cdb.get(monster.code()).data();
			if (!"monster".equals(monster.kind()) || !data.isType(OcgConstants.TYPE_RITUAL)) {
				continue;
			}
			// Classic wording of a dedicated ritual spell; later generic support ("Chaos Form", ...) is not pulled in.
			String quoted = "used to Ritual Summon \"" + monster.name() + "\"";
			for (var entry : cdb.entrySet()) {
				CardData spell = entry.getValue().data();
				if (spell.isType(OcgConstants.TYPE_SPELL) && spell.isType(OcgConstants.TYPE_RITUAL)
						&& entry.getKey() < GoatVariantResolver.FIRST_GOAT_CODE
						&& entry.getValue().text().description().contains(quoted)
						&& officialScripts.contains("c" + entry.getKey() + ".lua")
						&& !pool.containsKey(entry.getKey())) {
					YgoJson.Card card = cardsByCode.get(entry.getKey());
					pool.put(entry.getKey(), new PoolCard(entry.getKey(), entry.getValue().text().name(), "spell", false,
							card == null ? null : firstRelease(card, setFirstDate), null, "ritual for " + monster.name()));
				}
			}
		}

		// ---- 5. fusion material check -------------------------------------------------------------------------
		Set<String> poolNames = new TreeSet<>();
		pool.values().forEach(c -> poolNames.add(c.name()));
		for (PoolCard card : pool.values()) {
			CardData data = cdb.get(card.code()).data();
			if (!data.isType(OcgConstants.TYPE_FUSION)) {
				continue;
			}
			String firstLine = cdb.get(card.code()).text().description().split("\\R", 2)[0];
			Matcher matcher = QUOTED.matcher(firstLine);
			while (matcher.find()) {
				if (!poolNames.contains(matcher.group(1))) {
					warnings.add("Fusion " + card.name() + ": material \"" + matcher.group(1) + "\" is not obtainable");
				}
			}
		}

		// ---- 6. scripts ------------------------------------------------------------------------------------------
		GoatVariantResolver goat = new GoatVariantResolver(goatMapping(cdb));
		Set<String> scripts = new TreeSet<>(listScripts(cardScripts));
		scripts.add("unofficial/proc_unofficial.lua");
		Set<String> scriptPaths = new TreeSet<>(scripts);
		for (int code : pool.keySet()) {
			scriptPaths.add("official/c" + code + ".lua");
			int goatCode = goat.toGoat(code);
			if (goatCode != code && Files.isRegularFile(cardScripts.resolve("goat/c" + goatCode + ".lua"))) {
				scriptPaths.add("goat/c" + goatCode + ".lua");
			}
		}
		scriptPaths.removeIf(path -> !Files.isRegularFile(cardScripts.resolve(path)));
		closeOverLoadScript(cardScripts, scriptPaths);

		// ---- 7. outputs ------------------------------------------------------------------------------------------
		Set<Integer> textCodes = new TreeSet<>(pool.keySet());
		textCodes.addAll(referencedTokens(cardScripts, scriptPaths, cdb));
		writeCards(cdb);
		writePool(pool, cutoff);
		writeTexts(textCodes, cdb);
		writeScripts(scriptPaths);
		int setCount = writeSets(sets, cardsById, pool, cutoff);
		writeReport(pool, folders.size(), cutoff, scriptPaths.size(), setCount, textCodes.size() - pool.size());
		warnings.forEach(w -> System.out.println("WARN " + w));
		System.out.println("Pool: " + pool.size() + " cards, cutoff " + cutoff);
	}

	/** The card's passcode as known to BabelCDB: the first listed password that is an original (alias 0) entry. */
	private static Optional<Integer> resolveCode(YgoJson.Card card, Map<Integer, CdbReader.Entry> cdb) {
		return card.passwords().stream().filter(code -> cdb.containsKey(code) && cdb.get(code).data().alias() == 0).findFirst()
				.or(() -> card.passwords().stream().filter(cdb::containsKey).findFirst());
	}

	private static String suggestions(String folder, Map<String, List<YgoJson.Card>> monstersByName) {
		String key = NameNormalizer.normalize(folder);
		return monstersByName.keySet().stream()
				.filter(name -> NameNormalizer.levenshtein(key, name) <= 3)
				.sorted(Comparator.comparingInt(name -> NameNormalizer.levenshtein(key, name)))
				.limit(3)
				.map(name -> monstersByName.get(name).getFirst().name())
				.toList()
				.toString();
	}

	private static String firstRelease(YgoJson.Card card, Map<String, String> setFirstDate) {
		return card.setIds().stream().map(setFirstDate::get).filter(d -> d != null).min(String::compareTo).orElse(null);
	}

	private static Set<Integer> codes(JsonArray array) {
		Set<Integer> codes = new TreeSet<>();
		if (array != null) {
			array.forEach(e -> codes.add(e.getAsInt()));
		}
		return codes;
	}

	private static Set<String> listScripts(Path dir) {
		try (Stream<Path> files = Files.list(dir)) {
			Set<String> names = new TreeSet<>();
			files.filter(p -> p.toString().endsWith(".lua")).forEach(p -> names.add(p.getFileName().toString()));
			return names;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Map<Integer, Integer> goatMapping(Map<Integer, CdbReader.Entry> cdb) {
		Map<Integer, Integer> mapping = new HashMap<>();
		cdb.forEach((code, entry) -> {
			if (code >= GoatVariantResolver.FIRST_GOAT_CODE && code <= GoatVariantResolver.LAST_GOAT_CODE && entry.data().alias() != 0) {
				mapping.put(entry.data().alias(), code);
			}
		});
		return mapping;
	}

	/** Adds scripts referenced literally via Duel.LoadScript / Duel.LoadCardScript until nothing new appears. */
	private static void closeOverLoadScript(Path root, Set<String> paths) {
		Map<String, String> byName = new HashMap<>();
		try (Stream<Path> files = Files.walk(root, 2)) {
			files.filter(p -> p.toString().endsWith(".lua")).forEach(p -> byName.putIfAbsent(p.getFileName().toString(),
					root.relativize(p).toString().replace('\\', '/')));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		List<String> queue = new ArrayList<>(paths);
		while (!queue.isEmpty()) {
			String path = queue.removeLast();
			Matcher matcher = LOAD_SCRIPT.matcher(read(root.resolve(path)));
			while (matcher.find()) {
				String target = byName.get(matcher.group(1));
				if (target != null && paths.add(target)) {
					queue.add(target);
				}
			}
		}
	}

	/** Token cards whose codes appear in the bundled scripts (their names are needed for display). */
	private static Set<Integer> referencedTokens(Path root, Set<String> paths, Map<Integer, CdbReader.Entry> cdb) {
		Pattern number = Pattern.compile("\\b(\\d{5,9})\\b");
		Set<Integer> tokens = new TreeSet<>();
		for (String path : paths) {
			if (!path.startsWith("official/") && !path.startsWith("goat/")) {
				continue;
			}
			Matcher matcher = number.matcher(read(root.resolve(path)));
			while (matcher.find()) {
				int code = Integer.parseInt(matcher.group(1));
				CdbReader.Entry entry = cdb.get(code);
				if (entry != null && entry.data().isToken()) {
					tokens.add(code);
				}
			}
		}
		return tokens;
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// ---- writers ---------------------------------------------------------------------------------------------

	/** All cards, one per line: [code, alias, [setcodes], type, level, attribute, race, atk, def, lscale, rscale, link]. */
	private void writeCards(Map<Integer, CdbReader.Entry> cdb) {
		StringBuilder json = new StringBuilder("{\"format\":1,\"cards\":[\n");
		boolean first = true;
		for (CdbReader.Entry entry : cdb.values()) {
			CardData c = entry.data();
			if (!first) {
				json.append(",\n");
			}
			first = false;
			JsonArray row = new JsonArray();
			row.add(c.code());
			row.add(c.alias());
			JsonArray setcodes = new JsonArray();
			for (int sc : c.setcodes()) {
				setcodes.add(sc);
			}
			row.add(setcodes);
			row.add(Integer.toUnsignedLong(c.type()));
			row.add(c.level());
			row.add(c.attribute());
			row.add(c.race());
			row.add(c.attack());
			row.add(c.defense());
			row.add(c.leftScale());
			row.add(c.rightScale());
			row.add(c.linkMarker());
			json.append(Json.COMPACT.toJson(row));
		}
		json.append("\n]}\n");
		Json.write(out.resolve("cards.json"), json.toString());
	}

	private void writePool(Map<Integer, PoolCard> pool, String cutoff) {
		JsonObject root = new JsonObject();
		root.addProperty("format", 1);
		root.addProperty("eraCutoff", cutoff);
		JsonArray list = new JsonArray();
		for (PoolCard card : pool.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("code", card.code());
			o.addProperty("name", card.name());
			o.addProperty("kind", card.kind());
			if (card.extraDeck()) {
				o.addProperty("extraDeck", true);
			}
			if (card.firstRelease() != null) {
				o.addProperty("firstRelease", card.firstRelease());
			}
			if (card.model() != null) {
				o.addProperty("model", card.model());
			}
			o.addProperty("reason", card.reason());
			list.add(o);
		}
		root.add("cards", list);
		Json.write(out.resolve("pool.json"), Json.PRETTY.toJson(root) + "\n");
	}

	private void writeTexts(Set<Integer> codes, Map<Integer, CdbReader.Entry> cdb) {
		JsonObject root = new JsonObject();
		for (int code : codes) {
			CdbReader.Text text = cdb.get(code).text();
			JsonObject o = new JsonObject();
			o.addProperty("name", text.name());
			o.addProperty("desc", text.description());
			if (!text.strings().isEmpty()) {
				JsonArray strings = new JsonArray();
				text.strings().forEach(strings::add);
				o.add("strings", strings);
			}
			root.add(Integer.toString(code), o);
		}
		Json.write(out.resolve("texts_en.json"), Json.PRETTY.toJson(root) + "\n");
	}

	private void writeScripts(Set<String> paths) {
		Json.write(out.resolve("scripts.txt"), String.join("\n", paths) + "\n");
	}

	/** Booster candidates: sets first released up to the cutoff, restricted to pool cards (with rarity). */
	private int writeSets(List<YgoJson.CardSet> sets, Map<String, YgoJson.Card> cardsById, Map<Integer, PoolCard> pool,
			String cutoff) {
		JsonArray list = new JsonArray();
		List<YgoJson.CardSet> sorted = new ArrayList<>(sets);
		sorted.sort(Comparator.comparing((YgoJson.CardSet s) -> s.firstLocale().map(YgoJson.Locale::date).orElse("9999"))
				.thenComparing(YgoJson.CardSet::id));
		for (YgoJson.CardSet set : sorted) {
			Optional<YgoJson.Locale> first = set.firstLocale();
			if (first.isEmpty() || first.get().date().compareTo(cutoff) > 0 || set.contents().isEmpty()) {
				continue;
			}
			String language = first.get().language();
			YgoJson.Content content = set.contents().stream().filter(c -> c.locales().contains(language)).findFirst()
					.orElse(set.contents().getFirst());
			Map<Integer, String> printings = new LinkedHashMap<>();
			for (YgoJson.Printing printing : content.cards()) {
				YgoJson.Card card = cardsById.get(printing.cardId());
				if (card == null) {
					continue;
				}
				card.passwords().stream().filter(pool::containsKey).findFirst().ifPresent(code ->
						printings.putIfAbsent(code, printing.rarity() == null ? "common" : printing.rarity()));
			}
			if (printings.size() < 5) {
				continue;
			}
			JsonObject o = new JsonObject();
			o.addProperty("id", set.id());
			o.addProperty("name", set.name());
			o.addProperty("prefix", first.get().prefix());
			o.addProperty("date", first.get().date());
			o.addProperty("language", language);
			JsonArray cards = new JsonArray();
			printings.forEach((code, rarity) -> {
				JsonArray entry = new JsonArray();
				entry.add(code);
				entry.add(rarity);
				cards.add(entry);
			});
			o.add("cards", cards);
			list.add(o);
		}
		JsonObject root = new JsonObject();
		root.addProperty("format", 1);
		root.add("sets", list);
		Json.write(out.resolve("sets.json"), Json.COMPACT.toJson(root).replace("},{", "},\n{") + "\n");
		return list.size();
	}

	private void writeReport(Map<Integer, PoolCard> pool, int folders, String cutoff, int scripts, int sets, int tokens) {
		Map<String, Integer> byKind = new TreeMap<>();
		Map<String, Integer> byYear = new TreeMap<>();
		Map<String, Integer> byReason = new TreeMap<>();
		for (PoolCard card : pool.values()) {
			byKind.merge(card.kind() + (card.extraDeck() ? " (extra deck)" : ""), 1, Integer::sum);
			byYear.merge(card.firstRelease() == null ? "unknown" : card.firstRelease().substring(0, 4), 1, Integer::sum);
			byReason.merge(card.reason().startsWith("ritual") ? "ritual" : card.reason(), 1, Integer::sum);
		}
		StringBuilder md = new StringBuilder();
		md.append("# Card pool report\n\n");
		md.append("Generated by `./gradlew :tools:generatePool` from the pinned upstreams in `sources.lock.json`.\n\n");
		md.append("| | |\n|---|---|\n");
		md.append("| Model folders | ").append(folders).append(" |\n");
		md.append("| Pool cards | ").append(pool.size()).append(" |\n");
		md.append("| Era cutoff | ").append(cutoff).append(" |\n");
		md.append("| Bundled scripts | ").append(scripts).append(" |\n");
		md.append("| Booster set candidates | ").append(sets).append(" |\n");
		md.append("| Token texts | ").append(tokens).append(" |\n\n");
		md.append("## By kind\n\n");
		byKind.forEach((k, v) -> md.append("- ").append(k).append(": ").append(v).append('\n'));
		md.append("\n## By reason\n\n");
		byReason.forEach((k, v) -> md.append("- ").append(k).append(": ").append(v).append('\n'));
		md.append("\n## By first release year\n\n");
		byYear.forEach((k, v) -> md.append("- ").append(k).append(": ").append(v).append('\n'));
		md.append("\n## Warnings\n\n");
		if (warnings.isEmpty()) {
			md.append("None.\n");
		}
		warnings.stream().sorted().forEach(w -> md.append("- ").append(w).append('\n'));
		md.append("\n## Pool\n\n| Code | Name | Kind | First release | Model |\n|---|---|---|---|---|\n");
		for (PoolCard card : pool.values()) {
			md.append("| ").append(card.code()).append(" | ").append(card.name().replace("|", "\\|")).append(" | ")
					.append(card.kind()).append(card.extraDeck() ? " (extra)" : "").append(" | ")
					.append(card.firstRelease() == null ? "" : card.firstRelease()).append(" | ")
					.append(card.model() == null ? "" : card.model()).append(" |\n");
		}
		Json.write(report, md.toString());
	}
}
