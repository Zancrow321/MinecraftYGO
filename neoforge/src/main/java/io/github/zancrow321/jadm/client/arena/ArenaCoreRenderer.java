package io.github.zancrow321.jadm.client.arena;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.zancrow321.jadm.arena.ArenaCoreBlockEntity;
import io.github.zancrow321.jadm.arena.BuiltArena;
import io.github.zancrow321.jadm.arena.DuelDome;
import io.github.zancrow321.jadm.client.field.FieldLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

import static io.github.zancrow321.jadm.engine.OcgConstants.LOCATION_MZONE;

/**
 * Draws what an Arena Core adds to the arena players built: the field's outline over the floor after the core
 * measured it (green when the arena can be used, red marks on the podiums when it can't), the podiums rising as
 * pillars during a duel, and the words over the podiums while someone waits for an opponent.
 */
public final class ArenaCoreRenderer implements BlockEntityRenderer<ArenaCoreBlockEntity> {
    public ArenaCoreRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ArenaCoreBlockEntity core, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BuiltArena.Survey survey = core.survey();
        if (core.getLevel() == null || survey == null) {
            return;
        }
        float time = core.getLevel().getGameTime() + partialTick;
        BlockPos origin = core.getBlockPos();
        if (core.previewUntil() > time && !core.raised()) {
            drawPreview(core, survey, time, poseStack, buffers, origin);
        }
        double lift = core.lift(time);
        if (lift > 0.01) {
            drawPillars(core, survey, lift, poseStack, buffers, origin);
        }
        if (!core.raised() && core.waiting() != 0 && survey.ok()) {
            drawLobby(core, survey, time, poseStack, buffers, origin);
        }
    }

    private static Vec3 local(BlockPos origin, Vec3 world) {
        return world.subtract(origin.getX(), origin.getY(), origin.getZ());
    }

    private static Vec3 top(BlockPos origin, BlockPos podium) {
        return local(origin, Vec3.atBottomCenterOf(podium.above()));
    }

    /** The mat's border and every zone, as the field will lie, and a mark over each podium. */
    private static void drawPreview(ArenaCoreBlockEntity core, BuiltArena.Survey survey, float time,
                                    PoseStack poseStack, MultiBufferSource buffers, BlockPos origin) {
        float fade = Mth.clamp((core.previewUntil() - time) / 20f, 0, 1);
        float pulse = 0.75f + 0.25f * Mth.sin(time / 5f);
        int alpha = (int) (0xD0 * fade * pulse);
        if (survey.ok() && survey.size() > 0) {
            VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
            Matrix4f pose = poseStack.last().pose();
            Vec3 forward = Vec3.directionFromRotation(0, survey.yaw()).scale(survey.size());
            Vec3 right = new Vec3(-forward.z, 0, forward.x);
            Vec3 c = local(origin, survey.center()).add(0, 0.03, 0);
            frame(vc, pose, c, right.scale(FieldLayout.HALF_WIDTH + 0.3),
                    forward.scale(FieldLayout.halfLength() + 0.3), 0.12, alpha << 24 | 0x60FF90);
            for (int[] zone : FieldLayout.ZONES) {
                FieldLayout.Slot s = FieldLayout.slot(zone[0], zone[1], zone[2]);
                Vec3 at = c.add(right.scale(s.x())).add(forward.scale(s.z()));
                int color = zone[1] == LOCATION_MZONE && zone[2] >= 5 ? 0xE8F0FF
                        : zone[0] == 0 ? 0xE040A0 : 0x38E0FF;
                frame(vc, pose, at, right.scale(FieldLayout.ZONE_WIDTH / 2 - 0.08),
                        forward.scale(FieldLayout.ZONE_DEPTH / 2 - 0.08), 0.07, alpha << 24 | color);
            }
        }
        // The +1 side's zones are drawn blue (player 1 sits at +z), the -1 side's pink.
        for (int end : new int[]{1, -1}) {
            for (BlockPos podium : survey.side(end)) {
                int color = !survey.ok() ? 0xFF6060 : end > 0 ? 0x38E0FF : 0xE040A0;
                float bob = Mth.sin(time * 0.15f) * 0.15f;
                ArenaRenderer.label(poseStack, buffers, top(origin, podium).add(0, 1.4 + bob, 0),
                        Component.literal("▼"), color, 3f, false);
            }
        }
        if (!survey.ok()) {
            Component problem = Component.translatable("screen.jadm.arena_core.problem." + survey.problems().get(0));
            ArenaRenderer.label(poseStack, buffers, new Vec3(0.5, 2.5, 0.5), problem, 0xFF8080, 2f, true);
        }
    }

    /** A flat frame around {@code c}, {@code u} and {@code v} being its half sides. */
    private static void frame(VertexConsumer vc, Matrix4f pose, Vec3 c, Vec3 u, Vec3 v, double t, int argb) {
        Vec3 un = u.normalize().scale(t);
        Vec3 vn = v.normalize().scale(t);
        quad(vc, pose, c.subtract(u).subtract(v), c.add(u).subtract(v), c.add(u).subtract(v).add(vn),
                c.subtract(u).subtract(v).add(vn), argb);
        quad(vc, pose, c.subtract(u).add(v).subtract(vn), c.add(u).add(v).subtract(vn), c.add(u).add(v),
                c.subtract(u).add(v), argb);
        quad(vc, pose, c.subtract(u).subtract(v), c.subtract(u).add(un).subtract(v), c.subtract(u).add(un).add(v),
                c.subtract(u).add(v), argb);
        quad(vc, pose, c.add(u).subtract(un).subtract(v), c.add(u).subtract(v), c.add(u).add(v),
                c.add(u).subtract(un).add(v), argb);
    }

    private static void quad(VertexConsumer vc, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        int alpha = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
        // Both ways round, so it shows from above and below.
        for (Vec3 v : new Vec3[]{a, b, c, d, d, c, b, a}) {
            vc.addVertex(pose, (float) v.x, (float) v.y, (float) v.z).setColor(r, g, bl, alpha);
        }
    }

    /** Each podium someone duels on, as a pillar that grows under its duelist. */
    private static void drawPillars(ArenaCoreBlockEntity core, BuiltArena.Survey survey, double lift,
                                    PoseStack poseStack, MultiBufferSource buffers, BlockPos origin) {
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var state = DuelDome.PLATFORM.get().defaultBlockState();
        for (BlockPos podium : core.riding()) {
            int light = LevelRenderer.getLightColor(core.getLevel(), podium.above());
            poseStack.pushPose();
            poseStack.translate(podium.getX() - origin.getX(), podium.getY() + 1 - origin.getY(),
                    podium.getZ() - origin.getZ());
            poseStack.scale(1, (float) lift, 1);
            dispatcher.renderSingleBlock(state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    /** Words over the podiums while someone waits, and the countdown once both sides are taken. */
    private static void drawLobby(ArenaCoreBlockEntity core, BuiltArena.Survey survey, float time,
                                  PoseStack poseStack, MultiBufferSource buffers, BlockPos origin) {
        if (core.startsAt() > 0) {
            int seconds = Math.max(1, Mth.ceil((core.startsAt() - time) / 20f));
            float into = 1 - ((core.startsAt() - time) / 20f - (seconds - 1));
            float scale = 5 + 3 * Math.max(0, 1 - into * 4);
            Vec3 middle = local(origin, survey.center());
            ArenaRenderer.label(poseStack, buffers, middle.add(0, 4.5, 0), Component.literal(String.valueOf(seconds)),
                    0xFFD54F, scale, false);
            ArenaRenderer.label(poseStack, buffers, middle.add(0, 2.8, 0),
                    Component.translatable("message.jadm.arena.get_ready"), 0xFFFFFF, 2.5f, true);
            return;
        }
        String dots = ".".repeat(1 + (int) (time / 10 % 3));
        for (int i = 0; i < 2; i++) {
            List<BlockPos> side = survey.side(i == 0 ? 1 : -1);
            for (BlockPos podium : side) {
                Vec3 at = top(origin, podium);
                if ((core.waiting() & 1 << i) != 0) {
                    ArenaRenderer.label(poseStack, buffers, at.add(0, 3, 0),
                            Component.translatable("message.jadm.arena.waiting", dots), 0xFFE066, 2f, true);
                } else {
                    float bob = Mth.sin(time * 0.15f) * 0.15f;
                    ArenaRenderer.label(poseStack, buffers, at.add(0, 2 + bob, 0),
                            Component.translatable("message.jadm.arena.step_up"), 0x7CFC9A, 2f, true);
                    ArenaRenderer.label(poseStack, buffers, at.add(0, 1.3 + bob, 0), Component.literal("▼"),
                            0x7CFC9A, 3f, false);
                }
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(ArenaCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(BuiltArena.SEARCH_RENDER, 10, BuiltArena.SEARCH_RENDER);
    }

    @Override
    public boolean shouldRenderOffScreen(ArenaCoreBlockEntity core) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
