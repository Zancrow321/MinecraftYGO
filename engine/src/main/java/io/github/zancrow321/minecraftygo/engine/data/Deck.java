package io.github.zancrow321.minecraftygo.engine.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A deck as lists of passcodes, read from EDOPro's {@code .ydk} format.
 */
public record Deck(String name, List<Integer> main, List<Integer> extra, List<Integer> side) {
    public static final String BUNDLED_ROOT = "/minecraftygo/decks/";

    public Deck {
        main = List.copyOf(main);
        extra = List.copyOf(extra);
        side = List.copyOf(side);
    }

    /** Loads a deck bundled under {@value #BUNDLED_ROOT}, e.g. {@code starter_yugi}. */
    public static Deck bundled(String id) {
        try (InputStream in = Deck.class.getResourceAsStream(BUNDLED_ROOT + id + ".ydk")) {
            if (in == null) {
                throw new IllegalArgumentException("No bundled deck " + id);
            }
            return parseYdk(id, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Deck parseYdk(String name, String text) {
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> side = new ArrayList<>();
        List<Integer> current = main;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            switch (line) {
                case "#main" -> current = main;
                case "#extra" -> current = extra;
                case "!side" -> current = side;
                default -> {
                    if (!line.startsWith("#") && !line.startsWith("!")) {
                        current.add(Integer.parseInt(line));
                    }
                }
            }
        }
        return new Deck(name, main, extra, side);
    }
}
