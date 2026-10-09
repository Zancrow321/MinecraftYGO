package io.github.zancrow321.jadm.client.ranking;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.zancrow321.jadm.ranking.RankingBoard;
import io.github.zancrow321.jadm.ranking.RankingBoardBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws a Ranking Board: a glowing panel three blocks wide and two high, from the plate upward, with the ten best
 * ranked duelists, their rank and rating.
 */
public final class RankingBoardRenderer implements BlockEntityRenderer<RankingBoardBlockEntity> {
    /** The board, in blocks: centered on the plate, from its bottom up. */
    private static final float WIDTH = 3;
    private static final float HEIGHT = 2;
    /** Text units per block. */
    private static final float UNITS = 64;
    private static final int FRAME = 0xFFC9A227;
    private static final int PANEL = 0xF0101421;
    private static final int STRIPE = 0x40FFFFFF;
    private static final int GOLD = 0xFFFFD54F;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF9AA3B5;

    public RankingBoardRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RankingBoardBlockEntity board, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = board.getBlockState().getValue(RankingBoard.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // The plate's front face, a hair in front of it.
        poseStack.translate(0, 0, -0.5 + 2 / 16f + 0.01f);
        float left = -WIDTH / 2;
        VertexConsumer fill = buffers.getBuffer(RenderType.textBackground());
        Matrix4f pose = poseStack.last().pose();
        quad(fill, pose, left, 0, left + WIDTH, HEIGHT, 0, FRAME);
        quad(fill, pose, left + 0.06f, 0.06f, left + WIDTH - 0.06f, HEIGHT - 0.06f, 0.002f, PANEL);
        // A line under the heading.
        quad(fill, pose, left + 0.12f, HEIGHT - 0.30f, left + WIDTH - 0.12f, HEIGHT - 0.28f, 0.004f, STRIPE);

        poseStack.translate(left, HEIGHT, 0.006f);
        float s = 1 / UNITS;
        poseStack.scale(s, -s, s);
        drawText(board, poseStack, buffers);
        poseStack.popPose();
    }

    private static void drawText(RankingBoardBlockEntity board, PoseStack poseStack, MultiBufferSource buffers) {
        Font font = Minecraft.getInstance().font;
        Matrix4f pose = poseStack.last().pose();
        int width = (int) (WIDTH * UNITS);
        String title = "RANKING";
        draw(font, pose, buffers, title, 8, 7, GOLD);
        String season = "Season " + board.season();
        draw(font, pose, buffers, season, width - 8 - font.width(season), 7, GRAY);
        List<RankingBoardBlockEntity.Row> rows = board.rows();
        if (rows.isEmpty()) {
            String none = "No ranked duels yet";
            draw(font, pose, buffers, none, (width - font.width(none)) / 2, 60, GRAY);
            String how = "/jadm duel <player> ranked";
            draw(font, pose, buffers, how, (width - font.width(how)) / 2, 74, GOLD);
            return;
        }
        int y = 23;
        for (int i = 0; i < rows.size(); i++) {
            RankingBoardBlockEntity.Row row = rows.get(i);
            int color = 0xFF000000 | row.color();
            String place = (i + 1) + ".";
            draw(font, pose, buffers, place, 22 - font.width(place), y, i < 3 ? GOLD : GRAY);
            draw(font, pose, buffers, row.tier(), 28, y, color);
            String rating = String.valueOf(row.rating());
            int ratingX = width - 8 - font.width(rating);
            String name = font.plainSubstrByWidth(row.name(), ratingX - 82 - 4);
            draw(font, pose, buffers, name, 82, y, WHITE);
            draw(font, pose, buffers, rating, ratingX, y, color);
            y += 10;
        }
    }

    private static void draw(Font font, Matrix4f pose, MultiBufferSource buffers, String text, int x, int y,
                             int color) {
        font.drawInBatch(text, x, y, color, false, pose, buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                LightTexture.FULL_BRIGHT);
    }

    /** A flat rectangle facing the board's front, in blocks, fully lit. */
    private static void quad(VertexConsumer consumer, Matrix4f pose, float x0, float y0, float x1, float y1, float z,
                             int argb) {
        int light = LightTexture.FULL_BRIGHT;
        consumer.addVertex(pose, x0, y0, z).setColor(argb).setLight(light);
        consumer.addVertex(pose, x1, y0, z).setColor(argb).setLight(light);
        consumer.addVertex(pose, x1, y1, z).setColor(argb).setLight(light);
        consumer.addVertex(pose, x0, y1, z).setColor(argb).setLight(light);
    }

    @Override
    public AABB getRenderBoundingBox(RankingBoardBlockEntity board) {
        return new AABB(board.getBlockPos()).inflate(1.5, 0, 1.5).expandTowards(0, 1, 0);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
