package io.github.zancrow321.minecraftygo.client.render;

import io.github.zancrow321.minecraftygo.entity.MonsterEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MonsterRenderer extends GeoEntityRenderer<MonsterEntity> {
    public MonsterRenderer(EntityRendererProvider.Context context) {
        super(context, new MonsterModel());
        shadowRadius = 0.5f;
    }
}
