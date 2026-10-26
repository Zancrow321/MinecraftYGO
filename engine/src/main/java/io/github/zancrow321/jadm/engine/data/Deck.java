package io.github.zancrow321.jadm.engine.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A deck as lists of passcodes, read from EDOPro's {@code .ydk} format.
 *
 * @param foils the rarities of the deck's foil copies, by passcode (the other copies are commons), so the duel shows
 *              each copy as the player pulled it
 * @param boss  the monster its owner marked as the deck's boss (it gets the ace shot in a duel), or 0 for none
 */
public record Deck(String name, List<Integer> main, List<Integer> extra, List<Integer> side,
                   Map<Integer, List<BoosterSets.Rarity>> foils, int boss) {
    public static final String BUNDLED_ROOT = "/jadm/decks/";

    public Deck {
        main = List.copyOf(main);
        extra = List.copyOf(extra);
        side = List.copyOf(side);
        Map<Integer, List<BoosterSets.Rarity>> copies = new HashMap<>();
        foils.forEach((code, rarities) -> copies.put(code, List.copyOf(rarities)));
        foils = Map.copyOf(copies);
    }

    /** A deck of commons without a boss monster. */
    public Deck(String name, List<Integer> main, List<Integer> extra, List<Integer> side) {
        this(name, main, extra, side, Map.of(), 0);
    }

    /** A deck without a boss monster. */
    public Deck(String name, List<Integer> main, List<Integer> extra, List<Integer> side,
                Map<Integer, List<BoosterSets.Rarity>> foils) {
        this(name, main, extra, side, foils, 0);
    }

    /** A deck of commons. */
    public Deck(String name, List<Integer> main, List<Integer> extra, List<Integer> side, int boss) {
        this(name, main, extra, side, Map.of(), boss);
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
