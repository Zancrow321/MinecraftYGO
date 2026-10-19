package io.github.zancrow321.jadm.client.collection;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.zancrow321.jadm.Jadm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Light for the pack opening show, added onto what is drawn already: soft glows, light rays, beams and sparks. The
 * few textures it needs (a soft dot, a ring, a square) are made when first used, not shipped.
 */
final class ShowFx {
    static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "show/glow");
    static final ResourceLocation RING = ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "show/ring");
    static final ResourceLocation SQUARE = ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "show/square");

    private static boolean made;

    private ShowFx() {
    }

    private interface Alpha {
        /** Opacity 0..1 at a distance {@code r} from the middle, 1 being the edge. */
        double at(double r);
    }

    static void ensureTextures() {
        if (made) {
            return;
        }
        made = true;
        make(GLOW, 64, r -> Math.pow(Math.max(0, 1 - r), 2.2));
        make(RING, 128, r -> Math.exp(-Math.pow((r - 0.8) / 0.09, 2)));
        make(SQUARE, 4, r -> 1);
    }

    private static void make(ResourceLocation location, int size, Alpha alpha) {
        NativeImage image = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = (x + 0.5) / size * 2 - 1;
                double dy = (y + 0.5) / size * 2 - 1;
                int v = (int) Math.round(Mth.clamp(alpha.at(Math.sqrt(dx * dx + dy * dy)), 0, 1) * 255);
                // The light is in the color, not the alpha: the shader throws away nearly clear pixels, which would
                // cut a hard edge into a soft glow.
                image.setPixelRGBA(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(location, texture);
    }

    /** Starts a batch of quads of one texture, added onto the screen. */
    static BufferBuilder begin(ResourceLocation texture) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    static void end(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    /**
     * A quad of the batch's texture centered on {@code cx, cy}, {@code rx × ry} from the middle, turned by
     * {@code angle} radians, in {@code argb} (its alpha is how strong).
     */
    static void quad(BufferBuilder b, Matrix4f m, float cx, float cy, float rx, float ry, float angle, int argb) {
        int a = argb >>> 24;
        int color = 0xFF000000 | ((argb >> 16 & 0xFF) * a / 255) << 16 | ((argb >> 8 & 0xFF) * a / 255) << 8
                | (argb & 0xFF) * a / 255;
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float[][] corners = {{-1, -1, 0, 0}, {-1, 1, 0, 1}, {1, 1, 1, 1}, {1, -1, 1, 0}};
        for (float[] c : corners) {
            float x = c[0] * rx;
            float y = c[1] * ry;
            b.addVertex(m, cx + x * cos - y * sin, cy + x * sin + y * cos, 0).setUv(c[2], c[3]).setColor(color);
        }
    }

    /** One soft glow on its own. */
    static void glow(Matrix4f m, float cx, float cy, float rx, float ry, float angle, int argb) {
        BufferBuilder b = begin(GLOW);
        quad(b, m, cx, cy, rx, ry, angle, argb);
        end(b);
    }

    /** Light rays around {@code cx, cy}, fading out to {@code radius}, turned by {@code spin} radians. */
    static void rays(Matrix4f m, float cx, float cy, float radius, int count, float spin, int argb) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int rim = argb & 0x00FFFFFF;
        float half = Mth.PI / count * 0.45f;
        for (int i = 0; i < count; i++) {
            float a = spin + i * Mth.TWO_PI / count;
            // Every other ray longer, like the burst behind a reveal in a card game.
            float r = radius * (i % 2 == 0 ? 1 : 0.7f);
            b.addVertex(m, cx, cy, 0).setColor(argb);
            b.addVertex(m, cx + Mth.cos(a - half) * r, cy + Mth.sin(a - half) * r, 0).setColor(rim);
            b.addVertex(m, cx + Mth.cos(a + half) * r, cy + Mth.sin(a + half) * r, 0).setColor(rim);
        }
        MeshData mesh = b.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    /** {@code argb} with its alpha times {@code alpha}. */
    static int fade(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Mth.clamp(alpha, 0, 1));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** A color of the rainbow, going round once every {@code period} ticks. */
    static int rainbow(float time, float period, float offset) {
        return 0xFF000000 | Mth.hsvToRgb(((time / period + offset) % 1 + 1) % 1, 0.75f, 1);
    }

    enum Kind {
        /** A soft dot that shrinks away */
        SPARK,
        /** A four-pointed twinkle */
        STAR,
        /** A ring that grows and fades, a shock wave */
        RING,
        /** A tumbling scrap of colored paper that falls */
        CONFETTI
    }

    static final class Particle {
        Kind kind;
        float x, y, vx, vy, lastX, lastY;
        float gravity, drag = 0.92f;
        float size, grow;
        float angle, spin;
        int color, age, life;
    }

    /** Sparks and the like, moved on each tick and drawn between ticks. */
    static final class Particles {
        private final List<Particle> list = new ArrayList<>();
        final RandomSource random = RandomSource.create();

        Particle add(Kind kind, float x, float y, float vx, float vy, float size, int color, int life) {
            Particle p = new Particle();
            p.kind = kind;
            p.x = p.lastX = x;
            p.y = p.lastY = y;
            p.vx = vx;
            p.vy = vy;
            p.size = size;
            p.color = color;
            p.life = life;
            p.angle = random.nextFloat() * Mth.TWO_PI;
            list.add(p);
            return p;
        }

        /** {@code count} sparks and twinkles flying out from {@code x, y} as fast as {@code speed}. */
        void burst(float x, float y, int count, float speed, int[] colors) {
            for (int i = 0; i < count; i++) {
                float a = random.nextFloat() * Mth.TWO_PI;
                float v = speed * (0.3f + random.nextFloat() * 0.7f);
                Kind kind = random.nextInt(3) == 0 ? Kind.STAR : Kind.SPARK;
                Particle p = add(kind, x, y, Mth.cos(a) * v, Mth.sin(a) * v, 2 + random.nextFloat() * 3,
                        colors[random.nextInt(colors.length)], 14 + random.nextInt(16));
                p.gravity = 0.08f;
            }
        }

        /** Paper scraps raining from above {@code x}, over {@code spread} to each side. */
        void confetti(float x, float y, int count, float spread, int[] colors) {
            for (int i = 0; i < count; i++) {
                Particle p = add(Kind.CONFETTI, x + (random.nextFloat() * 2 - 1) * spread, y,
                        (random.nextFloat() * 2 - 1) * 4, -2 - random.nextFloat() * 7, 2 + random.nextFloat() * 1.5f,
                        colors[random.nextInt(colors.length)], 40 + random.nextInt(30));
                p.gravity = 0.25f;
                p.drag = 0.95f;
                p.spin = (random.nextFloat() * 2 - 1) * 0.5f;
            }
        }

        void ring(float x, float y, float size, float grow, int color, int life) {
            Particle p = add(Kind.RING, x, y, 0, 0, size, color, life);
            p.grow = grow;
            p.drag = 1;
        }

        void tick() {
            for (Iterator<Particle> it = list.iterator(); it.hasNext(); ) {
                Particle p = it.next();
                p.lastX = p.x;
                p.lastY = p.y;
                p.x += p.vx;
                p.y += p.vy;
                p.vx *= p.drag;
                p.vy = p.vy * p.drag + p.gravity;
                p.size += p.grow;
                p.angle += p.spin;
                if (++p.age >= p.life) {
                    it.remove();
                }
            }
        }

        void clear() {
            list.clear();
        }

        void render(Matrix4f m, float partialTick) {
            if (list.isEmpty()) {
                return;
            }
            for (ResourceLocation texture : List.of(GLOW, RING, SQUARE)) {
                BufferBuilder b = begin(texture);
                for (Particle p : list) {
                    float x = Mth.lerp(partialTick, p.lastX, p.x);
                    float y = Mth.lerp(partialTick, p.lastY, p.y);
                    float life = (p.age + partialTick) / p.life;
                    switch (p.kind) {
                        case SPARK -> {
                            if (texture == GLOW) {
                                float s = p.size * (1 - life);
                                quad(b, m, x, y, s * 2, s * 2, 0, fade(p.color, 1 - life * life));
                                quad(b, m, x, y, s * 0.7f, s * 0.7f, 0, fade(0xFFFFFFFF, 1 - life));
                            }
                        }
                        case STAR -> {
                            if (texture == GLOW) {
                                float s = p.size * 2.5f * Mth.sin(Mth.PI * Math.min(1, life * 1.4f));
                                int c = fade(p.color, 1 - life);
                                quad(b, m, x, y, s * 2, s * 0.25f, p.angle, c);
                                quad(b, m, x, y, s * 2, s * 0.25f, p.angle + Mth.HALF_PI, c);
                                quad(b, m, x, y, s * 0.5f, s * 0.5f, 0, fade(0xFFFFFFFF, 1 - life));
                            }
                        }
                        case RING -> {
                            if (texture == RING) {
                                quad(b, m, x, y, p.size, p.size, 0, fade(p.color, (1 - life) * (1 - life)));
                            }
                        }
                        case CONFETTI -> {
                            if (texture == SQUARE) {
                                // Turning over as it falls: its width swings, like paper catching the air.
                                float w = p.size * (0.3f + 0.7f * Math.abs(Mth.cos(p.angle * 2)));
                                quad(b, m, x, y, w, p.size * 0.6f, p.angle, fade(p.color, Math.min(1, (1 - life) * 3)));
                            }
                        }
                    }
                }
                end(b);
            }
        }
    }
}
