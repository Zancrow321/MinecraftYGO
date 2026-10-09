package io.github.zancrow321.jadm.ranking;

import java.util.List;

/**
 * The ranks a rating falls into, from Bronze to Duel King. Their names and colors are fixed; the ratings they start
 * at come from {@code [ranking] tiers} in the server config.
 */
public final class Tiers {
    public static final List<String> NAMES = List.of("Bronze", "Silver", "Gold", "Platinum", "Diamond", "Duel King");
    public static final int[] COLORS = {0xCD8A4A, 0xC8D0DA, 0xFFD54F, 0x5FE0C8, 0x6FB7FF, 0xFF5A8A};
    /** Where each rank but Bronze starts, when the config has no usable list. */
    public static final List<Integer> DEFAULT_STARTS = List.of(1100, 1200, 1350, 1500, 1700);

    private Tiers() {
    }

    /**
     * @param starts the rating each rank above Bronze starts at, lowest first
     * @return the rank {@code rating} falls into, 0 for Bronze
     */
    public static int of(int rating, List<Integer> starts) {
        int tier = 0;
        for (int i = 0; i < starts.size() && i + 1 < NAMES.size(); i++) {
            if (rating >= starts.get(i)) {
                tier = i + 1;
            }
        }
        return tier;
    }

    public static String name(int tier) {
        return NAMES.get(Math.max(0, Math.min(tier, NAMES.size() - 1)));
    }

    public static int color(int tier) {
        return COLORS[Math.max(0, Math.min(tier, COLORS.length - 1))];
    }
}
