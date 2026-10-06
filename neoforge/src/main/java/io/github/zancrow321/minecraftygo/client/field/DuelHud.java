package io.github.zancrow321.minecraftygo.client.field;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.client.ClientDuel;
import io.github.zancrow321.minecraftygo.client.duel.DuelMode;
import io.github.zancrow321.minecraftygo.client.duel.DuelStaging;
import io.github.zancrow321.minecraftygo.client.duel.DuelUi;
import io.github.zancrow321.minecraftygo.engine.duel.Board;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.engine.duel.FieldEvent;
import io.github.zancrow321.minecraftygo.engine.text.PromptView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The in-world duel overlay: life points, turn and phase, the prompt waiting for you, and floating life point changes.
 * Your hand, the card panel and everything you click are drawn by {@link DuelUi}.
 */
public final class DuelHud {
    private static final int GOLD = 0xFFFFD040;
    private static final int DIM = 0xFFB0C4D8;
    private static final int PANEL = 0xA0081828;
    /** Right edge of the life panels, plus a gap. */
    private static final int LIFE_PANEL_RIGHT = 108;
    private static final int PROMPT_HALF_WIDTH = 160;
    private static final int MIN_PROMPT_HALF_WIDTH = 96;

    private DuelHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientField.active() || mc.options.hideGui || !DuelMode.showsHud(mc.screen)) {
            return;
        }
        DuelView view = ClientDuel.view();
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        int me = view.you();
        int opp = 1 - me;
        Board board = view.board();
        float partial = delta.getGameTimeDeltaPartialTick(false);

        // Life points: opponent top-left, you bottom-left.
        lifePanel(g, font, 6, 6, view.names().get(opp), board.side(opp), false);
        lifePanel(g, font, 6, h - 70, view.names().get(me), board.side(me), true);
        damagePopups(g, font, me, h, partial);

        // Turn and phase, top centre, between the life panel and the right edge.
        String phase = "Turn " + board.turn() + "  ·  " + YgoData.text().phase(board.phase())
                + (board.turnPlayer() == me ? "  (your turn)" : "");
        g.drawCenteredString(font, font.plainSubstrByWidth(phase, w - 2 * LIFE_PANEL_RIGHT), w / 2, 6, DIM);

        int half = Math.min(PROMPT_HALF_WIDTH, w / 2 - LIFE_PANEL_RIGHT);
        int top = promptTop(w);
        if (top != 18) {
            half = Math.min(PROMPT_HALF_WIDTH, w / 2 - 6);
        }
        PromptView prompt = ClientDuel.prompt();
        if (view.result() != null || DuelStaging.introRunning()) {
            return; // the start and the result screen speak for themselves
        }
        if (prompt != null) {
            g.fill(w / 2 - half, top, w / 2 + half, top + 26, PANEL);
            g.drawCenteredString(font, font.plainSubstrByWidth(prompt.title(), 2 * half - 8), w / 2, top + 4, GOLD);
            String help = DuelUi.help(prompt);
            if (font.width(help) > 2 * half - 8) {
                help = DuelUi.shortHelp(prompt);
            }
            g.drawCenteredString(font, help, w / 2, top + 15, DIM);
        } else {
            g.drawCenteredString(font, font.plainSubstrByWidth("Waiting for " + view.names().get(opp) + "...",
                    2 * half), w / 2, top + 4, DIM);
        }
    }

    /** The prompt sits next to the opponent's life panel, or below it when the screen is too narrow for that. */
    private static int promptTop(int w) {
        return Math.min(PROMPT_HALF_WIDTH, w / 2 - LIFE_PANEL_RIGHT) < MIN_PROMPT_HALF_WIDTH ? 50 : 18;
    }

    /** @return the first line below the prompt bar, where windows may start */
    public static int promptBottom(int w) {
        return promptTop(w) + 30;
    }

    /** A tag team's name ("Alex & Steve") goes on two lines so it fits the panel. */
    private static void lifePanel(GuiGraphics g, Font font, int x, int y, String name, Board.Side side, boolean own) {
        String[] names = name.split(" & ");
        int extra = (names.length - 1) * 11;
        g.fill(x, y - (own ? extra : 0), x + 96, y + 38 + (own ? 0 : extra), PANEL);
        int top = own ? y - extra : y;
        for (int i = 0; i < names.length; i++) {
            g.drawString(font, font.plainSubstrByWidth(names[i] + (i < names.length - 1 ? " &" : ""), 88), x + 4,
                    top + 3 + i * 11, own ? 0xFF88D8FF : 0xFFFF9090);
        }
        g.drawString(font, "LP " + side.lifePoints(), x + 4, top + 14 + extra, GOLD);
        g.drawString(font, "Hand " + side.hand().size() + "  Deck " + side.deckCount(), x + 4, top + 26 + extra, DIM);
    }

    private static void damagePopups(GuiGraphics g, Font font, int me, int h, float partial) {
        long now = ClientField.tick();
        for (FieldAnimation a : ClientField.animations()) {
            FieldEvent e = a.event();
            if ((e.kind() != FieldEvent.Kind.DAMAGE && e.kind() != FieldEvent.Kind.RECOVER) || !a.started(now)) {
                continue;
            }
            float p = a.progress(now, partial);
            int y = (e.player() == me ? h - 70 : 6) + 14 - (int) (p * 14);
            int alpha = (int) (255 * (1 - p * p));
            if (alpha < 8) {
                continue;
            }
            boolean damage = e.kind() == FieldEvent.Kind.DAMAGE;
            String text = (damage ? "-" : "+") + e.amount();
            g.drawString(font, text, 62, y, (alpha << 24) | (damage ? 0xFF4040 : 0x40FF80));
        }
    }
}
