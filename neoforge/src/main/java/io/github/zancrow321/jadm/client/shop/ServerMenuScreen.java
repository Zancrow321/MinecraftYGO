package io.github.zancrow321.jadm.client.shop;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.client.JadmClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Locale;

/**
 * A chest window in the mod's dark and gold instead of the vanilla chest, for the menus servers build out of chests
 * (a plugin's "Format Selector", rewards or kits): those whose title is listed in {@code styledMenus}, and with
 * {@code styleGlassMenus} any chest filled with renamed glass panes, the way plugins pad their menus. The panes become
 * coloured tiles instead of items; clicks go to the server as before. Ordinary chests keep the vanilla look.
 */
@EventBusSubscriber(modid = Jadm.MOD_ID, value = Dist.CLIENT)
public final class ServerMenuScreen extends ContainerScreen {
    private static final int GOLD = 0xFFFFD040;
    private static final int DIM = 0xFF8FA4B8;
    private static final int PANEL = 0xF0181820;
    private static final int EDGE = 0xFFC0A040;
    private static final int SLOT = 0x60000000;
    private static final int SLOT_EDGE = 0xFF44445A;
    private static final int ITEM_SLOT = 0xFF26263A;

    private final int rows;
    private final boolean listed;

    public ServerMenuScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        rows = menu.getRowCount();
        listed = listed(title.getString());
    }

    /** Every vanilla chest window opens as this one; it only looks different when {@link #styled} says so. */
    @SubscribeEvent
    static void onOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof ContainerScreen screen && screen.getClass() == ContainerScreen.class
                && (!JadmClientConfig.STYLED_MENUS.get().isEmpty() || JadmClientConfig.STYLE_GLASS_MENUS.get())) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                event.setNewScreen(new ServerMenuScreen(screen.getMenu(), player.getInventory(), screen.getTitle()));
            }
        }
    }

    private static boolean listed(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        for (String entry : JadmClientConfig.STYLED_MENUS.get()) {
            String e = entry.strip().toLowerCase(Locale.ROOT);
            if (e.equals("*") || !e.isEmpty() && lower.contains(e)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this window gets the mod's look: its title is listed, or (with {@code styleGlassMenus}) at least a
     * quarter of the chest is renamed glass panes. Decided while drawing, since the items arrive after it opens.
     */
    private boolean styled() {
        if (listed) {
            return true;
        }
        if (!JadmClientConfig.STYLE_GLASS_MENUS.get()) {
            return false;
        }
        int panes = 0;
        for (int i = 0; i < rows * 9; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (pane(stack) && stack.has(DataComponents.CUSTOM_NAME)) {
                panes++;
            }
        }
        return panes * 4 >= rows * 9;
    }

    private static boolean pane(ItemStack stack) {
        return stack.getItem() instanceof BlockItem block && block.getBlock() instanceof IronBarsBlock
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                .endsWith("glass_pane");
    }

    /** A pane that only fills space: it has no name, or a blank one. Named panes are buttons and stay items. */
    private static boolean filler(ItemStack stack) {
        return pane(stack) && (!stack.has(DataComponents.CUSTOM_NAME)
                || stack.getHoverName().getString().strip().isEmpty());
    }

    /** The colour a pane's tile takes: its dye's, or a faint white for clear glass. */
    private static int tint(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof StainedGlassPaneBlock stained) {
            return stained.getColor().getTextureDiffuseColor() & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }

    private boolean inChest(Slot slot) {
        return slot.index < rows * 9;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (!styled()) {
            super.renderBg(g, partialTick, mouseX, mouseY);
            return;
        }
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, EDGE);
        int line = topPos + rows * 18 + 21;
        g.fill(leftPos + 7, line, leftPos + imageWidth - 7, line + 1, 0x60C0A040);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            ItemStack stack = slot.getItem();
            if (inChest(slot) && filler(stack)) {
                int tint = tint(stack);
                g.fill(x - 1, y - 1, x + 17, y + 17, 0x58000000 | tint);
                g.renderOutline(x - 1, y - 1, 18, 18, 0xA0000000 | tint);
                continue;
            }
            g.renderOutline(x - 1, y - 1, 18, 18, inChest(slot) && !stack.isEmpty() ? 0xFF6A6A84 : SLOT_EDGE);
            g.fill(x, y, x + 16, y + 16, inChest(slot) && !stack.isEmpty() ? ITEM_SLOT : SLOT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (!styled()) {
            super.renderLabels(g, mouseX, mouseY);
            return;
        }
        g.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false);
    }

    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        if (inChest(slot) && filler(slot.getItem()) && styled()) {
            return;
        }
        super.renderSlot(g, slot);
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics g, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (!styled()) {
            super.renderSlotHighlight(g, slot, mouseX, mouseY, partialTick);
        } else if (!(inChest(slot) && filler(slot.getItem()))) {
            // A gold frame instead of the white wash, like the mod's own windows.
            g.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), slot.x, slot.y, slot.x + 16,
                    slot.y + 16, 0x40FFD040, 0x40FFD040, 0);
            g.renderOutline(slot.x - 1, slot.y - 1, 18, 18, EDGE);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (hoveredSlot != null && inChest(hoveredSlot) && filler(hoveredSlot.getItem()) && styled()) {
            return;
        }
        super.renderTooltip(g, mouseX, mouseY);
    }
}
