package io.github.zancrow321.minecraftygo.client.collection;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.data.PoolMode;
import io.github.zancrow321.minecraftygo.item.BinderItem;
import io.github.zancrow321.minecraftygo.item.YgoItems;
import io.github.zancrow321.minecraftygo.network.CollectionActionPayload;
import io.github.zancrow321.minecraftygo.network.CollectionActionPayload.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Browses a binder: click a card to take one out (shift-click for every copy), or put all loose cards in.
 */
public final class BinderScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 196;

    private final InteractionHand hand;
    private CardGrid grid;
    private EditBox search;
    private boolean unlockedOnly;
    private int left;
    private int top;

    public BinderScreen(InteractionHand hand) {
        super(Component.translatable("screen.minecraftygo.binder"));
        this.hand = hand;
    }

    private ItemStack binder() {
        return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int page = grid == null ? 0 : grid.page;
        grid = new CardGrid(left + 10, top + 34, 9, 3, 28);
        grid.page = page;
        search = addRenderableWidget(new EditBox(font, left + 10, top + 16, 120, 14,
                Component.translatable("screen.minecraftygo.search")));
        search.setHint(Component.translatable("screen.minecraftygo.search"));
        grid.locked = code -> YgoData.locked(minecraft.player, code);
        if (YgoData.poolMode() == PoolMode.PROGRESSION) {
            addRenderableWidget(Button.builder(unlockedLabel(), b -> {
                unlockedOnly = !unlockedOnly;
                b.setMessage(unlockedLabel());
            }).bounds(left + 134, top + 15, 58, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.minecraftygo.binder.deposit"),
                b -> send(Action.DEPOSIT_ALL, 0, false)).bounds(left + WIDTH - 110, top + 15, 100, 16).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> grid.page = Math.max(0, grid.page - 1))
                .bounds(left + 10, top + HEIGHT - 22, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                b -> grid.page = Math.min(grid.pages() - 1, grid.page + 1))
                .bounds(left + WIDTH - 30, top + HEIGHT - 22, 20, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!binder().is(YgoItems.BINDER.get())) {
            onClose();
            return;
        }
        var collection = BinderItem.collection(binder());
        java.util.Map<Integer, Integer> shown = collection.counts();
        if (unlockedOnly) {
            shown = new java.util.HashMap<>(shown);
            shown.keySet().removeIf(code -> grid.locked.test(code));
        }
        grid.set(shown, search.getValue());
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title.copy().append(" · " + collection.total() + " cards, "
                + collection.counts().size() + " different"), left + 10, top + 4, 0xFFFFFFFF, false);
        grid.render(g, font, mouseX, mouseY);
        if (collection.counts().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.minecraftygo.binder.empty"), left + WIDTH / 2,
                    top + HEIGHT / 2, 0xFFAAAAAA);
        }
        CardGrid.Entry hovered = grid.at(mouseX, mouseY);
        if (hovered != null) {
            List<Component> tooltip = CardGrid.tooltip(hovered.code(), "Click: take one out · Shift-click: take all");
            if (grid.locked.test(hovered.code())) {
                tooltip.add(Math.min(1, tooltip.size()), CardGrid.lockedLine(hovered.code()));
            }
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private Component unlockedLabel() {
        return Component.translatable(unlockedOnly ? "screen.minecraftygo.binder.unlocked_only"
                : "screen.minecraftygo.binder.all_cards");
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xE0181820);
        g.renderOutline(left, top, WIDTH, HEIGHT, 0xFF5070A0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        CardGrid.Entry e = grid.at(mouseX, mouseY);
        if (e != null) {
            send(Action.WITHDRAW, e.code(), hasShiftDown());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        grid.page = Math.max(0, Math.min(grid.pages() - 1, grid.page - (int) Math.signum(scrollY)));
        return true;
    }

    private void send(Action action, int code, boolean all) {
        PacketDistributor.sendToServer(new CollectionActionPayload(action, hand == InteractionHand.OFF_HAND, code, all));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
