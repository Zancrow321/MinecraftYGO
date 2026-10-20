package io.github.zancrow321.jadm.client.collection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import io.github.zancrow321.jadm.client.CardArt;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The foil of a card, drawn over its image like the foil on a real card, one look per {@link Rarity}: a Rare's
 * silver name, a Super Rare's holofoil artwork, a Gold Rare's gold frame, a Starlight Rare's rainbow over the whole
 * card, a Ghost Rare's pale silver artwork... A glare sweeps over the foil now and then.
 * <p>
 * The looks are data, in {@code assets/jadm/foil/looks.json}: layers of foil textures over parts of the card,
 * added (black adds nothing) or multiplied (to color white parts, which adding light can't). A name's foil colors its
 * letters alone, through a mask made from the card image ({@link CardArt#letters}). The textures come from
 * {@code tools/textures/make_foil_textures.py}; {@code tools/textures/preview_foils.py} draws the looks as the game
 * does.
 */
public final class FoilEffect {
    private static final String LOOKS = "/assets/jadm/foil/looks.json";

    /**
     * Multiplies a layer into what is drawn already: {@code dst × (1 - a + a × src)} for a source color of
     * {@code a × color} and alpha {@code a}, so {@code a} is how strongly it tints.
     */
    private static final RenderStateShard.TransparencyStateShard MULTIPLY = new RenderStateShard.TransparencyStateShard(
            "jadm_foil_multiply", FoilEffect::multiplyBlend, FoilEffect::defaultBlend);

    private FoilEffect() {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "textures/foil/" + name + ".png");
    }

    /** A part of the card image, as fractions of its width and height. */
    private record Box(float left, float top, float right, float bottom) {
    }

    private enum Mode {
        /** The texture drifts slowly over the box. */
        PATTERN,
        /** A narrow glare passes over the box every few seconds. */
        SHEEN,
        /** The middle of the texture, as one color over the whole box. */
        FLAT,
        /** Only on letters: the colors of the rainbow along the name, drifting. */
        RAINBOW
    }

    /** How many strips a name's letters are drawn in, so a glare or the rainbow can run along it. */
    private static final int LETTER_STRIPS = 24;

    /**
     * One layer of foil: a texture over parts of the card, tinted, added or multiplied. A ghost layer instead draws
     * the card's {@link CardArt#ghost} image over its boxes; a letters layer colors the lettering in its box (the
     * name) through the card's {@link CardArt#letters} mask.
     */
    private record Layer(ResourceLocation texture, List<Box> boxes, int color, Mode mode, float strength,
                         boolean ghost, boolean letters) {
        boolean multiply() {
            return strength > 0;
        }
    }

    private record Looks(Map<Rarity, List<Layer>> plain, Map<Rarity, List<Layer>> pendulum) {
    }

    private static Looks looks;

    private static Looks looks() {
        if (looks == null) {
            try (InputStream in = FoilEffect.class.getResourceAsStream(LOOKS)) {
                if (in == null) {
                    throw new IllegalStateException("Missing " + LOOKS);
                }
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                looks = new Looks(parse(json, false), parse(json, true));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return looks;
    }

    /** Every rarity's layers; on a pendulum card its wider artwork box. */
    private static Map<Rarity, List<Layer>> parse(JsonObject json, boolean pendulum) {
        Map<Rarity, List<Layer>> out = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            List<Layer> layers = new ArrayList<>();
            JsonArray entries = json.getAsJsonObject("looks").getAsJsonArray(rarity.id());
            if (entries == null) {
                Jadm.LOGGER.warn("No foil look for {}", rarity.id());
                entries = new JsonArray();
            }
            for (JsonElement e : entries) {
                JsonObject entry = e.getAsJsonObject();
                if (entry.has("use")) {
                    for (JsonElement p : json.getAsJsonObject("presets").getAsJsonArray(entry.get("use").getAsString())) {
                        layers.add(layer(json, p.getAsJsonObject(), pendulum));
                    }
                } else {
                    layers.add(layer(json, entry, pendulum));
                }
            }
            out.put(rarity, List.copyOf(layers));
        }
        return out;
    }

    private static Layer layer(JsonObject json, JsonObject o, boolean pendulum) {
        if (o.has("ghost")) {
            return new Layer(null, boxes(json, o.get("ghost"), pendulum), 0xFFFFFF, Mode.FLAT, 0, true, false);
        }
        float strength = "multiply".equals(o.has("blend") ? o.get("blend").getAsString() : "add")
                ? o.get("strength").getAsFloat() : 0;
        boolean letters = o.has("letters");
        return new Layer(letters ? null : texture(o.get("texture").getAsString()),
                boxes(json, o.get(letters ? "letters" : "on"), pendulum),
                Integer.parseInt(o.get("color").getAsString(), 16),
                Mode.valueOf(o.get("mode").getAsString().toUpperCase(Locale.ROOT)), strength, false, letters);
    }

    /** The boxes a layer covers, by name, with groups such as {@code metal} spelled out. */
    private static List<Box> boxes(JsonObject json, JsonElement on, boolean pendulum) {
        List<Box> out = new ArrayList<>();
        if (on.isJsonArray() && !on.getAsJsonArray().isEmpty() && on.getAsJsonArray().get(0).isJsonPrimitive()
                && on.getAsJsonArray().get(0).getAsJsonPrimitive().isNumber()) {
            JsonArray b = on.getAsJsonArray();
            out.add(new Box(b.get(0).getAsFloat(), b.get(1).getAsFloat(), b.get(2).getAsFloat(), b.get(3).getAsFloat()));
        } else if (on.isJsonArray()) {
            on.getAsJsonArray().forEach(e -> out.addAll(boxes(json, e, pendulum)));
        } else {
            String name = on.getAsString();
            JsonObject pendulumBoxes = json.getAsJsonObject("pendulum_boxes");
            JsonElement named = pendulum && pendulumBoxes.has(name) ? pendulumBoxes.get(name)
                    : json.getAsJsonObject("boxes").get(name);
            if (named == null) {
                throw new IllegalStateException("No foil box " + name);
            }
            out.addAll(boxes(json, named, pendulum));
        }
        return out;
    }

    private static List<Layer> layers(int code, Rarity rarity) {
        CardInfo card = JadmData.cards().card(code);
        boolean pendulum = card != null && card.is(OcgConstants.TYPE_PENDULUM);
        return (pendulum ? looks().pendulum() : looks().plain()).get(rarity);
    }

    /** Seconds, wrapped so the scroll offsets stay precise as floats. */
    private static float time() {
        return (Util.getMillis() % 400_000L) / 1000f;
    }

    /**
     * The texture coordinates of a point on the card, at card fractions {@code fx, fy}, in {@code box}. A glare is a
     * narrow band that moves diagonally and passes every few seconds; the foil patterns drift slowly. A ghost layer
     * shows the matching point of the card's ghost image, a letters layer that of the box's lettering mask.
     */
    private static float u(Layer layer, Box box, float fx, float fy, float t) {
        if (layer.ghost()) {
            return fx;
        }
        if (layer.letters()) {
            return (fx - box.left()) / (box.right() - box.left());
        }
        return switch (layer.mode()) {
            case FLAT, RAINBOW -> 0.5f;
            case SHEEN -> fx * 0.25f + fy * 0.12f - t * 0.15f;
            case PATTERN -> fx * 1.2f + t * 0.03f;
        };
    }

    private static float v(Layer layer, Box box, float fx, float fy, float t) {
        if (layer.ghost()) {
            return fy;
        }
        if (layer.letters()) {
            return (fy - box.top()) / (box.bottom() - box.top());
        }
        return switch (layer.mode()) {
            case FLAT, SHEEN, RAINBOW -> 0.5f;
            case PATTERN -> fy * 1.2f * 1.46f - t * 0.02f;
        };
    }

    /**
     * The vertex color of a layer at card fractions {@code fx, fy}: its tint, scaled by its strength when multiplied.
     * On letters, which have no foil texture of their own, the glare and the rainbow are worked out here instead,
     * as the sheen texture and a hue that turns along the name.
     */
    private static int color(Layer layer, float fx, float fy, float t) {
        float r = ((layer.color() >> 16) & 0xFF) / 255f;
        float g = ((layer.color() >> 8) & 0xFF) / 255f;
        float b = (layer.color() & 0xFF) / 255f;
        if (layer.letters() && layer.mode() == Mode.SHEEN) {
            float u = fx * 0.25f + fy * 0.12f - t * 0.15f;
            float d = Math.abs((u - (float) Math.floor(u)) * 512 - 256) / 20;
            float glare = d < 1 ? (float) Math.pow(Math.cos(d * Math.PI / 2), 2) : 0;
            r *= glare;
            g *= glare;
            b *= glare;
        } else if (layer.letters() && layer.mode() == Mode.RAINBOW) {
            float hue = fx * 2 + t * 0.2f;
            int rgb = java.awt.Color.HSBtoRGB(hue - (float) Math.floor(hue), 0.6f, 1);
            r *= ((rgb >> 16) & 0xFF) / 255f;
            g *= ((rgb >> 8) & 0xFF) / 255f;
            b *= (rgb & 0xFF) / 255f;
        }
        float a = layer.multiply() ? layer.strength() : 1;
        return Math.round(a * 255) << 24 | Math.round(r * a * 255) << 16 | Math.round(g * a * 255) << 8
                | Math.round(b * a * 255);
    }

    /** The texture a layer draws: its foil, or the card's ghost image or lettering; {@code null} if not ready. */
    private static ResourceLocation texture(Layer layer, int code) {
        if (layer.ghost()) {
            CardArt.Texture ghost = CardArt.ghost(code);
            return ghost == null ? null : ghost.location();
        }
        if (layer.letters()) {
            Box box = layer.boxes().getFirst();
            CardArt.Texture letters = CardArt.letters(code, box.left(), box.top(), box.right(), box.bottom());
            return letters == null ? null : letters.location();
        }
        return layer.texture();
    }

    /**
     * The left and right edges of the strips a box is drawn in: one strip, or on letters several, so their color can
     * change along the name.
     */
    private static float[] strips(Layer layer, Box box) {
        int n = layer.letters() ? LETTER_STRIPS : 1;
        float[] xs = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            xs[i] = box.left() + (box.right() - box.left()) * i / n;
        }
        return xs;
    }

    private static void multiplyBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO,
                GlStateManager.DestFactor.ONE);
    }

    private static void defaultBlend() {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** Adds the foil of a card drawn in a screen at {@code x, y} with size {@code w × h}. */
    public static void drawGui(GuiGraphics g, int code, Rarity rarity, int x, int y, int w, int h) {
        List<Layer> layers = layers(code, rarity);
        if (layers.isEmpty()) {
            return;
        }
        g.flush();
        float t = time();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for (Layer layer : layers) {
            ResourceLocation texture = texture(layer, code);
            if (texture == null) {
                continue;
            }
            if (layer.ghost()) {
                RenderSystem.disableBlend();
            } else if (layer.multiply()) {
                multiplyBlend();
            } else {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            }
            RenderSystem.setShaderTexture(0, texture);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Box box : layer.boxes()) {
                float[] xs = strips(layer, box);
                for (int i = 0; i + 1 < xs.length; i++) {
                    float[][] corners = {{xs[i], box.top()}, {xs[i], box.bottom()},
                            {xs[i + 1], box.bottom()}, {xs[i + 1], box.top()}};
                    for (float[] c : corners) {
                        b.addVertex(m, x + c[0] * w, y + c[1] * h, 0).setUv(u(layer, box, c[0], c[1], t),
                                v(layer, box, c[0], c[1], t)).setColor(layer.ghost() ? 0xFFFFFFFF
                                : color(layer, c[0], c[1], t));
                    }
                }
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
        defaultBlend();
    }

    /**
     * Adds the foil of a card item, whose front is the quad from {@code -halfWidth, -halfHeight} to
     * {@code halfWidth, halfHeight} at {@code z}, facing +z, lit by {@code light}.
     */
    public static void drawItem(int code, Rarity rarity, PoseStack poses, MultiBufferSource buffers,
                                float halfWidth, float halfHeight, float z, int light, int overlay) {
        float t = time();
        Matrix4f m = poses.last().pose();
        for (Layer layer : layers(code, rarity)) {
            ResourceLocation texture = texture(layer, code);
            if (texture == null) {
                continue;
            }
            VertexConsumer vc;
            float depth = z;
            if (layer.ghost()) {
                vc = buffers.getBuffer(RenderType.entityCutout(texture));
                // Between the card's front and its foil.
                depth = z * 0.75f;
            } else {
                // Eyes are drawn at full brightness, like the foil catching the light; added, or multiplied in.
                vc = buffers.getBuffer(RenderType.EYES.apply(texture,
                        layer.multiply() ? MULTIPLY : RenderStateShard.ADDITIVE_TRANSPARENCY));
            }
            for (Box box : layer.boxes()) {
                float[] xs = strips(layer, box);
                for (int i = 0; i + 1 < xs.length; i++) {
                    float[][] corners = {{xs[i], box.bottom()}, {xs[i + 1], box.bottom()},
                            {xs[i + 1], box.top()}, {xs[i], box.top()}};
                    for (float[] c : corners) {
                        vc.addVertex(m, -halfWidth + c[0] * 2 * halfWidth, halfHeight - c[1] * 2 * halfHeight,
                                        depth)
                                .setColor(layer.ghost() ? 0xFFFFFFFF : color(layer, c[0], c[1], t))
                                .setUv(u(layer, box, c[0], c[1], t), v(layer, box, c[0], c[1], t))
                                .setOverlay(overlay).setLight(layer.ghost() ? light : 0xF000F0)
                                .setNormal(poses.last(), 0, 0, 1);
                    }
                }
            }
        }
    }
}
