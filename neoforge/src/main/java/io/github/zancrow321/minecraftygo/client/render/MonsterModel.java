package io.github.zancrow321.minecraftygo.client.render;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.entity.MonsterEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Picks the geometry, texture and animations for a monster from its card. */
public final class MonsterModel extends GeoModel<MonsterEntity> {
    /** Used for a card that has no model of its own. */
    private static final String FALLBACK = "kuriboh";

    @Override
    public ResourceLocation getModelResource(MonsterEntity monster) {
        return location("geo/monster/", ".geo.json", monster);
    }

    @Override
    public ResourceLocation getTextureResource(MonsterEntity monster) {
        return location("textures/monster/", ".png", monster);
    }

    @Override
    public ResourceLocation getAnimationResource(MonsterEntity monster) {
        return location("animations/monster/", ".animation.json", monster);
    }

    private static ResourceLocation location(String folder, String extension, MonsterEntity monster) {
        CardPool.Model model = monster.model();
        return ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID,
                folder + (model != null ? model.id() : FALLBACK) + extension);
    }
}
