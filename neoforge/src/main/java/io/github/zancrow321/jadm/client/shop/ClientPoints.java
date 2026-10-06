package io.github.zancrow321.jadm.client.shop;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.network.PointShopPayload;
import io.github.zancrow321.jadm.network.PointsPayload;
import io.github.zancrow321.jadm.points.PointShopMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** The player's Duel Points as the server last sent them, shown in the inventory and the points shops. */
@EventBusSubscriber(modid = Jadm.MOD_ID, value = Dist.CLIENT)
public final class ClientPoints {
    private static boolean active;
    private static long balance;
    private static String symbol = "DP";

    private ClientPoints() {
    }

    public static void receive(PointsPayload payload) {
        active = payload.active();
        balance = payload.balance();
        symbol = payload.symbol();
    }

    /** The lines of the points shop that is open. */
    public static void shop(PointShopPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof PointShopMenu menu
                && menu.containerId == payload.containerId()) {
            menu.entries(payload.entries());
        }
    }

    public static long balance() {
        return balance;
    }

    /** An amount with the points' symbol, e.g. "1,250 DP". */
    public static String format(long amount) {
        return String.format("%,d %s", amount, symbol);
    }

    /** The balance above the inventory (in the corner, in creative). */
    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!active) {
            return;
        }
        String text = format(balance);
        var font = Minecraft.getInstance().font;
        if (event.getScreen() instanceof InventoryScreen screen) {
            event.getGuiGraphics().drawString(font, text,
                    screen.getGuiLeft() + screen.getXSize() - font.width(text) - 2, screen.getGuiTop() - 11,
                    0xFFFFD040);
        } else if (event.getScreen() instanceof CreativeModeInventoryScreen) {
            // Tabs sit above and below the creative inventory, so the balance goes in the corner.
            event.getGuiGraphics().drawString(font, text, 6, 6, 0xFFFFD040);
        }
    }
}
