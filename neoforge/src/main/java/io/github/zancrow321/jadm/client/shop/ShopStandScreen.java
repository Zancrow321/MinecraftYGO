package io.github.zancrow321.jadm.client.shop;

import io.github.zancrow321.jadm.network.StandPricePayload;
import io.github.zancrow321.jadm.village.ShopStandMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The owner's Shop Stand window: a large chest with the rows named on the left (wares, prices, stock, till), the two
 * sample rows tinted, and a hint on an empty sample slot. With points as the currency the price row shows each
 * price in points, and clicking one opens a box to type it in.
 */
public final class ShopStandScreen extends AbstractContainerScreen<ShopStandMenu> {
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace(
            "textures/gui/container/generic_54.png");
    private static final int ROWS = 6;

    /** The box a price in points is typed in, while one is; and its column. */
    private EditBox priceBox;
    private int priceColumn = -1;

    public ShopStandScreen(ShopStandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 114 + ROWS * 18;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        label(graphics, "wares", 0, 0xFFE070);
        label(graphics, "prices", 1, 0x80FF80);
        label(graphics, "stock", 3, 0xFFFFFF);
        label(graphics, "till", 5, 0xFFD040);
        if (menu.pointsMode()) {
            renderPoints(graphics);
        }
        if (priceBox != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            priceBox.render(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    /** Each price in points, in the slot under its ware. */
    private void renderPoints(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        for (int column = 0; column < ShopStandMenu.OFFERS; column++) {
            int price = menu.points(column);
            if (price <= 0) {
                continue;
            }
            String text = price >= 10_000 ? price / 1000 + "k" : String.valueOf(price);
            int x = leftPos + 8 + column * 18 + 8;
            graphics.pose().pushPose();
            graphics.pose().translate(x, topPos + 18 + 18 + 5, 0);
            float scale = Math.min(1f, 15f / font.width(text));
            graphics.pose().scale(scale, scale, 1);
            graphics.drawCenteredString(font, text, 0, 0, 0xFFFFD040);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (priceBox != null) {
            if (priceBox.isMouseOver(mouseX, mouseY)) {
                return priceBox.mouseClicked(mouseX, mouseY, button);
            }
            commitPrice();
            return true;
        }
        if (menu.pointsMode() && hoveredSlot != null && hoveredSlot.index >= ShopStandMenu.PRICES
                && hoveredSlot.index < ShopStandMenu.STOCK) {
            openPriceBox(hoveredSlot.index - ShopStandMenu.PRICES);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openPriceBox(int column) {
        priceColumn = column;
        priceBox = new EditBox(font, leftPos + 8 + column * 18 - 12, topPos + 18 + 18, 40, 16,
                Component.translatable("gui.jadm.shop_stand.points_price"));
        priceBox.setMaxLength(7);
        priceBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        int price = menu.points(column);
        priceBox.setValue(price > 0 ? String.valueOf(price) : "");
        priceBox.setFocused(true);
    }

    /** Sends the typed price and closes the box. */
    private void commitPrice() {
        String text = priceBox.getValue();
        int price = text.isEmpty() ? 0 : (int) Math.min(Long.parseLong(text), ShopStandMenu.MAX_POINTS);
        PacketDistributor.sendToServer(new StandPricePayload(priceColumn, price));
        priceBox = null;
        priceColumn = -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (priceBox != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitPrice();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                priceBox = null;
            } else {
                priceBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (priceBox != null) {
            return priceBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        if (priceBox != null) {
            commitPrice();
        }
        super.removed();
    }

    /** Names a row to the left of the window. */
    private void label(GuiGraphics graphics, String key, int row, int color) {
        Component text = Component.translatable("gui.jadm.shop_stand." + key);
        graphics.drawString(font, text, leftPos - font.width(text) - 4, topPos + 22 + row * 18, color);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(CHEST, leftPos, topPos, 0, 0, imageWidth, ROWS * 18 + 17);
        graphics.blit(CHEST, leftPos, topPos + ROWS * 18 + 17, 0, 126, imageWidth, 96);
        // Tint the sample rows: the wares gold, the prices green.
        graphics.fill(leftPos + 7, topPos + 17, leftPos + 169, topPos + 35, 0x40FFC000);
        graphics.fill(leftPos + 7, topPos + 35, leftPos + 169, topPos + 53, 0x4040FF40);
        graphics.fill(leftPos + 7, topPos + 107, leftPos + 169, topPos + 125, 0x40FFD040);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && menu.pointsMode() && hoveredSlot.index >= ShopStandMenu.PRICES
                && hoveredSlot.index < ShopStandMenu.STOCK) {
            graphics.renderComponentTooltip(font, List.of(Component.translatable(
                    "gui.jadm.shop_stand.points_hint", ClientPoints.format(menu.points(
                            hoveredSlot.index - ShopStandMenu.PRICES)))), mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && ShopStandMenu.isSample(hoveredSlot) && !hoveredSlot.hasItem()
                && menu.getCarried().isEmpty()) {
            String key = hoveredSlot.index < ShopStandMenu.PRICES ? "ware_hint" : "price_hint";
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.jadm.shop_stand." + key),
                    Component.translatable("gui.jadm.shop_stand.sample_hint")), mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
