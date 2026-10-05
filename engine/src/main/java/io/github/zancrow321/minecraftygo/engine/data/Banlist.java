package io.github.zancrow321.minecraftygo.engine.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * How many copies of each card a deck may hold: 3 unless the list says 0 (forbidden), 1 or 2.
 */
public record Banlist(String name, Map<Integer, Integer> limits) {
    public static final String RESOURCE = "/minecraftygo/banlist.json";
    public static final int MAX_COPIES = 3;

    public Banlist {
        limits = Map.copyOf(limits);
    }

    public int limit(int code) {
        return limits.getOrDefault(code, MAX_COPIES);
    }

    public static Banlist loadBundled() {
        try (InputStream in = Banlist.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            return load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads {@code {"name": "...", "limits": {"<passcode>": <copies>, ...}}}. */
    public static Banlist load(Reader reader) {
        JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
        Map<Integer, Integer> limits = new HashMap<>();
        o.getAsJsonObject("limits").entrySet()
                .forEach(e -> limits.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt()));
        return new Banlist(o.has("name") ? o.get("name").getAsString() : "custom", limits);
    }
}
