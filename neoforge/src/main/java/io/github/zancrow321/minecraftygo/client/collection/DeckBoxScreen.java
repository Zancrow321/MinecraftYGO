package io.github.zancrow321.minecraftygo.client.collection;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.item.BinderItem;
import io.github.zancrow321.minecraftygo.item.CardItem;
import io.github.zancrow321.minecraftygo.item.DeckBoxItem;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the deck in a deck box: the main deck and below it the extra deck on the left (click to put a card back),
 * the cards you own on the right (click to add; Fusion, Synchro, Xyz and Link monsters go to the extra deck), and
 * whether the deck is legal at the top.
 */
public final class DeckBoxScreen extends Screen {
    private static final int WIDTH = 380;
    private static final int HEIGHT = 236;

    private final InteractionHand hand;
    private CardGrid deckGrid;
    private CardGrid extraGrid;
    private CardGrid ownedGrid;
    private EditBox search;
    private int left;
    private int top;

    public DeckBoxScreen(InteractionHand hand) {
        super(Component.translatable("screen.minecraftygo.deck_box"));
        this.hand = hand;
    }

    private ItemStack box() {
        return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        deckGrid = new CardGrid(left + 8, top + 40, 6, 3, 26);
        // Smaller cards, so the extra deck fits under the main deck on a 240-pixel-high GUI.
        extraGrid = new CardGrid(left + 8, top + 186, 7, 1, 20);
        ownedGrid = new CardGrid(left + WIDTH / 2 + 10, top + 40, 6, 4, 26);
        search = addRenderableWidget(new EditBox(font, left + WIDTH / 2 + 10, top + 22, 100, 14,
                Component.translatable("screen.minecraftygo.search")));
        search.setHint(Component.translatable("screen.minecraftygo.deck_box.owned"));
        // Locked cards stay in view but can't be added (unless the server allows them in decks).
        ownedGrid.locked = code -> !YgoData.playable(minecraft.player).test(code);
        deckGrid.locked = ownedGrid.locked;
        extraGrid.locked = ownedGrid.locked;
        addRenderableWidget(Button.builder(Component.translatable("screen.minecraftygo.deck_box.clear"),
                b -> send(Action.DECK_CLEAR, 0)).bounds(left + WIDTH / 2 - 52, top + 21, 44, 16).build());
        int buttonsY = top + HEIGHT - 20;
        pager(deckGrid);
        pager(extraGrid);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page(ownedGrid, -1))
                .bounds(left + WIDTH / 2 + 10, buttonsY, 16, 14).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page(ownedGrid, 1))
                .bounds(left + WIDTH - 26, buttonsY, 16, 14).build());
    }

    /** Page buttons on both sides of a grid's page number. */
    private void pager(CardGrid grid) {
        int y = grid.y + grid.height() + 1;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page(grid, -1))
                .bounds(grid.x + grid.width() / 2 - 40, y, 16, 12).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page(grid, 1))
                .bounds(grid.x + grid.width() / 2 + 24, y, 16, 12).build());
    }

    private static void page(CardGrid grid, int delta) {
        grid.page = Math.max(0, Math.min(grid.pages() - 1, grid.page + delta));
    }

    /** What the player can add: everything in their binders plus loose cards. */
    private Map<Integer, Integer> owned() {
        Map<Integer, Integer> counts = new HashMap<>();
        var inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(YgoItems.BINDER.get())) {
                BinderItem.collection(stack).counts().forEach((code, n) -> counts.merge(code, n, Integer::sum));
            } else if (stack.is(YgoItems.CARD.get()) && CardItem.code(stack) != 0) {
                counts.merge(CardItem.code(stack), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ItemStack box = box();
        if (!box.is(YgoItems.DECK_BOX.get())) {
            onClose();
            return;
        }
        var deck = DeckBoxItem.deck(box);
        Map<Integer, Integer> inMain = new HashMap<>();
        deck.main().forEach(c -> inMain.merge(c, 1, Integer::sum));
        Map<Integer, Integer> inExtra = new HashMap<>();
        deck.extra().forEach(c -> inExtra.merge(c, 1, Integer::sum));
        deckGrid.set(inMain, "");
        extraGrid.set(inExtra, "");
        ownedGrid.set(owned(), search.getValue());
        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(font, box.getHoverName(), left + 8, top + 6, 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable("screen.minecraftygo.deck_box.main", deck.main().size()), left + 8,
                top + 26, 0xFFAAAAAA, false);
        g.drawString(font, Component.translatable("screen.minecraftygo.deck_box.extra", deck.extra().size()),
                left + 8, top + 175, 0xFFAAAAAA, false);
        List<String> problems = DeckBoxItem.problems(box, minecraft.player, YgoData.banlist(minecraft.player));
        Component status = problems.isEmpty()
                ? Component.translatable("item.minecraftygo.deck_box.legal")
                : Component.literal(problems.get(0));
        g.drawString(font, font.plainSubstrByWidth(status.getString(), WIDTH - 140), left + 130, top + 6,
                problems.isEmpty() ? 0xFF60E060 : 0xFFFF7070, false);
        deckGrid.render(g, font, mouseX, mouseY);
        extraGrid.render(g, font, mouseX, mouseY);
        ownedGrid.render(g, font, mouseX, mouseY);
        if (ownedGrid.entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.minecraftygo.deck_box.no_cards"),
                    left + WIDTH * 3 / 4, top + HEIGHT / 2, 0xFFAAAAAA);
        }

        CardGrid.Entry hovered = deckGrid.at(mouseX, mouseY);
        if (hovered == null) {
            hovered = extraGrid.at(mouseX, mouseY);
        }
        if (hovered != null) {
            g.renderComponentTooltip(font, withLock(hovered.code(), CardGrid.tooltip(hovered.code(), "Click: put back")),
                    mouseX, mouseY);
        }
        hovered = ownedGrid.at(mouseX, mouseY);
        if (hovered != null) {
            boolean locked = ownedGrid.locked.test(hovered.code());
            g.renderComponentTooltip(font, withLock(hovered.code(),
                    CardGrid.tooltip(hovered.code(), locked ? null : "Click: add to deck")), mouseX, mouseY);
        }
    }

    private List<Component> withLock(int code, List<Component> tooltip) {
        if (ownedGrid.locked.test(code)) {
            tooltip.add(Math.min(1, tooltip.size()), CardGrid.lockedLine(code));
        }
        return tooltip;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xE0181820);
        g.renderOutline(left, top, WIDTH, HEIGHT, 0xFF5070A0);
        g.fill(left + WIDTH / 2, top + 20, left + WIDTH / 2 + 1, top + HEIGHT - 4, 0xFF5070A0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        CardGrid.Entry e = deckGrid.at(mouseX, mouseY);
        if (e == null) {
            e = extraGrid.at(mouseX, mouseY);
        }
        if (e != null) {
            send(Action.DECK_REMOVE, e.code());
            return true;
        }
        e = ownedGrid.at(mouseX, mouseY);
        if (e != null) {
            send(Action.DECK_ADD, e.code());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        CardGrid grid = mouseX >= left + WIDTH / 2.0 ? ownedGrid : mouseY >= extraGrid.y - 14 ? extraGrid : deckGrid;
        page(grid, -(int) Math.signum(scrollY));
        return true;
    }

    private void send(Action action, int code) {
        PacketDistributor.sendToServer(new CollectionActionPayload(action, hand == InteractionHand.OFF_HAND, code,
                false));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
