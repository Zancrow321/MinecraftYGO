package io.github.zancrow321.minecraftygo.client.collection;

import io.github.zancrow321.minecraftygo.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.minecraftygo.item.CardItem;
import io.github.zancrow321.minecraftygo.item.YgoComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Reveals the cards of a freshly opened booster pack one by one, the rare slot last. Click or Esc to close.
 */
public final class PackOpenScreen extends Screen {
    private static final int REVEAL_TICKS = 5;

    private final List<YgoComponents.CardStack> cards;
    private int ticks;

    public PackOpenScreen(String setName, List<YgoComponents.CardStack> cards) {
        super(Component.literal(setName));
        this.cards = cards;
    }

    private int revealed() {
        return Math.min(cards.size(), ticks / REVEAL_TICKS + 1);
    }

    @Override
    public void tick() {
        int before = revealed();
        ticks++;
        if (revealed() > before && minecraft != null && minecraft.player != null) {
            boolean last = revealed() == cards.size();
            minecraft.player.playSound(last && rarity(cards.size() - 1).ordinal() >= Rarity.SUPER.ordinal()
                    ? SoundEvents.PLAYER_LEVELUP : SoundEvents.BOOK_PAGE_TURN, 0.6f, last ? 1.2f : 1.0f);
        }
    }

    private Rarity rarity(int i) {
        try {
            return Rarity.parse(cards.get(i).rarity());
        } catch (IllegalArgumentException e) {
            return Rarity.COMMON;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int n = cards.size();
        int cardWidth = Math.min(48, (width - 40) / n - 6);
        int cardHeight = Math.round(cardWidth * 391f / 268f);
        int total = n * (cardWidth + 6) - 6;
        int x0 = (width - total) / 2;
        int y0 = height / 2 - cardHeight / 2;
        g.drawCenteredString(font, title, width / 2, y0 - 24, 0xFFFFFFFF);
        int revealed = revealed();
        int hovered = -1;
        for (int i = 0; i < n; i++) {
            int x = x0 + i * (cardWidth + 6);
            Rarity rarity = rarity(i);
            if (i < revealed) {
                if (rarity != Rarity.COMMON) {
                    int glow = 0xFF000000 | colorOf(rarity);
                    g.fill(x - 2, y0 - 2, x + cardWidth + 2, y0 + cardHeight + 2, glow);
                }
                CardGrid.drawCard(g, font, cards.get(i).code(), x, y0, cardWidth, cardHeight);
                if (rarity != Rarity.COMMON) {
                    String label = CardItem.rarityName(rarity);
                    g.drawCenteredString(font, label, x + cardWidth / 2, y0 + cardHeight + 6, colorOf(rarity));
                }
                if (mouseX >= x && mouseX < x + cardWidth && mouseY >= y0 && mouseY < y0 + cardHeight) {
                    hovered = i;
                }
            } else {
                g.blit(CardGrid.CARD_BACK, x, y0, cardWidth, cardHeight, 0, 0, 68, 100, 68, 100);
            }
        }
        if (revealed == n) {
            g.drawCenteredString(font, Component.translatable("screen.minecraftygo.pack.close"), width / 2,
                    y0 + cardHeight + 22, 0xFFAAAAAA);
        }
        if (hovered >= 0) {
            g.renderComponentTooltip(font, CardGrid.tooltip(cards.get(hovered).code(), null), mouseX, mouseY);
        }
    }

    private static int colorOf(Rarity rarity) {
        Integer color = CardItem.color(rarity).getColor();
        return color == null ? 0xFFFFFF : color;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (revealed() < cards.size()) {
            ticks = cards.size() * REVEAL_TICKS;
        } else {
            onClose();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
