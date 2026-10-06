package io.github.zancrow321.minecraftygo.client.collection;

import io.github.zancrow321.minecraftygo.network.StarterChoicesPayload;
import io.github.zancrow321.minecraftygo.network.StarterPickPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * A new player's first deck: one card cover per starter or structure deck, a page at a time. "Later" closes it;
 * {@code /ygo starter} brings it back.
 */
public final class StarterScreen extends Screen {
    private static final int PER_PAGE = 5;
    private static final int CARD_WIDTH = 56;

    private final List<StarterChoicesPayload.Choice> choices;
    private int page;

    public StarterScreen(List<StarterChoicesPayload.Choice> choices) {
        super(Component.translatable("screen.minecraftygo.starter"));
        this.choices = choices;
    }

    private int pages() {
        return Math.max(1, (choices.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private int cardHeight() {
        return Math.round(CARD_WIDTH * 391f / 268f);
    }

    private int x0() {
        int shown = Math.min(PER_PAGE, choices.size() - page * PER_PAGE);
        return width / 2 - (shown * (CARD_WIDTH + 24) - 24) / 2;
    }

    private int y0() {
        return height / 2 - cardHeight() / 2 - 10;
    }

    @Override
    protected void init() {
        int y = y0() + cardHeight() + 40;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1))
                .bounds(width / 2 - 110, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.minecraftygo.starter.later"),
                b -> onClose()).bounds(width / 2 - 40, y, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(pages() - 1, page + 1))
                .bounds(width / 2 + 90, y, 20, 20).build());
    }

    /** @return the index of the choice under the mouse, or -1 */
    private int at(double mouseX, double mouseY) {
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= choices.size()) {
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, y0() - 34, 0xFFFFE070);
        g.drawCenteredString(font, Component.translatable("screen.minecraftygo.starter.hint"), width / 2, y0() - 20,
                0xFFAAAAAA);
        int hovered = at(mouseX, mouseY);
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= choices.size()) {
                break;
            }
            StarterChoicesPayload.Choice choice = choices.get(index);
            int x = x0() + i * (CARD_WIDTH + 24);
            if (index == hovered) {
                g.fill(x - 3, y0() - 3, x + CARD_WIDTH + 3, y0() + cardHeight() + 3, 0xFFFFE070);
            }
            CardGrid.drawCard(g, font, choice.cover(), x, y0(), CARD_WIDTH, cardHeight());
            List<net.minecraft.util.FormattedCharSequence> lines =
                    font.split(Component.literal(choice.name()), CARD_WIDTH + 20);
            for (int l = 0; l < Math.min(3, lines.size()); l++) {
                g.drawCenteredString(font, lines.get(l), x + CARD_WIDTH / 2, y0() + cardHeight() + 5 + l * 10,
                        index == hovered ? 0xFFFFE070 : 0xFFFFFFFF);
            }
        }
        String pageText = (page + 1) + " / " + pages();
        g.drawCenteredString(font, pageText, width / 2, y0() + cardHeight() + 64, 0xFFAAAAAA);
        if (hovered >= 0) {
            g.renderTooltip(font, Component.translatable("screen.minecraftygo.starter.pick",
                    choices.get(hovered).cards()), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = at(mouseX, mouseY);
        if (index >= 0) {
            PacketDistributor.sendToServer(new StarterPickPayload(choices.get(index).id()));
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
