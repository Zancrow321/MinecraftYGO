package io.github.zancrow321.jadm.client.collection;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * The shine of foil cards, added on top of the card image like the foil on a real card:
 * <ul>
 *     <li>Rare: silver name.</li>
 *     <li>Super Rare: holofoil artwork.</li>
 *     <li>Ultra Rare: holofoil artwork and gold name.</li>
 *     <li>Secret Rare: rainbow-lined artwork and silver name.</li>
 * </ul>
 * A glare sweeps over the foil now and then. The textures are drawn additively, so black adds nothing; they come
 * from {@code tools/textures/make_foil_textures.py}.
 */
public final class FoilEffect {
    private static final ResourceLocation HOLO = texture("holo");
    private static final ResourceLocation SECRET = texture("secret");
    private static final ResourceLocation SHEEN = texture("sheen");

    private static final int SILVER = 0x606878;
    private static final int GOLD = 0x806020;

    /** Where things are on the card image, as fractions of its width and height. */
    private record Box(float left, float top, float right, float bottom) {
    }

    private static final Box NAME = new Box(0.06f, 0.045f, 0.84f, 0.115f);
    private static final Box ARTWORK = new Box(0.119f, 0.182f, 0.881f, 0.702f);
    /** Pendulum artwork is wider and runs behind the scale box. */
    private static final Box PENDULUM_ARTWORK = new Box(0.066f, 0.18f, 0.934f, 0.63f);

    private FoilEffect() {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "textures/foil/" + name + ".png");
    }

    /** One layer of foil: a texture over a part of the card, tinted and scrolled. */
    private record Layer(ResourceLocation texture, Box box, int color, boolean sheen) {
    }

    private static Layer[] layers(int code, Rarity rarity) {
        CardInfo card = JadmData.cards().card(code);
        Box art = card != null && card.is(OcgConstants.TYPE_PENDULUM) ? PENDULUM_ARTWORK : ARTWORK;
        return switch (rarity) {
            case COMMON -> new Layer[0];
            case RARE -> new Layer[]{
                    new Layer(SHEEN, NAME, SILVER, false), new Layer(SHEEN, NAME, 0xFFFFFF, true)};
            case SUPER -> new Layer[]{
                    new Layer(HOLO, art, 0x707070, false), new Layer(SHEEN, art, 0xA0A0A0, true)};
            case ULTRA -> new Layer[]{
                    new Layer(HOLO, art, 0x707070, false), new Layer(SHEEN, art, 0xA0A0A0, true),
                    new Layer(SHEEN, NAME, GOLD, false), new Layer(SHEEN, NAME, 0xFFD870, true)};
            case SECRET -> new Layer[]{
                    new Layer(SECRET, art, 0x585858, false), new Layer(SHEEN, art, 0xA0A0A0, true),
                    new Layer(SHEEN, NAME, SILVER, false), new Layer(SHEEN, NAME, 0xFFFFFF, true)};
        };
    }

    /** Seconds, wrapped so the scroll offsets stay precise as floats. */
    private static float time() {
        return (Util.getMillis() % 400_000L) / 1000f;
    }

    /**
     * The texture coordinates of a point on the card, at card fractions {@code fx, fy}. A glare is a narrow band
     * that moves diagonally and passes every few seconds; the foil patterns drift slowly.
     */
    private static float u(Layer layer, float fx, float fy, float t) {
        return layer.sheen() ? fx * 0.25f + fy * 0.12f - t * 0.15f : fx * 1.2f + t * 0.03f;
    }

    private static float v(Layer layer, float fx, float fy, float t) {
        return layer.sheen() ? 0.5f : fy * 1.2f * 1.46f - t * 0.02f;
    }

    /** A flat glow over the box, for the always-on part of a foil name: the middle of the sheen texture. */
    private static boolean flat(Layer layer) {
        return layer.texture() == SHEEN && !layer.sheen();
    }

    /** Adds the foil of a card drawn in a screen at {@code x, y} with size {@code w × h}. */
    public static void drawGui(GuiGraphics g, int code, Rarity rarity, int x, int y, int w, int h) {
        Layer[] layers = layers(code, rarity);
        if (layers.length == 0) {
            return;
        }
        g.flush();
        float t = time();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        for (Layer layer : layers) {
            RenderSystem.setShaderTexture(0, layer.texture());
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            Box box = layer.box();
            float[][] corners = {{box.left(), box.top()}, {box.left(), box.bottom()}, {box.right(), box.bottom()},
                    {box.right(), box.top()}};
            for (float[] c : corners) {
                float u = flat(layer) ? 0.5f : u(layer, c[0], c[1], t);
                float v = flat(layer) ? 0.5f : v(layer, c[0], c[1], t);
                b.addVertex(m, x + c[0] * w, y + c[1] * h, 0).setUv(u, v).setColor(0xFF000000 | layer.color());
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Adds the foil of a card item, whose front is the quad from {@code -halfWidth, -halfHeight} to
     * {@code halfWidth, halfHeight} at {@code z}, facing +z.
     */
    public static void drawItem(int code, Rarity rarity, PoseStack poses, MultiBufferSource buffers,
                                float halfWidth, float halfHeight, float z, int overlay) {
        Layer[] layers = layers(code, rarity);
        float t = time();
        Matrix4f m = poses.last().pose();
        for (Layer layer : layers) {
            // Eyes are drawn additively and at full brightness, like the foil catching the light.
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(layer.texture()));
            Box box = layer.box();
            float[][] corners = {{box.left(), box.bottom()}, {box.right(), box.bottom()}, {box.right(), box.top()},
                    {box.left(), box.top()}};
            for (float[] c : corners) {
                float u = flat(layer) ? 0.5f : u(layer, c[0], c[1], t);
                float v = flat(layer) ? 0.5f : v(layer, c[0], c[1], t);
                vc.addVertex(m, -halfWidth + c[0] * 2 * halfWidth, halfHeight - c[1] * 2 * halfHeight, z)
                        .setColor(0xFF000000 | layer.color()).setUv(u, v).setOverlay(overlay)
                        .setLight(0xF000F0).setNormal(poses.last(), 0, 0, 1);
            }
        }
    }
}
