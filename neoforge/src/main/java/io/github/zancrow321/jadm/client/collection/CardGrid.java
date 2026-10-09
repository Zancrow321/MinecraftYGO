package io.github.zancrow321.jadm.client.collection;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.CardArt;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.item.CardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A paged grid of card thumbnails with copy counts, used by the binder and deck box screens.
 */
final class CardGrid {
    static final ResourceLocation CARD_BACK =
            ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "textures/field/card_back.png");
    static final ResourceLocation CARD_BLANK =
            ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "textures/field/card_blank.png");

    /** Copies of a card; the binder lists each rarity on its own, the deck box only cares about the card. */
    record Entry(int code, Rarity rarity, int count) {
        Entry(int code, int count) {
            this(code, Rarity.COMMON, count);
        }
    }

    final int x;
    final int y;
    final int columns;
    final int rows;
    final int cardWidth;
    final int cardHeight;
    int page;
    List<Entry> entries = List.of();
    /** Cards from sets that are still locked: dimmed, with a padlock. */
    java.util.function.IntPredicate locked = code -> false;
    /** Cards the player doesn't have, in the Set Collection Book: greyed out. */
    java.util.function.IntPredicate missing = code -> false;

    CardGrid(int x, int y, int columns, int rows, int cardWidth) {
        this.x = x;
        this.y = y;
        this.columns = columns;
        this.rows = rows;
        this.cardWidth = cardWidth;
        this.cardHeight = Math.round(cardWidth * 391f / 268f);
    }

    /** Sorts the counts by card name and keeps those that match the search {@code filter}. */
    void set(Map<Integer, Integer> counts, String filter) {
        set(counts, filter, code -> true,
                (a, b) -> name(a).compareToIgnoreCase(name(b)));
    }

    /**
     * Keeps the counts that match the search {@code filter} (see {@link CardFilter#matches}) and pass {@code keep}, in
     * the given order.
     */
    void set(Map<Integer, Integer> counts, String filter, java.util.function.IntPredicate keep,
            java.util.Comparator<Integer> order) {
        List<Entry> list = new ArrayList<>();
        counts.forEach((code, n) -> {
            if (n > 0 && keep.test(code) && CardFilter.matches(code, filter)) {
                list.add(new Entry(code, n));
            }
        });
        list.sort((a, b) -> order.compare(a.code(), b.code()));
        entries = list;
        page = Math.max(0, Math.min(page, pages() - 1));
    }

    /** Keeps the copies that match the search {@code filter} and pass {@code keep}, in the given order, then by rarity. */
    void setCopies(List<Entry> copies, String filter, java.util.function.Predicate<Entry> keep,
            java.util.Comparator<Integer> order) {
        List<Entry> list = new ArrayList<>();
        for (Entry e : copies) {
            if (e.count() > 0 && keep.test(e) && CardFilter.matches(e.code(), filter)) {
                list.add(e);
            }
        }
        list.sort(java.util.Comparator.<Entry, Integer>comparing(Entry::code, order).thenComparing(Entry::rarity));
        entries = list;
        page = Math.max(0, Math.min(page, pages() - 1));
    }

    int pages() {
        return Math.max(1, (entries.size() + columns * rows - 1) / (columns * rows));
    }

    int width() {
        return columns * (cardWidth + 4) - 4;
    }

    int height() {
        return rows * (cardHeight + 4) - 4;
    }

    /** @return the entry under the mouse, or {@code null} */
    Entry at(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - x) / (cardWidth + 4));
        int row = (int) Math.floor((mouseY - y) / (cardHeight + 4));
        if (col < 0 || col >= columns || row < 0 || row >= rows
                || mouseX - x - col * (cardWidth + 4) > cardWidth || mouseY - y - row * (cardHeight + 4) > cardHeight) {
            return null;
        }
        int index = page * columns * rows + row * columns + col;
        return index < entries.size() ? entries.get(index) : null;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int start = page * columns * rows;
        for (int i = 0; i < columns * rows && start + i < entries.size(); i++) {
            Entry e = entries.get(start + i);
            int cx = x + (i % columns) * (cardWidth + 4);
            int cy = y + (i / columns) * (cardHeight + 4);
            drawCard(g, font, e.code(), e.rarity(), cx, cy, cardWidth, cardHeight);
            if (locked.test(e.code())) {
                drawLock(g, cx, cy, cardWidth, cardHeight);
            } else if (missing.test(e.code())) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 20);
                g.fill(cx, cy, cx + cardWidth, cy + cardHeight, 0xC0181820);
                g.pose().popPose();
            }
            if (e.count() > 1) {
                String n = "×" + e.count();
                g.fill(cx + cardWidth - font.width(n) - 3, cy + cardHeight - 10, cx + cardWidth, cy + cardHeight,
                        0xC0000000);
                g.drawString(font, n, cx + cardWidth - font.width(n) - 1, cy + cardHeight - 9, 0xFFFFFFFF, false);
            }
        }
        Entry hovered = at(mouseX, mouseY);
        if (hovered != null) {
            int i = entries.indexOf(hovered) - start;
            int cx = x + (i % columns) * (cardWidth + 4);
            int cy = y + (i / columns) * (cardHeight + 4);
            g.renderOutline(cx - 1, cy - 1, cardWidth + 2, cardHeight + 2, 0xFFFFE070);
        }
        if (entries.isEmpty()) {
            return;
        }
        String pageText = (page + 1) + " / " + pages();
        g.drawString(font, pageText, x + width() / 2 - font.width(pageText) / 2, y + height() + 4, 0xFFAAAAAA, false);
    }

    static void drawCard(GuiGraphics g, Font font, int code, int x, int y, int w, int h) {
        drawCard(g, font, code, Rarity.COMMON, x, y, w, h);
    }

    /** Draws a card with its foil, and a foil card's rarity in the bottom left corner. */
    static void drawCard(GuiGraphics g, Font font, int code, Rarity rarity, int x, int y, int w, int h) {
        CardArt.Texture art = CardArt.get(code);
        if (art != null) {
            g.blit(art.location(), x, y, w, h, 0, 0, art.width(), art.height(), art.width(), art.height());
            FoilEffect.drawGui(g, code, rarity, x, y, w, h);
            drawRarityTag(g, font, rarity, x, y, w, h);
            return;
        }
        g.blit(CARD_BLANK, x, y, w, h, 0, 0, 68, 100, 68, 100);
        // Until the art has downloaded, show the name on the blank card.
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(name(code)), w * 2 - 4);
        g.pose().pushPose();
        g.pose().translate(x + 2, y + 3, 0);
        g.pose().scale(0.5f, 0.5f, 1);
        for (int i = 0; i < Math.min(lines.size(), 6); i++) {
            g.drawString(font, lines.get(i), 0, i * 9, 0xFF202020, false);
        }
        g.pose().popPose();
        drawRarityTag(g, font, rarity, x, y, w, h);
    }

    /**
     * The short rarity of a foil card (R, SR, UR, ScR) in its bottom left corner, over the passcode, where it hides
     * nothing that matters. At half size on small cards.
     */
    static void drawRarityTag(GuiGraphics g, Font font, Rarity rarity, int x, int y, int w, int h) {
        if (rarity == Rarity.COMMON) {
            return;
        }
        String tag = switch (rarity) {
            case RARE -> "R";
            case SUPER -> "SR";
            case ULTRA -> "UR";
            default -> "ScR";
        };
        Integer color = CardItem.color(rarity).getColor();
        float scale = w < 60 ? 0.5f : 1f;
        int tw = Math.round(font.width(tag) * scale);
        int th = Math.round(8 * scale);
        int left = x + 1;
        int bottom = y + h - 1;
        g.pose().pushPose();
        g.pose().translate(0, 0, 10);
        g.fill(left, bottom - th - 2, left + tw + 2, bottom, 0xD0000000);
        g.pose().translate(left + 1, bottom - th - 1, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, tag, 0, 0, 0xFF000000 | (color == null ? 0xFFFFFF : color), false);
        g.pose().popPose();
    }

    /** The rarity of a foil copy, for its tooltip; {@code null} for a common. */
    static Component rarityLine(Rarity rarity) {
        return rarity == Rarity.COMMON ? null
                : Component.literal(CardItem.rarityName(rarity)).withStyle(CardItem.color(rarity));
    }

    /** Dims a card and puts a padlock on it. */
    static void drawLock(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xA0101018);
        int cx = x + w / 2;
        int cy = y + h / 2;
        // shackle
        g.fill(cx - 4, cy - 7, cx + 4, cy - 5, 0xFFE0C060);
        g.fill(cx - 4, cy - 7, cx - 2, cy - 1, 0xFFE0C060);
        g.fill(cx + 2, cy - 7, cx + 4, cy - 1, 0xFFE0C060);
        // body and keyhole
        g.fill(cx - 6, cy - 2, cx + 6, cy + 7, 0xFFE0C060);
        g.fill(cx - 1, cy + 1, cx + 1, cy + 4, 0xFF402810);
    }

    /** The tooltip line of a locked card: which set unlocks it. */
    static Component lockedLine(int code) {
        var product = JadmData.progression().unlockedBy(code);
        return Component.literal(product == null ? "Locked: unlocks with the last set"
                : "Locked: unlocks with " + product.name() + " (" + product.code() + ")")
                .withStyle(ChatFormatting.GOLD);
    }

    static String name(int code) {
        CardInfo card = JadmData.cards().card(code);
        return card == null ? "#" + code : card.name();
    }

    /** Name, type line and card text, for a tooltip. */
    static List<Component> tooltip(int code, String action) {
        return tooltip(code, action, true);
    }

    /** Name, and with {@code text} the type line and card text, for a tooltip; without, the preview shows those. */
    static List<Component> tooltip(int code, String action, boolean text) {
        List<Component> lines = new ArrayList<>();
        CardInfo card = JadmData.cards().card(code);
        if (card == null) {
            return lines;
        }
        lines.add(Component.literal(card.name()).withStyle(ChatFormatting.WHITE));
        if (!text) {
            if (action != null) {
                lines.add(Component.literal(action).withStyle(ChatFormatting.YELLOW));
            }
            return lines;
        }
        // Narrow enough to fit beside the cursor on a small window, so the game doesn't wrap the lines again.
        String type = CardItem.typeLine(card);
        int stats = type.indexOf(" · ATK");
        lines.add(Component.literal(stats < 0 ? type : type.substring(0, stats)).withStyle(ChatFormatting.GRAY));
        if (stats >= 0) {
            lines.add(Component.literal(type.substring(stats + 3)).withStyle(ChatFormatting.GRAY));
        }
        for (String line : wrap(card.description(), 30)) {
            lines.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (action != null) {
            lines.add(Component.literal(action).withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }
}
