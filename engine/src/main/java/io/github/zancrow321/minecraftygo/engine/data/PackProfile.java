package io.github.zancrow321.minecraftygo.engine.data;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How a booster product's packs are made: how many cards, and what the slots above common can turn out as. The
 * rates are those of the TCG as far as they are known, rounded; a set without a rarity falls back to the next lower.
 *
 * @param name  a short name for the format, e.g. {@code core} or {@code tournament}
 * @param size  cards per pack
 * @param slots the slots that are rare or better; the others are commons
 */
public record PackProfile(String name, int size, List<Slot> slots) {
    /**
     * The odds of one slot; what is left over is a plain rare.
     */
    public record Slot(double secret, double ultra, double superRare) {
        BoosterSets.Rarity roll(java.util.random.RandomGenerator random) {
            double r = random.nextDouble();
            if (r < secret) {
                return BoosterSets.Rarity.SECRET;
            }
            if (r < secret + ultra) {
                return BoosterSets.Rarity.ULTRA;
            }
            return r < secret + ultra + superRare ? BoosterSets.Rarity.SUPER : BoosterSets.Rarity.RARE;
        }
    }

    /** The early sets' one rare slot: secret 1 in 24, ultra 1 in 12, super 1 in 6. */
    public static final Slot CLASSIC = new Slot(1 / 24.0, 1 / 12.0, 1 / 6.0);
    /** Always a plain rare. */
    public static final Slot RARE = new Slot(0, 0, 0);
    /** The modern foil slot: secret 1 in 12, ultra 1 in 6, otherwise super. */
    public static final Slot FOIL = new Slot(1 / 12.0, 1 / 6.0, 1 - 1 / 12.0 - 1 / 6.0);
    /** All-foil sets: mostly ultra, secret 1 in 4. */
    public static final Slot PREMIUM = new Slot(1 / 4.0, 3 / 4.0, 0);

    /** Nine cards, eight of them common: the core boosters until mid-2014, and the modeled pool's sets. */
    public static final PackProfile CLASSIC_CORE = new PackProfile("core", 9, List.of(CLASSIC));
    /** Nine cards from Duelist Alliance on: seven commons, a rare and a super, ultra or secret. */
    public static final PackProfile MODERN_CORE = new PackProfile("core", 9, List.of(RARE, FOIL));
    /** The day the core boosters went to a rare and a foil per pack (Duelist Alliance). */
    public static final LocalDate MODERN_FROM = LocalDate.of(2014, 8, 14);

    private static final Pattern TOURNAMENT = Pattern.compile(
            "tournament pack|turbo pack|champion pack|astral pack|star pack|participation");
    private static final Pattern ALL_FOIL = Pattern.compile(
            "dragons of legend|premium gold|maximum gold|legendary collection|battles of legend|gold series"
                    + "|platinum|25th anniversary|rarity collection|quarter century|ghosts from the past"
                    + "|spirit warriors|secret slayers|wing raiders|fusion enforcers|destiny soldiers"
                    + "|legendary duelists: season");
    private static final Pattern BATTLE = Pattern.compile("battle pack|speed duel");
    private static final Pattern MINI = Pattern.compile(
            "duelist pack|hidden arsenal|duel terminal|world superstars|millennium pack"
                    + "|dimension of chaos: special|high-speed riders|mega pack|ancient guardians"
                    + "|dark saviors|soul fusion|brothers of legend|valiant smashers|genesis impact|legendary duelists");

    /** The pack format of a product. */
    public static PackProfile of(Products.Product product) {
        String name = product.name().toLowerCase(Locale.ROOT);
        if (TOURNAMENT.matcher(name).find()) {
            return new PackProfile("tournament", 3, List.of(CLASSIC));
        }
        if (ALL_FOIL.matcher(name).find()) {
            return new PackProfile("premium", 5, List.of(PREMIUM, PREMIUM, PREMIUM, PREMIUM, PREMIUM));
        }
        if (BATTLE.matcher(name).find()) {
            return new PackProfile("battle", 5, List.of(CLASSIC));
        }
        boolean modern = !product.date().isBefore(MODERN_FROM);
        // Smaller sets (Duelist Packs, Hidden Arsenal, Duel Terminal, Legendary Duelists...) come in packs of five.
        if (product.kind() != Products.Kind.BOOSTER || MINI.matcher(name).find()) {
            return new PackProfile("mini", 5, List.of(modern ? FOIL : CLASSIC));
        }
        return modern ? MODERN_CORE : CLASSIC_CORE;
    }
}
