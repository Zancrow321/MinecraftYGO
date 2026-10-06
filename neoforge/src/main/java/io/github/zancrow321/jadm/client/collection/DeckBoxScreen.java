package io.github.zancrow321.jadm.client.collection;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.data.DeckRules;
import io.github.zancrow321.jadm.item.BinderItem;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.DeckBoxItem;
import io.github.zancrow321.jadm.item.JadmItems;
import io.github.zancrow321.jadm.network.CollectionActionPayload;
import io.github.zancrow321.jadm.network.CollectionActionPayload.Action;
import net.minecraft.Util;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Builds the deck in a deck box: the main deck and below it the extra deck on the left (click to put a card back),
 * the cards you own on the right (click to add; Fusion, Synchro, Xyz and Link monsters go to the extra deck), and
 * whether the deck is legal at the top. The cards you own can be filtered by type, attribute and level and sorted;
 * the deck can be copied out and pasted in as a YDK list, the format of YGOPro and most deck builders.
 */
public final class DeckBoxScreen extends Screen {
    private static final int WIDTH = 380;
    private static final int HEIGHT = 236;

    private final InteractionHand hand;
    private CardGrid deckGrid;
    private CardGrid extraGrid;
    private CardGrid ownedGrid;
    private EditBox search;
    private final CardFilter filter = new CardFilter();
    /** A line shown over the legality status for a few seconds, such as what an import left out. */
    private Component notice;
    private int noticeColor;
    private long noticeUntil;
    private int left;
    private int top;

    public DeckBoxScreen(InteractionHand hand) {
        super(Component.translatable("screen.jadm.deck_box"));
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
        int right = left + WIDTH / 2 + 10;
        // Smaller than the deck's cards, to make room for the filter row.
        ownedGrid = new CardGrid(right, top + 56, 6, 4, 24);
        search = addRenderableWidget(new EditBox(font, right, top + 22, 108, 14,
                Component.translatable("screen.jadm.search")));
        search.setHint(Component.translatable("screen.jadm.deck_box.owned"));
        // Locked cards stay in view but can't be added (unless the server allows them in decks).
        ownedGrid.locked = code -> !JadmData.playable(minecraft.player).test(code);
        deckGrid.locked = ownedGrid.locked;
        extraGrid.locked = ownedGrid.locked;
        cycle(filter.sortLabel(), step -> filter.nextSort(step), filter::sortLabel, right + 112, top + 22, 58);
        cycle(filter.kindLabel(), step -> filter.nextKind(step), filter::kindLabel, right, top + 38, 56);
        cycle(filter.attributeLabel(), step -> filter.nextAttribute(step), filter::attributeLabel, right + 57, top + 38,
                56);
        cycle(filter.levelLabel(), step -> filter.nextLevel(step), filter::levelLabel, right + 114, top + 38, 56);
        int deckButtons = left + WIDTH / 2 - 6;
        addRenderableWidget(Button.builder(Component.translatable("screen.jadm.deck_box.clear"),
                b -> send(Action.DECK_CLEAR, 0)).bounds(deckButtons - 106, top + 21, 32, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.jadm.deck_box.export"),
                        b -> exportDeck()).bounds(deckButtons - 73, top + 21, 36, 16)
                .tooltip(Tooltip.create(Component.translatable("screen.jadm.deck_box.export.hint"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.jadm.deck_box.import"),
                        b -> importDeck()).bounds(deckButtons - 36, top + 21, 36, 16)
                .tooltip(Tooltip.create(Component.translatable("screen.jadm.deck_box.import.hint"))).build());
        int buttonsY = top + HEIGHT - 20;
        pager(deckGrid);
        pager(extraGrid);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page(ownedGrid, -1))
                .bounds(left + WIDTH / 2 + 10, buttonsY, 16, 14).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page(ownedGrid, 1))
                .bounds(left + WIDTH - 26, buttonsY, 16, 14).build());
    }

    /** A button that steps through a filter's choices: forward on click, back on shift-click. */
    private void cycle(Component label, Consumer<Integer> step, java.util.function.Supplier<Component> now, int x,
            int y, int w) {
        addRenderableWidget(Button.builder(label, b -> {
            step.accept(hasShiftDown() ? -1 : 1);
            b.setMessage(now.get());
            ownedGrid.page = 0;
        }).bounds(x, y, w, 14).tooltip(Tooltip.create(Component.translatable("screen.jadm.deck_box.cycle")))
                .build());

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
            if (stack.is(JadmItems.BINDER.get())) {
                BinderItem.collection(stack).counts().forEach((code, n) -> counts.merge(code, n, Integer::sum));
            } else if (stack.is(JadmItems.CARD.get()) && CardItem.code(stack) != 0) {
                counts.merge(CardItem.code(stack), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ItemStack box = box();
        if (!box.is(JadmItems.DECK_BOX.get())) {
            onClose();
            return;
        }
        var deck = DeckBoxItem.deck(box);
        Map<Integer, Integer> inMain = new HashMap<>();
        deck.main().forEach(c -> inMain.merge(c, 1, Integer::sum));
        Map<Integer, Integer> inExtra = new HashMap<>();
        deck.extra().forEach(c -> inExtra.merge(c, 1, Integer::sum));
        deckGrid.set(inMain, "", code -> true, CardFilter.deckOrder());
        extraGrid.set(inExtra, "", code -> true, CardFilter.deckOrder());
        ownedGrid.set(owned(), search.getValue(), filter, filter.order());
        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(font, box.getHoverName(), left + 8, top + 6, 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable("screen.jadm.deck_box.main", deck.main().size()), left + 8,
                top + 26, 0xFFAAAAAA, false);
        g.drawString(font, Component.translatable("screen.jadm.deck_box.extra", deck.extra().size()),
                left + 8, top + 175, 0xFFAAAAAA, false);
        List<String> problems = DeckBoxItem.problems(box, minecraft.player, JadmData.banlist(minecraft.player));
        Component status = problems.isEmpty()
                ? Component.translatable("item.jadm.deck_box.legal")
                : Component.literal(problems.get(0));
        int statusColor = problems.isEmpty() ? 0xFF60E060 : 0xFFFF7070;
        if (notice != null && Util.getMillis() < noticeUntil) {
            status = notice;
            statusColor = noticeColor;
        }
        g.drawString(font, font.plainSubstrByWidth(status.getString(), WIDTH - 140), left + 130, top + 6,
                statusColor, false);
        boolean overStats = drawStats(g, deck.main(), mouseX, mouseY);
        deckGrid.render(g, font, mouseX, mouseY);
        extraGrid.render(g, font, mouseX, mouseY);
        ownedGrid.render(g, font, mouseX, mouseY);
        if (ownedGrid.entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.jadm.deck_box.no_cards"),
                    left + WIDTH * 3 / 4, top + HEIGHT / 2, 0xFFAAAAAA);
        }

        if (overStats) {
            int[] n = groups(deck.main());
            g.renderTooltip(font, Component.translatable("screen.jadm.deck_box.stats", n[0], n[1], n[2]),
                    mouseX, mouseY);
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

    /**
     * Monster, spell and trap counts of the main deck, right of the extra deck's label: each a count beside a little
     * card in that frame colour.
     */
    private boolean drawStats(GuiGraphics g, List<Integer> main, int mouseX, int mouseY) {
        int[] n = groups(main);
        int[] colors = {0xFFC08040, 0xFF1D9E74, 0xFFBC5A84};
        int x = left + WIDTH / 2 - 6;
        int start = x;
        for (int i = 2; i >= 0; i--) {
            String text = String.valueOf(n[i]);
            x -= font.width(text);
            g.drawString(font, text, x, top + 175, 0xFFDDDDDD, false);
            x -= 8;
            g.fill(x, top + 174, x + 6, top + 183, colors[i]);
            g.renderOutline(x, top + 174, 6, 9, 0xFF000000);
            x -= 6;
        }
        return mouseY >= top + 174 && mouseY < top + 184 && mouseX >= x && mouseX < start;
    }

    private static int[] groups(List<Integer> main) {
        int[] n = new int[4];
        main.forEach(code -> n[CardFilter.group(code)]++);
        return n;
    }

    private void notice(Component text, int color) {
        notice = text;
        noticeColor = color;
        noticeUntil = Util.getMillis() + 6000;
    }

    /** Copies the deck to the clipboard as a YDK list. */
    private void exportDeck() {
        var deck = DeckBoxItem.deck(box());
        List<Integer> main = new ArrayList<>(deck.main());
        List<Integer> extra = new ArrayList<>(deck.extra());
        main.sort(CardFilter.deckOrder());
        extra.sort(CardFilter.deckOrder());
        minecraft.keyboardHandler.setClipboard(Ydk.write(main, extra));
        notice(Component.translatable("screen.jadm.deck_box.exported", main.size() + extra.size()),
                0xFF60E060);
    }

    /**
     * Replaces the deck with the YDK list in the clipboard, built from the cards you own: the current deck goes back
     * first, and a card you have no copy of left is skipped. Another printing of the same card (an alternate art)
     * stands in for one you don't have.
     */
    private void importDeck() {
        Ydk.Deck list = Ydk.read(minecraft.keyboardHandler.getClipboard());
        if (list == null) {
            notice(Component.translatable("screen.jadm.deck_box.import.none"), 0xFFFF7070);
            return;
        }
        Map<Integer, Integer> available = owned();
        var deck = DeckBoxItem.deck(box());
        deck.main().forEach(c -> available.merge(c, 1, Integer::sum));
        deck.extra().forEach(c -> available.merge(c, 1, Integer::sum));
        send(Action.DECK_CLEAR, 0);
        // The same checks the server makes, so the notice can say what was left out.
        var banlist = JadmData.banlist(minecraft.player);
        var playable = JadmData.playable(minecraft.player);
        Map<Integer, Integer> copies = new HashMap<>();
        int main = 0;
        int extra = 0;
        Set<String> missing = new LinkedHashSet<>();
        Set<String> refused = new LinkedHashSet<>();
        List<Integer> wanted = new ArrayList<>(list.main());
        wanted.addAll(list.extra());
        for (int code : wanted) {
            int have = pick(available, code);
            if (have == 0) {
                missing.add(CardGrid.name(code));
                continue;
            }
            boolean toExtra = DeckRules.isExtra(JadmData.cards().card(have));
            if (!playable.test(have) || copies.getOrDefault(have, 0) >= banlist.limit(have)
                    || (toExtra ? extra >= DeckRules.EXTRA_MAX : main >= DeckRules.MAIN_MAX)) {
                refused.add(CardGrid.name(have));
                continue;
            }
            available.merge(have, -1, Integer::sum);
            copies.merge(have, 1, Integer::sum);
            if (toExtra) {
                extra++;
            } else {
                main++;
            }
            send(Action.DECK_ADD, have);
        }
        int added = main + extra;
        if (missing.isEmpty() && refused.isEmpty()) {
            notice(Component.translatable("screen.jadm.deck_box.imported", added), 0xFF60E060);
            return;
        }
        List<String> parts = new ArrayList<>();
        if (!missing.isEmpty()) {
            parts.add(Component.translatable("screen.jadm.deck_box.imported.missing",
                    String.join(", ", missing)).getString());
        }
        if (!refused.isEmpty()) {
            parts.add(Component.translatable("screen.jadm.deck_box.imported.refused",
                    String.join(", ", refused)).getString());
        }
        Component text = Component.translatable("screen.jadm.deck_box.imported.partly", added,
                wanted.size(), String.join(". ", parts));
        notice(text, 0xFFFFC060);
        // The whole list may not fit the top line, so it goes to chat too.
        minecraft.gui.getChat().addMessage(text);
    }

    /** The code of a copy you can still add for {@code code}: that card or another printing of it, or 0. */
    private static int pick(Map<Integer, Integer> available, int code) {
        if (available.getOrDefault(code, 0) > 0) {
            return code;
        }
        int base = base(code);
        for (var e : available.entrySet()) {
            if (e.getValue() > 0 && base(e.getKey()) == base) {
                return e.getKey();
            }
        }
        return 0;
    }

    /** The original printing's code: alternate arts carry it as their alias. */
    private static int base(int code) {
        var card = JadmData.cards().card(code);
        return card == null || card.data().alias() == 0 ? code : card.data().alias();
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
        if (super.mouseClicked(mouseX, mouseY, button)) {
            // A clicked button would keep focus and with it its tooltip.
            if (getFocused() instanceof Button) {
                setFocused(null);
            }
            return true;
        }
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
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= left + WIDTH / 2.0 && mouseY < ownedGrid.y) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
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
