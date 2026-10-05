package io.github.zancrow321.minecraftygo.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.zancrow321.minecraftygo.entity.MonsterEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

public final class MonsterRenderer extends GeoEntityRenderer<MonsterEntity> {
    private final MonsterModel model;

    public MonsterRenderer(EntityRendererProvider.Context context) {
        this(context, new MonsterModel());
    }

    private MonsterRenderer(EntityRendererProvider.Context context, MonsterModel model) {
        super(context, model);
        this.model = model;
        shadowRadius = 0.5f;
    }

    @Override
    public void render(MonsterEntity monster, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int light) {
        try {
            super.render(monster, yaw, partialTick, poses, buffers, light);
        } finally {
            model.undoPose();
        }
    }

    /** Fading monsters (summons, departures) need a translucent pass. */
    @Override
    public RenderType getRenderType(MonsterEntity monster, ResourceLocation texture, MultiBufferSource buffers,
                                    float partialTick) {
        return monster.alpha < 1 ? RenderType.entityTranslucent(texture) : super.getRenderType(monster, texture,
                buffers, partialTick);
    }

    @Override
    public Color getRenderColor(MonsterEntity monster, float partialTick, int packedLight) {
        return Color.ofARGB(Math.round(monster.alpha * 255), (monster.tint >> 16) & 0xFF, (monster.tint >> 8) & 0xFF,
                monster.tint & 0xFF);
    }
}
