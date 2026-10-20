package io.github.zancrow321.jadm.client.display;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.JadmClientConfig;
import io.github.zancrow321.jadm.client.ProductArt;
import io.github.zancrow321.jadm.display.DisplayBlock;
import io.github.zancrow321.jadm.display.DisplayBlockEntity;
import io.github.zancrow321.jadm.display.DisplayGeometry;
import io.github.zancrow321.jadm.display.DisplayKind;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.github.zancrow321.jadm.item.JadmItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws what a collector display holds: cards as the real card with their foil, products as the real product, and
 * the glass in front. Shiny cards glow around their edge and sparkle, the rarer the more; Secret Rares and up send a
 * beam of light upward. A name plate shows the item's name (or the display's own, from an anvil); in the showcase the
 * name of the card being looked at floats above it.
 */
public final class DisplayRenderer implements BlockEntityRenderer<DisplayBlockEntity> {
    private static final ResourceLocation GLOW = texture("glow");
    private static final ResourceLocation GLASS = texture("glass");
    private static final ResourceLocation TOPLOADER = texture("toploader");
    private static final ResourceLocation BEAM = ResourceLocation.withDefaultNamespace(
            "textures/entity/beacon_beam.png");
    /** A card's width against its height. */
    private static final float CARD_ASPECT = 0.68f;
    private static final float PX = 1 / 16f;
    private static final int PLATE_TEXT = 0xFF3A2A06;
    /** How high the beam of a Secret Rare goes, in blocks. */
    private static final int BEAM_HEIGHT = 2;

    /** A beam to draw, from {@code start} (from the block's corner). */
    private record Beam(Vector3f start, int color) {
    }

    public DisplayRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "textures/display/" + name + ".png");
    }

    @Override
    public void render(DisplayBlockEntity display, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int light, int overlay) {
        BlockState state = display.getBlockState();
        if (!(state.getBlock() instanceof DisplayBlock)) {
            return;
        }
        DisplayKind kind = display.kind();
        Level level = display.getLevel();
        boolean lit = state.getValue(DisplayBlock.LIT);
        int itemLight = lit ? LightTexture.FULL_BRIGHT : light;
        boolean effects = JadmClientConfig.DISPLAY_EFFECTS.get();
        boolean sparkle = effects && level != null && level.getGameTime() != display.sparkled;
        if (sparkle) {
            display.sparkled = level.getGameTime();
        }
        boolean lying = kind.wall() && state.getValue(DisplayBlock.FACE) == AttachFace.FLOOR;
        Quaternionf rotation = DisplayGeometry.rotation(state);
        List<DisplayGeometry.Place> places = DisplayGeometry.places(kind);
        float time = level == null ? 0 : (level.getGameTime() + partialTick) / 20f;
        List<Beam> beams = new ArrayList<>();

        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(rotation);
        for (int slot = 0; slot < places.size(); slot++) {
            ItemStack stack = display.items().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            DisplayGeometry.Place place = places.get(slot);
            poses.pushPose();
            poses.translate(place.x(), place.y(), place.z());
            poses.mulPose(Axis.XN.rotationDegrees(place.tilt()));
            if (stack.is(JadmItems.CARD.get())) {
                Matrix4f card = new Matrix4f().translate(0.5f, 0.5f, 0.5f).rotate(rotation)
                        .translate(place.x(), place.y(), place.z()).rotateX((float) Math.toRadians(-place.tilt()))
                        .translate(0, place.height() / 2, 0);
                if (lit) {
                    backlight(poses, buffers, place.height() * CARD_ASPECT, place.height(), overlay);
                }
                drawCard(stack, place, kind, poses, buffers, itemLight, overlay, effects, time);
                if (effects) {
                    int tier = tier(stack);
                    if (sparkle) {
                        sparkle(level, display.getBlockPos(), card, place.height(), tier);
                    }
                    if (tier >= 4) {
                        Vector3f start = card.transformPosition(new Vector3f(0, lying ? 0 : place.height() / 2, 0));
                        beams.add(new Beam(start, color(stack)));
                    }
                }
            } else {
                if (lit) {
                    backlight(poses, buffers, place.width(), place.height(), overlay);
                }
                drawProduct(stack, place, poses, buffers, itemLight, overlay);
            }
            poses.popPose();
        }
        drawGlass(kind, poses, buffers, light, overlay);
        drawPlate(display, kind, poses, buffers, light);
        poses.popPose();
        for (Beam beam : beams) {
            beam(poses, buffers, beam.start(), beam.color(), partialTick, level.getGameTime());
        }
        if (kind == DisplayKind.SHOWCASE) {
            drawLookedAt(display, rotation, places, poses, buffers);
        }
    }

    /** With the light on, a warm glow behind an item, as if a little lamp lit it from behind. */
    private static void backlight(PoseStack poses, MultiBufferSource buffers, float width, float height,
                                  int overlay) {
        poses.pushPose();
        poses.translate(0, height / 2, -0.004f);
        quad(buffers.getBuffer(RenderType.eyes(GLOW)), poses, width * 0.8f, height * 0.68f, 0, 0xFF6E5A3C,
                LightTexture.FULL_BRIGHT, overlay);
        poses.popPose();
    }

    /** A card standing in its place, from its bottom edge up, with its halo (and toploader). */
    private static void drawCard(ItemStack stack, DisplayGeometry.Place place, DisplayKind kind, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay, boolean effects, float time) {
        float h = place.height();
        float w = h * CARD_ASPECT;
        poses.pushPose();
        poses.translate(0, h / 2, 0);
        int tier = tier(stack);
        if (effects && tier >= 2) {
            // A halo just behind the card, pulsing softly; only its rim shows around the card.
            float pulse = 0.95f + 0.3f * Mth.sin(time * 2.4f + place.x() * 9);
            float grow = 1.2f + 0.05f * tier;
            quad(buffers.getBuffer(RenderType.eyes(GLOW)), poses, w / 2 * grow, h / 2 * (1 + (grow - 1) * 0.7f),
                    -0.003f, scale(color(stack), pulse * (tier >= 4 ? 1.2f : 1)), LightTexture.FULL_BRIGHT,
                    overlay);
        }
        poses.pushPose();
        poses.scale(h, h, h);
        poses.translate(-0.5, -0.5, -0.5);
        IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(stack, ItemDisplayContext.FIXED, poses,
                buffers, light, overlay);
        poses.popPose();
        if (kind == DisplayKind.TOPLOADER_STAND) {
            VertexConsumer plastic = buffers.getBuffer(RenderType.entityTranslucent(TOPLOADER));
            quad(plastic, poses, w / 2 * 1.12f, h / 2 * 1.06f, 0.014f, 0xFFFFFFFF, light, overlay);
            quadBack(plastic, poses, w / 2 * 1.12f, h / 2 * 1.06f, -0.014f, 0xFFFFFFFF, light, overlay);
        }
        poses.popPose();
    }

    /** A sealed product standing in its place, as big as fits. */
    private static void drawProduct(ItemStack stack, DisplayGeometry.Place place, PoseStack poses,
                                    MultiBufferSource buffers, int light, int overlay) {
        String id = stack.get(JadmComponents.PACK_SET.get());
        Products.Product product = id == null ? null : JadmData.products().get(id);
        ProductArt.Art art = product == null ? null : ProductArt.get(product.code());
        float size = art == null ? 1 : stack.is(JadmItems.STRUCTURE_DECK.get()) ? 0.8f
                : stack.is(JadmItems.TIN.get()) ? 0.85f : 1;
        float aspect = art == null ? 1 : art.aspect();
        float height = (aspect < 1 ? 1 : 1 / aspect) * size;
        float width = (aspect < 1 ? aspect : 1) * size;
        float s = Math.min(place.height() / height, place.width() / width);
        poses.pushPose();
        poses.translate(0, s * height / 2, 0.04f);
        poses.scale(s, s, s);
        poses.translate(-0.5, -0.5, -0.5);
        IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(stack, ItemDisplayContext.FIXED, poses,
                buffers, light, overlay);
        poses.popPose();
    }

    /** The glass in front of a frame's or showcase's cards. */
    private static void drawGlass(DisplayKind kind, PoseStack poses, MultiBufferSource buffers, int light,
                                  int overlay) {
        float left, right, bottom, top, z;
        switch (kind) {
            case CARD_FRAME -> {
                left = 3;
                right = 13;
                bottom = 2;
                top = 14;
                z = 1.9f;
            }
            case SHOWCASE -> {
                left = 1;
                right = 15;
                bottom = 1;
                top = 15;
                z = 2.9f;
            }
            default -> {
                return;
            }
        }
        poses.pushPose();
        poses.translate(((left + right) / 2 - 8) * PX, ((bottom + top) / 2 - 8) * PX, (z - 8) * PX);
        // Drawn as a faint reflection added on top, so it hides nothing behind it.
        quad(buffers.getBuffer(RenderType.eyes(GLASS)), poses, (right - left) / 2 * PX, (top - bottom) / 2 * PX, 0,
                0xFFFFFFFF, LightTexture.FULL_BRIGHT, overlay);
        poses.popPose();
    }

    /** The name plate: the display's own name, else what it holds (the first product of a pack display). */
    private static void drawPlate(DisplayBlockEntity display, DisplayKind kind, PoseStack poses,
                                  MultiBufferSource buffers, int light) {
        float x, y, z, width;
        switch (kind) {
            case CARD_FRAME -> {
                x = 8;
                y = 3;
                z = 1.5f;
                width = 5.4f;
            }
            case TOPLOADER_STAND -> {
                x = 8;
                y = 1;
                z = 11.3f;
                width = 7.4f;
            }
            case PACK_DISPLAY -> {
                x = 8;
                y = 2;
                z = 13.1f;
                width = 11.4f;
            }
            default -> {
                return;
            }
        }
        Component text = display.customName();
        if (text == null) {
            for (int slot = 0; slot < display.items().getContainerSize() && text == null; slot++) {
                ItemStack stack = display.items().getItem(slot);
                if (!stack.isEmpty()) {
                    text = name(stack);
                }
            }
        }
        if (text == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int textWidth = Math.max(1, font.width(text));
        float scale = Math.min(width * PX / textWidth, 1.3f * PX / font.lineHeight);
        poses.pushPose();
        poses.translate((x - 8) * PX, (y - 8) * PX, (z - 8) * PX);
        poses.scale(scale, -scale, scale);
        font.drawInBatch(text, -textWidth / 2f, -font.lineHeight / 2f + 1, PLATE_TEXT, false, poses.last().pose(),
                buffers, Font.DisplayMode.NORMAL, 0, light);
        poses.popPose();
    }

    /** The name of the showcase's card under the crosshair, floating above the showcase. */
    private static void drawLookedAt(DisplayBlockEntity display, Quaternionf rotation,
                                     List<DisplayGeometry.Place> places, PoseStack poses, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                || !blockHit.getBlockPos().equals(display.getBlockPos())) {
            return;
        }
        Vec3 at = hit.getLocation().subtract(Vec3.atCenterOf(display.getBlockPos()));
        Vector3f local = new Quaternionf(rotation).conjugate().transform(new Vector3f((float) at.x, (float) at.y,
                (float) at.z));
        for (int slot = 0; slot < places.size(); slot++) {
            DisplayGeometry.Place place = places.get(slot);
            ItemStack stack = display.items().getItem(slot);
            float halfWidth = place.height() * CARD_ASPECT / 2 + PX / 2;
            if (stack.isEmpty() || Math.abs(local.x - place.x()) > halfWidth
                    || local.y < place.y() - PX / 2 || local.y > place.y() + place.height() + PX / 2) {
                continue;
            }
            // In front of the glass, so the case doesn't hide it.
            Vector3f top = rotation.transform(new Vector3f(place.x(), place.y() + place.height(), place.z() + 0.3f));
            Component name = name(stack);
            Font font = mc.font;
            poses.pushPose();
            poses.translate(0.5 + top.x, 0.5 + Math.max(top.y, 0.5f) + 0.4, 0.5 + top.z);
            poses.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            poses.scale(0.02f, -0.02f, 0.02f);
            float x = -font.width(name) / 2f;
            int background = (int) (mc.options.getBackgroundOpacity(0.25f) * 255) << 24;
            font.drawInBatch(name, x, 0, 0xFFFFFFFF, false, poses.last().pose(), buffers, Font.DisplayMode.NORMAL,
                    background, LightTexture.FULL_BRIGHT);
            Component rarity = Component.literal(CardItem.rarityName(CardItem.rarity(stack)))
                    .withStyle(CardItem.color(CardItem.rarity(stack)));
            font.drawInBatch(rarity, -font.width(rarity) / 2f, font.lineHeight + 1, 0xFFFFFFFF, false,
                    poses.last().pose(), buffers, Font.DisplayMode.NORMAL, background, LightTexture.FULL_BRIGHT);
            poses.popPose();
            return;
        }
    }

    private static Component name(ItemStack stack) {
        if (stack.is(JadmItems.CARD.get())) {
            return Component.literal(JadmData.cards().name(CardItem.code(stack)));
        }
        return stack.getHoverName();
    }

    /**
     * How shiny a card is: 0 Common, 1 Rare, 2 Super, 3 Ultra and the gold foils, 4 Secret and up. Read from the
     * rarity's color, which follows its pack slot.
     */
    private static int tier(ItemStack stack) {
        return switch (CardItem.color(CardItem.rarity(stack))) {
            case AQUA -> 1;
            case GOLD -> 2;
            case LIGHT_PURPLE, YELLOW -> 3;
            case RED -> 4;
            default -> 0;
        };
    }

    /** The rarity's color, for the halo, the sparkles and the beam. */
    private static int color(ItemStack stack) {
        Integer color = CardItem.color(CardItem.rarity(stack)).getColor();
        return color == null ? 0xFFFFFF : color;
    }

    private static int scale(int rgb, float factor) {
        int r = Math.min(255, (int) ((rgb >> 16 & 0xFF) * factor));
        int g = Math.min(255, (int) ((rgb >> 8 & 0xFF) * factor));
        int b = Math.min(255, (int) ((rgb & 0xFF) * factor));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Now and then a sparkle somewhere on the card, more often the rarer it is. */
    private static void sparkle(Level level, BlockPos pos, Matrix4f card, float height, int tier) {
        if (tier == 0) {
            return;
        }
        RandomSource random = level.getRandom();
        float chance = switch (tier) {
            case 1 -> 0.05f;
            case 2 -> 0.10f;
            case 3 -> 0.18f;
            default -> 0.32f;
        };
        // Smaller cards (the showcase's) sparkle less and smaller, so the sparkles don't hide them.
        float size = height / 0.6f;
        if (random.nextFloat() >= chance * Math.min(1, size)) {
            return;
        }
        float halfWidth = height * CARD_ASPECT / 2;
        Vector3f at = card.transformPosition(new Vector3f((random.nextFloat() * 2 - 1) * halfWidth * 0.95f,
                (random.nextFloat() * 2 - 1) * height / 2 * 0.95f, 0.02f));
        Vector3f out = card.transformDirection(new Vector3f(0, 0, 1)).normalize(0.004f);
        double x = pos.getX() + at.x;
        double y = pos.getY() + at.y;
        double z = pos.getZ() + at.z;
        // Half the sparkles white, like light catching the foil, half in the rarity's color.
        int c = tier >= 2 && random.nextBoolean() ? 0xFFFFFF : colorOfTier(tier);
        level.addParticle(new DustParticleOptions(new Vector3f((c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f,
                (c & 0xFF) / 255f), (0.15f + 0.05f * tier) * size), x, y, z, 0, 0, 0);
        if (tier >= 4 && size > 0.8f && random.nextFloat() < 0.12f) {
            level.addParticle(ParticleTypes.END_ROD, x, y, z, out.x, 0.004, out.z);
        }
    }

    private static int colorOfTier(int tier) {
        return switch (tier) {
            case 1 -> 0x7FE8FF;
            case 2 -> 0xFFD34D;
            case 3 -> 0xF07CFF;
            default -> 0xFF5A5A;
        };
    }

    /** A thin beam of light in the rarity's color, straight up from {@code start} (from the block's corner). */
    private static void beam(PoseStack poses, MultiBufferSource buffers, Vector3f start, int color,
                             float partialTick, long gameTime) {
        poses.pushPose();
        // The beam is drawn around the middle of its block; move that middle to the start.
        poses.translate(start.x - 0.5, start.y, start.z - 0.5);
        BeaconRenderer.renderBeaconBeam(poses, buffers, BEAM, partialTick, 1, gameTime, 0, BEAM_HEIGHT,
                0xFF000000 | color, 0.025f, 0.06f);
        poses.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack poses, float halfWidth, float halfHeight, float z,
                             int color, int light, int overlay) {
        Matrix4f m = poses.last().pose();
        vertex(vc, poses, m, -halfWidth, -halfHeight, z, 0, 1, 1, color, light, overlay);
        vertex(vc, poses, m, halfWidth, -halfHeight, z, 1, 1, 1, color, light, overlay);
        vertex(vc, poses, m, halfWidth, halfHeight, z, 1, 0, 1, color, light, overlay);
        vertex(vc, poses, m, -halfWidth, halfHeight, z, 0, 0, 1, color, light, overlay);
    }

    private static void quadBack(VertexConsumer vc, PoseStack poses, float halfWidth, float halfHeight, float z,
                                 int color, int light, int overlay) {
        Matrix4f m = poses.last().pose();
        vertex(vc, poses, m, halfWidth, -halfHeight, z, 0, 1, -1, color, light, overlay);
        vertex(vc, poses, m, -halfWidth, -halfHeight, z, 1, 1, -1, color, light, overlay);
        vertex(vc, poses, m, -halfWidth, halfHeight, z, 1, 0, -1, color, light, overlay);
        vertex(vc, poses, m, halfWidth, halfHeight, z, 0, 0, -1, color, light, overlay);
    }

    private static void vertex(VertexConsumer vc, PoseStack poses, Matrix4f m, float x, float y, float z, float u,
                               float v, float nz, int color, int light, int overlay) {
        vc.addVertex(m, x, y, z).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(poses.last(), 0, 0, nz);
    }

    @Override
    public AABB getRenderBoundingBox(DisplayBlockEntity display) {
        return new AABB(display.getBlockPos()).inflate(0.25).expandTowards(0, BEAM_HEIGHT + 0.5, 0);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
