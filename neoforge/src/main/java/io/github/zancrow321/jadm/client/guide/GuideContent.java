package io.github.zancrow321.jadm.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zancrow321.jadm.Jadm;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The handbook's text, from {@code assets/jadm/guide/<language>.json} (English when the language has none), so
 * a resource pack can rewrite or translate it:
 * <pre>
 * {"title": "...", "subtitle": "...", "intro": ["paragraph", ...],
 *  "chapters": [{"title": "...", "icon": "{modid}:duel_disk", "text": ["paragraph", ...]}]}
 * </pre>
 * See {@link GuideText} for what a paragraph can contain.
 */
record GuideContent(String title, String subtitle, List<String> intro, List<Chapter> chapters) {
    record Chapter(String title, ItemStack icon, List<String> text) {
    }

    private static final String FALLBACK = "en_us";

    static GuideContent load() {
        Minecraft minecraft = Minecraft.getInstance();
        String language = minecraft.getLanguageManager().getSelected();
        Optional<Resource> resource = minecraft.getResourceManager().getResource(location(language));
        if (resource.isEmpty()) {
            resource = minecraft.getResourceManager().getResource(location(FALLBACK));
        }
        if (resource.isEmpty()) {
            return new GuideContent("?", "", List.of("The handbook's pages are missing."), List.of());
        }
        try (Reader reader = resource.get().openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            Jadm.LOGGER.error("Could not read the handbook for {}", language, e);
            return new GuideContent("?", "", List.of("The handbook's pages could not be read: " + e.getMessage()),
                    List.of());
        }
    }

    private static ResourceLocation location(String language) {
        return ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "guide/" + language + ".json");
    }

    private static GuideContent parse(JsonObject json) {
        List<Chapter> chapters = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("chapters")) {
            JsonObject chapter = element.getAsJsonObject();
            chapters.add(new Chapter(chapter.get("title").getAsString(), icon(chapter), strings(chapter, "text")));
        }
        return new GuideContent(json.get("title").getAsString(),
                json.has("subtitle") ? json.get("subtitle").getAsString() : "", strings(json, "intro"), chapters);
    }

    private static ItemStack icon(JsonObject chapter) {
        if (!chapter.has("icon")) {
            return new ItemStack(Items.BOOK);
        }
        ResourceLocation id = ResourceLocation.tryParse(GuideText.names(chapter.get("icon").getAsString()));
        return id == null || !BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(Items.BOOK)
                : new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        if (json.has(key)) {
            JsonArray array = json.getAsJsonArray(key);
            array.forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }
}
