package io.github.zancrow321.jadm.engine.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomProductsTest {
    private static final int BLUE_EYES = 89631139;
    private static final int DARK_MAGICIAN = 46986414;
    private static final int STARDUST = 44508094;
    private static final int MST = 5318639;

    private static CardDatabase cards;
    private static CardPool everything;
    private static Products bundled;
    private static CustomProducts.Names names;

    @BeforeAll
    static void load() {
        cards = CardDatabase.loadBundled();
        everything = CardPool.everything(cards, CardPool.loadBundled().models());
        bundled = Products.loadBundled();
        names = new CustomProducts.Names(cards);
    }

    private static Products.Product parse(String id, String json, List<String> problems) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        return CustomProducts.parse(id, "test:" + id, o, names, problems::add);
    }

    @Test
    void readsABoosterByNameAndPasscode() {
        List<String> problems = new ArrayList<>();
        Products.Product p = parse("legends", """
                {"type": "booster", "name": "Server Legends", "code": "SVL", "date": "2003-01-01",
                 "description": "Only on our server",
                 "cards": [
                   {"card": "blue-eyes white dragon", "rarity": ["Ultra Rare", "starlight"]},
                   {"card": 46986414, "rarity": "ScR"},
                   "Mystical Space Typhoon",
                   {"card": "No Such Card"},
                   {"card": "Stardust Dragon", "rarity": "Shiny"}
                 ]}""", problems);
        assertNotNull(p);
        assertEquals(Products.Kind.BOOSTER, p.kind());
        assertEquals("SVL", p.code());
        assertEquals(LocalDate.of(2003, 1, 1), p.date());
        assertTrue(p.custom().dated());
        assertEquals("Only on our server", p.custom().description());
        assertEquals(4, p.cards().size());
        assertEquals(new Products.Printing(BLUE_EYES, "Ultra Rare", List.of("Starlight Rare")), p.cards().get(0));
        assertEquals(new Products.Printing(DARK_MAGICIAN, "Secret Rare"), p.cards().get(1));
        assertEquals(new Products.Printing(MST, "Common"), p.cards().get(2));
        assertEquals(new Products.Printing(STARDUST, "Common"), p.cards().get(3));
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("No Such Card"));
        assertTrue(problems.get(1).contains("Shiny"));
    }

    @Test
    void rejectsWhatCantBeUsed() {
        List<String> problems = new ArrayList<>();
        assertNull(parse("a", "{\"type\": \"box\", \"name\": \"A\", \"cards\": [\"Blue-Eyes White Dragon\"]}",
                problems));
        assertNull(parse("b", "{\"type\": \"booster\", \"cards\": [\"Blue-Eyes White Dragon\"]}", problems));
        assertNull(parse("c", "{\"type\": \"booster\", \"name\": \"C\", \"cards\": []}", problems));
        assertEquals(3, problems.size());
    }

    @Test
    void packFormats() {
        List<String> problems = new ArrayList<>();
        Products.Product named = parse("p", """
                {"type": "booster", "name": "P", "pack": "premium", "cards": ["Blue-Eyes White Dragon"]}""",
                problems);
        assertEquals(5, PackProfile.of(named).size());
        Products.Product own = parse("q", """
                {"type": "booster", "name": "Q", "pack": {"size": 6, "slots": ["ultra", {"secret": 0.5}]},
                 "cards": ["Blue-Eyes White Dragon"]}""", problems);
        PackProfile profile = PackProfile.of(own);
        assertEquals(6, profile.size());
        assertEquals(List.of(PackProfile.ULTRA, new PackProfile.Slot(0.5, 0, 0)), profile.slots());
        Products.Product plain = parse("r", """
                {"type": "booster", "name": "R", "cards": ["Blue-Eyes White Dragon"]}""", problems);
        assertEquals(PackProfile.MODERN_CORE, PackProfile.of(plain), "no date: a modern core booster");
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void opensPacksOfItsCards() {
        List<String> problems = new ArrayList<>();
        Products.Product p = parse("opened", """
                {"type": "booster", "name": "Opened", "pack": {"size": 5, "slots": ["ultra"]},
                 "cards": ["Mystical Space Typhoon", "Dark Hole", "Raigeki", "Pot of Greed", "Monster Reborn",
                           {"card": "Blue-Eyes White Dragon", "rarity": "Ultra Rare"}]}""", problems);
        BoosterSets sets = BoosterSets.fromProducts(bundled.withCustom(List.of(p)), everything);
        BoosterSets.BoosterSet set = sets.get("opened");
        assertNotNull(set);
        Random random = new Random(1);
        for (int i = 0; i < 20; i++) {
            List<BoosterSets.Card> pack = set.open(random);
            assertEquals(5, pack.size());
            assertEquals(new BoosterSets.Card(BLUE_EYES, BoosterSets.Rarity.ULTRA), pack.get(4));
            assertTrue(pack.subList(0, 4).stream().allMatch(c -> c.rarity() == BoosterSets.Rarity.COMMON));
        }
    }

    @Test
    void decksKeepTheirCopies() {
        List<String> problems = new ArrayList<>();
        Products.Product p = parse("deck", """
                {"type": "deck", "name": "Server Deck",
                 "cards": [{"card": "Blue-Eyes White Dragon", "count": 3}, "Stardust Dragon",
                           {"card": "Mystical Space Typhoon", "count": 2}, {"card": "Mystical Space Typhoon"}]}""",
                problems);
        assertEquals(Map.of(BLUE_EYES, 3, STARDUST, 1, MST, 3), p.custom().counts());
        assertEquals(3, p.cards().size());
        assertTrue(p.custom().starter());
    }

    @Test
    void tinsNameTheirPacks() {
        List<String> problems = new ArrayList<>();
        Products.Product p = parse("tin", """
                {"type": "tin", "name": "Server Tin", "promos": 2,
                 "cards": ["Blue-Eyes White Dragon", "Dark Magician"],
                 "packs": ["LOB", {"product": "legends", "count": 2}]}""", problems);
        assertEquals(List.of("LOB", "legends", "legends"), p.custom().tinPacks());
        assertEquals(2, p.custom().promos());
        assertEquals("Ultra Rare", p.cards().get(0).rarity(), "a tin's cards are ultras unless they say");
        assertFalse(BoosterSets.isBooster(p), "a datapack tin's promos are no pack of their own");
    }

    @Test
    void comesOutOnItsDateAndUnlocksItsCards() {
        List<String> problems = new ArrayList<>();
        Products.Product early = parse("early", """
                {"type": "booster", "name": "Early", "date": "2002-03-08", "cards": ["Stardust Dragon"]}""",
                problems);
        Products.Product start = parse("start", """
                {"type": "booster", "name": "Start", "cards": ["Mystical Space Typhoon"]}""", problems);
        Products.Product quiet = parse("quiet", """
                {"type": "booster", "name": "Quiet", "date": "2002-03-08", "unlocks_cards": false,
                 "cards": ["Raigeki Break"]}""", problems);
        Products merged = bundled.withCustom(List.of(early, start, quiet));
        assertEquals(bundled.products().size() + 3, merged.products().size());
        assertEquals("start", merged.products().get(0).id(), "without a date: before everything");
        int lob = indexOf(merged, "legend_of_blue_eyes_white_dragon");
        assertEquals(lob + 1, indexOf(merged, "early"), "after the real products of its day");
        Progression progression = new Progression(merged, cards, everything, Banlists.loadBundled());
        assertFalse(progression.pool(lob).contains(STARDUST), "not before Early");
        assertTrue(progression.pool(lob + 1).contains(STARDUST), "unlocked by Early");
        assertTrue(progression.pool(0).contains(MST), "unlocked from the start");
        assertTrue(progression.unlockedBy(STARDUST).id().equals("early"));
        int raigekiBreak = names.find(new com.google.gson.JsonPrimitive("Raigeki Break"));
        assertFalse(progression.unlockedBy(raigekiBreak).id().equals("quiet"), "Quiet unlocks nothing");
        // The real product that brought Stardust Dragon no longer has it as new.
        String real = bundled.products().stream().filter(p -> p.newCards().contains(STARDUST)).findFirst()
                .orElseThrow().id();
        assertFalse(merged.get(real).newCards().contains(STARDUST));
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void replacesARealProductOfItsId() {
        Products.Product lob = parse("legend_of_blue_eyes_white_dragon", """
                {"type": "booster", "name": "LOB Remix", "code": "LOB", "date": "2002-03-08",
                 "cards": ["Blue-Eyes White Dragon"]}""", new ArrayList<>());
        Products merged = bundled.withCustom(List.of(lob));
        assertEquals(bundled.products().size(), merged.products().size());
        assertEquals("LOB Remix", merged.get("legend_of_blue_eyes_white_dragon").name());
    }

    @Test
    void sendsAndReadsBack() {
        List<String> problems = new ArrayList<>();
        Products.Product deck = parse("deck", """
                {"type": "deck", "name": "D", "date": "2010-01-01", "starter": false, "price": 7,
                 "image": "duelmc:textures/item/d.png",
                 "cards": [{"card": "Blue-Eyes White Dragon", "count": 3}]}""", problems);
        Products.Product booster = parse("b", """
                {"type": "booster", "name": "B", "pack": "tournament",
                 "cards": [{"card": "Blue-Eyes White Dragon", "rarity": ["Rare", "Ghost Rare"]}]}""", problems);
        Products.Product tin = parse("t", """
                {"type": "tin", "name": "T", "packs": ["LOB"], "cards": []}""", problems);
        List<Products.Product> sent = List.of(deck, booster, tin);
        List<Products.Product> read = CustomProducts.parseAll(CustomProducts.toJson(sent), cards, problems::add);
        assertEquals(sent, read);
        assertTrue(problems.isEmpty(), problems.toString());
    }

    private static int indexOf(Products products, String id) {
        for (int i = 0; i < products.products().size(); i++) {
            if (products.products().get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
