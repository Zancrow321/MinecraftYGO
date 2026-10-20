package io.github.zancrow321.jadm.client.collection;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Reveals the cards of a freshly opened booster pack one by one, the rare slot last. Click or Esc to close (or go
 * on to the next pack).
 */
public final class PackOpenScreen extends Screen {
    private static final int REVEAL_TICKS = 5;

    private final List<JadmComponents.CardStack> cards;
    /** The screen to go on to once this one is closed, or {@code null} */
    private final Screen next;
    /** Whether the cards went straight into the player's binder. */
    private final boolean intoBinder;
    private int ticks;

    public PackOpenScreen(String setName, List<JadmComponents.CardStack> cards, boolean intoBinder) {
        this(setName, cards, null, intoBinder);
    }

    /** A pack of several opened one after the other, e.g. the packs of a Sealed tournament. */
    public PackOpenScreen(String setName, List<JadmComponents.CardStack> cards, Screen next) {
        this(setName, cards, next, false);
    }

    private PackOpenScreen(String setName, List<JadmComponents.CardStack> cards, Screen next, boolean intoBinder) {
        super(Component.literal(setName));
        this.cards = cards;
        this.next = next;
        this.intoBinder = intoBinder;
    }

    @Override
    public void onClose() {
        if (next != null && minecraft != null) {
            minecraft.setScreen(next);
        } else {
            super.onClose();
        }
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
            minecraft.player.playSound(last && rarity(cards.size() - 1).slot().compareTo(Rarity.SUPER) >= 0
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
                CardGrid.drawCard(g, font, cards.get(i).code(), rarity, x, y0, cardWidth, cardHeight);
                if (JadmData.isAltArt(cards.get(i).code())) {
                    g.drawCenteredString(font, Component.translatable("screen.jadm.pack.alt_art"),
                            x + cardWidth / 2, y0 - 12, 0xFFFFC040);
                }
                if (rarity != Rarity.COMMON) {
                    // Wrapped to the card's width, so the labels of cards side by side don't run into each other.
                    var lines = font.split(Component.literal(CardItem.rarityName(rarity)), cardWidth + 4);
                    for (int l = 0; l < lines.size(); l++) {
                        g.drawCenteredString(font, lines.get(l), x + cardWidth / 2, y0 + cardHeight + 6 + l * 10,
                                colorOf(rarity));
                    }
                }
                if (mouseX >= x && mouseX < x + cardWidth && mouseY >= y0 && mouseY < y0 + cardHeight) {
                    hovered = i;
                }
            } else {
                g.blit(CardGrid.CARD_BACK, x, y0, cardWidth, cardHeight, 0, 0, 68, 100, 68, 100);
            }
        }
        if (revealed == n) {
            if (intoBinder) {
                g.drawCenteredString(font, Component.translatable("screen.jadm.pack.binder"), width / 2,
                        y0 + cardHeight + 32, 0xFF55FF55);
            }
            g.drawCenteredString(font, Component.translatable("screen.jadm.pack.close"), width / 2,
                    y0 + cardHeight + (intoBinder ? 44 : 32), 0xFFAAAAAA);
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
