package io.github.zancrow321.minecraftygo.tools.pool;

import java.util.Locale;

/** Compares YGOMCModels folder names (PascalCase) with card names: lower case, letters and digits only. */
public final class NameNormalizer {
	private NameNormalizer() {}

	public static String normalize(String name) {
		StringBuilder out = new StringBuilder(name.length());
		for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				out.append(c);
			}
		}
		return out.toString();
	}

	public static int levenshtein(String a, String b) {
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			current[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[b.length()];
	}
}
