package io.github.zancrow321.jadm.engine.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zancrow321.jadm.engine.OcgConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * How many copies of each card a deck may hold: 3 unless the list says 0 (forbidden), 1 or 2. A Genesys list
 * instead gives cards points: a deck's cards (main, extra and side together) may cost at most {@link #pointCap()},
 * and Link and Pendulum monsters aren't allowed.
 */
public record Banlist(String name, Map<Integer, Integer> limits, Map<Integer, Integer> points, int pointCap) {
    public static final String RESOURCE = "/jadm/banlist.json";
    public static final int MAX_COPIES = 3;
    /** The {@link #pointCap()} of a Forbidden & Limited List, which has no points. */
    public static final int NO_POINTS = -1;
    /** The standard Genesys point cap. */
    public static final int GENESYS_POINT_CAP = 100;

    public Banlist {
        limits = Map.copyOf(limits);
        points = Map.copyOf(points);
    }

    public Banlist(String name, Map<Integer, Integer> limits) {
        this(name, limits, Map.of(), NO_POINTS);
    }

    /**
     * A Genesys list: Link and Pendulum monsters forbidden (so everything that only asks {@link #limit} leaves them
     * out), every other card up to 3 copies unless {@code limits} says fewer.
     */
    public static Banlist genesys(String name, Map<Integer, Integer> limits, Map<Integer, Integer> points,
                                  int pointCap, CardDatabase cards) {
        limits = new HashMap<>(limits);
        for (CardInfo card : cards.all()) {
            if (genesysForbidden(card)) {
                limits.put(card.code(), 0);
            }
        }
        return new Banlist(name, limits, points, Math.max(0, pointCap));
    }

    /** Whether Genesys leaves a card out: Link and Pendulum monsters. */
    public static boolean genesysForbidden(CardInfo card) {
        return card != null && (card.data().type() & (OcgConstants.TYPE_LINK | OcgConstants.TYPE_PENDULUM)) != 0;
    }

    /** Whether this is a Genesys list, with points and a point cap. */
    public boolean genesys() {
        return pointCap != NO_POINTS;
    }

    public int limit(int code) {
        return limits.getOrDefault(code, MAX_COPIES);
    }

    /** A card's Genesys points (0 for most cards, and for every card on a Forbidden & Limited List). */
    public int points(int code) {
        return points.getOrDefault(code, 0);
    }

    /** The same Genesys list with another point cap; a Forbidden & Limited List stays as it is. */
    public Banlist withPointCap(int cap) {
        return genesys() && cap != pointCap ? new Banlist(name, limits, points, Math.max(0, cap)) : this;
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

    /**
     * Reads {@code {"name": "...", "limits": {"<passcode>": <copies>, ...}}}, or a Genesys list
     * {@code {"name": "...", "points": {"<passcode>": <points>, ...}, "pointCap": 100}} (limits optional). Use
     * {@link #genesys} on a Genesys list to keep Link and Pendulum monsters out.
     */
    public static Banlist load(Reader reader) {
        JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
        Map<Integer, Integer> limits = codes(o, "limits");
        String name = o.has("name") ? o.get("name").getAsString() : "custom";
        if (!o.has("points")) {
            return new Banlist(name, limits);
        }
        int cap = o.has("pointCap") ? o.get("pointCap").getAsInt() : GENESYS_POINT_CAP;
        return new Banlist(name, limits, codes(o, "points"), Math.max(0, cap));
    }

    /** {@code {"<passcode>": <number>}} under {@code key}, empty if there is none. */
    static Map<Integer, Integer> codes(JsonObject o, String key) {
        Map<Integer, Integer> codes = new HashMap<>();
        if (o.has(key)) {
            o.getAsJsonObject(key).entrySet()
                    .forEach(e -> codes.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt()));
        }
        return codes;
    }
}
