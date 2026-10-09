package io.github.zancrow321.jadm.client.collection;

import io.github.zancrow321.jadm.admin.AdminMenu;
import io.github.zancrow321.jadm.collection.SetBook;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.network.SetBookActionPayload;
import io.github.zancrow321.jadm.network.SetBookActionPayload.Action;
import io.github.zancrow321.jadm.network.SetBookPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Set Collection Book: every set that is out with how much of it the player owns, and the reward for a complete
 * one to claim. Click a set to see its cards, the missing ones greyed out.
 */
public final class SetBookScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 226;
    private static final int ROW = 22;
    private static final int ROWS = 7;
    private static final int GOLD = 0xFFFFD040;
    private static final int GREEN = 0xFF60E070;
    private static final int DIM = 0xFF8FA4B8;
    private static final int BAR = 100;

    /** Which sets the list shows. */
    private enum Show {
        ALL, STARTED, COMPLETE, REWARD, MISSING
    }

    /** How the list is sorted. */
    private enum Sort {
        OLDEST, NEWEST, PERCENT, NAME
    }

    /** Which cards a set's page shows. */
    private enum Cards {
        ALL, MISSING, OWNED
    }

    /** A set in the list, with how many of its cards the player owns and the item that stands for it. */
    private record Row(SetBook.Entry set, int owned, ItemStack item) {
        boolean complete() {
            return owned == set.size();
        }

        float fraction() {
            return set.size() == 0 ? 0 : owned / (float) set.size();
        }
    }

    private Set<Integer> owned = Set.of();
    private Set<String> claimed = Set.of();
    private SetBookPayload.Reward reward;
    private List<Row> rows = List.of();
    private List<Row> shown = List.of();
    private final Map<String, ItemStack> items = new HashMap<>();

    private String query = "";
    private Show show = Show.ALL;
    private Sort sort = Sort.OLDEST;
    private double scroll;

    /** The set whose cards are open, or {@code null} for the list. */
    private Row open;
    private Cards cards = Cards.ALL;
    private CardGrid grid;
    private int left;
    private int top;

    public SetBookScreen(SetBookPayload payload) {
        super(Component.translatable("screen.jadm.sets"));
        update(payload);
    }

    /** New contents from the server: after a claim, or the book opened again. */
    public void update(SetBookPayload payload) {
        owned = new HashSet<>(payload.owned());
        claimed = new HashSet<>(payload.claimed());
        reward = payload.reward();
        List<Row> list = new ArrayList<>();
        for (SetBook.Entry set : SetBook.sets(net.minecraft.client.Minecraft.getInstance().player)) {
            ItemStack item = items.computeIfAbsent(set.product().id(), id -> AdminMenu.item(set.product()));
            list.add(new Row(set, set.owned(owned), item));
        }
        rows = list;
        if (open != null) {
            String id = open.set().product().id();
            open = rows.stream().filter(r -> r.set().product().id().equals(id)).findFirst().orElse(null);
        }
        filter();
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        if (open == null) {
            initList();
        } else {
            initSet();
        }
    }

    private void initList() {
        EditBox search = addRenderableWidget(new EditBox(font, left + 8, top + 20, 140, 14,
                Component.translatable("screen.jadm.search")));
        search.setHint(Component.translatable("screen.jadm.sets.search"));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            filter();
        });
        addRenderableWidget(Button.builder(showLabel(), b -> {
            show = Show.values()[Math.floorMod(show.ordinal() + (hasShiftDown() ? -1 : 1), Show.values().length)];
            b.setMessage(showLabel());
            filter();
        }).bounds(left + 152, top + 19, 92, 16).tooltip(Tooltip.create(
                Component.translatable("screen.jadm.deck_box.cycle"))).build());
        addRenderableWidget(Button.builder(sortLabel(), b -> {
            sort = Sort.values()[Math.floorMod(sort.ordinal() + (hasShiftDown() ? -1 : 1), Sort.values().length)];
            b.setMessage(sortLabel());
            filter();
        }).bounds(left + 248, top + 19, WIDTH - 256, 16).tooltip(Tooltip.create(
                Component.translatable("screen.jadm.deck_box.cycle"))).build());
        int ready = claimable().size();
        if (ready > 1) {
            addRenderableWidget(Button.builder(Component.translatable("screen.jadm.sets.claim_all", ready),
                    b -> send(Action.CLAIM_ALL, "")).bounds(left + WIDTH - 128, top + HEIGHT - 22, 120, 16).build());
        }
    }

    private void initSet() {
        grid = new CardGrid(left + 12, top + 56, 10, 3, 28);
        // Grey out the missing cards among all of them; on their own they show as they are.
        grid.missing = code -> cards == Cards.ALL && !owned.contains(code);
        fillGrid();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            open = null;
            rebuildWidgets();
        }).bounds(left + 8, top + 6, 16, 14).tooltip(Tooltip.create(Component.translatable("screen.jadm.sets.back")))
                .build());
        addRenderableWidget(Button.builder(cardsLabel(), b -> {
            cards = Cards.values()[Math.floorMod(cards.ordinal() + (hasShiftDown() ? -1 : 1), Cards.values().length)];
            b.setMessage(cardsLabel());
            fillGrid();
        }).bounds(left + 8, top + 38, 96, 14).build());
        if (claimable(open)) {
            addRenderableWidget(Button.builder(Component.translatable("screen.jadm.sets.claim")
                            .withStyle(ChatFormatting.GOLD), b -> send(Action.CLAIM, open.set().product().id()))
                    .bounds(left + WIDTH - 98, top + 36, 90, 16).build());
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> grid.page = Math.max(0, grid.page - 1))
                .bounds(left + 8, top + HEIGHT - 22, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                b -> grid.page = Math.min(grid.pages() - 1, grid.page + 1))
                .bounds(left + WIDTH - 28, top + HEIGHT - 22, 20, 16).build());
    }

    private void fillGrid() {
        List<CardGrid.Entry> list = new ArrayList<>();
        for (int code : open.set().cards()) {
            boolean have = owned.contains(code);
            if (cards == Cards.ALL || (cards == Cards.OWNED) == have) {
                list.add(new CardGrid.Entry(code, 1));
            }
        }
        grid.entries = list;
        grid.page = Math.max(0, Math.min(grid.page, grid.pages() - 1));
    }

    // ---- data

    private void filter() {
        String f = query.trim().toLowerCase(Locale.ROOT);
        Comparator<Row> order = switch (sort) {
            case OLDEST -> (a, b) -> 0;
            case NEWEST -> Comparator.comparing((Row r) -> r.set().product().date()).reversed();
            case PERCENT -> Comparator.comparing(Row::fraction).reversed().thenComparing(Row::owned,
                    Comparator.reverseOrder());
            case NAME -> Comparator.comparing((Row r) -> r.set().product().name(), String.CASE_INSENSITIVE_ORDER);
        };
        shown = rows.stream()
                .filter(r -> f.isEmpty() || r.set().product().name().toLowerCase(Locale.ROOT).contains(f)
                        || r.set().product().code().toLowerCase(Locale.ROOT).startsWith(f))
                .filter(r -> switch (show) {
                    case ALL -> true;
                    case STARTED -> r.owned() > 0 && !r.complete();
                    case COMPLETE -> r.complete();
                    case REWARD -> claimable(r);
                    case MISSING -> r.owned() == 0;
                })
                .sorted(order)
                .toList();
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private boolean claimable(Row row) {
        return row.complete() && reward != null && reward.any() && !claimed.contains(row.set().product().id());
    }

    private List<Row> claimable() {
        return rows.stream().filter(this::claimable).toList();
    }

    private int maxScroll() {
        return Math.max(0, shown.size() * ROW - ROWS * ROW);
    }

    private int listTop() {
        return top + 40;
    }

    /** What completing a set brings, e.g. "1,260 DP, 3 booster packs". */
    private Component rewardText(SetBook.Entry set) {
        List<String> parts = new ArrayList<>();
        if (reward.pointsPerCard() > 0) {
            parts.add(String.format("%,d %s", (long) reward.pointsPerCard() * set.size(), reward.symbol()));
        }
        if (reward.packs() > 0) {
            parts.add(Component.translatable("screen.jadm.sets.reward.packs", reward.packs()).getString());
        }
        if (reward.emeralds() > 0) {
            parts.add(Component.translatable("screen.jadm.sets.reward.emeralds", reward.emeralds()).getString());
        }
        if (reward.xp() > 0) {
            parts.add(Component.translatable("screen.jadm.sets.reward.xp", reward.xp()).getString());
        }
        return Component.literal(String.join(", ", parts));
    }

    private Component showLabel() {
        return Component.translatable("screen.jadm.sets.show." + show.name().toLowerCase(Locale.ROOT));
    }

    private Component sortLabel() {
        return Component.translatable("screen.jadm.sets.sort." + sort.name().toLowerCase(Locale.ROOT));
    }

    private Component cardsLabel() {
        return Component.translatable("screen.jadm.sets.cards." + cards.name().toLowerCase(Locale.ROOT));
    }

    private static String percent(int owned, int size) {
        if (size == 0) {
            return "0 %";
        }
        // Never round a set that isn't complete up to 100 %.
        int p = owned == size ? 100 : Math.min(99, Math.round(owned * 100f / size));
        return p + " %";
    }

    private static String kind(Products.Product product) {
        return Component.translatable("screen.jadm.admin.kind." + switch (product.kind()) {
            case DECK -> "deck";
            case TIN -> "tin";
            default -> "booster";
        }).getString();
    }

    private void send(Action action, String id) {
        PacketDistributor.sendToServer(new SetBookActionPayload(action, id));
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.5f, 1.4f);
        }
    }

    // ---- drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xF0181820);
        g.renderOutline(left, top, WIDTH, HEIGHT, 0xFFC0A040);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (open == null) {
            renderList(g, mouseX, mouseY);
        } else {
            renderSet(g, mouseX, mouseY);
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int complete = (int) rows.stream().filter(Row::complete).count();
        long have = rows.stream().mapToLong(Row::owned).sum();
        long all = rows.stream().mapToLong(r -> r.set().size()).sum();
        g.drawString(font, title, left + 8, top + 7, GOLD);
        Component stats = Component.translatable("screen.jadm.sets.stats", complete, rows.size(),
                percent((int) Math.min(have, Integer.MAX_VALUE), (int) Math.min(all, Integer.MAX_VALUE)));
        g.drawString(font, stats, left + WIDTH - 8 - font.width(stats), top + 7, DIM, false);

        int x = left + 8;
        int w = WIDTH - 16;
        int y0 = listTop();
        Row hovered = null;
        boolean overClaim = false;
        g.enableScissor(x, y0, x + w, y0 + ROWS * ROW);
        for (int i = 0; i < shown.size(); i++) {
            int y = y0 + i * ROW - (int) scroll;
            if (y + ROW < y0 || y > y0 + ROWS * ROW) {
                continue;
            }
            Row r = shown.get(i);
            boolean over = mouseX >= x && mouseX < x + w && mouseY >= Math.max(y, y0)
                    && mouseY < Math.min(y + ROW, y0 + ROWS * ROW);
            int background = r.complete() ? (over ? 0x60D0B040 : 0x40A08020) : over ? 0x50FFFFFF : 0x30000000;
            g.fill(x, y + 1, x + w - 6, y + ROW - 1, background);
            if (!r.item().isEmpty()) {
                g.renderItem(r.item(), x + 3, y + 3);
            }
            int textRight = x + w - BAR - 16;
            g.drawString(font, font.plainSubstrByWidth(r.set().product().name(), textRight - x - 24), x + 24, y + 3,
                    r.complete() ? GOLD : 0xFFFFFFFF, false);
            g.drawString(font, r.set().product().code() + " · " + r.set().product().date().getYear() + " · "
                    + kind(r.set().product()), x + 24, y + 12, DIM, false);
            int barX = x + w - BAR - 12;
            if (claimable(r)) {
                boolean overButton = over && mouseX >= barX;
                g.fill(barX, y + 3, barX + BAR, y + ROW - 3, overButton ? 0xFFFFE070 : 0xFFE0B030);
                g.renderOutline(barX, y + 3, BAR, ROW - 6, 0xFF805010);
                Component claim = Component.translatable("screen.jadm.sets.claim");
                g.drawString(font, claim, barX + (BAR - font.width(claim)) / 2, y + 7, 0xFF402000, false);
                if (overButton) {
                    overClaim = true;
                }
            } else {
                String count = r.owned() + " / " + r.set().size();
                String pct = percent(r.owned(), r.set().size());
                boolean done = claimed.contains(r.set().product().id());
                g.drawString(font, count, barX, y + 3, DIM, false);
                g.drawString(font, (done ? "✔ " : "") + pct, barX + BAR - font.width((done ? "✔ " : "") + pct),
                        y + 3, r.complete() ? GREEN : 0xFFFFFFFF, false);
                g.fill(barX, y + 13, barX + BAR, y + 18, 0xFF303040);
                int filled = Math.round(BAR * r.fraction());
                g.fill(barX, y + 13, barX + filled, y + 18, r.complete() ? GREEN : 0xFF5090E0);
            }
            if (over) {
                hovered = r;
            }
        }
        g.disableScissor();
        if (shown.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(rows.isEmpty() ? "screen.jadm.sets.none"
                    : "screen.jadm.sets.no_match"), left + WIDTH / 2, y0 + ROWS * ROW / 2 - 4, DIM);
        }
        if (maxScroll() > 0) {
            int h = ROWS * ROW;
            int bar = Math.max(8, h * h / (shown.size() * ROW));
            int barY = y0 + (int) ((h - bar) * scroll / maxScroll());
            g.fill(left + WIDTH - 7, barY, left + WIDTH - 5, barY + bar, 0xA0FFFFFF);
        }
        int hintRight = claimable().size() > 1 ? left + WIDTH - 134 : left + WIDTH - 8;
        Component hint = Component.translatable("screen.jadm.sets.hint");
        if (reward == null || !reward.any() || font.width(hint) > hintRight - left - 8) {
            hint = Component.translatable("screen.jadm.sets.hint_short");
        }
        g.drawString(font, font.plainSubstrByWidth(hint.getString(), hintRight - left - 8), left + 8,
                top + HEIGHT - 18, DIM, false);
        if (hovered != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hovered.set().product().name()).withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("screen.jadm.sets.tooltip.cards", hovered.owned(),
                    hovered.set().size(), hovered.set().size() - hovered.owned()).withStyle(ChatFormatting.GRAY));
            if (reward != null && reward.any()) {
                boolean done = claimed.contains(hovered.set().product().id());
                tooltip.add(Component.translatable(done ? "screen.jadm.sets.tooltip.claimed"
                        : "screen.jadm.sets.tooltip.reward", rewardText(hovered.set()))
                        .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            }
            tooltip.add(Component.translatable(overClaim ? "screen.jadm.sets.tooltip.claim"
                    : "screen.jadm.sets.tooltip.open").withStyle(ChatFormatting.YELLOW));
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void renderSet(GuiGraphics g, int mouseX, int mouseY) {
        Products.Product product = open.set().product();
        String count = open.owned() + " / " + open.set().size() + " · " + percent(open.owned(), open.set().size());
        int countX = left + WIDTH - 8 - font.width(count);
        g.drawString(font, font.plainSubstrByWidth(product.name(), countX - left - 34), left + 28, top + 9,
                open.complete() ? GOLD : 0xFFFFFFFF, false);
        g.drawString(font, count, countX, top + 9, open.complete() ? GREEN : 0xFFFFFFFF, false);
        g.drawString(font, product.code() + " · " + product.date() + " · " + kind(product), left + 8, top + 24, DIM,
                false);
        int barX = left + WIDTH - 8 - 140;
        g.fill(barX, top + 25, barX + 140, top + 31, 0xFF303040);
        g.fill(barX, top + 25, barX + Math.round(140 * open.fraction()), top + 31,
                open.complete() ? GREEN : 0xFF5090E0);
        if (reward != null && reward.any() && !claimable(open)) {
            boolean done = claimed.contains(product.id());
            Component text = Component.translatable(done ? "screen.jadm.sets.tooltip.claimed"
                    : "screen.jadm.sets.tooltip.reward", rewardText(open.set()));
            String line = font.plainSubstrByWidth(text.getString(), WIDTH - 124);
            g.drawString(font, line, left + WIDTH - 8 - font.width(line), top + 41, done ? GREEN : GOLD, false);
        }
        grid.render(g, font, mouseX, mouseY);
        if (grid.entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(cards == Cards.MISSING ? "screen.jadm.sets.all_owned"
                    : "screen.jadm.sets.none_owned"), left + WIDTH / 2, top + 110, DIM);
        }
        CardGrid.Entry hovered = grid.at(mouseX, mouseY);
        if (hovered != null) {
            boolean preview = CardPreview.render(g, font, hovered.code(), Rarity.COMMON, left, height);
            List<Component> tooltip = CardGrid.tooltip(hovered.code(), null, !preview);
            boolean have = owned.contains(hovered.code());
            tooltip.add(Math.min(1, tooltip.size()), Component.translatable(have ? "screen.jadm.sets.owned"
                    : "screen.jadm.sets.missing").withStyle(have ? ChatFormatting.GREEN : ChatFormatting.RED));
            String printed = printedRarities(product, hovered.code());
            if (!printed.isEmpty()) {
                tooltip.add(Math.min(2, tooltip.size()), Component.translatable("screen.jadm.sets.printed", printed)
                        .withStyle(ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    /** The rarities a card was printed in in a product, e.g. "Ultra Rare, Secret Rare". */
    private static String printedRarities(Products.Product product, int code) {
        Set<String> rarities = new LinkedHashSet<>();
        for (Products.Printing printing : product.cards()) {
            if (SetBook.original(printing.code()) == code) {
                rarities.add(printing.rarity());
            }
        }
        return String.join(", ", rarities);
    }

    // ---- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (open != null) {
            return false;
        }
        int x = left + 8;
        int w = WIDTH - 16;
        int y0 = listTop();
        if (mouseX < x || mouseX >= x + w - 6 || mouseY < y0 || mouseY >= y0 + ROWS * ROW) {
            return false;
        }
        int i = (int) ((mouseY - y0 + scroll) / ROW);
        if (i < 0 || i >= shown.size()) {
            return false;
        }
        Row r = shown.get(i);
        if (claimable(r) && mouseX >= x + w - BAR - 12) {
            send(Action.CLAIM, r.set().product().id());
            return true;
        }
        open = r;
        cards = Cards.ALL;
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8f, 1f);
        }
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (open != null) {
            grid.page = Math.max(0, Math.min(grid.pages() - 1, grid.page - (int) Math.signum(scrollY)));
        } else {
            scroll = Mth.clamp(scroll - scrollY * ROW, 0, maxScroll());
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape on a set's page goes back to the list rather than closing the book.
        if (keyCode == 256 && open != null) {
            open = null;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
