package io.github.zancrow321.jadm.engine.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The official lists a server can pick by name besides the TCG history of {@link Banlists}: the OCG, Traditional,
 * Worlds and Master Duel Forbidden & Limited Lists and the Genesys point lists, bundled by
 * {@code tools/carddata/build_banlists.py}. {@code /jadm banlist update} downloads newer ones (the newest TCG list
 * too, as {@code tcg}) from where that script reads them: Project Ignis' {@code .lflist.conf} files, keyed by
 * passcode, and yaml-yugi-limit-regulation's JSON, keyed by Konami's card id.
 */
public record OfficialBanlists(List<Entry> lists) {
    public static final String RESOURCE = "/jadm/official_banlists.json";
    public static final String KONAMI_IDS = "/jadm/konami_ids.json";
    /** The id of the newest TCG list; the bundled one is the last of {@link Banlists}. */
    public static final String TCG = "tcg";
    public static final String GENESYS = "genesys";

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final Pattern LFLIST_HEADER = Pattern.compile("!(\\d{4})\\.(\\d{1,2})(?:\\.(\\d{1,2}))?.*");
    private static final Pattern LFLIST_CARD = Pattern.compile("(\\d+)\\s+(-?\\d+).*");

    /**
     * @param limits the copies of each limited card; empty on a Genesys list
     * @param points the Genesys points of each pointed card, {@code null} on a Forbidden & Limited List
     */
    public record Entry(String id, String name, LocalDate date, Map<Integer, Integer> limits,
                        Map<Integer, Integer> points) {
        public Entry {
            limits = Map.copyOf(limits);
            points = points == null ? null : Map.copyOf(points);
        }

        public boolean genesys() {
            return points != null;
        }

        /** The list to check decks against; {@code pointCap} only counts for a Genesys list. */
        public Banlist banlist(CardDatabase cards, int pointCap) {
            return genesys() ? Banlist.genesys(name, limits, points, pointCap, cards) : new Banlist(name, limits);
        }

        /** How many cards the list names. */
        public int size() {
            return genesys() ? points.size() : limits.size();
        }
    }

    public OfficialBanlists {
        lists = List.copyOf(lists);
    }

    /** @return the list with this id, or {@code null} */
    public Entry get(String id) {
        for (Entry e : lists) {
            if (e.id().equalsIgnoreCase(id)) {
                return e;
            }
        }
        return null;
    }

    /** These lists with {@code entry} in place of the one with its id. */
    public OfficialBanlists with(Entry entry) {
        List<Entry> out = new ArrayList<>();
        boolean replaced = false;
        for (Entry e : lists) {
            if (e.id().equalsIgnoreCase(entry.id())) {
                out.add(entry);
                replaced = true;
            } else {
                out.add(e);
            }
        }
        if (!replaced) {
            out.add(entry);
        }
        return new OfficialBanlists(out);
    }

    /** These lists, each replaced by the one of {@code newer} with the same id. */
    public OfficialBanlists with(OfficialBanlists newer) {
        OfficialBanlists out = this;
        for (Entry e : newer.lists()) {
            out = out.with(e);
        }
        return out;
    }

    public static OfficialBanlists loadBundled() {
        try (InputStream in = OfficialBanlists.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            return load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads {@code {"lists": [{"id", "name", "date", "limits" or "points"}, ...]}} as {@link #toJson} writes it. */
    public static OfficialBanlists load(Reader reader) {
        List<Entry> lists = new ArrayList<>();
        for (JsonElement e : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("lists")) {
            JsonObject o = e.getAsJsonObject();
            lists.add(new Entry(o.get("id").getAsString(), o.get("name").getAsString(),
                    LocalDate.parse(o.get("date").getAsString()), Banlist.codes(o, "limits"),
                    o.has("points") ? Banlist.codes(o, "points") : null));
        }
        return new OfficialBanlists(lists);
    }

    public static OfficialBanlists parse(String json) {
        return load(new StringReader(json));
    }

    public String toJson() {
        JsonArray array = new JsonArray();
        for (Entry e : lists) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id());
            o.addProperty("name", e.name());
            o.addProperty("date", e.date().toString());
            o.add(e.genesys() ? "points" : "limits", codes(e.genesys() ? e.points() : e.limits()));
            array.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("lists", array);
        return root.toString();
    }

    private static JsonObject codes(Map<Integer, Integer> codes) {
        JsonObject o = new JsonObject();
        new TreeMap<>(codes).forEach((code, n) -> o.addProperty(String.valueOf(code), n));
        return o;
    }

    /**
     * The first list of a Project Ignis {@code .lflist.conf} ({@code !2026.09 TCG}, then {@code <passcode>
     * <copies> --name} lines), named e.g. "TCG, September 2026".
     *
     * @throws IllegalArgumentException if there is no list in it
     */
    public static Entry parseLflist(String id, String displayName, String text) {
        LocalDate date = null;
        Map<Integer, Integer> limits = new HashMap<>();
        for (String line : text.split("\\R")) {
            line = line.strip();
            if (line.startsWith("!")) {
                if (date != null) {
                    break;
                }
                Matcher m = LFLIST_HEADER.matcher(line);
                if (!m.matches()) {
                    throw new IllegalArgumentException("Unknown list header " + line);
                }
                date = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        m.group(3) == null ? 1 : Integer.parseInt(m.group(3)));
                continue;
            }
            Matcher m = LFLIST_CARD.matcher(line);
            if (date != null && m.matches()) {
                limits.put(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            }
        }
        if (date == null || limits.isEmpty()) {
            throw new IllegalArgumentException("No list in it");
        }
        return new Entry(id, displayName + ", " + MONTH.format(date), date, limits, null);
    }

    /**
     * A yaml-yugi-limit-regulation list ({@code {"date": "2026-10-18", "regulation": {"<Konami id>": <n>}}}), the
     * numbers copies or, with {@code points}, Genesys points. Cards without a passcode in {@code konamiIds} (newer
     * than the bundled card data) are left out.
     */
    public static Entry parseVector(String id, String displayName, boolean points, String json,
                                    Map<Integer, List<Integer>> konamiIds) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        LocalDate date = LocalDate.parse(o.get("date").getAsString());
        Map<Integer, Integer> values = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("regulation").entrySet()) {
            for (int code : konamiIds.getOrDefault(Integer.parseInt(e.getKey()), List.of())) {
                values.put(code, e.getValue().getAsInt());
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("No cards in it");
        }
        return points ? new Entry(id, displayName + ", " + DAY.format(date), date, Map.of(), values)
                : new Entry(id, displayName + ", " + MONTH.format(date), date, values, null);
    }

    /** Konami's card ids with the passcodes of the bundled cards (alternate artworks share an id). */
    public static Map<Integer, List<Integer>> loadKonamiIds() {
        try (InputStream in = OfficialBanlists.class.getResourceAsStream(KONAMI_IDS)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + KONAMI_IDS);
            }
            Map<Integer, List<Integer>> ids = new HashMap<>();
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()
                    .forEach(e -> {
                        List<Integer> codes = new ArrayList<>();
                        e.getValue().getAsJsonArray().forEach(c -> codes.add(c.getAsInt()));
                        ids.put(Integer.parseInt(e.getKey()), List.copyOf(codes));
                    });
            return ids;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
