package io.github.zancrow321.minecraftygo.engine.deck;

import java.util.ArrayList;
import java.util.List;

/** Reads and writes the YGOPro/EDOPro {@code .ydk} deck format ({@code #main}, {@code #extra}, {@code !side}). */
public final class YdkCodec {
	private YdkCodec() {}

	public static Deck parse(String text) {
		List<Integer> main = new ArrayList<>();
		List<Integer> extra = new ArrayList<>();
		List<Integer> side = new ArrayList<>();
		List<Integer> current = main;
		for (String raw : text.split("\\R")) {
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("#")) {
				if (line.equalsIgnoreCase("#main")) {
					current = main;
				} else if (line.equalsIgnoreCase("#extra")) {
					current = extra;
				}
				continue;
			}
			if (line.startsWith("!")) {
				if (line.equalsIgnoreCase("!side")) {
					current = side;
				}
				continue;
			}
			try {
				current.add(Integer.parseInt(line));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid card code in .ydk: " + line, e);
			}
		}
		return new Deck(main, extra, side);
	}

	public static String format(Deck deck, String creator) {
		StringBuilder out = new StringBuilder("#created by ").append(creator).append('\n').append("#main\n");
		deck.main().forEach(code -> out.append(code).append('\n'));
		out.append("#extra\n");
		deck.extra().forEach(code -> out.append(code).append('\n'));
		out.append("!side\n");
		deck.side().forEach(code -> out.append(code).append('\n'));
		return out.toString();
	}
}
