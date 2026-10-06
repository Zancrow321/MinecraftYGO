package io.github.zancrow321.minecraftygo.client.duel;

import io.github.zancrow321.minecraftygo.client.YgoClient;
import io.github.zancrow321.minecraftygo.client.field.ClientField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * The see-through layer that holds the mouse cursor in duel mode. It hands drawing and clicks to {@link DuelUi} and
 * keeps the world running behind it.
 */
public final class DuelModeScreen extends Screen {
    public DuelModeScreen() {
        super(Component.translatable("screen.minecraftygo.duel_mode"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing: the world is the background.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (ClientField.active()) {
            Vec3[] ray = DuelMode.rayThrough((double) mouseX / width, (double) mouseY / height);
            ClientField.hoverRay(ray[0], ray[1]);
        }
        DuelUi.render(graphics, mouseX, mouseY, width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !ClientField.active()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return DuelUi.mouseClicked(mouseX, mouseY, width, height);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            DuelUi.mouseReleased(mouseX, mouseY, width, height);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return DuelUi.scroll(mouseX, mouseY, scrollY, width, height)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            DuelMode.look(dragX, dragY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            DuelUi.mouseDragged(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (DuelUi.closeMenu()) {
                return true;
            }
            minecraft.setScreen(new DuelMenuScreen());
            return true;
        }
        if (YgoClient.DUEL_LOG.matches(keyCode, scanCode)) {
            DuelUi.toggleLog();
            return true;
        }
        if (YgoClient.DUEL_CAMERA.matches(keyCode, scanCode)) {
            DuelMode.toggleView();
            return true;
        }
        if (minecraft.options.keyChat.matches(keyCode, scanCode)) {
            minecraft.setScreen(new ChatScreen(""));
            return true;
        }
        if (minecraft.options.keyCommand.matches(keyCode, scanCode)) {
            minecraft.setScreen(new ChatScreen("/"));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
