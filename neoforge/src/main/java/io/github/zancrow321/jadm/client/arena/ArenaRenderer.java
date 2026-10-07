package io.github.zancrow321.jadm.client.arena;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.arena.ArenaBlock;
import io.github.zancrow321.jadm.arena.ArenaBlockEntity;
import io.github.zancrow321.jadm.arena.DuelArena;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws the whole arena from its middle block; screens and lanterns glow. While someone waits on a podium for an
 * opponent, words float over the podiums, and a countdown over the middle once both are taken.
 */
public final class ArenaRenderer extends GeoBlockRenderer<ArenaBlockEntity> {
    public ArenaRenderer(BlockEntityRendererProvider.Context context) {
        super(new DefaultedBlockGeoModel<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "duel_arena")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(ArenaBlockEntity arena, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        // The middle block sits inside the platform; light it as the platform's top is lit.
        int light = arena.getLevel() == null ? packedLight
                : LevelRenderer.getLightColor(arena.getLevel(), arena.getBlockPos().above(DuelArena.HEIGHT));
        super.render(arena, partialTick, poseStack, buffers, light, packedOverlay);
        if (arena.getLevel() != null && !arena.raised() && arena.waiting() != 0) {
            drawLobby(arena, arena.getLevel().getGameTime() + partialTick, poseStack, buffers);
        }
    }

    private static void drawLobby(ArenaBlockEntity arena, float time, PoseStack poseStack, MultiBufferSource buffers) {
        Direction facing = arena.getBlockState().getValue(ArenaBlock.FACING);
        if (arena.startsAt() > 0) {
            int seconds = Math.max(1, Mth.ceil((arena.startsAt() - time) / 20f));
            // Each number pops in big and settles.
            float into = 1 - ((arena.startsAt() - time) / 20f - (seconds - 1));
            float scale = 5 + 3 * Math.max(0, 1 - into * 4);
            label(poseStack, buffers, new Vec3(0.5, DuelArena.HEIGHT + 5, 0.5), Component.literal(
                    String.valueOf(seconds)), 0xFFD54F, scale, false);
            label(poseStack, buffers, new Vec3(0.5, DuelArena.HEIGHT + 3.2, 0.5),
                    Component.translatable("message.jadm.arena.get_ready"), 0xFFFFFF, 2.5f, true);
            return;
        }
        String dots = ".".repeat(1 + (int) (time / 10 % 3));
        for (int i = 0; i < 2; i++) {
            Vec3 at = DuelArena.podiumOffset(facing, i == 0 ? 1 : -1);
            if ((arena.waiting() & 1 << i) != 0) {
                label(poseStack, buffers, at.add(0, 3, 0),
                        Component.translatable("message.jadm.arena.waiting", dots), 0xFFE066, 2.5f, true);
            } else {
                // The free podium bobs a call to step up.
                float bob = Mth.sin(time * 0.15f) * 0.15f;
                label(poseStack, buffers, at.add(0, 2 + bob, 0),
                        Component.translatable("message.jadm.arena.step_up"), 0x7CFC9A, 2.5f, true);
                label(poseStack, buffers, at.add(0, 1.3 + bob, 0), Component.literal("\u25BC"), 0x7CFC9A, 3f, false);
            }
        }
    }

    /**
     * Text facing the camera at {@code at} (from the middle block's corner), seen through walls a little, on a dark
     * plate if {@code plate}.
     */
    private static void label(PoseStack poseStack, MultiBufferSource buffers, Vec3 at, Component text, int color,
                              float scale, boolean plate) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float s = 0.025f * scale;
        poseStack.scale(s, -s, s);
        Matrix4f pose = poseStack.last().pose();
        float x = -font.width(text) / 2f;
        int background = plate ? (int) (mc.options.getBackgroundOpacity(0.25f) * 255) << 24 : 0;
        font.drawInBatch(text, x, 0, 0x40FFFFFF, false, pose, buffers, Font.DisplayMode.SEE_THROUGH, background,
                LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, x, 0, 0xFF000000 | color, false, pose, buffers, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ArenaBlockEntity arena) {
        return new AABB(arena.getBlockPos()).inflate(16, 0, 16).expandTowards(0, 12, 0);
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
