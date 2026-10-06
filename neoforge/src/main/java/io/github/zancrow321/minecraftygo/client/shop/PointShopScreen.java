package io.github.zancrow321.minecraftygo.client.shop;

import io.github.zancrow321.minecraftygo.network.PointShopTradePayload;
import io.github.zancrow321.minecraftygo.points.PointShopMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * A shop that takes Duel Points: a scrolling list of what it sells, each with its price and how many are left, and
 * under "Sell" what it buys from you. Click buys or sells once, shift-click as often as you can.
 */
public final class PointShopScreen extends AbstractContainerScreen<PointShopMenu> {
    private static final int GOLD = 0xFFFFD040;
    private static final int DIM = 0xFF8FA4B8;
    private static final int GREEN = 0xFF70E070;
    private static final int RED = 0xFFFF6060;
    private static final int ROW = 20;
    private static final int ROWS = 8;
    private static final int TOP = 24;

    /** A row of the list: an entry (by its index), or the "Sell" heading when {@code entry} is -1. */
    private record Row(int entry) {
    }

    private double scroll;

    public PointShopScreen(PointShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 236;
        imageHeight = TOP + ROWS * ROW + 18;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        List<PointShopMenu.Entry> entries = menu.entries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).sell() && (i == 0 || !entries.get(i - 1).sell())) {
                rows.add(new Row(-1));
            }
            rows.add(new Row(i));
        }
        return rows;
    }

    private int maxScroll() {
        return Math.max(0, rows().size() * ROW - ROWS * ROW);
    }

    /** Whether the player can trade an entry now: it is in stock, and they have the points or the items. */
    private boolean enabled(PointShopMenu.Entry entry) {
        return entry.sell() ? entry.left() > 0 : entry.left() != 0 && ClientPoints.balance() >= entry.price();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<Row> rows = rows();
        int x = leftPos + 6;
        int w = imageWidth - 12;
        int top = topPos + TOP;
        PointShopMenu.Entry tooltip = null;
        g.enableScissor(x, top, x + w, top + ROWS * ROW);
        for (int r = 0; r < rows.size(); r++) {
            int y = top + r * ROW - (int) scroll;
            if (y + ROW < top || y > top + ROWS * ROW) {
                continue;
            }
            Row row = rows.get(r);
            if (row.entry() < 0) {
                g.drawString(font, Component.translatable("gui.minecraftygo.points.sell"), x + 4, y + 7, GOLD);
                g.fill(x + 4, y + ROW - 3, x + w - 4, y + ROW - 2, 0x60FFD040);
                continue;
            }
            PointShopMenu.Entry entry = menu.entries().get(row.entry());
            boolean enabled = enabled(entry);
            boolean over = mouseX >= x && mouseX < x + w && mouseY >= Math.max(y, top)
                    && mouseY < Math.min(y + ROW, top + ROWS * ROW);
            g.fill(x, y + 1, x + w, y + ROW - 1, over && enabled ? 0x50FFFFFF : 0x30000000);
            g.renderItem(entry.item(), x + 2, y + 2);
            g.renderItemDecorations(font, entry.item(), x + 2, y + 2);
            String price = (entry.sell() ? "+" : "") + ClientPoints.format(entry.price());
            int priceColor = entry.sell() ? GREEN : enabled || entry.left() == 0 ? GOLD : RED;
            g.drawString(font, price, x + w - 4 - font.width(price), y + 6, priceColor);
            int nameWidth = w - 30 - font.width(price) - 8;
            String name = font.plainSubstrByWidth(entry.item().getHoverName().getString(), nameWidth);
            g.drawString(font, name, x + 22, y + 2, enabled ? 0xFFFFFFFF : DIM);
            Component sub = entry.sell() ? Component.translatable("gui.minecraftygo.points.have", entry.left())
                    : entry.left() == 0 ? Component.translatable("gui.minecraftygo.points.sold_out")
                    : entry.left() > 0 ? Component.translatable("gui.minecraftygo.points.left", entry.left()) : null;
            if (sub != null) {
                g.drawString(font, sub, x + 22, y + 11, entry.left() == 0 ? RED : DIM, false);
            }
            if (over && mouseX < x + 20) {
                tooltip = entry;
            }
        }
        g.disableScissor();
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.minecraftygo.points.empty"),
                    leftPos + imageWidth / 2, top + ROWS * ROW / 2 - 4, DIM);
        }
        if (maxScroll() > 0) {
            int bar = ROWS * ROW * ROWS * ROW / (rows.size() * ROW);
            int barY = top + (int) ((ROWS * ROW - bar) * scroll / maxScroll());
            g.fill(leftPos + imageWidth - 5, barY, leftPos + imageWidth - 3, barY + bar, 0xA0FFFFFF);
        }
        if (tooltip != null) {
            g.renderTooltip(font, tooltip.item(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, 0xFF3A6A8A);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0081828);
        g.fill(leftPos + 6, topPos + TOP - 3, leftPos + imageWidth - 6, topPos + TOP - 2, 0x803A6A8A);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, font.plainSubstrByWidth(title.getString(), imageWidth - 100), 8, 8, GOLD);
        String balance = ClientPoints.format(ClientPoints.balance());
        g.drawString(font, balance, imageWidth - 8 - font.width(balance), 8, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.minecraftygo.points.hint"), imageWidth / 2,
                imageHeight - 13, DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int top = topPos + TOP;
        if (button == 0 && mouseX >= leftPos + 6 && mouseX < leftPos + imageWidth - 6 && mouseY >= top
                && mouseY < top + ROWS * ROW) {
            int r = (int) ((mouseY - top + scroll) / ROW);
            List<Row> rows = rows();
            if (r < rows.size() && rows.get(r).entry() >= 0
                    && enabled(menu.entries().get(rows.get(r).entry()))) {
                PacketDistributor.sendToServer(new PointShopTradePayload(menu.containerId, rows.get(r).entry(),
                        hasShiftDown()));
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * ROW, 0, maxScroll());
        return true;
    }
}
