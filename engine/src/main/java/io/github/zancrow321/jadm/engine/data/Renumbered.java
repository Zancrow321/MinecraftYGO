package io.github.zancrow321.jadm.engine.data;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Cards whose passcode changed in a card data refresh: a set that is new in the TCG first comes with temporary
 * passcodes (Beyond the Brave's 101402xxx) and gets its real ones later. {@code tools/carddata/build_carddata.py}
 * writes {@code renumbered.json}, old passcode to current one, and cards saved under an old passcode (card items,
 * binders, deck boxes) load as the current card.
 */
public final class Renumbered {
    public static final String RESOURCE = "/jadm/renumbered.json";
    private static volatile Map<Integer, Integer> bundled;

    private Renumbered() {
    }

    /** @return the passcode a card has now: {@code code} itself unless it was renumbered */
    public static int current(int code) {
        return bundled().getOrDefault(code, code);
    }

    /** The bundled table, old passcode to current one. */
    public static Map<Integer, Integer> bundled() {
        Map<Integer, Integer> map = bundled;
        if (map == null) {
            map = load();
            bundled = map;
        }
        return map;
    }

    private static Map<Integer, Integer> load() {
        try (InputStream in = Renumbered.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Map.of();
            }
            Map<Integer, Integer> map = new HashMap<>();
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()
                    .forEach(e -> {
                        if (!e.getKey().startsWith("_")) {
                            map.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt());
                        }
                    });
            return Collections.unmodifiableMap(map);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
