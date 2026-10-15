package io.github.zancrow321.jadm.client.shop;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.ProductArt;
import io.github.zancrow321.jadm.client.collection.CardGrid;
import io.github.zancrow321.jadm.client.collection.CardPreview;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.github.zancrow321.jadm.network.PointShopTradePayload;
import io.github.zancrow321.jadm.points.PointShopMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A shop that takes Duel Points, laid out like a card shop: what it sells as tiles with the real product pictures
 * (and what it buys under a second tab), and the picked one large on the right with its price and the buttons to
 * buy or sell it once or as often as you can. The balance in the corner counts up and down as you trade.
 */
public final class PointShopScreen extends AbstractContainerScreen<PointShopMenu> {
    private static final int GOLD = 0xFFFFD040;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DIM = 0xFF8FA4B8;
    private static final int GREEN = 0xFF70E070;
    private static final int RED = 0xFFFF6060;
    private static final int PANEL = 0xF0181820;
    private static final int EDGE = 0xFFC0A040;
    private static final int BOX = 0xFF22222E;
    private static final int BOX_EDGE = 0xFF44445A;
    private static final int PICTURE = 0xFF14141C;

    private static final int MAX_WIDTH = 404;
    private static final int MAX_HEIGHT = 244;
    private static final int HEADER = 24;
    private static final int TABS = 16;
    private static final int FOOTER = 16;
    private static final int GAP = 4;
    private static final int TILE_WIDTH = 58;
    private static final int TILE_HEIGHT = 84;
    private static final int TILE_PICTURE = 50;
    private static final int DETAIL_WIDTH = 128;
    private static final int BUTTON = 16;
    /** Shift trades as often as it can, up to this many times (as the server does). */
    private static final int MAX_TIMES = 64;
    private static final long DOUBLE_CLICK_MS = 300;

    /** Whether the "sell" tab is shown instead of the "buy" one. */
    private boolean selling;
    /** The picked line, by its index in the menu's entries; -1 for none. */
    private int picked = -1;
    private double scroll;

    /** The balance as drawn, which runs towards the real one; and the last real one seen. */
    private double shownBalance;
    private long seenBalance;
    /** "-250 DP" rising from the balance after a trade: the amount and when it changed. */
    private final List<long[]> changes = new ArrayList<>();
    private long lastClick;
    private int lastClicked = -1;

    public PointShopScreen(PointShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        seenBalance = ClientPoints.balance();
        shownBalance = seenBalance;
    }

    @Override
    protected void init() {
        imageWidth = Math.min(MAX_WIDTH, width - 12);
        imageHeight = Math.min(MAX_HEIGHT, height - 12);
        super.init();
    }

    // ---- layout

    private int gridX() {
        return leftPos + 8;
    }

    private int gridY() {
        return topPos + HEADER + TABS + 4;
    }

    private int gridWidth() {
        return imageWidth - 16 - DETAIL_WIDTH - 6;
    }

    private int gridHeight() {
        return imageHeight - HEADER - TABS - 4 - FOOTER;
    }

    private int columns() {
        return Math.max(1, (gridWidth() - 6 + GAP) / (TILE_WIDTH + GAP));
    }

    /** Tiles stretch to fill the row. */
    private int tileWidth() {
        return (gridWidth() - 6 - (columns() - 1) * GAP) / columns();
    }

    private int detailX() {
        return leftPos + imageWidth - 8 - DETAIL_WIDTH;
    }

    private int detailY() {
        return topPos + HEADER;
    }

    private int detailHeight() {
        return imageHeight - HEADER - FOOTER;
    }

    // ---- data

    private boolean hasSell() {
        return menu.entries().stream().anyMatch(PointShopMenu.Entry::sell);
    }

    private boolean hasBuy() {
        return menu.entries().stream().anyMatch(e -> !e.sell());
    }

    /** The indices of the lines on the tab that is open. */
    private List<Integer> shown() {
        if (selling && !hasSell()) {
            selling = false;
        } else if (!selling && !hasBuy() && hasSell()) {
            selling = true;
        }
        List<Integer> shown = new ArrayList<>();
        List<PointShopMenu.Entry> entries = menu.entries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).sell() == selling) {
                shown.add(i);
            }
        }
        return shown;
    }

    private PointShopMenu.Entry pickedEntry(List<Integer> shown) {
        if (!shown.contains(picked)) {
            picked = shown.isEmpty() ? -1 : shown.get(0);
        }
        return picked < 0 ? null : menu.entries().get(picked);
    }

    /** How many times a line can be traded right now (0 if not at all), up to {@link #MAX_TIMES}. */
    private static int times(PointShopMenu.Entry entry) {
        if (entry.sell()) {
            return Math.min(MAX_TIMES, Math.max(0, entry.left()));
        }
        long afford = entry.price() <= 0 ? MAX_TIMES : ClientPoints.balance() / entry.price();
        long left = entry.left() < 0 ? MAX_TIMES : entry.left();
        return (int) Math.min(MAX_TIMES, Math.min(afford, left));
    }

    private int rows(int count) {
        return (count + columns() - 1) / columns();
    }

    private int maxScroll(int count) {
        return Math.max(0, rows(count) * (TILE_HEIGHT + GAP) - GAP - gridHeight());
    }

    private void trade(int index, boolean all) {
        PointShopMenu.Entry entry = menu.entries().get(index);
        if (times(entry) == 0) {
            return;
        }
        PacketDistributor.sendToServer(new PointShopTradePayload(menu.containerId, index, all));
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
        }
    }

    // ---- drawing

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, EDGE);
        // A gold rule under the title, fading out to the right.
        for (int i = 0; i < 4; i++) {
            int x0 = leftPos + 8 + i * (imageWidth - 16) / 4;
            int x1 = leftPos + 8 + (i + 1) * (imageWidth - 16) / 4;
            g.fill(x0, topPos + HEADER - 3, x1, topPos + HEADER - 2, (0xC0 - i * 0x28) << 24 | 0xC0A040);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Everything is drawn in render.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<Integer> shown = shown();
        scroll = Mth.clamp(scroll, 0, maxScroll(shown.size()));
        renderHeader(g);
        renderTabs(g, mouseX, mouseY);
        int hovered = renderGrid(g, mouseX, mouseY, shown);
        renderDetail(g, mouseX, mouseY, pickedEntry(shown));
        Component hint = Component.translatable("gui.jadm.points.hint");
        g.drawString(font, font.plainSubstrByWidth(hint.getString(), imageWidth - 16), leftPos + 8,
                topPos + imageHeight - 12, DIM, false);
        if (hovered >= 0) {
            renderHovered(g, mouseX, mouseY, menu.entries().get(hovered));
        }
    }

    private void renderHeader(GuiGraphics g) {
        long balance = ClientPoints.balance();
        if (balance != seenBalance) {
            changes.add(new long[] {balance - seenBalance, Util.getMillis()});
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35f, balance > seenBalance ? 1.6f
                        : 1.1f);
            }
            seenBalance = balance;
        }
        shownBalance += (balance - shownBalance) * 0.2;
        if (Math.abs(balance - shownBalance) < 0.5) {
            shownBalance = balance;
        }
        String text = ClientPoints.format(Math.round(shownBalance));
        int pillWidth = font.width(text) + 14;
        int px = leftPos + imageWidth - 8 - pillWidth;
        int py = topPos + 5;
        g.fill(px, py, px + pillWidth, py + 13, 0xFF2A2410);
        g.renderOutline(px, py, pillWidth, 13, EDGE);
        g.fill(px + 3, py + 4, px + 8, py + 9, GOLD);
        g.fill(px + 4, py + 5, px + 7, py + 8, 0xFFB08020);
        g.drawString(font, text, px + 11, py + 3, WHITE, false);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), px - leftPos - 16), leftPos + 8, topPos + 8,
                GOLD, false);
        long now = Util.getMillis();
        changes.removeIf(c -> now - c[1] > 1400);
        for (long[] change : changes) {
            float age = (now - change[1]) / 1400f;
            int alpha = Math.max(8, (int) (255 * (1 - age)));
            String amount = (change[0] > 0 ? "+" : "-") + ClientPoints.format(Math.abs(change[0]));
            int color = (change[0] > 0 ? GREEN : RED) & 0xFFFFFF | alpha << 24;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawString(font, amount, px - 6 - font.width(amount), py + 3 - (int) (age * 8), color);
            g.pose().popPose();
        }
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int y = topPos + HEADER;
        int x = leftPos + 8;
        for (boolean sell : new boolean[] {false, true}) {
            if (sell ? !hasSell() : !hasBuy()) {
                continue;
            }
            Component label = tabLabel(sell);
            int w = font.width(label) + 16;
            boolean active = sell == selling;
            boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + TABS;
            g.fill(x, y, x + w, y + TABS, active ? BOX : over ? 0x40FFFFFF : 0x20FFFFFF);
            if (active) {
                g.fill(x, y + TABS - 2, x + w, y + TABS, GOLD);
            }
            g.drawString(font, label, x + 8, y + 4, active ? GOLD : over ? WHITE : DIM, false);
            x += w + 2;
        }
    }

    private Component tabLabel(boolean sell) {
        long count = menu.entries().stream().filter(e -> e.sell() == sell).count();
        return Component.translatable(sell ? "gui.jadm.points.sell" : "gui.jadm.points.buy_tab")
                .append(" (" + count + ")");
    }

    /** @return the line of the tile under the mouse, or -1 */
    private int renderGrid(GuiGraphics g, int mouseX, int mouseY, List<Integer> shown) {
        int x0 = gridX();
        int y0 = gridY();
        int h = gridHeight();
        if (shown.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.jadm.points.empty"), x0 + gridWidth() / 2,
                    y0 + h / 2 - 4, DIM);
            return -1;
        }
        int hovered = -1;
        int tw = tileWidth();
        boolean inGrid = mouseX >= x0 && mouseX < x0 + gridWidth() - 6 && mouseY >= y0 && mouseY < y0 + h;
        g.enableScissor(x0, y0, x0 + gridWidth() - 5, y0 + h);
        for (int i = 0; i < shown.size(); i++) {
            int tx = x0 + (i % columns()) * (tw + GAP);
            int ty = y0 + (i / columns()) * (TILE_HEIGHT + GAP) - (int) scroll;
            if (ty + TILE_HEIGHT < y0 || ty > y0 + h) {
                continue;
            }
            int index = shown.get(i);
            boolean over = inGrid && mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + TILE_HEIGHT;
            if (over) {
                hovered = index;
            }
            renderTile(g, menu.entries().get(index), tx, ty, tw, over, index == picked);
        }
        g.disableScissor();
        int max = maxScroll(shown.size());
        if (max > 0) {
            int bar = Math.max(10, h * h / (h + max));
            int barY = y0 + (int) ((h - bar) * scroll / max);
            int bx = x0 + gridWidth() - 3;
            g.fill(bx, y0, bx + 2, y0 + h, 0x30FFFFFF);
            g.fill(bx, barY, bx + 2, barY + bar, 0xC0C0A040);
        }
        return hovered;
    }

    private void renderTile(GuiGraphics g, PointShopMenu.Entry entry, int x, int y, int w, boolean over,
                            boolean selected) {
        boolean open = times(entry) > 0;
        g.fill(x, y, x + w, y + TILE_HEIGHT, over ? 0xFF2C2C3C : BOX);
        g.fill(x + 3, y + 3, x + w - 3, y + 3 + TILE_PICTURE, PICTURE);
        drawPicture(g, entry.item(), x + 4, y + 4, w - 8, TILE_PICTURE - 2, over ? 1.06f : 1f);
        boolean soldOut = !entry.sell() && entry.left() == 0;
        if (soldOut || entry.sell() && entry.left() <= 0) {
            g.fill(x + 3, y + 3, x + w - 3, y + 3 + TILE_PICTURE, 0xA0101018);
        }
        if (soldOut) {
            Component text = Component.translatable("gui.jadm.points.sold_out");
            drawScaled(g, text.getString(), x + w / 2f, y + 3 + TILE_PICTURE / 2f - 3, 0.75f, RED, true);
        }
        // How many are left (or how many you have to sell) in the corner of the picture.
        if (entry.left() > 0) {
            String count = "×" + entry.left();
            int cw = Math.round(font.width(count) * 0.75f) + 4;
            g.fill(x + w - 3 - cw, y + 3, x + w - 3, y + 11, 0xC0000000);
            drawScaled(g, count, x + w - 3 - cw + 2, y + 4.5f, 0.75f, entry.sell() ? WHITE : DIM, false);
        }
        // The name in up to two small lines.
        List<FormattedCharSequence> lines = font.split(Component.literal(name(entry.item())),
                (int) ((w - 4) / 0.75f));
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            FormattedCharSequence line = lines.get(i);
            if (i == 1 && lines.size() > 2) {
                int room = (int) ((w - 4) / 0.75f) - font.width("…");
                line = Component.literal(font.plainSubstrByWidth(text(line), room).stripTrailing() + "…")
                        .getVisualOrderText();
            }
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + TILE_PICTURE + 6 + i * 8, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, line, -font.width(line) / 2, 0, open ? WHITE : DIM, false);
            g.pose().popPose();
        }
        // The price on a badge at the bottom.
        String price = (entry.sell() ? "+" : "") + ClientPoints.format(entry.price());
        int by = y + TILE_HEIGHT - 13;
        g.fill(x + 3, by, x + w - 3, by + 10, entry.sell() ? 0xFF143018 : 0xFF2A2410);
        int priceColor = entry.sell() ? (open ? GREEN : DIM) : soldOut ? DIM : open ? GOLD : RED;
        drawScaled(g, font.plainSubstrByWidth(price, (int) ((w - 8) / 0.85f)), x + w / 2f, by + 1.5f, 0.85f,
                priceColor, true);
        int edge = selected ? EDGE : over ? 0xFF8888A0 : BOX_EDGE;
        g.renderOutline(x, y, w, TILE_HEIGHT, edge);
        if (selected) {
            g.renderOutline(x + 1, y + 1, w - 2, TILE_HEIGHT - 2, 0x80C0A040);
        }
    }

    private static String text(FormattedCharSequence line) {
        StringBuilder text = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return text.toString();
    }

    private void renderDetail(GuiGraphics g, int mouseX, int mouseY, PointShopMenu.Entry entry) {
        int x = detailX();
        int y = detailY();
        int w = DETAIL_WIDTH;
        int h = detailHeight();
        g.fill(x, y, x + w, y + h, 0xFF101018);
        g.renderOutline(x, y, w, h, BOX_EDGE);
        if (entry == null) {
            return;
        }
        int pictureHeight = Math.max(40, Math.min(92, h - 108));
        g.fill(x + 4, y + 4, x + w - 4, y + 4 + pictureHeight, PICTURE);
        drawPicture(g, entry.item(), x + 6, y + 6, w - 12, pictureHeight - 4, 1f);
        int ty = y + pictureHeight + 8;
        List<FormattedCharSequence> name = font.split(Component.literal(name(entry.item())), w - 10);
        for (int i = 0; i < Math.min(2, name.size()); i++) {
            FormattedCharSequence line = name.get(i);
            if (i == 1 && name.size() > 2) {
                line = Component.literal(font.plainSubstrByWidth(text(line), w - 10 - font.width("…")).stripTrailing()
                        + "…").getVisualOrderText();
            }
            g.drawString(font, line, x + 5, ty, WHITE, false);
            ty += 9;
        }
        String about = about(entry.item());
        if (!about.isEmpty()) {
            drawScaled(g, font.plainSubstrByWidth(about, (int) ((w - 10) / 0.75f)), x + 5, ty + 1, 0.75f, DIM,
                    false);
            ty += 8;
        }
        Component stock = entry.sell() ? Component.translatable("gui.jadm.points.have", entry.left())
                : entry.left() == 0 ? Component.translatable("gui.jadm.points.sold_out")
                : entry.left() > 0 ? Component.translatable("gui.jadm.points.left", entry.left())
                : Component.translatable("gui.jadm.points.unlimited");
        boolean none = entry.left() == 0 || entry.sell() && entry.left() < 0;
        drawScaled(g, stock.getString(), x + 5, ty + 1, 0.75f, none ? RED : DIM, false);
        ty += 11;

        // The price, large, above the buttons; what the item's tooltip says in the room above it.
        int buttonsY = y + h - 2 * BUTTON - 8;
        renderAbout(g, entry.item(), x + 5, ty, w - 10, buttonsY - 26 - ty);
        Component label = Component.translatable(entry.sell() ? "gui.jadm.points.you_get" : "gui.jadm.points.price");
        drawScaled(g, label.getString(), x + 5, buttonsY - 20, 0.75f, DIM, false);
        String price = (entry.sell() ? "+" : "") + ClientPoints.format(entry.price());
        int times = times(entry);
        float scale = Math.min(1.5f, (w - 10f) / font.width(price));
        drawScaled(g, price, x + 5, buttonsY - 13, scale,
                entry.sell() ? GREEN : times > 0 || entry.left() == 0 ? GOLD : RED, false);

        Component once;
        Component all;
        if (times == 0) {
            once = Component.translatable(entry.sell() ? "gui.jadm.points.none_left"
                    : entry.left() == 0 ? "gui.jadm.points.sold_out" : "gui.jadm.points.not_enough",
                    ClientPoints.symbol());
            all = null;
        } else {
            once = Component.translatable(entry.sell() ? "gui.jadm.points.sell_one" : "gui.jadm.points.buy");
            all = Component.translatable(entry.sell() ? "gui.jadm.points.sell_all" : "gui.jadm.points.buy_max",
                    times);
        }
        button(g, mouseX, mouseY, x + 4, buttonsY, w - 8, once, times > 0, true);
        button(g, mouseX, mouseY, x + 4, buttonsY + BUTTON + 3, w - 8,
                all == null ? Component.literal("—") : all, times > 0, false);
    }

    /** The item's tooltip below its name, small, in up to {@code height} pixels; cut off with "…". */
    private void renderAbout(GuiGraphics g, ItemStack stack, int x, int y, int width, int height) {
        if (minecraft == null || minecraft.player == null || height < 7) {
            return;
        }
        List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player,
                TooltipFlag.Default.NORMAL);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : tooltip.subList(Math.min(1, tooltip.size()), tooltip.size())) {
            if (!line.getString().isBlank()) {
                lines.addAll(font.split(line, (int) (width / 0.75f)));
            }
        }
        int fit = height / 7;
        for (int i = 0; i < Math.min(fit, lines.size()); i++) {
            FormattedCharSequence line = lines.get(i);
            if (i == fit - 1 && lines.size() > fit) {
                line = Component.literal(font.plainSubstrByWidth(text(line), (int) (width / 0.75f)
                        - font.width("…")).stripTrailing() + "…").getVisualOrderText();
            }
            g.pose().pushPose();
            g.pose().translate(x, y + i * 7, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, line, 0, 0, DIM, false);
            g.pose().popPose();
        }
    }

    private void button(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, Component text, boolean enabled,
                        boolean main) {
        boolean over = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BUTTON;
        int fill;
        int edge;
        int color;
        if (!enabled) {
            fill = 0xFF1C1C24;
            edge = 0xFF33333F;
            color = 0xFF6A6A7A;
        } else if (main) {
            fill = over ? 0xFFFFE070 : 0xFFE0B030;
            edge = 0xFF805010;
            color = 0xFF402000;
        } else {
            fill = over ? 0xFF3A3A50 : 0xFF2A2A3A;
            edge = over ? EDGE : 0xFF55556A;
            color = over ? GOLD : WHITE;
        }
        g.fill(x, y, x + w, y + BUTTON, fill);
        g.renderOutline(x, y, w, BUTTON, edge);
        String label = font.plainSubstrByWidth(text.getString(), w - 6);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, color, false);
    }

    /** The tooltip of a tile, and a card's big preview left of the window. */
    private void renderHovered(GuiGraphics g, int mouseX, int mouseY, PointShopMenu.Entry entry) {
        ItemStack stack = entry.item();
        boolean card = stack.getItem() instanceof CardItem && CardItem.code(stack) != 0;
        if (card && CardPreview.render(g, font, CardItem.code(stack), CardItem.rarity(stack), leftPos, height)) {
            CardItem.previewed = stack;
        }
        g.renderTooltip(font, stack, mouseX, mouseY);
        CardItem.previewed = ItemStack.EMPTY;
    }

    /**
     * An item as big as fits the box: a pack, deck or tin as its real picture, a card as its card, anything else
     * as its icon blown up.
     */
    private void drawPicture(GuiGraphics g, ItemStack stack, int x, int y, int w, int h, float zoom) {
        Products.Product product = product(stack);
        ProductArt.Art art = product == null ? null : ProductArt.get(product.code());
        if (art != null) {
            float fit = Math.min(w / art.aspect(), h) * zoom;
            int ph = Math.round(Math.min(fit, h));
            int pw = Math.round(ph * art.aspect());
            RenderSystem.enableBlend();
            g.blit(art.full(), x + (w - pw) / 2, y + (h - ph) / 2, pw, ph, 0, 0, 1, 1, 1, 1);
            RenderSystem.disableBlend();
            return;
        }
        if (stack.getItem() instanceof CardItem && CardItem.code(stack) != 0) {
            int ch = h;
            int cw = Math.round(ch * 268f / 391f);
            if (cw > w) {
                cw = w;
                ch = Math.round(cw * 391f / 268f);
            }
            CardGrid.drawCard(g, font, CardItem.code(stack), CardItem.rarity(stack), x + (w - cw) / 2,
                    y + (h - ch) / 2, cw, ch);
            return;
        }
        float scale = Math.min(w, h) / 16f * 0.8f * zoom;
        g.pose().pushPose();
        g.pose().translate(x + w / 2f - 8 * scale, y + h / 2f - 8 * scale, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static Products.Product product(ItemStack stack) {
        String id = stack.get(JadmComponents.PACK_SET.get());
        return id == null ? null : JadmData.products().get(id);
    }

    private static String name(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    /** A line about the item: a product's set code, year and kind, a card's rarity. */
    private static String about(ItemStack stack) {
        Products.Product product = product(stack);
        if (product != null) {
            String kind = Component.translatable("screen.jadm.admin.kind." + switch (product.kind()) {
                case DECK -> "deck";
                case TIN -> "tin";
                default -> "booster";
            }).getString();
            return product.code() + " · " + product.date().getYear() + " · " + kind;
        }
        if (stack.getItem() instanceof CardItem && CardItem.code(stack) != 0) {
            return CardItem.rarityName(CardItem.rarity(stack));
        }
        return "";
    }

    private void drawScaled(GuiGraphics g, String text, float x, float y, float scale, int color, boolean centred) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, centred ? -font.width(text) / 2 : 0, 0, color, false);
        g.pose().popPose();
    }

    // ---- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        List<Integer> shown = shown();
        // Tabs.
        int tx = leftPos + 8;
        int ty = topPos + HEADER;
        for (boolean sell : new boolean[] {false, true}) {
            if (sell ? !hasSell() : !hasBuy()) {
                continue;
            }
            int w = font.width(tabLabel(sell)) + 16;
            if (mouseX >= tx && mouseX < tx + w && mouseY >= ty && mouseY < ty + TABS) {
                if (selling != sell) {
                    selling = sell;
                    scroll = 0;
                    picked = -1;
                    click();
                }
                return true;
            }
            tx += w + 2;
        }
        // Tiles: a click picks, a double click trades once (with shift: as often as it can).
        int tile = tileAt(mouseX, mouseY, shown);
        if (tile >= 0) {
            long now = Util.getMillis();
            if (tile == lastClicked && now - lastClick < DOUBLE_CLICK_MS) {
                trade(tile, hasShiftDown());
                lastClicked = -1;
            } else {
                if (picked != tile) {
                    click();
                }
                picked = tile;
                lastClicked = tile;
                lastClick = now;
            }
            return true;
        }
        // The buttons under the picked line.
        PointShopMenu.Entry entry = pickedEntry(shown);
        if (entry != null) {
            int bx = detailX() + 4;
            int by = detailY() + detailHeight() - 2 * BUTTON - 8;
            if (mouseX >= bx && mouseX < bx + DETAIL_WIDTH - 8) {
                if (mouseY >= by && mouseY < by + BUTTON) {
                    trade(picked, hasShiftDown());
                    return true;
                }
                if (mouseY >= by + BUTTON + 3 && mouseY < by + 2 * BUTTON + 3 && times(entry) > 0) {
                    trade(picked, true);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int tileAt(double mouseX, double mouseY, List<Integer> shown) {
        int x0 = gridX();
        int y0 = gridY();
        if (mouseX < x0 || mouseX >= x0 + gridWidth() - 6 || mouseY < y0 || mouseY >= y0 + gridHeight()) {
            return -1;
        }
        int tw = tileWidth();
        int column = (int) ((mouseX - x0) / (tw + GAP));
        int row = (int) ((mouseY - y0 + scroll) / (TILE_HEIGHT + GAP));
        if (column >= columns() || mouseX - x0 - column * (tw + GAP) >= tw
                || mouseY - y0 + scroll - row * (TILE_HEIGHT + GAP) >= TILE_HEIGHT) {
            return -1;
        }
        int i = row * columns() + column;
        return i < shown.size() ? shown.get(i) : -1;
    }

    private void click() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.6f);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * (TILE_HEIGHT + GAP) / 2.0, 0, maxScroll(shown().size()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE)
                && pickedEntry(shown()) != null) {
            trade(picked, hasShiftDown());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && hasBuy() && hasSell()) {
            selling = !selling;
            scroll = 0;
            picked = -1;
            click();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_RIGHT && keyCode <= GLFW.GLFW_KEY_UP) {
            List<Integer> shown = shown();
            int at = shown.indexOf(picked);
            if (at >= 0) {
                int step = switch (keyCode) {
                    case GLFW.GLFW_KEY_LEFT -> -1;
                    case GLFW.GLFW_KEY_RIGHT -> 1;
                    case GLFW.GLFW_KEY_UP -> -columns();
                    default -> columns();
                };
                int next = Mth.clamp(at + step, 0, shown.size() - 1);
                picked = shown.get(next);
                // Keep the picked tile in view.
                int top = next / columns() * (TILE_HEIGHT + GAP);
                if (top < scroll) {
                    scroll = top;
                } else if (top + TILE_HEIGHT > scroll + gridHeight()) {
                    scroll = top + TILE_HEIGHT - gridHeight();
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
