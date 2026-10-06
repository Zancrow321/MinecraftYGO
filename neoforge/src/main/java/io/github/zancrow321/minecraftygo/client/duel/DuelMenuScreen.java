package io.github.zancrow321.minecraftygo.client.duel;

import io.github.zancrow321.minecraftygo.client.field.ClientField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The duel menu behind Escape: carry on, surrender, duel settings, the list of every choice for when something
 * can't be clicked, and the normal game menu.
 */
public final class DuelMenuScreen extends Screen {
    private static final int BUTTON_WIDTH = 204;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 24;

    /** Surrendering takes a second click on the same button. */
    private boolean confirmSurrender;

    public DuelMenuScreen() {
        super(Component.translatable("screen.minecraftygo.duel_menu"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 2 - 3 * SPACING;
        addRenderableWidget(Button.builder(Component.translatable("screen.minecraftygo.duel_menu.continue"),
                b -> onClose()).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(cameraLabel(), b -> {
            DuelMode.toggleView();
            b.setMessage(cameraLabel());
        }).bounds(x, y + SPACING, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        if (ClientField.watching()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.minecraftygo.duel_menu.stop_watching"),
                    b -> {
                        minecraft.player.connection.sendCommand("ygo unwatch");
                        onClose();
                    }).bounds(x, y + 2 * SPACING, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("screen.minecraftygo.duel_menu.surrender"), b -> {
                if (!confirmSurrender) {
                    confirmSurrender = true;
                    b.setMessage(Component.translatable("screen.minecraftygo.duel_menu.surrender.confirm"));
                    return;
                }
                minecraft.player.connection.sendCommand("ygo forfeit");
                onClose();
            }).bounds(x, y + 2 * SPACING, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.minecraftygo.duel_menu.game_menu"),
                b -> minecraft.setScreen(new PauseScreen(true))).bounds(x, y + 3 * SPACING + 8, BUTTON_WIDTH,
                BUTTON_HEIGHT).build());
    }

    private static Component cameraLabel() {
        return Component.translatable("screen.minecraftygo.duel_menu.camera", Component.translatable(
                DuelMode.view() == DuelMode.View.TOP_DOWN ? "screen.minecraftygo.duel_menu.camera.top_down"
                        : "screen.minecraftygo.duel_menu.camera.first_person"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 3 * SPACING - 20, 0xFFFFFFFF);
    }
}
