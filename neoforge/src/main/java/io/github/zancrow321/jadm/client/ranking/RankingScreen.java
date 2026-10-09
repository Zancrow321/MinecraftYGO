package io.github.zancrow321.jadm.client.ranking;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.mojang.math.Axis;
import io.github.zancrow321.jadm.ranking.RankingView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The ranking window ({@code /jadm rank}, its key, or a Ranking Board): your rank, rating and the way to the next
 * rank on the left, the best duelists of the season on the right.
 */
public final class RankingScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int GOLD = 0xFFFFE070;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF9A9A9A;
    private static final int DARK = 0xFF5A5A5A;
    private static final int PANEL = 0xC0101018;
    private static final int BOX = 0xFF22222E;
    private static final int BOX_EDGE = 0xFF44445A;
    private static final int MINE = 0x40FFE070;
    private static final int ROW_H = 12;
    private static final int TOP = 34;
    private static final int LEFT_W = 170;

    private final RankingView view;
    private double scroll;

    private RankingScreen(RankingView view) {
        super(Component.literal("Ranking"));
        this.view = view;
    }

    /** The server sent the ranking: open the window. */
    public static void receive(String json) {
        try {
            RankingView view = GSON.fromJson(json, RankingView.class);
            if (view != null) {
                Minecraft.getInstance().setScreen(new RankingScreen(view));
            }
        } catch (JsonParseException ignored) {
            // A broken payload just doesn't open anything.
        }
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width - 70, height - 24, 60, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, PANEL);
        g.drawString(font, "Ranking", 10, 10, GOLD);
        g.drawString(font, "Season " + view.season, 10 + font.width("Ranking") + 8, 10, GRAY);
        if (!view.enabled) {
            String off = "Ranked duels are turned off on this server.";
            g.drawString(font, off, width - 10 - font.width(off), 10, 0xFFFF7070);
        }
        drawMe(g);
        drawTable(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The panel drawn in render is the background.
    }

    private void drawMe(GuiGraphics g) {
        RankingView.Row me = view.me;
        int x = 10;
        int y = TOP;
        int h = height - TOP - 32;
        box(g, x, y, LEFT_W, h);
        int color = 0xFF000000 | tierColor(me.tier);
        int cx = x + LEFT_W / 2;
        emblem(g, cx, y + 38, 20, color, me.tier);
        String tier = tierName(me.tier);
        g.pose().pushPose();
        g.pose().translate(cx, y + 72, 0);
        g.pose().scale(2, 2, 1);
        g.drawCenteredString(font, tier, 0, 0, color);
        g.pose().popPose();
        g.drawCenteredString(font, me.name, cx, y + 94, WHITE);
        g.drawCenteredString(font, me.rating + " rating", cx, y + 106, GOLD);
        String place = view.myPlace > 0 ? "Place " + view.myPlace + " of " + view.total : "Not ranked yet";
        g.drawCenteredString(font, place, cx, y + 118, GRAY);

        // The way to the next rank.
        int barY = y + 134;
        int barX = x + 12;
        int barW = LEFT_W - 24;
        g.fill(barX, barY, barX + barW, barY + 6, 0xFF0A0A10);
        int next = me.tier + 1;
        if (next < view.tierStarts.size() && view.tierStarts.get(next) != Integer.MAX_VALUE) {
            int from = view.tierStarts.get(me.tier);
            int to = view.tierStarts.get(next);
            float part = Mth.clamp((me.rating - from) / (float) Math.max(1, to - from), 0, 1);
            g.fill(barX, barY, barX + (int) (barW * part), barY + 6, color);
            g.drawCenteredString(font, (to - me.rating) + " to " + tierName(next), cx, barY + 10, GRAY);
        } else {
            g.fill(barX, barY, barX + barW, barY + 6, color);
            g.drawCenteredString(font, "Highest rank", cx, barY + 10, GRAY);
        }

        int ty = barY + 26;
        g.drawString(font, "Won " + me.wins + "  Lost " + me.losses + "  Drawn " + me.draws, x + 10, ty, WHITE);
        g.drawString(font, "Best rating: " + me.peak, x + 10, ty + 12, GRAY);

        // The ladder, highest rank on top.
        int ly = ty + 28;
        g.drawString(font, "Ranks", x + 10, ly, GOLD);
        ly += 12;
        for (int i = view.tierNames.size() - 1; i >= 0 && ly + 10 < y + h; i--) {
            int c = 0xFF000000 | tierColor(i);
            int start = view.tierStarts.get(i);
            if (i == me.tier) {
                g.fill(x + 6, ly - 2, x + LEFT_W - 6, ly + 9, MINE);
            }
            g.drawString(font, tierName(i), x + 10, ly, c);
            String from = start == Integer.MAX_VALUE ? "-" : "from " + start;
            g.drawString(font, from, x + LEFT_W - 10 - font.width(from), ly, GRAY);
            ly += 11;
        }
    }

    private void drawTable(GuiGraphics g) {
        int x = 10 + LEFT_W + 8;
        int y = TOP;
        int w = width - x - 10;
        int h = height - TOP - 32;
        box(g, x, y, w, h);
        int place = x + 8;
        int rank = x + 36;
        int name = x + 100;
        int record = x + w - 60;
        int rating = x + w - 110;
        g.drawString(font, "#", place, y + 6, GRAY);
        g.drawString(font, "Rank", rank, y + 6, GRAY);
        g.drawString(font, "Duelist", name, y + 6, GRAY);
        g.drawString(font, "Rating", rating, y + 6, GRAY);
        g.drawString(font, "W-L-D", record, y + 6, GRAY);
        g.fill(x + 4, y + 17, x + w - 4, y + 18, BOX_EDGE);
        if (view.rows.isEmpty()) {
            g.drawCenteredString(font, "Nobody has played a ranked duel this season.", x + w / 2, y + 40, GRAY);
            g.drawCenteredString(font, "Challenge someone with /jadm duel <player> ranked", x + w / 2, y + 54,
                    DARK);
            return;
        }
        int listTop = y + 22;
        int visible = (h - 26) / ROW_H;
        int maxScroll = Math.max(0, view.rows.size() - visible);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int first = (int) scroll;
        String me = Minecraft.getInstance().player == null ? ""
                : Minecraft.getInstance().player.getScoreboardName();
        g.enableScissor(x, listTop, x + w, listTop + visible * ROW_H);
        for (int i = first; i < Math.min(view.rows.size(), first + visible); i++) {
            RankingView.Row row = view.rows.get(i);
            int ry = listTop + (i - first) * ROW_H;
            if (row.name.equals(me)) {
                g.fill(x + 4, ry - 2, x + w - 4, ry + ROW_H - 2, MINE);
            }
            int c = 0xFF000000 | tierColor(row.tier);
            g.drawString(font, (i + 1) + ".", place, ry, i < 3 ? GOLD : WHITE);
            g.drawString(font, tierName(row.tier), rank, ry, c);
            g.drawString(font, font.plainSubstrByWidth(row.name, rating - name - 6), name, ry, WHITE);
            g.drawString(font, String.valueOf(row.rating), rating, ry, c);
            g.drawString(font, row.wins + "-" + row.losses + "-" + row.draws, record, ry, GRAY);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int trackH = visible * ROW_H;
            int thumbH = Math.max(10, trackH * visible / view.rows.size());
            int thumbY = listTop + (int) ((trackH - thumbH) * (scroll / maxScroll));
            g.fill(x + w - 4, thumbY, x + w - 2, thumbY + thumbH, BOX_EDGE);
        }
        if (view.total > view.rows.size()) {
            g.drawString(font, "Showing the best " + view.rows.size() + " of " + view.total, x + 8, y + h - 10,
                    DARK);
        }
    }

    /** A rank emblem: a diamond in the rank's color with its first letter. */
    private void emblem(GuiGraphics g, int cx, int cy, int r, int color, int tier) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        // Turned by 45 degrees, the square reaches r * 1.41 up and down.
        int side = r;
        g.fill(-side, -side, side, side, 0xFF0A0A10);
        g.fill(-side + 2, -side + 2, side - 2, side - 2, color);
        g.fill(-side + 6, -side + 6, side - 6, side - 6, 0x60000000);
        g.pose().popPose();
        String mark = String.valueOf(tierName(tier).charAt(0));
        g.pose().pushPose();
        g.pose().translate(cx, cy - 6, 0);
        g.pose().scale(2, 2, 1);
        g.drawCenteredString(font, mark, 0, 0, WHITE);
        g.pose().popPose();
    }

    private static void box(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BOX_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BOX);
    }

    private String tierName(int tier) {
        return view.tierNames.get(Mth.clamp(tier, 0, view.tierNames.size() - 1));
    }

    private int tierColor(int tier) {
        return view.tierColors.get(Mth.clamp(tier, 0, view.tierColors.size() - 1));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 3;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
