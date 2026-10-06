package io.github.zancrow321.minecraftygo.client.collection;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.client.ProductArt;
import io.github.zancrow321.minecraftygo.engine.data.Products;
import io.github.zancrow321.minecraftygo.item.YgoComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Draws a booster pack, structure deck or tin as the real product: its picture, cut out, as a slab as thick as the
 * product. In slots only the front is drawn, from a smaller picture. Without a picture (real product pictures off,
 * still downloading, none for the product, or a random pack) the item's own icon is drawn instead.
 */
public final class ProductItemRenderer extends BlockEntityWithoutLevelRenderer {
    /**
     * Layers per sixteenth of thickness that make up the sides of the slab (a cut-out picture can't have side faces
     * of its own); close enough together that they read as a solid edge.
     */
    private static final int LAYERS_PER_PIXEL = 8;
    /** Brightness of the back and of the inner layers that form the sides. */
    private static final int BACK = 0xFFB8B8B8;
    private static final int SIDE = 0xFF707070;

    private final ModelResourceLocation fallback;
    /** Thickness in pixels (sixteenths of the item). */
    private final float thickness;
    /** Size in a hand, on the ground and in frames, against a flat item's full square. */
    private final float size;

    /**
     * @param item      the item's name, whose own icon is the model {@code item/<item>_icon}
     * @param thickness how thick the product is, in sixteenths of the item
     * @param size      how big it is outside slots, against a flat item's full square (a thick box filling the
     *                  whole square looks too big in the hand)
     */
    public ProductItemRenderer(String item, float thickness, float size) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.fallback = iconModel(item);
        this.thickness = thickness;
        this.size = size;
    }

    /** The item's own icon, registered as an extra model. */
    public static ModelResourceLocation iconModel(String item) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "item/" + item + "_icon"));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers,
                             int light, int overlay) {
        String id = stack.get(YgoComponents.PACK_SET.get());
        Products.Product product = id == null ? null : YgoData.products().get(id);
        ProductArt.Art art = product == null ? null : ProductArt.get(product.code());
        if (art == null) {
            renderIcon(stack, poses, buffers, light, overlay);
            return;
        }
        // Fit the picture into the item's square, standing on the same middle plane as a flat item.
        float halfWidth = art.aspect() < 1 ? art.aspect() / 2 : 0.5f;
        float halfHeight = art.aspect() < 1 ? 0.5f : 0.5f / art.aspect();
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        Matrix4f m = poses.last().pose();
        if (context == ItemDisplayContext.GUI) {
            VertexConsumer front = buffers.getBuffer(RenderType.entityCutout(art.icon()));
            quad(front, poses, m, halfWidth * 0.94f, halfHeight * 0.94f, 0, false, 0xFFFFFFFF, light, overlay);
        } else {
            poses.scale(size, size, size);
            m = poses.last().pose();
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(art.full()));
            float half = thickness / 32;
            int layers = Math.max(1, Math.round(thickness * LAYERS_PER_PIXEL));
            quad(vc, poses, m, halfWidth, halfHeight, half, false, 0xFFFFFFFF, light, overlay);
            quad(vc, poses, m, halfWidth, halfHeight, -half, true, BACK, light, overlay);
            for (int i = 1; i < layers; i++) {
                float z = -half + 2 * half * i / layers;
                quad(vc, poses, m, halfWidth, halfHeight, z, false, SIDE, light, overlay);
                quad(vc, poses, m, halfWidth, halfHeight, z, true, SIDE, light, overlay);
            }
        }
        poses.popPose();
    }

    /** The item's own icon, as if it had no renderer of its own (same transforms as a flat item). */
    private void renderIcon(ItemStack stack, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(fallback);
        ItemRenderer items = Minecraft.getInstance().getItemRenderer();
        for (RenderType type : model.getRenderTypes(stack, true)) {
            items.renderModelLists(model, stack, light, overlay, poses,
                    ItemRenderer.getFoilBufferDirect(buffers, type, true, stack.hasFoil()));
        }
    }

    /** One face of the picture at depth {@code z}; a back face shows the picture the right way round from behind. */
    private static void quad(VertexConsumer vc, PoseStack poses, Matrix4f m, float halfWidth, float halfHeight,
                             float z, boolean back, int color, int light, int overlay) {
        float nz = back ? -1 : 1;
        float l = back ? halfWidth : -halfWidth;
        float r = -l;
        vertex(vc, poses, m, l, -halfHeight, z, 0, 1, nz, color, light, overlay);
        vertex(vc, poses, m, r, -halfHeight, z, 1, 1, nz, color, light, overlay);
        vertex(vc, poses, m, r, halfHeight, z, 1, 0, nz, color, light, overlay);
        vertex(vc, poses, m, l, halfHeight, z, 0, 0, nz, color, light, overlay);
    }

    private static void vertex(VertexConsumer vc, PoseStack poses, Matrix4f m, float x, float y, float z, float u,
                               float v, float nz, int color, int light, int overlay) {
        vc.addVertex(m, x, y, z).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(poses.last(), 0, 0, nz);
    }
}
