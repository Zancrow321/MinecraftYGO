package io.github.zancrow321.jadm.client.trade;

import io.github.zancrow321.jadm.client.shop.ClientPoints;
import io.github.zancrow321.jadm.network.TradePointsPayload;
import io.github.zancrow321.jadm.trade.Trade;
import io.github.zancrow321.jadm.trade.TradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The trade window: your offer on the left, theirs on the right, each with the Duel Points put in and whether that
 * side confirmed (the frame turns green). The confirm button waits a moment after any change, so a last-second swap
 * can't slip through; it confirms, or takes a confirmation back.
 */
public final class TradeScreen extends AbstractContainerScreen<TradeMenu> {
    private static final int GOLD = 0xFFFFD040;
    private static final int DIM = 0xFF8FA4B8;
    private static final int GREEN = 0xFF70E070;
    private static final int RED = 0xFFFF6060;
    private static final int FRAME = 0xFF3A6A8A;
    private static final int POINTS_Y = 90;
    private static final int STATUS_Y = 107;
    private static final int BUTTON_Y = 118;
    private static final int PANEL_W = TradeMenu.COLUMNS * 18;
    private static final int PANEL_H = Trade.SLOTS / TradeMenu.COLUMNS * 18;

    private EditBox pointsBox;
    private Button confirm;
    /** The points last sent, so typing only sends changes. */
    private int sentPoints;

    public TradeScreen(TradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = TradeMenu.INVENTORY_Y + 82;
        inventoryLabelY = TradeMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        pointsBox = new EditBox(font, leftPos + TradeMenu.OWN_X + 1, topPos + POINTS_Y, 48, 12,
                Component.translatable("gui.jadm.trade.points"));
        pointsBox.setMaxLength(9);
        pointsBox.setHint(Component.literal("0").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        pointsBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        pointsBox.setValue(menu.points() > 0 ? String.valueOf(menu.points()) : "");
        pointsBox.setResponder(this::typed);
        pointsBox.setTooltip(Tooltip.create(Component.translatable("gui.jadm.trade.points_hint")));
        sentPoints = menu.points();
        addRenderableWidget(pointsBox);
        confirm = addRenderableWidget(Button.builder(Component.translatable("gui.jadm.trade.confirm"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                TradeMenu.BUTTON_CONFIRM))
                .bounds(leftPos + 7, topPos + BUTTON_Y, 79, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.jadm.trade.confirm_hint")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.jadm.trade.cancel"), button -> onClose())
                .bounds(leftPos + 90, topPos + BUTTON_Y, 79, 18).build());
        updateWidgets();
    }

    private void typed(String text) {
        int points = text.isEmpty() ? 0 : (int) Math.min(Long.parseLong(text), Integer.MAX_VALUE);
        if (points != sentPoints) {
            sentPoints = points;
            PacketDistributor.sendToServer(new TradePointsPayload(menu.containerId, points));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
    }

    private void updateWidgets() {
        pointsBox.visible = menu.pointsAllowed();
        // The server keeps what you put in to what you have; show that once you stop typing.
        if (!pointsBox.isFocused() && menu.points() != sentPoints) {
            sentPoints = menu.points();
            pointsBox.setValue(sentPoints > 0 ? String.valueOf(sentPoints) : "");
        }
        pointsBox.setTextColor(sentPoints > ClientPoints.balance() ? RED : 0xFFE0E0E0);
        int lock = menu.lockTicks();
        if (menu.confirmed()) {
            confirm.setMessage(Component.translatable("gui.jadm.trade.unconfirm"));
            confirm.active = true;
        } else if (lock > 0) {
            confirm.setMessage(Component.translatable("gui.jadm.trade.wait", (lock + 19) / 20));
            confirm.active = false;
        } else {
            confirm.setMessage(Component.translatable("gui.jadm.trade.confirm"));
            confirm.active = menu.hasOffers();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, FRAME);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0081828);
        panel(g, TradeMenu.OWN_X, menu.confirmed(), 0x00000000);
        panel(g, TradeMenu.THEIR_X, menu.partnerConfirmed(), 0x30FFD040);
        g.fill(leftPos + 6, topPos + inventoryLabelY - 4, leftPos + imageWidth - 6, topPos + inventoryLabelY - 3,
                0x803A6A8A);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF0E2A3E);
            g.fill(x, y, x + 16, y + 16, 0xFF16384F);
        }
    }

    /** The frame around one offer: green once that side confirmed. */
    private void panel(GuiGraphics g, int x, boolean confirmed, int tint) {
        int left = leftPos + x - 3;
        int top = topPos + TradeMenu.SLOT_Y - 3;
        int color = confirmed ? 0xFF50D050 : FRAME;
        g.fill(left, top, left + PANEL_W + 4, top + PANEL_H + 4, color);
        g.fill(left + 1, top + 1, left + PANEL_W + 3, top + PANEL_H + 3, 0xFF081828);
        if (tint != 0) {
            g.fill(left + 1, top + 1, left + PANEL_W + 3, top + PANEL_H + 3, tint);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, font.plainSubstrByWidth(title.getString(), imageWidth - 16), 8, 7, GOLD, false);
        g.drawString(font, Component.translatable("gui.jadm.trade.yours"), TradeMenu.OWN_X, 18, DIM, false);
        g.drawString(font, font.plainSubstrByWidth(menu.partner(), PANEL_W), TradeMenu.THEIR_X, 18, DIM, false);
        // Arrows between the two offers.
        g.drawCenteredString(font, "⇄", 88, TradeMenu.SLOT_Y + PANEL_H / 2 - 4, GOLD);
        if (menu.pointsAllowed()) {
            g.drawString(font, ClientPoints.symbol(), TradeMenu.OWN_X + 53, POINTS_Y + 2,
                    GOLD, false);
            int theirs = menu.partnerPoints();
            g.drawString(font, theirs > 0 ? ClientPoints.format(theirs) : "–", TradeMenu.THEIR_X, POINTS_Y + 2,
                    theirs > 0 ? GOLD : DIM, false);
        }
        status(g, TradeMenu.OWN_X, menu.confirmed());
        status(g, TradeMenu.THEIR_X, menu.partnerConfirmed());
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false);
    }

    private void status(GuiGraphics g, int x, boolean confirmed) {
        Component text = Component.translatable(confirmed ? "gui.jadm.trade.confirmed" : "gui.jadm.trade.open");
        g.drawString(font, text, x, STATUS_Y, confirmed ? GREEN : DIM, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pointsBox.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                pointsBox.setFocused(false);
                setFocused(null);
            } else {
                pointsBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pointsBox.isFocused() && !pointsBox.isMouseOver(mouseX, mouseY)) {
            pointsBox.setFocused(false);
            setFocused(null);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
