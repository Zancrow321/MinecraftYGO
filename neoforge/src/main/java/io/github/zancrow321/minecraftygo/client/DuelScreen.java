package io.github.zancrow321.minecraftygo.client;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.data.CardInfo;
import io.github.zancrow321.minecraftygo.engine.duel.Board;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.engine.protocol.CardState;
import io.github.zancrow321.minecraftygo.engine.text.PromptView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A plain text duel screen for M1: both fields, hands, life points, the commentary log and the current prompt.
 * The 3D field replaces most of this in M3.
 */
public final class DuelScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int LINE = 10;
    private static final int ZONE_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_ROWS = 4;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFAAAAAA;
    private static final int ZONE_BG = 0x80202020;
    private static final int OWN_ZONE_BG = 0x80203040;

    private final List<Hover> hovers = new ArrayList<>();
    private final List<Integer> selected = new ArrayList<>();
    private PromptView shownPrompt;
    private int page;

    /** A screen area that shows a card's text on hover. */
    private record Hover(int x, int y, int w, int h, int code) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public DuelScreen() {
        super(Component.literal("Duel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Called when a new view arrives. */
    public void refresh() {
        rebuildWidgets();
    }

    @Override
    protected void init() {
        PromptView prompt = ClientDuel.prompt();
        if (prompt != shownPrompt) {
            shownPrompt = prompt;
            selected.clear();
            page = 0;
        }
        if (prompt == null) {
            return;
        }
        int columns = 2;
        int buttonWidth = (width - 3 * MARGIN) / columns;
        int top = height - MARGIN - (BUTTON_ROWS + 1) * (BUTTON_HEIGHT + 2);
        int perPage = BUTTON_ROWS * columns;

        List<Button> buttons = new ArrayList<>();
        if (prompt.multi() == null) {
            for (PromptView.Choice choice : prompt.choices()) {
                buttons.add(Button.builder(Component.literal(fit(choice.label(), buttonWidth)),
                        b -> ClientDuel.answer(choice.response())).build());
            }
        } else {
            PromptView.MultiSelect multi = prompt.multi();
            for (int i = 0; i < multi.options().size(); i++) {
                int index = i;
                String mark = selected.contains(i) ? "[x] " : "[ ] ";
                buttons.add(Button.builder(Component.literal(fit(mark + multi.options().get(i), buttonWidth)), b -> {
                    if (!selected.remove((Integer) index)) {
                        selected.add(index);
                    }
                    rebuildWidgets();
                }).build());
            }
        }
        int pages = Math.max(1, (buttons.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        for (int i = page * perPage; i < Math.min(buttons.size(), (page + 1) * perPage); i++) {
            int slot = i - page * perPage;
            Button button = buttons.get(i);
            button.setRectangle(buttonWidth, BUTTON_HEIGHT, MARGIN + (slot % columns) * (buttonWidth + MARGIN),
                    top + (slot / columns) * (BUTTON_HEIGHT + 2));
            addRenderableWidget(button);
        }

        // Bottom row: paging and, for multi-selects, confirm/cancel.
        int y = top + BUTTON_ROWS * (BUTTON_HEIGHT + 2);
        int x = MARGIN;
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = (page + pages - 1) % pages;
                rebuildWidgets();
            }).bounds(x, y, 20, BUTTON_HEIGHT).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = (page + 1) % pages;
                rebuildWidgets();
            }).bounds(x + 24, y, 20, BUTTON_HEIGHT).build());
            x += 50;
        }
        if (prompt.multi() != null) {
            PromptView.MultiSelect multi = prompt.multi();
            Button confirm = Button.builder(Component.literal("Confirm (" + selected.size() + ")"),
                    b -> ClientDuel.answer(multi.encode(List.copyOf(selected)))).bounds(x, y, 100, BUTTON_HEIGHT)
                    .build();
            confirm.active = multi.canConfirm(selected);
            addRenderableWidget(confirm);
            if (multi.cancel() != null) {
                addRenderableWidget(Button.builder(Component.literal("Cancel"),
                        b -> ClientDuel.answer(multi.cancel().response())).bounds(x + 104, y, 70, BUTTON_HEIGHT)
                        .build());
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hovers.clear();
        DuelView view = ClientDuel.view();
        if (view == null) {
            g.drawCenteredString(font, "No duel in progress", width / 2, height / 2, TEXT);
            return;
        }
        int fieldWidth = (int) (width * 0.62);
        drawBoard(g, view, fieldWidth);
        drawLog(g, fieldWidth + MARGIN, width - fieldWidth - 2 * MARGIN);

        PromptView prompt = ClientDuel.prompt();
        int promptTop = height - MARGIN - (BUTTON_ROWS + 1) * (BUTTON_HEIGHT + 2) - LINE - 2;
        String status = prompt != null ? prompt.title()
                : view.result() != null ? view.result() : "Waiting for " + view.names().get(1 - view.you()) + "...";
        g.drawString(font, fit(status, width - 2 * MARGIN), MARGIN, promptTop, prompt != null ? 0xFFFFFF55 : DIM);

        for (Hover hover : hovers) {
            if (hover.contains(mouseX, mouseY) && hover.code() != 0) {
                CardInfo card = YgoData.cards().card(hover.code());
                if (card != null) {
                    drawArt(g, hover.code());
                    List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(card.name()), 220));
                    lines.addAll(font.split(Component.literal(card.description()), 220));
                    g.renderTooltip(font, lines, mouseX, mouseY);
                }
            }
        }
    }

    /** The hovered card's artwork in the top-right corner, once it has downloaded. */
    private void drawArt(GuiGraphics g, int code) {
        CardArt.Texture art = CardArt.get(code);
        int w = 84;
        int h = 123;
        int x = width - MARGIN - w;
        g.fill(x - 2, MARGIN - 2, x + w + 2, MARGIN + h + 2, 0xE0101010);
        if (art != null) {
            g.blit(art.location(), x, MARGIN, w, h, 0, 0, art.width(), art.height(), art.width(), art.height());
        } else {
            g.drawCenteredString(font, "...", x + w / 2, MARGIN + h / 2 - 4, DIM);
        }
    }

    private void drawBoard(GuiGraphics g, DuelView view, int fieldWidth) {
        int me = view.you();
        int opp = 1 - me;
        Board board = view.board();
        int y = MARGIN;
        y = drawPlayerLine(g, view, opp, y);
        y = drawHand(g, board.side(opp).hand(), y, fieldWidth, false);
        y = drawZones(g, board.side(opp).spells(), 5, y, fieldWidth, false, false);
        y = drawZones(g, board.side(opp).monsters(), 5, y, fieldWidth, true, false) + 4;
        y = drawZones(g, board.side(me).monsters(), 5, y, fieldWidth, true, true);
        y = drawZones(g, board.side(me).spells(), 5, y, fieldWidth, false, true);
        y = drawHand(g, board.side(me).hand(), y, fieldWidth, true);
        drawPlayerLine(g, view, me, y);
    }

    private int drawPlayerLine(GuiGraphics g, DuelView view, int player, int y) {
        Board.Side side = view.board().side(player);
        String phase = view.board().turnPlayer() == player ? "  (" + YgoData.text().phase(view.board().phase())
                + ")" : "";
        String line = view.names().get(player) + "  LP " + side.lifePoints() + "  Deck " + side.deckCount()
                + "  GY " + side.graveyard().size() + "  Banished " + side.banished().size() + phase;
        g.drawString(font, line, MARGIN, y, player == view.you() ? 0xFF88CCFF : 0xFFFF8888);
        return y + LINE + 2;
    }

    private int drawHand(GuiGraphics g, List<CardState> hand, int y, int fieldWidth, boolean own) {
        int x = MARGIN;
        g.drawString(font, own ? "Hand:" : "Hand (" + hand.size() + "):", x, y, DIM);
        x += font.width("Hand (00): ");
        for (CardState card : hand) {
            String name = card.code() == 0 ? "?" : YgoData.text().cardName(card.code());
            String label = fit(name, 70);
            int w = font.width(label);
            if (x + w > MARGIN + fieldWidth) {
                break;
            }
            g.drawString(font, label, x, y, own ? TEXT : DIM);
            hovers.add(new Hover(x, y, w, LINE, card.code()));
            x += w + 8;
        }
        return y + LINE + 2;
    }

    private int drawZones(GuiGraphics g, List<CardState> zones, int count, int y, int fieldWidth, boolean monsters,
                          boolean own) {
        int zoneWidth = (fieldWidth - (count - 1) * 3) / count;
        for (int i = 0; i < count && i < zones.size(); i++) {
            int x = MARGIN + i * (zoneWidth + 3);
            g.fill(x, y, x + zoneWidth, y + ZONE_HEIGHT, own ? OWN_ZONE_BG : ZONE_BG);
            CardState card = zones.get(i);
            if (card == null) {
                continue;
            }
            boolean faceUp = (card.position() & 0x5) != 0;
            String name = card.code() == 0 ? "Face-down" : YgoData.text().cardName(card.code())
                    + (faceUp ? "" : " (set)");
            g.drawString(font, fit(name, zoneWidth - 4), x + 2, y + 2, faceUp ? TEXT : DIM);
            if (monsters && card.code() != 0) {
                String stats = card.attack() + "/" + card.defense() + ((card.position() & 0x3) != 0 ? " ATK" : " DEF");
                g.drawString(font, fit(stats, zoneWidth - 4), x + 2, y + 12, DIM);
            }
            hovers.add(new Hover(x, y, zoneWidth, ZONE_HEIGHT, card.code()));
        }
        return y + ZONE_HEIGHT + 3;
    }

    private void drawLog(GuiGraphics g, int x, int logWidth) {
        int bottom = height - MARGIN - (BUTTON_ROWS + 1) * (BUTTON_HEIGHT + 2) - LINE - 6;
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String entry : ClientDuel.log()) {
            lines.addAll(font.split(Component.literal(entry), logWidth));
        }
        int y = bottom - LINE;
        for (int i = lines.size() - 1; i >= 0 && y >= MARGIN; i--, y -= LINE) {
            g.drawString(font, lines.get(i), x, y, i == lines.size() - 1 ? TEXT : DIM);
        }
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
    }
}
