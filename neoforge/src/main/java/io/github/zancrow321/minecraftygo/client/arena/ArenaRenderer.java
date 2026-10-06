package io.github.zancrow321.minecraftygo.client.arena;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.arena.ArenaBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Draws the whole arena from its middle block; screens and lanterns glow. */
public final class ArenaRenderer extends GeoBlockRenderer<ArenaBlockEntity> {
    public ArenaRenderer(BlockEntityRendererProvider.Context context) {
        super(new DefaultedBlockGeoModel<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "duel_arena")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(ArenaBlockEntity arena, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        // The middle block sits inside the platform; light it as the platform's top is lit.
        int light = arena.getLevel() == null ? packedLight
                : LevelRenderer.getLightColor(arena.getLevel(), arena.getBlockPos().above());
        super.render(arena, partialTick, poseStack, buffers, light, packedOverlay);
    }

    @Override
    public AABB getRenderBoundingBox(ArenaBlockEntity arena) {
        return new AABB(arena.getBlockPos()).inflate(11, 0, 11).expandTowards(0, 9, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(ArenaBlockEntity arena) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
