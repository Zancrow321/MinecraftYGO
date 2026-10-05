package io.github.zancrow321.minecraftygo.client.field;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.client.CardArt;
import io.github.zancrow321.minecraftygo.client.ClientDuel;
import io.github.zancrow321.minecraftygo.engine.data.CardInfo;
import io.github.zancrow321.minecraftygo.engine.duel.Board;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.engine.duel.FieldEvent;
import io.github.zancrow321.minecraftygo.engine.protocol.CardState;
import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import io.github.zancrow321.minecraftygo.engine.text.PromptView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * The in-world duel overlay: life points, turn and phase, the prompt waiting for you, your hand, the card under the
 * crosshair, and floating life point changes.
 */
public final class DuelHud {
    private static final int GOLD = 0xFFFFD040;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFB0C4D8;
    private static final int PANEL = 0xA0081828;
    /** Right edge of the life panels, plus a gap. */
    private static final int LIFE_PANEL_RIGHT = 108;
    private static final int PROMPT_HALF_WIDTH = 160;
    private static final int MIN_PROMPT_HALF_WIDTH = 110;
    private static final ResourceLocation CARD_BACK =
            ResourceLocation.fromNamespaceAndPath("minecraftygo", "textures/field/card_back.png");

    private DuelHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientField.active() || mc.options.hideGui || mc.screen != null) {
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

        // The prompt sits next to the opponent's life panel, or below it when the screen is too narrow for that.
        int half = Math.min(PROMPT_HALF_WIDTH, w / 2 - LIFE_PANEL_RIGHT);
        int top = 18;
        if (half < MIN_PROMPT_HALF_WIDTH) {
            half = Math.min(PROMPT_HALF_WIDTH, w / 2 - 6);
            top = 50;
        }
        PromptView prompt = ClientDuel.prompt();
        if (view.result() != null) {
            g.pose().pushPose();
            g.pose().scale(2, 2, 1);
            g.drawCenteredString(font, view.result(), w / 4, h / 4 - 20, GOLD);
            g.pose().popPose();
        } else if (prompt != null) {
            g.fill(w / 2 - half, top, w / 2 + half, top + 26, PANEL);
            g.drawCenteredString(font, font.plainSubstrByWidth(prompt.title(), 2 * half - 8), w / 2, top + 4, GOLD);
            String help = prompt.multi() != null
                    ? "Right-click zones to pick (" + ClientDuel.selected().size() + "/" + prompt.multi().max()
                    + ")  ·  Y to confirm or pick from the hand"
                    : "Right-click a glowing zone  ·  Y for every choice";
            if (font.width(help) > 2 * half - 8) {
                help = prompt.multi() != null
                        ? "Pick " + ClientDuel.selected().size() + "/" + prompt.multi().max() + "  ·  Y to confirm"
                        : "Right-click a zone  ·  Y for all";
            }
            g.drawCenteredString(font, help, w / 2, top + 15, DIM);
        } else {
            g.drawCenteredString(font, font.plainSubstrByWidth("Waiting for " + view.names().get(opp) + "...",
                    2 * half), w / 2, top + 4, DIM);
        }

        hand(g, font, board.side(me).hand(), prompt, w, h);
        // The card panel goes below the prompt when the prompt spans the whole top.
        hovered(g, font, board, w, top == 18 ? 50 : top + 32);
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

    /** Your hand as small cards above the hotbar; cards with something to do are outlined. */
    private static void hand(GuiGraphics g, Font font, List<CardState> hand, PromptView prompt, int w, int h) {
        int cw = 20;
        int ch = 29;
        int gap = 3;
        int total = hand.size() * (cw + gap) - gap;
        int x = w / 2 - total / 2;
        int y = h - 64 - ch;
        for (int i = 0; i < hand.size(); i++) {
            CardState card = hand.get(i);
            boolean playable = prompt != null && actsOnHand(prompt, i);
            if (playable) {
                g.fill(x - 1, y - 1, x + cw + 1, y + ch + 1, GOLD);
            }
            CardArt.Texture art = card.code() == 0 ? null : CardArt.get(card.code());
            if (art != null) {
                g.blit(art.location(), x, y, cw, ch, 0, 0, art.width(), art.height(), art.width(), art.height());
            } else {
                g.blit(CARD_BACK, x, y, cw, ch, 0, 0, 68, 100, 68, 100);
                if (card.code() != 0) {
                    g.drawString(font, font.plainSubstrByWidth(YgoData.text().cardName(card.code()), cw), x + 1,
                            y + ch / 2 - 4, TEXT);
                }
            }
            x += cw + gap;
        }
    }

    private static boolean actsOnHand(PromptView prompt, int index) {
        Loc hand = new Loc(ClientDuel.view().you(), LOCATION_HAND, index, 0);
        if (prompt.multi() != null) {
            return prompt.multi().locs().stream().anyMatch(l -> l != null && l.location() == LOCATION_HAND
                    && l.controller() == hand.controller() && l.sequence() == index);
        }
        return prompt.choices().stream().anyMatch(c -> c.at() != null && c.at().location() == LOCATION_HAND
                && c.at().controller() == hand.controller() && c.at().sequence() == index);
    }

    /** Name, stats and art of the card under the crosshair, on the right. */
    private static void hovered(GuiGraphics g, Font font, Board board, int w, int y) {
        Loc loc = ClientField.hovered();
        if (loc == null) {
            return;
        }
        CardState card = FieldRenderer.cardAt(board, loc);
        String zone = (loc.controller() == ClientDuel.view().you() ? "Your " : "Opponent's ")
                + YgoData.text().location(loc.location()).toLowerCase();
        String name = card == null || card.code() == 0 ? "" : YgoData.text().cardName(card.code());
        // Wide enough for the zone and card name, within reason; longer names are cut.
        int width = Math.max(104, Math.min(160, Math.max(font.width(zone), font.width(name))));
        int x = w - 8 - width;
        if (card == null || card.code() == 0) {
            g.fill(x - 4, y - 4, w - 4, y + 12, PANEL);
            g.drawString(font, font.plainSubstrByWidth(zone, width), x, y, DIM);
            return;
        }
        CardInfo info = YgoData.cards().card(card.code());
        g.fill(x - 4, y - 4, w - 4, y + 132, PANEL);
        g.drawString(font, font.plainSubstrByWidth(zone, width), x, y, DIM);
        g.drawString(font, font.plainSubstrByWidth(name, width), x, y + 11, TEXT);
        if (info != null && info.is(TYPE_MONSTER)) {
            g.drawString(font, "ATK " + card.attack() + "  DEF " + card.defense(), x, y + 22, GOLD);
        }
        CardArt.Texture art = CardArt.get(card.code());
        if (art != null) {
            g.blit(art.location(), x + width / 2 - 32, y + 34, 64, 94, 0, 0, art.width(), art.height(), art.width(),
                    art.height());
        }
    }
}
