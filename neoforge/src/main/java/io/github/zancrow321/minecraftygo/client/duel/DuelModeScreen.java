package io.github.zancrow321.minecraftygo.client.duel;

import io.github.zancrow321.minecraftygo.client.ClientDuel;
import io.github.zancrow321.minecraftygo.client.YgoClient;
import io.github.zancrow321.minecraftygo.client.field.ClientField;
import io.github.zancrow321.minecraftygo.client.field.DuelHud;
import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.LOCATION_HAND;

/**
 * The see-through layer that holds the mouse cursor in duel mode. It draws nothing itself (the duel HUD and the
 * field show through), turns clicks into picks on the field or hand, and keeps the world running behind it.
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
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !ClientField.active()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int handCard = DuelHud.handCardAt(mouseX, mouseY, width, height);
        if (handCard >= 0) {
            ClientField.clickAt(new Loc(ClientDuel.view().you(), LOCATION_HAND, handCard, 0));
            return true;
        }
        Vec3[] ray = DuelMode.rayThrough(mouseX / width, mouseY / height);
        ClientField.hoverRay(ray[0], ray[1]);
        if (ClientField.hovered() != null) {
            ClientField.clickAt(ClientField.hovered());
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            DuelMode.look(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setScreen(new DuelMenuScreen());
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
