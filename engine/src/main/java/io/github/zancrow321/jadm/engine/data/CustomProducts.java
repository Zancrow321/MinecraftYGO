package io.github.zancrow321.jadm.engine.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Booster packs, structure decks and tins a server defines itself, one JSON file each in a datapack
 * ({@code data/<namespace>/jadm/products/<name>.json}). The format is in {@code docs/custom-products.md}:
 *
 * <pre>{@code
 * {
 *   "type": "booster",              // booster, deck or tin
 *   "name": "Legends of the Server",
 *   "code": "LOTS",                 // set code, optional
 *   "date": "2002-06-01",           // when it comes out in a progression world, optional
 *   "pack": "modern",               // booster pack format, optional
 *   "cards": [
 *     {"card": "Blue-Eyes White Dragon", "rarity": ["Ultra Rare", "Starlight Rare"]},
 *     {"card": 46986414, "rarity": "Secret Rare"},
 *     "Mystical Space Typhoon"
 *   ]
 * }
 * }</pre>
 *
 * The product's id is the file's path, e.g. {@code legends} or {@code event_tin} for {@code event/tin.json}; a file
 * with the id of a real product replaces it.
 */
public final class CustomProducts {
    private CustomProducts() {
    }

    /** The whole-pack formats a booster can name. */
    public static final Map<String, PackProfile> PACK_FORMATS = Map.ofEntries(
            Map.entry("classic", PackProfile.CLASSIC_CORE),
            Map.entry("modern", PackProfile.MODERN_CORE),
            Map.entry("core", PackProfile.MODERN_CORE),
            Map.entry("rare_only", PackProfile.RARE_ONLY),
            Map.entry("super_secret", PackProfile.SUPER_SECRET),
            Map.entry("mini", new PackProfile("mini", 5, List.of(PackProfile.FOIL))),
            Map.entry("mini_classic", new PackProfile("mini", 5, List.of(PackProfile.CLASSIC))),
            Map.entry("premium", new PackProfile("premium", 5, List.of(PackProfile.PREMIUM, PackProfile.PREMIUM,
                    PackProfile.PREMIUM, PackProfile.PREMIUM, PackProfile.PREMIUM))),
            Map.entry("tournament", new PackProfile("tournament", 3, List.of(PackProfile.CLASSIC))),
            Map.entry("battle", new PackProfile("battle", 5, List.of(PackProfile.CLASSIC))));

    /** The slots a pack format can list. */
    public static final Map<String, PackProfile.Slot> SLOTS = Map.of(
            "classic", PackProfile.CLASSIC,
            "rare", PackProfile.RARE,
            "foil", PackProfile.FOIL,
            "premium", PackProfile.PREMIUM,
            "secret", PackProfile.SECRET,
            "ultra", PackProfile.ULTRA,
            "super", PackProfile.SUPER);

    /** Most cards a pack may have. */
    public static final int MAX_PACK_SIZE = 30;

    /**
     * Finds cards by passcode or name: a name in any case, an alternate artwork's name finding the original.
     */
    public static final class Names {
        private final CardDatabase cards;
        private final Map<String, Integer> byName = new HashMap<>();

        public Names(CardDatabase cards) {
            this.cards = cards;
            for (CardInfo card : cards.all()) {
                String key = key(card.name());
                int original = cards.original(card.code());
                Integer known = byName.get(key);
                // The original artwork wins over its alternates, and of two different cards the lower passcode.
                if (known == null || card.code() == original && (known != cards.original(known) || card.code() < known)) {
                    byName.put(key, card.code());
                }
            }
        }

        private static String key(String name) {
            return name.strip().toLowerCase(Locale.ROOT).replace('’', '\'');
        }

        /** @return the card's passcode, or {@code null} if no card has that passcode or name */
        public Integer find(JsonElement e) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                int code = e.getAsInt();
                return cards.card(code) != null ? code : null;
            }
            if (!e.isJsonPrimitive()) {
                return null;
            }
            String text = e.getAsString().strip();
            if (text.matches("\\d{1,9}")) {
                int code = Integer.parseInt(text);
                if (cards.card(code) != null) {
                    return code;
                }
            }
            return byName.get(key(text));
        }

        public String name(int code) {
            return cards.name(code);
        }
    }

    /**
     * Reads one product.
     *
     * @param id       the product's id
     * @param source   where it came from, for messages, e.g. {@code duelmc:legends}
     * @param problems gets a line for everything wrong with it; what can be skipped is skipped
     * @return the product, or {@code null} if it can't be used at all
     */
    public static Products.Product parse(String id, String source, JsonObject o, Names names,
                                         Consumer<String> problems) {
        Consumer<String> problem = text -> problems.accept(source + ": " + text);
        String typeName = string(o, "type", string(o, "kind", "")).toLowerCase(Locale.ROOT);
        Products.Kind kind = switch (typeName) {
            case "booster", "pack", "booster_pack" -> Products.Kind.BOOSTER;
            case "deck", "structure_deck", "starter_deck" -> Products.Kind.DECK;
            case "tin" -> Products.Kind.TIN;
            default -> null;
        };
        if (kind == null) {
            problem.accept("\"type\" must be booster, deck or tin, not \"" + typeName + "\"");
            return null;
        }
        String name = string(o, "name", "").strip();
        if (name.isEmpty()) {
            problem.accept("has no \"name\"");
            return null;
        }
        String code = string(o, "code", "").strip();
        if (code.isEmpty()) {
            code = id.toUpperCase(Locale.ROOT);
        }
        LocalDate date = null;
        String dateText = string(o, "date", "").strip();
        if (!dateText.isEmpty()) {
            try {
                date = LocalDate.parse(dateText);
            } catch (DateTimeParseException e) {
                problem.accept("\"date\" must look like 2002-06-01, not \"" + dateText + "\"; it comes out at the start");
            }
        }
        String defaultRarity = kind == Products.Kind.TIN ? "Ultra Rare" : "Common";
        // Each card once, in the order listed; a card listed again adds its rarities (or, in a deck, its copies).
        Map<Integer, List<String>> rarities = new LinkedHashMap<>();
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        JsonArray cards = o.has("cards") && o.get("cards").isJsonArray() ? o.getAsJsonArray("cards") : new JsonArray();
        for (JsonElement entry : cards) {
            JsonElement cardRef = entry;
            JsonElement rarity = null;
            int count = 1;
            if (entry.isJsonObject()) {
                JsonObject c = entry.getAsJsonObject();
                cardRef = c.has("card") ? c.get("card") : c.has("code") ? c.get("code") : c.get("name");
                rarity = c.get("rarity");
                if (c.has("count")) {
                    count = Math.max(1, Math.min(60, c.get("count").getAsInt()));
                }
            }
            Integer found = cardRef == null ? null : names.find(cardRef);
            if (found == null) {
                problem.accept("no card " + (cardRef == null ? "(none given)" : cardRef) + "; left out");
                continue;
            }
            List<String> list = rarities.computeIfAbsent(found, k -> new ArrayList<>());
            for (String r : rarities(rarity, defaultRarity, problem)) {
                if (!list.contains(r)) {
                    list.add(r);
                }
            }
            counts.merge(found, count, Integer::sum);
        }
        List<Products.Printing> printings = new ArrayList<>();
        rarities.forEach((card, list) -> {
            // The plainest rarity first, as in the real products.
            list.sort(java.util.Comparator.comparing(BoosterSets.Rarity::ofPrinted));
            printings.add(new Products.Printing(card, list.get(0), list.subList(1, list.size())));
        });
        PackProfile pack = null;
        if (kind == Products.Kind.BOOSTER && o.has("pack")) {
            pack = pack(o.get("pack"), problem);
        }
        List<String> tinPacks = new ArrayList<>();
        if (kind == Products.Kind.TIN && o.has("packs") && o.get("packs").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("packs")) {
                String product = e.isJsonObject() ? string(e.getAsJsonObject(), "product", "")
                        : e.isJsonPrimitive() ? e.getAsString() : "";
                int n = e.isJsonObject() && e.getAsJsonObject().has("count")
                        ? Math.max(1, Math.min(24, e.getAsJsonObject().get("count").getAsInt())) : 1;
                if (product.isBlank()) {
                    problem.accept("a pack in \"packs\" names no product");
                    continue;
                }
                for (int i = 0; i < n; i++) {
                    tinPacks.add(product.strip());
                }
            }
        }
        if (printings.isEmpty() && !(kind == Products.Kind.TIN && !tinPacks.isEmpty())) {
            problem.accept("has no cards");
            return null;
        }
        int promos = kind == Products.Kind.TIN ? Math.max(0, Math.min(10, integer(o, "promos", 1))) : 0;
        boolean unlocks = bool(o, "unlocks_cards", true);
        boolean starter = kind == Products.Kind.DECK && bool(o, "starter", true);
        int price = o.has("price") ? Math.max(0, Math.min(64, integer(o, "price", 0))) : -1;
        Products.Custom custom = new Products.Custom(source, date != null, string(o, "description", "").strip(),
                string(o, "image", "").strip(), pack, kind == Products.Kind.DECK ? counts : Map.of(), tinPacks,
                promos, unlocks, starter, price);
        List<Integer> newCards = unlocks ? new ArrayList<>(rarities.keySet()) : List.of();
        return new Products.Product(id, code, name, date != null ? date : LocalDate.of(2000, 1, 1), kind, printings,
                newCards, custom);
    }

    /** Reads every product of a JSON array as {@link #toJson} writes it (on a client, from the server). */
    public static List<Products.Product> parseAll(String json, CardDatabase cards, Consumer<String> problems) {
        List<Products.Product> out = new ArrayList<>();
        if (json.isBlank()) {
            return out;
        }
        Names names = new Names(cards);
        for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            String id = o.get("id").getAsString();
            Products.Product p = parse(id, string(o, "source", id), o, names, problems);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    /** Writes products the way {@link #parseAll} reads them. */
    public static String toJson(List<Products.Product> products) {
        JsonArray array = new JsonArray();
        for (Products.Product p : products) {
            array.add(toJson(p));
        }
        return array.toString();
    }

    private static JsonObject toJson(Products.Product p) {
        Products.Custom c = p.custom();
        JsonObject o = new JsonObject();
        o.addProperty("id", p.id());
        o.addProperty("source", c.source());
        o.addProperty("type", switch (p.kind()) {
            case DECK -> "deck";
            case TIN -> "tin";
            default -> "booster";
        });
        o.addProperty("name", p.name());
        o.addProperty("code", p.code());
        if (c.dated()) {
            o.addProperty("date", p.date().toString());
        }
        if (!c.description().isEmpty()) {
            o.addProperty("description", c.description());
        }
        if (!c.image().isEmpty()) {
            o.addProperty("image", c.image());
        }
        if (c.pack() != null) {
            JsonObject pack = new JsonObject();
            pack.addProperty("name", c.pack().name());
            pack.addProperty("size", c.pack().size());
            JsonArray slots = new JsonArray();
            for (PackProfile.Slot s : c.pack().slots()) {
                JsonObject slot = new JsonObject();
                slot.addProperty("secret", s.secret());
                slot.addProperty("ultra", s.ultra());
                slot.addProperty("super", s.superRare());
                slots.add(slot);
            }
            pack.add("slots", slots);
            o.add("pack", pack);
        }
        JsonArray cards = new JsonArray();
        for (Products.Printing printing : p.cards()) {
            JsonObject card = new JsonObject();
            card.addProperty("card", printing.code());
            JsonArray rarities = new JsonArray();
            rarities.add(printing.rarity());
            printing.better().forEach(rarities::add);
            card.add("rarity", rarities);
            Integer count = c.counts().get(printing.code());
            if (count != null && count > 1) {
                card.addProperty("count", count);
            }
            cards.add(card);
        }
        o.add("cards", cards);
        if (p.kind() == Products.Kind.TIN) {
            JsonArray packs = new JsonArray();
            c.tinPacks().forEach(packs::add);
            o.add("packs", packs);
            o.addProperty("promos", c.promos());
        }
        o.addProperty("unlocks_cards", c.unlocksCards());
        if (p.kind() == Products.Kind.DECK) {
            o.addProperty("starter", c.starter());
        }
        if (c.price() >= 0) {
            o.addProperty("price", c.price());
        }
        return o;
    }

    /** A rarity as written: one, or a list of the ones the card comes in. */
    private static List<String> rarities(JsonElement e, String fallback, Consumer<String> problem) {
        List<String> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) {
            out.add(fallback);
        } else if (e.isJsonArray()) {
            for (JsonElement r : e.getAsJsonArray()) {
                out.add(rarity(r.getAsString(), fallback, problem));
            }
        } else {
            out.add(rarity(e.getAsString(), fallback, problem));
        }
        if (out.isEmpty()) {
            out.add(fallback);
        }
        return out;
    }

    /** A rarity's printed name from its name, short name ({@code UR}) or id ({@code ultra}). */
    public static String rarity(String text, String fallback, Consumer<String> problem) {
        String t = text.strip();
        for (BoosterSets.Rarity r : BoosterSets.Rarity.values()) {
            if (r.displayName().equalsIgnoreCase(t) || r.tag().equalsIgnoreCase(t) || r.id().equalsIgnoreCase(t)
                    || r.id().replace("_", " ").equalsIgnoreCase(t)) {
                return r.displayName();
            }
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("rare") || lower.contains("common") || lower.contains("short print")) {
            return t;
        }
        problem.accept("no rarity \"" + text + "\"; using " + fallback);
        return fallback;
    }

    private static PackProfile pack(JsonElement e, Consumer<String> problem) {
        if (e.isJsonPrimitive()) {
            String name = e.getAsString().strip().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            PackProfile format = PACK_FORMATS.get(name);
            if (format == null) {
                problem.accept("no pack format \"" + e.getAsString() + "\"; using the usual one for its cards. Formats: "
                        + String.join(", ", new java.util.TreeSet<>(PACK_FORMATS.keySet())));
            }
            return format;
        }
        if (!e.isJsonObject()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        List<PackProfile.Slot> slots = new ArrayList<>();
        if (o.has("slots") && o.get("slots").isJsonArray()) {
            for (JsonElement s : o.getAsJsonArray("slots")) {
                PackProfile.Slot slot = slot(s, problem);
                if (slot != null) {
                    slots.add(slot);
                }
            }
        }
        int size = integer(o, "size", 9);
        if (size < 1 || size > MAX_PACK_SIZE) {
            problem.accept("\"size\" must be 1 to " + MAX_PACK_SIZE + "; using 9");
            size = 9;
        }
        if (slots.size() > size) {
            problem.accept("more slots than cards in a pack; the pack has " + slots.size() + " cards");
            size = slots.size();
        }
        return new PackProfile(string(o, "name", "custom"), size, slots);
    }

    private static PackProfile.Slot slot(JsonElement e, Consumer<String> problem) {
        if (e.isJsonPrimitive()) {
            PackProfile.Slot slot = SLOTS.get(e.getAsString().strip().toLowerCase(Locale.ROOT));
            if (slot == null) {
                problem.accept("no slot \"" + e.getAsString() + "\"; slots: "
                        + String.join(", ", new java.util.TreeSet<>(SLOTS.keySet())) + " or odds like "
                        + "{\"secret\": 0.1, \"ultra\": 0.2, \"super\": 0.3}");
            }
            return slot;
        }
        if (!e.isJsonObject()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        double secret = odds(o, "secret");
        double ultra = odds(o, "ultra");
        double superRare = odds(o, "super");
        double total = secret + ultra + superRare;
        if (total > 1) {
            problem.accept("a slot's odds add up to more than 1; they are scaled down");
            secret /= total;
            ultra /= total;
            superRare /= total;
        }
        return new PackProfile.Slot(secret, ultra, superRare);
    }

    private static double odds(JsonObject o, String key) {
        return o.has(key) ? Math.max(0, o.get(key).getAsDouble()) : 0;
    }

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e instanceof JsonPrimitive p ? p.getAsString() : fallback;
    }

    private static int integer(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        try {
            return e instanceof JsonPrimitive p ? p.getAsInt() : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        return e instanceof JsonPrimitive p ? p.getAsBoolean() : fallback;
    }
}
