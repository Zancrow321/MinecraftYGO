package io.github.zancrow321.jadm.engine.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A deck as lists of passcodes, read from EDOPro's {@code .ydk} format.
 *
 * @param boss the monster its owner marked as the deck's boss (it gets the ace shot in a duel), or 0 for none
 */
public record Deck(String name, List<Integer> main, List<Integer> extra, List<Integer> side, int boss) {
    public static final String BUNDLED_ROOT = "/jadm/decks/";

    public Deck {
        main = List.copyOf(main);
        extra = List.copyOf(extra);
        side = List.copyOf(side);
    }

    /** A deck without a boss monster. */
    public Deck(String name, List<Integer> main, List<Integer> extra, List<Integer> side) {
        this(name, main, extra, side, 0);
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
