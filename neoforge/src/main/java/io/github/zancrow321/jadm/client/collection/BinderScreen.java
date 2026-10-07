package io.github.zancrow321.jadm.client.collection;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.engine.data.PoolMode;
import io.github.zancrow321.jadm.item.BinderItem;
import io.github.zancrow321.jadm.item.JadmItems;
import io.github.zancrow321.jadm.network.CollectionActionPayload;
import io.github.zancrow321.jadm.network.CollectionActionPayload.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Browses a binder: click a card to take one out (shift-click for every copy), or put all loose cards in. The search
 * box has the focus, so typing finds cards by name, text or type at once; the row below filters by card type,
 * attribute, level and rarity and sorts.
 */
public final class BinderScreen extends Screen {
    private static final int WIDTH = 336;
    private static final int HEIGHT = 214;
    /** The rarity filter's choices: any, every foil, then each rarity on its own. */
    private static final String[] RARITY_KEYS = {"any", "foil", "common", "rare", "super", "ultra", "secret"};

    private final InteractionHand hand;
    private final CardFilter filter = new CardFilter();
    private int rarity;
    private CardGrid grid;
    private EditBox search;
    private boolean unlockedOnly;
    private int left;
    private int top;

    public BinderScreen(InteractionHand hand) {
        super(Component.translatable("screen.jadm.binder"));
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
        String query = search == null ? "" : search.getValue();
        grid = new CardGrid(left + 10, top + 52, 10, 3, 28);
        grid.page = page;
        grid.locked = code -> JadmData.locked(minecraft.player, code);
        boolean progression = JadmData.poolMode() == PoolMode.PROGRESSION;
        int deposit = left + WIDTH - 118;
        search = addRenderableWidget(new EditBox(font, left + 10, top + 16, deposit - left - 14 - (progression ? 68 : 0),
                14, Component.translatable("screen.jadm.search")));
        search.setMaxLength(64);
        search.setValue(query);
        search.setTooltip(Tooltip.create(Component.translatable("screen.jadm.binder.search.hint")));
        search.setResponder(text -> grid.page = 0);
        setInitialFocus(search);
        if (progression) {
            addRenderableWidget(Button.builder(unlockedLabel(), b -> {
                unlockedOnly = !unlockedOnly;
                b.setMessage(unlockedLabel());
                grid.page = 0;
            }).bounds(deposit - 68, top + 15, 64, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.jadm.binder.deposit"),
                b -> send(Action.DEPOSIT_ALL, 0, false)).bounds(deposit, top + 15, 108, 16).build());
        int w = 60;
        int x = left + 10;
        cycle(filter.kindLabel(), filter::nextKind, filter::kindLabel, x, w);
        cycle(filter.attributeLabel(), filter::nextAttribute, filter::attributeLabel, x += w + 4, w);
        cycle(filter.levelLabel(), filter::nextLevel, filter::levelLabel, x += w + 4, w);
        cycle(rarityLabel(), step -> rarity = Math.floorMod(rarity + step, RARITY_KEYS.length), this::rarityLabel,
                x += w + 4, w);
        cycle(filter.sortLabel(), filter::nextSort, filter::sortLabel, x + w + 4, WIDTH - 20 - 4 * (w + 4));
        addRenderableWidget(Button.builder(Component.literal("<"), b -> grid.page = Math.max(0, grid.page - 1))
                .bounds(left + 10, top + HEIGHT - 22, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                b -> grid.page = Math.min(grid.pages() - 1, grid.page + 1))
                .bounds(left + WIDTH - 30, top + HEIGHT - 22, 20, 16).build());
    }

    /** A button that steps through a filter's choices: forward on click, back on shift-click. */
    private void cycle(Component label, IntConsumer step, Supplier<Component> now, int x, int w) {
        addRenderableWidget(Button.builder(label, b -> {
            step.accept(hasShiftDown() ? -1 : 1);
            b.setMessage(now.get());
            grid.page = 0;
        }).bounds(x, top + 34, w, 14).tooltip(Tooltip.create(Component.translatable("screen.jadm.deck_box.cycle")))
                .build());
    }

    private Component rarityLabel() {
        return Component.translatable("screen.jadm.binder.rarity." + RARITY_KEYS[rarity]);
    }

    private boolean rarityOk(Rarity r) {
        return rarity == 0 || (rarity == 1 ? r != Rarity.COMMON : r.ordinal() == rarity - 2);
    }

    /** Whether anything narrows down what the grid shows. */
    private boolean filtering() {
        return !search.getValue().isBlank() || unlockedOnly || rarity != 0 || filter.kind != CardFilter.Kind.ALL
                || filter.attribute != 0 || filter.level != 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!binder().is(JadmItems.BINDER.get())) {
            onClose();
            return;
        }
        var collection = BinderItem.collection(binder());
        List<CardGrid.Entry> copies = new ArrayList<>();
        for (BinderItem.Copies c : BinderItem.copies(binder())) {
            copies.add(new CardGrid.Entry(c.code(), c.rarity(), c.count()));
        }
        grid.setCopies(copies, search.getValue(), e -> (!unlockedOnly || !grid.locked.test(e.code()))
                && rarityOk(e.rarity()) && filter.test(e.code()), filter.order());
        super.render(g, mouseX, mouseY, partialTick);
        if (search.getValue().isEmpty()) {
            // The box's own hint hides while it has the focus, which it has from the start.
            g.drawString(font, Component.translatable("screen.jadm.binder.search"), search.getX() + 10,
                    search.getY() + 3, 0xFF707070, false);
        }
        g.drawString(font, title.copy().append(" · " + collection.total() + " cards, "
                + collection.counts().size() + " different"), left + 10, top + 4, 0xFFFFFFFF, false);
        if (filtering()) {
            int found = grid.entries.stream().mapToInt(CardGrid.Entry::count).sum();
            Component text = Component.translatable("screen.jadm.binder.found", found);
            g.drawString(font, text, left + WIDTH - 10 - font.width(text), top + 4,
                    found == 0 ? 0xFFFF7070 : 0xFFFFE070, false);
        }
        grid.render(g, font, mouseX, mouseY);
        if (collection.counts().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.jadm.binder.empty"), left + WIDTH / 2,
                    top + HEIGHT / 2, 0xFFAAAAAA);
        } else if (grid.entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.jadm.binder.no_match"), left + WIDTH / 2,
                    top + HEIGHT / 2, 0xFFAAAAAA);
        }
        CardGrid.Entry hovered = grid.at(mouseX, mouseY);
        if (hovered != null) {
            List<Component> tooltip = CardGrid.tooltip(hovered.code(), "Click: take one out · Shift-click: take all");
            if (grid.locked.test(hovered.code())) {
                tooltip.add(Math.min(1, tooltip.size()), CardGrid.lockedLine(hovered.code()));
            }
            Component rarity = CardGrid.rarityLine(hovered.rarity());
            if (rarity != null) {
                tooltip.add(Math.min(1, tooltip.size()), rarity);
            }
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private Component unlockedLabel() {
        return Component.translatable(unlockedOnly ? "screen.jadm.binder.unlocked_only"
                : "screen.jadm.binder.all_cards");
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xE0181820);
        g.renderOutline(left, top, WIDTH, HEIGHT, 0xFF5070A0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Right-click empties the search box, like the creative inventory's.
        if (button == 1 && search.isMouseOver(mouseX, mouseY)) {
            search.setValue("");
            setFocused(search);
            return true;
        }
        CardGrid.Entry e = grid.at(mouseX, mouseY);
        if (e != null) {
            PacketDistributor.sendToServer(new CollectionActionPayload(Action.WITHDRAW,
                    hand == InteractionHand.OFF_HAND, e.code(), hasShiftDown(), e.rarity()));
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // Keep typing going into the search after a filter button is clicked.
        setFocused(search);
        return handled;
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
