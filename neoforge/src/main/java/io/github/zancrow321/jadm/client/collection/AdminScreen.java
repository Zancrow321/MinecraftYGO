package io.github.zancrow321.jadm.client.collection;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.admin.AdminMenu;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.JadmItems;
import io.github.zancrow321.jadm.network.AdminActionPayload;
import io.github.zancrow321.jadm.network.AdminActionPayload.Action;
import io.github.zancrow321.jadm.network.AdminPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The admin menu for operators: any product or card (in any rarity) and Duel Points, for yourself, another player or
 * everyone online. It only sends what was clicked; the server checks the sender is an operator and does the rest.
 */
public final class AdminScreen extends Screen {
    private static final int WIDTH = 330;
    private static final int HEIGHT = 234;
    private static final int ROW = 20;
    private static final int ROWS = 7;
    private static final int PLAYER_ROW = 14;
    private static final int PLAYER_ROWS = 8;
    private static final int GOLD = 0xFFFFD040;
    private static final int DIM = 0xFF8FA4B8;

    private enum Tab { PRODUCTS, CARDS, POINTS }

    private enum Kind { ALL, BOOSTER, DECK, TIN }

    /** A product with the item that stands for it. */
    private record Entry(Products.Product product, ItemStack item, Kind kind) {
    }

    private static List<Entry> allProducts;
    private static List<Integer> allCards;
    /** The tab the menu opens on: the last one used. */
    private static Tab tab = Tab.PRODUCTS;

    private List<AdminPayload.Player> players;
    private boolean points;
    /** A player's name, empty for yourself, or {@link AdminMenu#EVERYONE}. */
    private String target = "";
    private String productSearch = "";
    private String cardSearch = "";
    private Kind kind = Kind.ALL;
    private final CardFilter cardFilter = new CardFilter();
    private Rarity rarity = Rarity.COMMON;
    private String count = "1";
    private String amount = "1000";
    private List<Entry> shownProducts = List.of();
    private double scroll;
    private int cardPage;
    private CardGrid grid;
    private Button targetButton;
    private int left;
    private int top;

    public AdminScreen(AdminPayload payload) {
        super(Component.translatable("screen.jadm.admin"));
        players = payload.players();
        points = payload.points();
    }

    /** New balances, or who is online, from the server. */
    public void update(AdminPayload payload) {
        boolean wasPoints = points;
        players = payload.players();
        points = payload.points();
        if (!target.isEmpty() && !target.equals(AdminMenu.EVERYONE)
                && players.stream().noneMatch(p -> p.name().equals(target))) {
            target = "";
        }
        if (wasPoints != points) {
            rebuildWidgets();
        } else if (targetButton != null) {
            targetButton.setMessage(targetLabel());
        }
    }

    // ---- layout

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        targetButton = addRenderableWidget(Button.builder(targetLabel(), b -> {
            cycleTarget(hasShiftDown() ? -1 : 1);
            b.setMessage(targetLabel());
        }).bounds(left + WIDTH - 148, top + 4, 140, 16).build());
        for (Tab t : Tab.values()) {
            Button button = addRenderableWidget(Button.builder(Component.translatable("screen.jadm.admin.tab."
                    + t.name().toLowerCase(Locale.ROOT)), b -> {
                tab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(left + 8 + t.ordinal() * 82, top + 24, 80, 16).build());
            button.active = tab != t;
        }
        switch (tab) {
            case PRODUCTS -> initProducts();
            case CARDS -> initCards();
            case POINTS -> initPoints();
        }
    }

    private int contentTop() {
        return top + 46;
    }

    private int listTop() {
        return top + 66;
    }

    private int bottom() {
        return top + HEIGHT - 20;
    }

    private void initProducts() {
        EditBox search = addRenderableWidget(new EditBox(font, left + 8, contentTop(), 150, 14,
                Component.translatable("screen.jadm.search")));
        search.setHint(Component.translatable("screen.jadm.admin.search_products"));
        search.setMaxLength(64);
        search.setValue(productSearch);
        search.setResponder(s -> {
            productSearch = s;
            scroll = 0;
            filterProducts();
        });
        setInitialFocus(search);
        addRenderableWidget(Button.builder(kindLabel(), b -> {
            kind = Kind.values()[(kind.ordinal() + (hasShiftDown() ? Kind.values().length - 1 : 1))
                    % Kind.values().length];
            b.setMessage(kindLabel());
            scroll = 0;
            filterProducts();
        }).bounds(left + 162, contentTop() - 1, 76, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.jadm.admin.random_pack"),
                b -> send(Action.RANDOM_PACK, "", 0, count())).bounds(left + 242, contentTop() - 1, 80, 16).build());
        initCount();
        filterProducts();
    }

    private void initCards() {
        EditBox search = addRenderableWidget(new EditBox(font, left + 8, contentTop(), 130, 14,
                Component.translatable("screen.jadm.search")));
        search.setHint(Component.translatable("screen.jadm.admin.search_cards"));
        search.setMaxLength(64);
        search.setValue(cardSearch);
        search.setResponder(s -> {
            cardSearch = s;
            cardPage = 0;
            filterCards();
        });
        setInitialFocus(search);
        addRenderableWidget(Button.builder(cardFilter.kindLabel(), b -> {
            cardFilter.nextKind(hasShiftDown() ? -1 : 1);
            b.setMessage(cardFilter.kindLabel());
            cardPage = 0;
            filterCards();
        }).bounds(left + 142, contentTop() - 1, 70, 16).build());
        addRenderableWidget(Button.builder(rarityLabel(), b -> {
            rarity = Rarity.values()[(rarity.ordinal() + (hasShiftDown() ? Rarity.values().length - 1 : 1))
                    % Rarity.values().length];
            b.setMessage(rarityLabel());
            filterCards();
        }).bounds(left + 216, contentTop() - 1, 106, 16).build());
        grid = new CardGrid(left + 12, listTop(), 10, 3, 27);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> grid.page = Math.max(0, grid.page - 1))
                .bounds(left + WIDTH - 52, bottom() - 2, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                b -> grid.page = Math.min(grid.pages() - 1, grid.page + 1))
                .bounds(left + WIDTH - 28, bottom() - 2, 20, 16).build());
        initCount();
        filterCards();
    }

    /** The item count for products and cards, with quick picks. */
    private void initCount() {
        EditBox box = addRenderableWidget(new EditBox(font, left + 52, bottom(), 30, 12,
                Component.translatable("screen.jadm.admin.count")));
        box.setFilter(s -> s.matches("\\d{0,3}"));
        box.setValue(count);
        box.setResponder(s -> count = s);
        int x = left + 86;
        for (int n : new int[]{1, 16, 64}) {
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(n)), b -> box.setValue(String.valueOf(n)))
                    .bounds(x, bottom() - 2, 22, 16).build());
            x += 24;
        }
    }

    private void initPoints() {
        if (!points) {
            return;
        }
        EditBox box = addRenderableWidget(new EditBox(font, left + 8, contentTop(), 80, 14,
                Component.translatable("screen.jadm.admin.amount")));
        box.setFilter(s -> s.matches("\\d{0,9}"));
        box.setValue(amount);
        box.setResponder(s -> amount = s);
        setInitialFocus(box);
        int x = left + 94;
        for (Action action : new Action[]{Action.POINTS_GIVE, Action.POINTS_TAKE, Action.POINTS_SET}) {
            addRenderableWidget(Button.builder(Component.translatable("screen.jadm.admin.points."
                    + action.name().substring(7).toLowerCase(Locale.ROOT)), b -> send(action, "", 0, amount()))
                    .bounds(x, contentTop() - 1, 74, 16).build());
            x += 76;
        }
        x = left + 8;
        for (int n : new int[]{100, 1000, 10000, 100000}) {
            addRenderableWidget(Button.builder(Component.literal(String.format("%,d", n)),
                    b -> box.setValue(String.valueOf(n))).bounds(x, contentTop() + 18, 52, 16).build());
            x += 54;
        }
    }

    // ---- data

    private static List<Entry> allProducts() {
        if (allProducts == null) {
            List<Entry> list = new ArrayList<>();
            for (Products.Product product : JadmData.products().products()) {
                ItemStack item = AdminMenu.item(product);
                if (!item.isEmpty()) {
                    list.add(new Entry(product, item, item.is(JadmItems.TIN.get()) ? Kind.TIN
                            : item.is(JadmItems.STRUCTURE_DECK.get()) ? Kind.DECK : Kind.BOOSTER));
                }
            }
            allProducts = List.copyOf(list);
        }
        return allProducts;
    }

    /** Every card but tokens and alternate artworks (whose passcode is within ten of the original's), by name. */
    private static List<Integer> allCards() {
        if (allCards == null) {
            allCards = JadmData.cards().all().stream().filter(c -> !c.is(OcgConstants.TYPE_TOKEN)
                            && (c.data().alias() == 0 || Math.abs(c.code() - c.data().alias()) > 10))
                    .sorted(Comparator.comparing(CardInfo::name, String.CASE_INSENSITIVE_ORDER))
                    .map(CardInfo::code).toList();
        }
        return allCards;
    }

    private void filterProducts() {
        String f = productSearch.trim().toLowerCase(Locale.ROOT);
        shownProducts = allProducts().stream()
                .filter(e -> kind == Kind.ALL || e.kind() == kind)
                .filter(e -> f.isEmpty() || e.product().name().toLowerCase(Locale.ROOT).contains(f)
                        || e.product().code().toLowerCase(Locale.ROOT).startsWith(f))
                .toList();
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    /** Cards whose name has the search in it, or whose passcode it is. */
    private void filterCards() {
        String f = cardSearch.trim().toLowerCase(Locale.ROOT);
        int passcode = f.matches("\\d{1,9}") ? Integer.parseInt(f) : -1;
        List<CardGrid.Entry> list = new ArrayList<>();
        for (int code : allCards()) {
            if ((f.isEmpty() || code == passcode || CardGrid.name(code).toLowerCase(Locale.ROOT).contains(f))
                    && cardFilter.test(code)) {
                list.add(new CardGrid.Entry(code, rarity, 1));
            }
        }
        grid.entries = list;
        grid.page = Math.max(0, Math.min(cardPage, grid.pages() - 1));
    }

    private int maxScroll() {
        return Math.max(0, shownProducts.size() * ROW - ROWS * ROW);
    }

    private int count() {
        return count.isEmpty() ? 1 : Math.max(1, Math.min(AdminMenu.MAX_ITEMS, Integer.parseInt(count)));
    }

    private int amount() {
        return amount.isEmpty() ? 0 : Integer.parseInt(amount);
    }

    // ---- target

    private String self() {
        return minecraft == null || minecraft.player == null ? "" : minecraft.player.getScoreboardName();
    }

    /** Yourself, then the others online, then everyone. */
    private List<String> targets() {
        List<String> targets = new ArrayList<>();
        targets.add("");
        for (AdminPayload.Player player : players) {
            if (!player.name().equals(self())) {
                targets.add(player.name());
            }
        }
        targets.add(AdminMenu.EVERYONE);
        return targets;
    }

    private void cycleTarget(int step) {
        List<String> targets = targets();
        int i = Math.max(0, targets.indexOf(target));
        target = targets.get(Math.floorMod(i + step, targets.size()));
    }

    private Component targetLabel() {
        Component who = target.isEmpty() ? Component.translatable("screen.jadm.admin.you", self())
                : target.equals(AdminMenu.EVERYONE) ? Component.translatable("screen.jadm.admin.everyone")
                : Component.literal(target);
        return Component.translatable("screen.jadm.admin.for", who);
    }

    private Component kindLabel() {
        return Component.translatable("screen.jadm.admin.kind." + kind.name().toLowerCase(Locale.ROOT));
    }

    private Component rarityLabel() {
        return Component.literal(CardItem.rarityName(rarity)).withStyle(CardItem.color(rarity));
    }

    private void send(Action action, String id, int code, int amount) {
        PacketDistributor.sendToServer(new AdminActionPayload(action, target, id, code, rarity, amount));
    }

    private void click() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
        }
    }

    // ---- drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xF0181820);
        g.renderOutline(left, top, WIDTH, HEIGHT, 0xFFC04040);
        g.fill(left + 8, top + 42, left + WIDTH - 8, top + 43, 0x80C04040);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + 8, top + 8, GOLD);
        switch (tab) {
            case PRODUCTS -> renderProducts(g, mouseX, mouseY);
            case CARDS -> renderCards(g, mouseX, mouseY);
            case POINTS -> renderPoints(g, mouseX, mouseY);
        }
    }

    private void renderCount(GuiGraphics g, Component hint) {
        g.drawString(font, Component.translatable("screen.jadm.admin.count"), left + 8, bottom() + 2, DIM, false);
        int right = tab == Tab.CARDS ? left + WIDTH - 56 : left + WIDTH - 8;
        String text = font.plainSubstrByWidth(hint.getString(), right - left - 166);
        g.drawString(font, text, right - font.width(text), bottom() + 2, DIM, false);
    }

    private void renderProducts(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 8;
        int w = WIDTH - 16;
        int y0 = listTop();
        Entry hovered = null;
        g.enableScissor(x, y0, x + w, y0 + ROWS * ROW);
        for (int i = 0; i < shownProducts.size(); i++) {
            int y = y0 + i * ROW - (int) scroll;
            if (y + ROW < y0 || y > y0 + ROWS * ROW) {
                continue;
            }
            Entry e = shownProducts.get(i);
            boolean over = mouseX >= x && mouseX < x + w && mouseY >= Math.max(y, y0)
                    && mouseY < Math.min(y + ROW, y0 + ROWS * ROW);
            g.fill(x, y + 1, x + w, y + ROW - 1, over ? 0x50FFFFFF : 0x30000000);
            g.renderItem(e.item(), x + 2, y + 2);
            g.drawString(font, font.plainSubstrByWidth(e.product().name(), w - 30), x + 22, y + 2, 0xFFFFFFFF);
            g.drawString(font, e.product().code() + " · " + e.product().date() + " · "
                    + Component.translatable("screen.jadm.admin.kind." + e.kind().name().toLowerCase(Locale.ROOT))
                    .getString(), x + 22, y + 11, DIM, false);
            if (over && mouseX < x + 20) {
                hovered = e;
            }
        }
        g.disableScissor();
        if (shownProducts.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.jadm.admin.nothing"), left + WIDTH / 2,
                    y0 + ROWS * ROW / 2 - 4, DIM);
        }
        if (maxScroll() > 0) {
            int h = ROWS * ROW;
            int bar = Math.max(8, h * h / (shownProducts.size() * ROW));
            int barY = y0 + (int) ((h - bar) * scroll / maxScroll());
            g.fill(left + WIDTH - 6, barY, left + WIDTH - 4, barY + bar, 0xA0FFFFFF);
        }
        renderCount(g, Component.translatable("screen.jadm.admin.hint.products", shownProducts.size()));
        if (hovered != null) {
            g.renderTooltip(font, hovered.item(), mouseX, mouseY);
        }
    }

    private void renderCards(GuiGraphics g, int mouseX, int mouseY) {
        grid.render(g, font, mouseX, mouseY);
        if (grid.entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.jadm.admin.nothing"), left + WIDTH / 2,
                    listTop() + grid.height() / 2 - 4, DIM);
        }
        renderCount(g, Component.translatable("screen.jadm.admin.hint.cards", grid.entries.size()));
        CardGrid.Entry hovered = grid.at(mouseX, mouseY);
        if (hovered != null) {
            List<Component> tooltip = CardGrid.tooltip(hovered.code(),
                    Component.translatable("screen.jadm.admin.give_card", count()).getString());
            Component line = CardGrid.rarityLine(rarity);
            if (line != null) {
                tooltip.add(Math.min(1, tooltip.size()), line);
            }
            tooltip.add(Math.min(1, tooltip.size()), Component.literal("#" + hovered.code()).withStyle(
                    net.minecraft.ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private int playersTop() {
        return contentTop() + 52;
    }

    private void renderPoints(GuiGraphics g, int mouseX, int mouseY) {
        if (!points) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    Component.translatable("screen.jadm.admin.points_off"), WIDTH - 40);
            for (int i = 0; i < lines.size(); i++) {
                g.drawCenteredString(font, lines.get(i), left + WIDTH / 2, contentTop() + 30 + i * 10, DIM);
            }
            return;
        }
        int x = left + 8;
        int w = WIDTH - 16;
        int y0 = playersTop();
        g.drawString(font, Component.translatable("screen.jadm.admin.balances"), x, y0 - 10, GOLD, false);
        int first = (int) scroll;
        for (int i = first; i < Math.min(players.size(), first + PLAYER_ROWS); i++) {
            AdminPayload.Player player = players.get(i);
            int y = y0 + (i - first) * PLAYER_ROW;
            boolean chosen = target.equals(AdminMenu.EVERYONE) || player.name().equals(target)
                    || target.isEmpty() && player.name().equals(self());
            boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + PLAYER_ROW;
            g.fill(x, y, x + w, y + PLAYER_ROW - 1, chosen ? 0x60C04040 : over ? 0x50FFFFFF : 0x30000000);
            g.drawString(font, player.name(), x + 4, y + 3, 0xFFFFFFFF, false);
            String balance = io.github.zancrow321.jadm.client.shop.ClientPoints.format(player.balance());
            g.drawString(font, balance, x + w - 4 - font.width(balance), y + 3, GOLD, false);
        }
        g.drawString(font, Component.translatable("screen.jadm.admin.hint.points"), x,
                y0 + PLAYER_ROWS * PLAYER_ROW + 6, DIM, false);
    }

    // ---- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        switch (tab) {
            case PRODUCTS -> {
                int y0 = listTop();
                if (mouseX >= left + 8 && mouseX < left + WIDTH - 8 && mouseY >= y0 && mouseY < y0 + ROWS * ROW) {
                    int i = (int) ((mouseY - y0 + scroll) / ROW);
                    if (i < shownProducts.size()) {
                        send(Action.PRODUCT, shownProducts.get(i).product().id(), 0, count());
                        click();
                        return true;
                    }
                }
            }
            case CARDS -> {
                CardGrid.Entry e = grid.at(mouseX, mouseY);
                if (e != null) {
                    send(Action.CARD, "", e.code(), count());
                    click();
                    return true;
                }
            }
            case POINTS -> {
                int y0 = playersTop();
                if (points && mouseX >= left + 8 && mouseX < left + WIDTH - 8 && mouseY >= y0
                        && mouseY < y0 + PLAYER_ROWS * PLAYER_ROW) {
                    int i = (int) scroll + (int) ((mouseY - y0) / PLAYER_ROW);
                    if (i < players.size()) {
                        String name = players.get(i).name();
                        target = name.equals(self()) ? "" : name;
                        targetButton.setMessage(targetLabel());
                        click();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        switch (tab) {
            case PRODUCTS -> scroll = Mth.clamp(scroll - scrollY * ROW, 0, maxScroll());
            case CARDS -> grid.page = Math.max(0, Math.min(grid.pages() - 1, grid.page - (int) Math.signum(scrollY)));
            case POINTS -> scroll = Mth.clamp(scroll - Math.signum(scrollY), 0,
                    Math.max(0, players.size() - PLAYER_ROWS));
        }
        if (tab == Tab.CARDS) {
            cardPage = grid.page;
        }
        return true;
    }

    @Override
    public void tick() {
        if (grid != null) {
            cardPage = grid.page;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
