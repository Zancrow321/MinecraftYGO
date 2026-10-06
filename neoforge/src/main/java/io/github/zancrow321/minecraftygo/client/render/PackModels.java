package io.github.zancrow321.minecraftygo.client.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Monster models that resource packs add or replace. Every {@code assets/<namespace>/minecraftygo/models.json}
 * lists models in the same format as the bundled one; a model without a namespace in its name belongs to the
 * pack's namespace, so {@code "dragon"} in {@code assets/mypack/...} is {@code mypack:geo/monster/dragon.geo.json}.
 * GeckoLib loads the geometry and animations from the pack itself. Packs higher in the list win.
 */
public final class PackModels {
    private static final String FILE = "minecraftygo/models.json";

    private PackModels() {
    }

    /** Reads every pack's model list; called whenever resources (re)load. */
    public static void reload(ResourceManager manager) {
        Map<Integer, CardPool.Model> models = new LinkedHashMap<>();
        for (String namespace : manager.getNamespaces()) {
            // The stack runs from the lowest pack to the highest, so later entries replace earlier ones.
            for (Resource resource : manager.getResourceStack(ResourceLocation.fromNamespaceAndPath(namespace, FILE))) {
                try (Reader reader = resource.openAsReader()) {
                    for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                        JsonObject o = element.getAsJsonObject();
                        CardPool.Model m = CardPool.parseModel(o);
                        String id = m.id().contains(":") ? m.id() : namespace + ":" + m.id();
                        models.put(m.code(), new CardPool.Model(m.code(), id, m.width(), m.height(),
                                m.animations()));
                    }
                } catch (Exception e) {
                    MinecraftYgo.LOGGER.error("Could not read {} from {}", FILE, resource.sourcePackId(), e);
                }
            }
        }
        YgoData.packModels(models);
        if (!models.isEmpty()) {
            MinecraftYgo.LOGGER.info("{} monster models from resource packs", models.size());
        }
    }
}
