package io.github.zancrow321.jadm.client.collection;

import io.github.zancrow321.jadm.network.DeckChoicePayload;
import io.github.zancrow321.jadm.network.DeckPickPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Before a duel, for a player carrying more than one legal deck box: one cover per deck (its boss monster, or its
 * first card), the one picked last time framed in gold. A click plays a deck; Enter, Space or Escape play the framed
 * one, the arrow keys move the frame. When the time runs out the framed deck is played.
 */
public final class DeckPickScreen extends Screen {
    private static final int PER_PAGE = 5;
    private static final int CARD_WIDTH = 56;
    private static final int GOLD = 0xFFFFE070;

    private final DeckChoicePayload choice;
    private final List<DeckChoicePayload.Option> decks;
    private int selected;
    private int page;
    private int ticksLeft;
    private boolean sent;

    public DeckPickScreen(DeckChoicePayload choice) {
        super(Component.translatable("screen.jadm.deck_pick"));
        this.choice = choice;
        this.decks = choice.decks();
        this.selected = Math.max(0, Math.min(decks.size() - 1, choice.selected()));
        this.page = selected / PER_PAGE;
        this.ticksLeft = choice.seconds() * 20;
    }

    private int pages() {
        return Math.max(1, (decks.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private int cardHeight() {
        return Math.round(CARD_WIDTH * 391f / 268f);
    }

    private int x0() {
        int shown = Math.min(PER_PAGE, decks.size() - page * PER_PAGE);
        return width / 2 - (shown * (CARD_WIDTH + 24) - 24) / 2;
    }

    private int y0() {
        return height / 2 - cardHeight() / 2 - 10;
    }

    @Override
    protected void init() {
        int y = y0() + cardHeight() + 44;
        if (pages() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1))
                    .bounds(width / 2 - 110, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(pages() - 1, page + 1))
                    .bounds(width / 2 + 90, y, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.jadm.deck_pick.play"), b -> pick(selected))
                .bounds(width / 2 - 50, y, 100, 20).build());
    }

    /** @return the index of the deck under the mouse, or -1 */
    private int at(double mouseX, double mouseY) {
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= decks.size()) {
                break;
            }
            int x = x0() + i * (CARD_WIDTH + 24);
            if (mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y0() && mouseY < y0() + cardHeight()) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public void tick() {
        if (--ticksLeft <= 0) {
            pick(selected);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, y0() - 46, GOLD);
        g.drawCenteredString(font, Component.literal(choice.matchup()), width / 2, y0() - 32, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.jadm.deck_pick.hint",
                Math.max(0, (ticksLeft + 19) / 20)), width / 2, y0() - 18, 0xFFAAAAAA);
        int hovered = at(mouseX, mouseY);
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= decks.size()) {
                break;
            }
            DeckChoicePayload.Option deck = decks.get(index);
            int x = x0() + i * (CARD_WIDTH + 24);
            if (index == selected) {
                g.fill(x - 3, y0() - 3, x + CARD_WIDTH + 3, y0() + cardHeight() + 3, GOLD);
            } else if (index == hovered) {
                g.fill(x - 2, y0() - 2, x + CARD_WIDTH + 2, y0() + cardHeight() + 2, 0xFFAAAAAA);
            }
            CardGrid.drawCard(g, font, deck.cover(), x, y0(), CARD_WIDTH, cardHeight());
            if (deck.boss()) {
                g.drawString(font, "♛", x + 3, y0() + 3, GOLD);
            }
            List<FormattedCharSequence> lines = font.split(Component.literal(deck.name()), CARD_WIDTH + 20);
            int ty = y0() + cardHeight() + 5;
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                g.drawCenteredString(font, lines.get(l), x + CARD_WIDTH / 2, ty + l * 10,
                        index == selected || index == hovered ? GOLD : 0xFFFFFFFF);
            }
            String counts = deck.extra() > 0 ? deck.main() + " + " + deck.extra() : String.valueOf(deck.main());
            g.drawCenteredString(font, counts, x + CARD_WIDTH / 2, ty + Math.min(2, lines.size()) * 10, 0xFF888888);
        }
        if (pages() > 1) {
            g.drawCenteredString(font, (page + 1) + " / " + pages(), width / 2, y0() + cardHeight() + 70,
                    0xFFAAAAAA);
        }
        if (hovered >= 0) {
            DeckChoicePayload.Option deck = decks.get(hovered);
            g.renderTooltip(font, Component.translatable("screen.jadm.deck_pick.tooltip", deck.main(),
                    deck.extra()), mouseX, mouseY);
        }
    }

    private void pick(int index) {
        if (!sent) {
            sent = true;
            PacketDistributor.sendToServer(new DeckPickPayload(index));
        }
        super.onClose();
    }

    @Override
    public void onClose() {
        pick(selected);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = at(mouseX, mouseY);
        if (index >= 0) {
            pick(index);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            pick(selected);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            selected = Math.floorMod(selected + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1), decks.size());
            page = selected / PER_PAGE;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        page = Math.max(0, Math.min(pages() - 1, page - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
