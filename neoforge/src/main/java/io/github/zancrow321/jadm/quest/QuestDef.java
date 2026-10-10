package io.github.zancrow321.jadm.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.item.CardItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One quest of {@code config/jadm/quests.json}, as Gson reads it. Filters left out don't restrict anything.
 */
public final class QuestDef {
    public static final String DAILY = "daily";
    public static final String WEEKLY = "weekly";
    /** What a quest counts. */
    public static final Set<String> TYPES = Set.of("duel_win", "duel_play", "summon", "damage", "activate",
            "open_packs", "trade", "tournament");
    static final Set<String> DUEL_TYPES = Set.of("duel_win", "duel_play", "summon", "damage", "activate");
    static final Set<String> AGAINST = Set.of("any", "players", "npcs", "bots", "computer");
    static final Set<String> SUMMONS = Set.of("any", "normal", "flip", "special", "fusion", "ritual", "synchro",
            "xyz", "pendulum", "link");
    static final Set<String> CARD_TYPES = Set.of("any", "monster", "spell", "trap");

    public String id;
    public String period = DAILY;
    public String type;
    public int goal = 1;
    /** How likely the quest is drawn compared to the others of its period; 0 never draws it. */
    public int weight = 10;
    /** A text, or one per language: {@code {"en_us": "...", "de_de": "..."}}; left out, the window writes one. */
    public JsonElement title;
    /** In a progression world, the quest is only drawn once this product (e.g. "TDGS") is out. */
    public String unlockedBy;

    // Filters for the duel types.
    public String against = "any";
    public Boolean ranked;
    public Boolean tournament;
    public Boolean won;
    /** The deck must hold at least {@link #deckCards} monsters of this type, e.g. "dragon" or "Drache". */
    public String race;
    /** The deck must hold at least {@link #deckCards} monsters of this attribute, e.g. "light". */
    public String attribute;
    /** The deck must hold at least {@link #deckCards} cards with this in their name, e.g. "Blue-Eyes". */
    public String name;
    /** The deck must hold this card (a passcode). */
    public Integer card;
    public Integer deckCards;
    public int minLifePoints;
    public int maxTurns;

    // Per type.
    public String summon = "any";
    public String cardType = "any";
    /** tournament: the place the player must reach at least; 0 counts taking part. */
    public int maxPlace;

    public Reward reward = new Reward();

    /** Resolved from {@link #race} and {@link #attribute} by {@link #check}. */
    transient long raceBits;
    transient int attributeBits;

    public static final class Reward {
        public int points;
        public int packs;
        /** The booster set the packs are of (e.g. "MRD"); left out, each is a random set that is out. */
        public String packSet;
        public int emeralds;
        public int xp;
        /** Other items as "minecraft:diamond 2". */
        public List<String> items = List.of();
    }

    public boolean weekly() {
        return WEEKLY.equals(period);
    }

    boolean duel() {
        return DUEL_TYPES.contains(type);
    }

    /** How many cards of the deck filter a deck needs, if the quest has one. */
    int deckCards() {
        if (deckCards != null) {
            return Math.max(1, deckCards);
        }
        if (card != null) {
            return 1;
        }
        if (name != null) {
            return 2;
        }
        return race != null ? 6 : 10;
    }

    boolean hasDeckFilter() {
        return race != null || attribute != null || name != null || card != null;
    }

    /** Fills in defaults and resolves names. @return what is wrong with the quest, empty if nothing */
    List<String> check() {
        List<String> problems = new ArrayList<>();
        if (id == null || id.isBlank()) {
            problems.add("a quest has no id");
            return problems;
        }
        period = period == null ? DAILY : period.toLowerCase(Locale.ROOT);
        if (!period.equals(DAILY) && !period.equals(WEEKLY)) {
            problems.add(id + ": period must be daily or weekly");
        }
        if (type == null || !TYPES.contains(type)) {
            problems.add(id + ": unknown type " + type + " (one of " + String.join(", ", TYPES) + ")");
        }
        goal = Math.max(1, goal);
        weight = Math.max(0, weight);
        against = against == null ? "any" : against.toLowerCase(Locale.ROOT);
        if (!AGAINST.contains(against)) {
            problems.add(id + ": against must be one of " + String.join(", ", AGAINST));
        }
        summon = summon == null ? "any" : summon.toLowerCase(Locale.ROOT);
        if (!SUMMONS.contains(summon)) {
            problems.add(id + ": summon must be one of " + String.join(", ", SUMMONS));
        }
        cardType = cardType == null ? "any" : cardType.toLowerCase(Locale.ROOT);
        if (!CARD_TYPES.contains(cardType)) {
            problems.add(id + ": cardType must be one of " + String.join(", ", CARD_TYPES));
        }
        if (race != null) {
            raceBits = bit(race, 1020, CardItem.RACES_DE);
            if (raceBits == 0) {
                problems.add(id + ": unknown monster type " + race);
            }
        }
        if (attribute != null) {
            attributeBits = (int) bit(attribute, 1010, CardItem.ATTRIBUTES_DE);
            if (attributeBits == 0) {
                problems.add(id + ": unknown attribute " + attribute);
            }
        }
        if (reward == null) {
            reward = new Reward();
        }
        if (reward.items == null) {
            reward.items = List.of();
        }
        return problems;
    }

    /** The bit a monster type or attribute name stands for, in English (as the game writes it) or German. */
    private static long bit(String name, int firstString, String[] german) {
        String wanted = name.strip().toLowerCase(Locale.ROOT).replace('_', ' ');
        for (int bit = 0; bit < 32; bit++) {
            String english = JadmData.text().system(firstString + bit);
            if (english != null && english.toLowerCase(Locale.ROOT).equals(wanted)
                    || bit < german.length && german[bit].toLowerCase(Locale.ROOT).equals(wanted)) {
                return 1L << bit;
            }
        }
        return 0;
    }

    /** The title per language ("" for one text for all). */
    Map<String, String> titles() {
        Map<String, String> titles = new LinkedHashMap<>();
        if (title == null || title.isJsonNull()) {
            return titles;
        }
        if (title.isJsonPrimitive()) {
            titles.put("", title.getAsString());
        } else if (title.isJsonObject()) {
            JsonObject object = title.getAsJsonObject();
            object.entrySet().forEach(e -> {
                if (e.getValue().isJsonPrimitive()) {
                    titles.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsString());
                }
            });
        }
        return titles;
    }

    /** The filters the quest window spells out, with names resolved to numbers where the client looks them up. */
    Map<String, String> filters() {
        Map<String, String> f = new LinkedHashMap<>();
        if (duel()) {
            if (!against.equals("any")) {
                f.put("against", against);
            }
            if (Boolean.TRUE.equals(ranked)) {
                f.put("ranked", "true");
            }
            if (tournament != null) {
                f.put("tournament", tournament.toString());
            }
            if (Boolean.TRUE.equals(won) && !type.equals("duel_win")) {
                f.put("won", "true");
            }
            if (raceBits != 0) {
                f.put("race", String.valueOf(Long.numberOfTrailingZeros(raceBits)));
            }
            if (attributeBits != 0) {
                f.put("attribute", String.valueOf(Integer.numberOfTrailingZeros(attributeBits)));
            }
            if (name != null) {
                f.put("name", name);
            }
            if (card != null) {
                f.put("card", card.toString());
            }
            if (hasDeckFilter()) {
                f.put("deckCards", String.valueOf(deckCards()));
            }
            if (minLifePoints > 0) {
                f.put("minLifePoints", String.valueOf(minLifePoints));
            }
            if (maxTurns > 0) {
                f.put("maxTurns", String.valueOf(maxTurns));
            }
        }
        if (type.equals("summon")) {
            f.put("summon", summon);
        }
        if (type.equals("activate")) {
            f.put("cardType", cardType);
        }
        if (type.equals("tournament") && maxPlace > 0) {
            f.put("maxPlace", String.valueOf(maxPlace));
        }
        return f;
    }
}
