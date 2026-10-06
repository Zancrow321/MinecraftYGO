package io.github.zancrow321.jadm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.zancrow321.jadm.entity.MonsterEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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

    /**
     * GeckoLib only turns living entities, so a monster would always face the same way. Face where the field points
     * it (toward the opponent), and turn models that were built facing south around.
     */
    @Override
    protected void applyRotations(MonsterEntity monster, PoseStack poses, float ageInTicks, float rotationYaw,
                                  float partialTick, float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, monster.yRotO, monster.getYRot())
                + (model.forward(monster) < 0 ? 180 : 0);
        super.applyRotations(monster, poses, ageInTicks, yaw, partialTick, nativeScale);
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
