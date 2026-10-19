package io.github.zancrow321.jadm.client.display;

import io.github.zancrow321.jadm.display.DisplayMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A collector display's window, dark blue and gold: the display's slots in the middle of a 3x3 grid, velvet in gold
 * frames, and for its owner a lock and a light button on the left.
 */
public final class DisplayScreen extends AbstractContainerScreen<DisplayMenu> {
    private static final int PANEL = 0xFF141A2E;
    private static final int GOLD = 0xFFC9A227;

    private Button lock;
    private Button light;

    public DisplayScreen(DisplayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        lock = addRenderableWidget(Button.builder(Component.empty(), b -> press(DisplayMenu.BUTTON_LOCK))
                .bounds(leftPos + 6, topPos + 22, 52, 18).build());
        light = addRenderableWidget(Button.builder(Component.empty(), b -> press(DisplayMenu.BUTTON_LIGHT))
                .bounds(leftPos + 6, topPos + 44, 52, 18).build());
        lock.setTooltip(Tooltip.create(Component.translatable("gui.jadm.display.lock.tooltip")));
        light.setTooltip(Tooltip.create(Component.translatable("gui.jadm.display.light.tooltip")));
        update();
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    private void update() {
        lock.visible = light.visible = menu.canManage();
        lock.setMessage(Component.translatable(menu.locked() ? "gui.jadm.display.locked" : "gui.jadm.display.open"));
        light.setMessage(Component.translatable(menu.lit() ? "gui.jadm.display.light_on"
                : "gui.jadm.display.light_off"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        update();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, GOLD);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        graphics.fillGradient(x + 2, y + 2, x + imageWidth - 2, y + 72, 0xFF22305A, PANEL);
        // The display's slots: velvet behind glass, framed in gold.
        for (int cell : DisplayMenu.cells(menu.kind())) {
            int sx = x + 61 + cell % 3 * 18;
            int sy = y + 16 + cell / 3 * 18;
            graphics.fill(sx, sy, sx + 18, sy + 18, GOLD);
            graphics.fillGradient(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF6A1222, 0xFF3A0812);
        }
        for (int i = 0; i < 36; i++) {
            int sx = x + 7 + i % 9 * 18;
            int sy = y + (i < 27 ? 83 + i / 9 * 18 : 141);
            graphics.fill(sx, sy, sx + 18, sy + 18, 0xFF2E3550);
            graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF0B0F1C);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFD54F, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF9AA3B5, false);
    }
}
