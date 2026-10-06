package io.github.zancrow321.minecraftygo.client.shop;

import io.github.zancrow321.minecraftygo.village.ShopStandMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * The owner's Shop Stand window: a large chest with the rows named on the left (wares, prices, stock, till), the two
 * sample rows tinted, and a hint on an empty sample slot.
 */
public final class ShopStandScreen extends AbstractContainerScreen<ShopStandMenu> {
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace(
            "textures/gui/container/generic_54.png");
    private static final int ROWS = 6;

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
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** Names a row to the left of the window. */
    private void label(GuiGraphics graphics, String key, int row, int color) {
        Component text = Component.translatable("gui.minecraftygo.shop_stand." + key);
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
        if (hoveredSlot != null && ShopStandMenu.isSample(hoveredSlot) && !hoveredSlot.hasItem()
                && menu.getCarried().isEmpty()) {
            String key = hoveredSlot.index < ShopStandMenu.PRICES ? "ware_hint" : "price_hint";
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.minecraftygo.shop_stand." + key),
                    Component.translatable("gui.minecraftygo.shop_stand.sample_hint")), mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
