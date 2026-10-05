package io.github.zancrow321.minecraftygo.client.collection;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.client.CardArt;
import io.github.zancrow321.minecraftygo.engine.data.CardInfo;
import io.github.zancrow321.minecraftygo.item.CardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A paged grid of card thumbnails with copy counts, used by the binder and deck box screens.
 */
final class CardGrid {
    static final ResourceLocation CARD_BACK =
            ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "textures/field/card_back.png");
    static final ResourceLocation CARD_BLANK =
            ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "textures/field/card_blank.png");

    record Entry(int code, int count) {
    }

    final int x;
    final int y;
    final int columns;
    final int rows;
    final int cardWidth;
    final int cardHeight;
    int page;
    List<Entry> entries = List.of();

    CardGrid(int x, int y, int columns, int rows, int cardWidth) {
        this.x = x;
        this.y = y;
        this.columns = columns;
        this.rows = rows;
        this.cardWidth = cardWidth;
        this.cardHeight = Math.round(cardWidth * 391f / 268f);
    }

    /** Sorts the counts by card name and keeps those whose name contains {@code filter}. */
    void set(Map<Integer, Integer> counts, String filter) {
        String f = filter.toLowerCase(Locale.ROOT);
        List<Entry> list = new ArrayList<>();
        counts.forEach((code, n) -> {
            if (n > 0 && (f.isEmpty() || name(code).toLowerCase(Locale.ROOT).contains(f))) {
                list.add(new Entry(code, n));
            }
        });
        list.sort((a, b) -> name(a.code()).compareToIgnoreCase(name(b.code())));
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
            drawCard(g, font, e.code(), cx, cy, cardWidth, cardHeight);
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
        CardArt.Texture art = CardArt.get(code);
        if (art != null) {
            g.blit(art.location(), x, y, w, h, 0, 0, art.width(), art.height(), art.width(), art.height());
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
    }

    static String name(int code) {
        CardInfo card = YgoData.cards().card(code);
        return card == null ? "#" + code : card.name();
    }

    /** Name, type line and card text, for a tooltip. */
    static List<Component> tooltip(int code, String action) {
        List<Component> lines = new ArrayList<>();
        CardInfo card = YgoData.cards().card(code);
        if (card == null) {
            return lines;
        }
        lines.add(Component.literal(card.name()).withStyle(ChatFormatting.WHITE));
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
