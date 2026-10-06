package io.github.zancrow321.minecraftygo.client.collection;

import java.util.ArrayList;
import java.util.List;

/**
 * The YDK deck list format that YGOPro, EDOPro and most deck builders read and write: a "#main" and a "#extra"
 * section of card passcodes, one per line, and a "!side" section.
 */
final class Ydk {
    record Deck(List<Integer> main, List<Integer> extra, List<Integer> side) {
        int size() {
            return main.size() + extra.size() + side.size();
        }
    }

    private Ydk() {
    }

    static String write(List<Integer> main, List<Integer> extra) {
        StringBuilder out = new StringBuilder("#created by MinecraftYGO\n#main\n");
        main.forEach(code -> out.append(code).append('\n'));
        out.append("#extra\n");
        extra.forEach(code -> out.append(code).append('\n'));
        return out.append("!side\n").toString();
    }

    /** @return the deck in {@code text}, or {@code null} if it has no "#main" section or no cards */
    static Deck read(String text) {
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> side = new ArrayList<>();
        List<Integer> section = null;
        boolean sawMain = false;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.equalsIgnoreCase("#main")) {
                section = main;
                sawMain = true;
            } else if (line.equalsIgnoreCase("#extra")) {
                section = extra;
            } else if (line.equalsIgnoreCase("!side")) {
                section = side;
            } else if (section != null && !line.isEmpty() && !line.startsWith("#")) {
                try {
                    section.add(Integer.parseInt(line));
                } catch (NumberFormatException ignored) {
                    // Not a passcode; deck builders sometimes leave notes.
                }
            }
        }
        Deck deck = new Deck(main, extra, side);
        return sawMain && deck.size() > 0 ? deck : null;
    }
}
