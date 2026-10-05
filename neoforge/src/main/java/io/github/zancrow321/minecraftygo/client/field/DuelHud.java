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

        // Turn and phase, top centre.
        String phase = "Turn " + board.turn() + "  ·  " + YgoData.text().phase(board.phase())
                + (board.turnPlayer() == me ? "  (your turn)" : "");
        g.drawCenteredString(font, phase, w / 2, 6, DIM);

        PromptView prompt = ClientDuel.prompt();
        if (view.result() != null) {
            g.pose().pushPose();
            g.pose().scale(2, 2, 1);
            g.drawCenteredString(font, view.result(), w / 4, h / 4 - 20, GOLD);
            g.pose().popPose();
        } else if (prompt != null) {
            g.fill(w / 2 - 160, 18, w / 2 + 160, 44, PANEL);
            g.drawCenteredString(font, font.plainSubstrByWidth(prompt.title(), 312), w / 2, 22, GOLD);
            String help = prompt.multi() != null
                    ? "Right-click zones to pick (" + ClientDuel.selected().size() + "/" + prompt.multi().max()
                    + ")  ·  Y to confirm or pick from the hand"
                    : "Right-click a glowing zone  ·  Y for every choice";
            g.drawCenteredString(font, help, w / 2, 33, DIM);
        } else {
            g.drawCenteredString(font, "Waiting for " + view.names().get(opp) + "...", w / 2, 22, DIM);
        }

        hand(g, font, board.side(me).hand(), prompt, w, h);
        hovered(g, font, board, w);
    }

    private static void lifePanel(GuiGraphics g, Font font, int x, int y, String name, Board.Side side, boolean own) {
        g.fill(x, y, x + 96, y + 38, PANEL);
        g.drawString(font, name, x + 4, y + 3, own ? 0xFF88D8FF : 0xFFFF9090);
        g.drawString(font, "LP " + side.lifePoints(), x + 4, y + 14, GOLD);
        g.drawString(font, "Hand " + side.hand().size() + "  Deck " + side.deckCount(), x + 4, y + 26, DIM);
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
            g.drawString(font, text, 104, y, (alpha << 24) | (damage ? 0xFF4040 : 0x40FF80));
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
    private static void hovered(GuiGraphics g, Font font, Board board, int w) {
        Loc loc = ClientField.hovered();
        if (loc == null) {
            return;
        }
        CardState card = FieldRenderer.cardAt(board, loc);
        String zone = (loc.controller() == ClientDuel.view().you() ? "Your " : "Opponent's ")
                + YgoData.text().location(loc.location()).toLowerCase();
        int x = w - 112;
        int y = 50;
        if (card == null || card.code() == 0) {
            g.fill(x - 4, y - 4, w - 4, y + 12, PANEL);
            g.drawString(font, zone, x, y, DIM);
            return;
        }
        CardInfo info = YgoData.cards().card(card.code());
        g.fill(x - 4, y - 4, w - 4, y + 132, PANEL);
        g.drawString(font, font.plainSubstrByWidth(zone, 104), x, y, DIM);
        g.drawString(font, font.plainSubstrByWidth(YgoData.text().cardName(card.code()), 104), x, y + 11, TEXT);
        if (info != null && info.is(TYPE_MONSTER)) {
            g.drawString(font, "ATK " + card.attack() + "  DEF " + card.defense(), x, y + 22, GOLD);
        }
        CardArt.Texture art = CardArt.get(card.code());
        if (art != null) {
            g.blit(art.location(), x + 20, y + 34, 64, 94, 0, 0, art.width(), art.height(), art.width(),
                    art.height());
        }
    }
}
