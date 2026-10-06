package io.github.zancrow321.jadm.client.collection;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.zancrow321.jadm.client.CardArt;
import io.github.zancrow321.jadm.item.CardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Draws a card item as the real card: its art on the front with its foil, the card back behind, in the inventory and
 * in the world.
 */
public final class CardItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float HALF_WIDTH = 0.34f;
    private static final float HALF_HEIGHT = 0.5f;

    public CardItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers,
                             int light, int overlay) {
        int code = CardItem.code(stack);
        CardArt.Texture art = code == 0 ? null : CardArt.get(code);
        ResourceLocation front = art != null ? art.location() : CardGrid.CARD_BLANK;
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        Matrix4f m = poses.last().pose();
        quad(buffers.getBuffer(RenderType.entityCutout(front)), poses, m, 0.001f, false, light, overlay);
        quad(buffers.getBuffer(RenderType.entityCutout(CardGrid.CARD_BACK)), poses, m, -0.001f, true, light,
                overlay);
        if (art != null) {
            FoilEffect.drawItem(code, CardItem.rarity(stack), poses, buffers, HALF_WIDTH, HALF_HEIGHT, 0.002f,
                    overlay);
        }
        poses.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack poses, Matrix4f m, float z, boolean back, int light,
                             int overlay) {
        float nz = back ? -1 : 1;
        float l = back ? HALF_WIDTH : -HALF_WIDTH;
        float r = -l;
        vertex(vc, poses, m, l, -HALF_HEIGHT, z, 0, 1, nz, light, overlay);
        vertex(vc, poses, m, r, -HALF_HEIGHT, z, 1, 1, nz, light, overlay);
        vertex(vc, poses, m, r, HALF_HEIGHT, z, 1, 0, nz, light, overlay);
        vertex(vc, poses, m, l, HALF_HEIGHT, z, 0, 0, nz, light, overlay);
    }

    private static void vertex(VertexConsumer vc, PoseStack poses, Matrix4f m, float x, float y, float z, float u,
                               float v, float nz, int light, int overlay) {
        vc.addVertex(m, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(poses.last(), 0, 0, nz);
    }
}
