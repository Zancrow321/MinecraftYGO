package io.github.zancrow321.jadm.client.quest;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.quest.QuestView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes out a quest for the quest window and its pop-ups: its title (the server's own, else one made from its type
 * and goal), the conditions in one line, and the reward.
 */
public final class QuestText {
    private QuestText() {
    }

    private static String language() {
        return Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(Locale.ROOT);
    }

    private static boolean german() {
        return language().startsWith("de");
    }

    public static Component title(QuestView.Entry e) {
        Map<String, String> titles = e.title;
        for (String key : new String[]{language(), "", "en_us"}) {
            if (titles.containsKey(key)) {
                return Component.literal(titles.get(key));
            }
        }
        if (!titles.isEmpty()) {
            return Component.literal(titles.values().iterator().next());
        }
        return generated(e);
    }

    /** "Win 3 duels", "Fusion Summon a monster", "Deal 10000 damage"... */
    private static Component generated(QuestView.Entry e) {
        String key = switch (e.type) {
            case "summon" -> "quest.jadm.summon." + e.filters.getOrDefault("summon", "any");
            case "activate" -> "quest.jadm.activate." + e.filters.getOrDefault("cardType", "any");
            case "tournament" -> {
                int place = Integer.parseInt(e.filters.getOrDefault("maxPlace", "0"));
                if (place > 1) {
                    yield null;
                }
                yield place == 1 ? "quest.jadm.tournament.win" : "quest.jadm.tournament.play";
            }
            default -> "quest.jadm." + e.type;
        };
        if (key == null) {
            return Component.translatable("quest.jadm.tournament.top" + (e.goal == 1 ? ".one" : ""), e.goal,
                    e.filters.get("maxPlace"));
        }
        if (e.type.equals("damage")) {
            return Component.translatable(key, e.goal);
        }
        return e.goal == 1 ? Component.translatable(key + ".one") : Component.translatable(key, e.goal);
    }

    /**
     * The quest's conditions, e.g. "Deck: 6+ Dragon · against players · ranked", or empty if it has none. A quest
     * with its own title says the rest itself, so only how many cards its deck needs is added.
     */
    public static Component details(QuestView.Entry e) {
        Map<String, String> f = e.filters;
        List<Component> parts = new ArrayList<>();
        boolean titled = !e.title.isEmpty();
        int deckCards = Integer.parseInt(f.getOrDefault("deckCards", "1"));
        if (f.containsKey("race")) {
            parts.add(Component.translatable("quest.jadm.filter.deck", deckCards,
                    trait(Integer.parseInt(f.get("race")), 1020, CardItem.RACES_DE)));
        }
        if (f.containsKey("attribute")) {
            parts.add(Component.translatable("quest.jadm.filter.deck", deckCards,
                    trait(Integer.parseInt(f.get("attribute")), 1010, CardItem.ATTRIBUTES_DE)));
        }
        if (f.containsKey("name")) {
            parts.add(Component.translatable("quest.jadm.filter.deck_name", deckCards, f.get("name")));
        }
        if (titled) {
            return join(parts);
        }
        if (f.containsKey("card")) {
            parts.add(Component.translatable("quest.jadm.filter.deck_card",
                    JadmData.text().cardName(Integer.parseInt(f.get("card")))));
        }
        if (f.containsKey("against")) {
            parts.add(Component.translatable("quest.jadm.filter.against." + f.get("against")));
        }
        if (f.containsKey("ranked")) {
            parts.add(Component.translatable("quest.jadm.filter.ranked"));
        }
        if (f.containsKey("tournament")) {
            parts.add(Component.translatable("quest.jadm.filter.tournament." + f.get("tournament")));
        }
        if (f.containsKey("won")) {
            parts.add(Component.translatable("quest.jadm.filter.won"));
        }
        if (f.containsKey("minLifePoints")) {
            parts.add(Component.translatable("quest.jadm.filter.min_lp", f.get("minLifePoints")));
        }
        if (f.containsKey("maxTurns")) {
            parts.add(Component.translatable("quest.jadm.filter.max_turns", f.get("maxTurns")));
        }
        return join(parts);
    }

    /** A monster type or attribute by its bit, in German for German players. */
    private static String trait(int bit, int firstString, String[] german) {
        if (german() && bit < german.length) {
            return german[bit];
        }
        return JadmData.text().system(firstString + bit);
    }

    /** "150 DP · 1 Booster Pack · 2x Diamond" */
    public static Component reward(QuestView.Reward r) {
        List<Component> parts = new ArrayList<>();
        if (r.points > 0) {
            parts.add(Component.literal(r.points + " " + r.symbol));
        }
        if (r.packs > 0) {
            parts.add(r.packName == null
                    ? Component.translatable(r.packs == 1 ? "quest.jadm.reward.pack" : "quest.jadm.reward.packs",
                    r.packs)
                    : Component.translatable(r.packs == 1 ? "quest.jadm.reward.pack_of" : "quest.jadm.reward.packs_of",
                    r.packs, r.packName));
        }
        if (r.emeralds > 0) {
            parts.add(Component.translatable("quest.jadm.reward.emeralds", r.emeralds));
        }
        if (r.xp > 0) {
            parts.add(Component.translatable("quest.jadm.reward.xp", r.xp));
        }
        for (QuestView.Item item : r.items) {
            ResourceLocation id = ResourceLocation.tryParse(item.id);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                parts.add(Component.literal(item.count + "x ").append(BuiltInRegistries.ITEM.get(id).getDescription()));
            }
        }
        return join(parts);
    }

    private static Component join(List<Component> parts) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                line.append(" · ");
            }
            line.append(parts.get(i));
        }
        return line;
    }

    /** The text cut to {@code width} pixels, with "..." where it was cut. */
    public static String fit(net.minecraft.client.gui.Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("...")).stripTrailing() + "...";
    }

    /** "5 h 12 min", "3 d 4 h" */
    public static String duration(long millis) {
        long minutes = Math.max(1, millis / 60_000);
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) {
            return days + " d " + hours % 24 + " h";
        }
        if (hours > 0) {
            return hours + " h " + minutes % 60 + " min";
        }
        return minutes + " min";
    }
}
