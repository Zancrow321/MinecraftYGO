package io.github.zancrow321.jadm.client.starchips;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.client.field.ClientField;
import io.github.zancrow321.jadm.network.StarChipsPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Duelist Glove in the top left corner while you are in a Star Chip event: one socket per chip you need, filled
 * with the chips you have. A chip won pops in, a chip lost flashes red as it goes. Hidden at a duel.
 */
public final class StarChipHud {
    private static final ResourceLocation CHIP = ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID,
            "textures/gui/star_chip.png");
    private static final ResourceLocation SOCKET = ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID,
            "textures/gui/star_chip_socket.png");
    private static final int COLUMNS = 5;
    private static final int CELL = 13;
    /** Goals above this show the count only. */
    private static final int MAX_SOCKETS = 20;
    private static final int LEATHER = 0xE0301C10;
    private static final int STITCH = 0xFF8A6A3A;
    private static final int GOLD = 0xFFFFD040;
    private static final long CHANGE_MILLIS = 1500;

    private static StarChipsPayload state = StarChipsPayload.none();
    private static int before;
    private static long changedAt;

    private StarChipHud() {
    }

    public static void receive(StarChipsPayload payload) {
        if (!payload.status().isEmpty() && payload.event().equals(state.event()) && payload.chips() != state.chips()) {
            before = state.chips();
            changedAt = System.currentTimeMillis();
        }
        state = payload;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        StarChipsPayload s = state;
        if (s.status().isEmpty() || s.status().equals("left") || mc.options.hideGui || ClientField.active()) {
            return;
        }
        Font font = mc.font;
        int sockets = s.goal() <= MAX_SOCKETS ? s.goal() : 0;
        int rows = (sockets + COLUMNS - 1) / COLUMNS;
        Component title = Component.translatable("hud.jadm.star_chips.title");
        String count = s.chips() + "/" + s.goal();
        Component status = switch (s.status()) {
            case "qualified" -> Component.translatable("hud.jadm.star_chips.qualified");
            case "out" -> Component.translatable("hud.jadm.star_chips.out");
            default -> null;
        };
        int width = Math.max(COLUMNS * CELL + 8, font.width(title) + font.width(count) + 14);
        if (status != null) {
            width = Math.max(width, font.width(status) + 8);
        }
        int height = 14 + rows * CELL + (status != null ? 11 : 0) + 3;
        int x = 6;
        int y = 6;
        // The glove: leather with a stitched edge and a cuff on the left.
        g.fill(x, y, x + width, y + height, LEATHER);
        g.renderOutline(x + 1, y + 1, width - 2, height - 2, STITCH);
        g.fill(x, y, x + 3, y + height, 0xFF5A3418);
        g.drawString(font, title, x + 5, y + 4, GOLD);
        g.drawString(font, count, x + width - 4 - font.width(count), y + 4,
                s.chips() >= s.goal() ? 0xFF7CFC9A : 0xFFFFFFFF);
        long since = System.currentTimeMillis() - changedAt;
        float change = since < CHANGE_MILLIS ? 1 - since / (float) CHANGE_MILLIS : 0;
        int gridX = x + (width - COLUMNS * CELL) / 2 + 1;
        int gridY = y + 15;
        for (int i = 0; i < sockets; i++) {
            int cx = gridX + i % COLUMNS * CELL;
            int cy = gridY + i / COLUMNS * CELL;
            g.blit(SOCKET, cx, cy, 12, 12, 0, 0, 12, 12, 12, 12);
            boolean has = i < s.chips();
            boolean won = has && i >= before && change > 0;
            boolean lost = !has && i < before && change > 0;
            if (has || lost) {
                g.pose().pushPose();
                float scale = won ? 1 + 0.6f * change * Mth.sin(change * Mth.PI) : 1;
                g.pose().translate(cx + 6, cy + 6, 0);
                g.pose().scale(scale, scale, 1);
                if (lost) {
                    g.setColor(1, 0.25f, 0.25f, change);
                }
                g.blit(CHIP, -6, -6, 12, 12, 0, 0, 12, 12, 12, 12);
                g.setColor(1, 1, 1, 1);
                g.pose().popPose();
            }
        }
        if (status != null) {
            g.drawString(font, status, x + 5, gridY + rows * CELL + 1,
                    s.status().equals("qualified") ? 0xFF7CFC9A : 0xFFFF6060);
        }
    }
}
