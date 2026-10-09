package io.github.zancrow321.jadm.client.collection;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.engine.data.DeckRules;
import io.github.zancrow321.jadm.engine.tournament.Draft;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.github.zancrow321.jadm.network.LimitedActionPayload;
import io.github.zancrow321.jadm.tournament.LimitedView;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The window of a Sealed or Draft tournament before its bracket: in a draft the pack in front of you (click a card
 * to take it; the rest goes on to the next duelist) and your picks so far, then the deck building, laid out like the
 * deck box: your deck on the left, the rest of your pool on the right. A Sealed pool's packs are revealed one by one
 * first. {@code /jadm tournament deck} opens it again.
 */
public final class LimitedScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int WIDTH = 380;
    private static final int HEIGHT = 236;
    private static final int GOLD = 0xFFFFE070;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GREEN = 0xFF60E060;
    private static final int RED = 0xFFFF7070;

    private static LimitedView latest;
    /** When the latest view arrived plus its seconds left: when the pick or the building ends, on this clock */
    private static long endsAt;
    /** The Sealed pools whose packs were revealed already, by tournament */
    private static long revealed = -1;

    private int left;
    private int top;
    /** Draft: the picks so far; building: the main deck */
    private CardGrid deckGrid;
    private CardGrid extraGrid;
    private CardGrid poolGrid;
    private EditBox search;
    private final CardFilter filter = new CardFilter();
    /** The pick the window was built for, to rebuild it and play a sound when the next pack comes. */
    private String shown = "";

    public LimitedScreen() {
        super(Component.literal("Draft & Sealed"));
    }

    /** An update from the server: opens the window if asked, refreshes it if it is open, closes it when it's over. */
    public static void receive(boolean open, String json) {
        Minecraft mc = Minecraft.getInstance();
        if (json.isEmpty()) {
            latest = null;
            if (mc.screen instanceof LimitedScreen) {
                mc.setScreen(null);
            }
            return;
        }
        try {
            latest = GSON.fromJson(json, LimitedView.class);
        } catch (JsonParseException e) {
            return;
        }
        endsAt = Util.getMillis() + latest.secondsLeft * 1000L;
        if (!open) {
            if (mc.screen instanceof LimitedScreen screen) {
                screen.refresh();
            }
            return;
        }
        Screen screen = mc.screen instanceof LimitedScreen s ? s : new LimitedScreen();
        // The packs of a Sealed pool are opened one after the other before the deck building.
        if (latest.mode.equals("sealed") && latest.phase.equals("build") && revealed != latest.id
                && !latest.packs.isEmpty()) {
            revealed = latest.id;
            for (int i = latest.packs.size() - 1; i >= 0; i--) {
                List<JadmComponents.CardStack> cards = latest.packs.get(i).stream()
                        .map(c -> new JadmComponents.CardStack(c.code, c.rarity)).toList();
                screen = new PackOpenScreen(latest.packNames.get(i) + " (" + (i + 1) + "/" + latest.packs.size()
                        + ")", cards, screen);
            }
        }
        if (screen != mc.screen) {
            mc.setScreen(screen);
        } else {
            ((LimitedScreen) screen).refresh();
        }
    }

    private boolean drafting() {
        return latest != null && latest.phase.equals("draft");
    }

    private void refresh() {
        String now = key();
        if (!now.equals(shown)) {
            if (drafting() && !shown.isEmpty() && minecraft != null && minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.6f, 1.0f);
            }
            rebuildWidgets();
        }
    }

    private static String key() {
        LimitedView v = latest;
        return v == null ? "" : v.phase + "/" + v.round + "/" + v.pick + "/" + v.chosen + "/" + v.built + "/"
                + v.problems.isEmpty();
    }

    @Override
    protected void init() {
        shown = key();
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        if (latest == null) {
            return;
        }
        if (drafting()) {
            deckGrid = new CardGrid(left + 8, top + 124, 13, 2, 24);
            pager(deckGrid);
            return;
        }
        deckGrid = new CardGrid(left + 8, top + 40, 6, 3, 26);
        extraGrid = new CardGrid(left + 8, top + 186, 7, 1, 20);
        int right = left + WIDTH / 2 + 10;
        poolGrid = new CardGrid(right, top + 56, 6, 4, 24);
        search = addRenderableWidget(new EditBox(font, right, top + 22, 108, 14,
                Component.translatable("screen.jadm.search")));
        search.setHint(Component.literal("Your pool"));
        cycle(filter.sortLabel(), filter::nextSort, filter::sortLabel, right + 112, top + 22, 58);
        cycle(filter.kindLabel(), filter::nextKind, filter::kindLabel, right, top + 38, 56);
        cycle(filter.attributeLabel(), filter::nextAttribute, filter::attributeLabel, right + 57, top + 38, 56);
        cycle(filter.levelLabel(), filter::nextLevel, filter::levelLabel, right + 114, top + 38, 56);
        int buttons = left + WIDTH / 2 - 6;
        boolean built = latest.built;
        Button done = addRenderableWidget(Button.builder(Component.literal(built ? "Edit" : "Done"),
                        b -> send(built ? "edit" : "done", 0)).bounds(buttons - 38, top + 21, 38, 16)
                .tooltip(Tooltip.create(Component.literal(built ? "Change your deck again (until time is up)"
                        : "Lock your deck in; the bracket is drawn once everyone is done"))).build());
        done.active = built || latest.problems.isEmpty();
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> send("clear", 0))
                .bounds(buttons - 74, top + 21, 34, 16).build()).active = !built;
        addRenderableWidget(Button.builder(Component.literal("Auto"), b -> send("auto", 0))
                        .bounds(buttons - 108, top + 21, 32, 16)
                        .tooltip(Tooltip.create(Component.literal("Build a deck from the best cards of your pool")))
                        .build()).active = !built;
        pager(deckGrid);
        pager(extraGrid);
        int buttonsY = top + HEIGHT - 20;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page(poolGrid, -1))
                .bounds(left + WIDTH / 2 + 10, buttonsY, 16, 14).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page(poolGrid, 1))
                .bounds(left + WIDTH - 26, buttonsY, 16, 14).build());
    }

    private void cycle(Component label, Consumer<Integer> step, java.util.function.Supplier<Component> now, int x,
            int y, int w) {
        addRenderableWidget(Button.builder(label, b -> {
            step.accept(hasShiftDown() ? -1 : 1);
            b.setMessage(now.get());
            poolGrid.page = 0;
        }).bounds(x, y, w, 14).tooltip(Tooltip.create(Component.translatable("screen.jadm.deck_box.cycle")))
                .build());
    }

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

    private static Map<Integer, Integer> counts(List<Integer> codes) {
        Map<Integer, Integer> counts = new HashMap<>();
        codes.forEach(c -> counts.merge(c, 1, Integer::sum));
        return counts;
    }

    private static Map<Integer, Integer> poolCounts(List<Draft.Card> pool) {
        return counts(pool.stream().map(c -> c.code).toList());
    }

    /** "1:05" */
    private static String clock() {
        long seconds = Math.max(0, (endsAt - Util.getMillis() + 999) / 1000);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private static int clockColor() {
        return endsAt - Util.getMillis() < 10_000 ? RED : GOLD;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        LimitedView v = latest;
        if (v == null) {
            onClose();
            return;
        }
        if (drafting()) {
            deckGrid.set(poolCounts(v.pool), "", code -> true, CardFilter.deckOrder());
        } else {
            Map<Integer, Integer> rest = poolCounts(v.pool);
            v.main.forEach(c -> rest.merge(c, -1, Integer::sum));
            v.extra.forEach(c -> rest.merge(c, -1, Integer::sum));
            deckGrid.set(counts(v.main), "", code -> true, CardFilter.deckOrder());
            extraGrid.set(counts(v.extra), "", code -> true, CardFilter.deckOrder());
            poolGrid.set(rest, search.getValue(), filter, filter.order());
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (drafting()) {
            renderDraft(g, v, mouseX, mouseY);
        } else {
            renderBuild(g, v, mouseX, mouseY);
        }
    }

    // ---- Draft --------------------------------------------------------------------------------------------------

    private record Slot(int x, int y, int w, int h) {
    }

    private Slot packSlot(int i, int n) {
        int w = Math.min(38, (WIDTH - 16) / Math.max(1, n) - 4);
        int h = Math.round(w * 391f / 268f);
        int total = n * (w + 4) - 4;
        return new Slot(left + (WIDTH - total) / 2 + i * (w + 4), top + 36, w, h);
    }

    private void renderDraft(GuiGraphics g, LimitedView v, int mouseX, int mouseY) {
        g.drawString(font, "Draft  ·  Pack " + (v.round + 1) + " of " + v.rounds + "  ·  Pick " + (v.pick + 1),
                left + 8, top + 6, 0xFFFFFFFF, false);
        String clock = clock();
        g.drawString(font, clock, left + WIDTH - 8 - font.width(clock), top + 6, clockColor(), false);
        String arrow = v.round % 2 == 0 ? "  →  " : "  ←  ";
        g.drawString(font, font.plainSubstrByWidth(v.setName, WIDTH - 175), left + 8, top + 19, GRAY, false);
        String pass = font.plainSubstrByWidth("passes" + arrow + v.passTo, 150);
        g.drawString(font, pass, left + WIDTH - 8 - font.width(pass), top + 19, GRAY, false);
        int hovered = -1;
        int n = v.pack.size();
        for (int i = 0; i < n; i++) {
            Slot s = packSlot(i, n);
            Draft.Card card = v.pack.get(i);
            Rarity rarity = rarity(card.rarity);
            if (rarity != Rarity.COMMON) {
                g.fill(s.x() - 2, s.y() - 2, s.x() + s.w() + 2, s.y() + s.h() + 2, 0xFF000000 | colorOf(rarity));
            }
            CardGrid.drawCard(g, font, card.code, rarity, s.x(), s.y(), s.w(), s.h());
            boolean over = mouseX >= s.x() && mouseX < s.x() + s.w() && mouseY >= s.y() && mouseY < s.y() + s.h();
            if (v.chosen) {
                g.fill(s.x(), s.y(), s.x() + s.w(), s.y() + s.h(), 0x90101018);
            } else if (over) {
                g.renderOutline(s.x() - 1, s.y() - 1, s.w() + 2, s.h() + 2, GOLD);
            }
            if (over) {
                hovered = i;
            }
        }
        int below = packSlot(0, n).y() + packSlot(0, n).h() + 8;
        String status = n == 0 ? "Waiting for the next pack..."
                : v.chosen ? "Picked. Waiting for " + v.waiting + (v.waiting == 1 ? " duelist..." : " duelists...")
                : "Click a card to take it; the rest goes on.";
        g.drawCenteredString(font, status, left + WIDTH / 2, below, v.chosen ? GRAY : GREEN);
        g.drawString(font, "Your picks (" + v.pool.size() + ")", left + 8, deckGrid.y - 11, GOLD, false);
        deckGrid.render(g, font, mouseX, mouseY);
        if (hovered >= 0) {
            Draft.Card card = v.pack.get(hovered);
            tooltip(g, card.code, rarity(card.rarity), v.chosen ? null : "Click: take it", mouseX, mouseY);
        }
        CardGrid.Entry e = deckGrid.at(mouseX, mouseY);
        if (e != null) {
            tooltip(g, e.code(), Rarity.COMMON, null, mouseX, mouseY);
        }
    }

    // ---- Deck building ------------------------------------------------------------------------------------------

    private void renderBuild(GuiGraphics g, LimitedView v, int mouseX, int mouseY) {
        String title = (v.mode.equals("sealed") ? "Sealed" : "Draft") + " deck  ";
        g.drawString(font, title, left + 8, top + 6, 0xFFFFFFFF, false);
        g.drawString(font, clock(), left + 8 + font.width(title), top + 6, clockColor(), false);
        String status;
        int color;
        if (v.built) {
            int others = v.waiting;
            status = others == 0 ? "Done. The bracket is drawn..." : "Done. Waiting for " + others
                    + (others == 1 ? " duelist" : " duelists") + " to finish.";
            color = GREEN;
        } else if (v.problems.isEmpty()) {
            status = "Legal: click Done to lock it in.";
            color = GREEN;
        } else {
            status = v.problems.get(0);
            color = RED;
        }
        g.drawString(font, font.plainSubstrByWidth(status, WIDTH - 140), left + 130, top + 6, color, false);
        g.drawString(font, "Main " + v.main.size() + " (" + v.minimum + "+)", left + 8, top + 26,
                v.main.size() >= v.minimum ? GRAY : RED, false);
        g.drawString(font, "Extra " + v.extra.size() + "/" + DeckRules.EXTRA_MAX, left + 8, top + 175, GRAY, false);
        deckGrid.render(g, font, mouseX, mouseY);
        extraGrid.render(g, font, mouseX, mouseY);
        poolGrid.render(g, font, mouseX, mouseY);
        if (poolGrid.entries.isEmpty()) {
            g.drawCenteredString(font, v.pool.isEmpty() ? "Your pool is empty." : "Every card is in your deck.",
                    left + WIDTH * 3 / 4, top + HEIGHT / 2, GRAY);
        }
        if (v.built) {
            g.fill(left + 4, top + 38, left + WIDTH / 2 - 4, top + HEIGHT - 4, 0x60101018);
        }
        CardGrid.Entry e = deckGrid.at(mouseX, mouseY);
        if (e == null) {
            e = extraGrid.at(mouseX, mouseY);
        }
        if (e != null) {
            tooltip(g, e.code(), Rarity.COMMON, v.built ? null : "Click: put back", mouseX, mouseY);
        }
        e = poolGrid.at(mouseX, mouseY);
        if (e != null) {
            tooltip(g, e.code(), Rarity.COMMON, v.built ? null : "Click: add to deck", mouseX, mouseY);
        }
    }

    private void tooltip(GuiGraphics g, int code, Rarity rarity, String action, int mouseX, int mouseY) {
        boolean preview = CardPreview.render(g, font, code, rarity, left, height);
        g.renderComponentTooltip(font, CardGrid.tooltip(code, action, !preview), mouseX, mouseY);
    }

    private static Rarity rarity(String id) {
        try {
            return Rarity.parse(id);
        } catch (RuntimeException e) {
            return Rarity.COMMON;
        }
    }

    private static int colorOf(Rarity rarity) {
        Integer color = CardItem.color(rarity).getColor();
        return color == null ? 0xFFFFFF : color;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xE0181820);
        g.renderOutline(left, top, WIDTH, HEIGHT, 0xFF5070A0);
        if (!drafting()) {
            g.fill(left + WIDTH / 2, top + 20, left + WIDTH / 2 + 1, top + HEIGHT - 4, 0xFF5070A0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            if (getFocused() instanceof Button) {
                setFocused(null);
            }
            return true;
        }
        LimitedView v = latest;
        if (v == null) {
            return false;
        }
        if (drafting()) {
            for (int i = 0; i < v.pack.size() && !v.chosen; i++) {
                Slot s = packSlot(i, v.pack.size());
                if (mouseX >= s.x() && mouseX < s.x() + s.w() && mouseY >= s.y() && mouseY < s.y() + s.h()) {
                    // Shown at once; the server's update follows.
                    v.chosen = true;
                    send("pick", i);
                    return true;
                }
            }
            return false;
        }
        if (v.built) {
            return false;
        }
        CardGrid.Entry e = deckGrid.at(mouseX, mouseY);
        if (e == null) {
            e = extraGrid.at(mouseX, mouseY);
        }
        if (e != null) {
            send("remove", e.code());
            return true;
        }
        e = poolGrid.at(mouseX, mouseY);
        if (e != null) {
            send("add", e.code());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (drafting()) {
            page(deckGrid, -(int) Math.signum(scrollY));
            return true;
        }
        if (mouseX >= left + WIDTH / 2.0 && mouseY < poolGrid.y) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        CardGrid grid = mouseX >= left + WIDTH / 2.0 ? poolGrid : mouseY >= extraGrid.y - 14 ? extraGrid : deckGrid;
        page(grid, -(int) Math.signum(scrollY));
        return true;
    }

    private static void send(String action, int value) {
        PacketDistributor.sendToServer(new LimitedActionPayload(action, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
