package io.github.zancrow321.minecraftygo.client.duel;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.client.CardArt;
import io.github.zancrow321.minecraftygo.client.ClientDuel;
import io.github.zancrow321.minecraftygo.client.YgoClientConfig;
import io.github.zancrow321.minecraftygo.client.field.ClientField;
import io.github.zancrow321.minecraftygo.client.field.DuelHud;
import io.github.zancrow321.minecraftygo.client.field.FieldLayout;
import io.github.zancrow321.minecraftygo.client.field.FieldRenderer;
import io.github.zancrow321.minecraftygo.engine.data.CardInfo;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.engine.protocol.CardState;
import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import io.github.zancrow321.minecraftygo.engine.text.PromptView;
import io.github.zancrow321.minecraftygo.engine.text.PromptView.Choice;
import io.github.zancrow321.minecraftygo.engine.text.PromptView.Kind;
import io.github.zancrow321.minecraftygo.item.CardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * Everything you click in duel mode, drawn over the world each frame: your hand (click a card for its actions, or
 * drag it onto a zone), the card panel, the phase buttons, the menu on a card, and one window for whatever the duel
 * is asking: a response, a pick from a pile, or a plain question. Immediate mode: each frame draws the controls and
 * remembers where they are for the next click. Only touched on the client thread.
 */
public final class DuelUi {
    private static final int GOLD = 0xFFFFD040;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFB0C4D8;
    private static final int PANEL = 0xD0081828;
    private static final int PANEL_EDGE = 0xFF3A6A9A;
    private static final int BUTTON = 0xFF1E3A5A;
    private static final int BUTTON_HOVER = 0xFF2F5C8C;
    private static final int BUTTON_OFF = 0xFF20262E;
    private static final int SELECTED = 0xFF40E0FF;
    private static final ResourceLocation CARD_BACK =
            ResourceLocation.fromNamespaceAndPath("minecraftygo", "textures/field/card_back.png");

    private static final int HAND_WIDTH = 32;
    private static final int HAND_HEIGHT = 46;
    private static final int HAND_LIFT = 10;
    /** How far the mouse moves with a hand card held down before it counts as a drag. */
    private static final double DRAG_START = 4;
    private static final int BUTTON_HEIGHT = 14;
    private static final int PHASE_WIDTH = 24;
    private static final int SIDE_BUTTON_WIDTH = 72;
    private static final int CARD_PANEL_WIDTH = 96;
    /** Life panels sit in the corners this wide; the hand and the panels keep clear of them. */
    private static final int SIDE_MARGIN = 110;
    /** How far the hand sits below its full height while the mouse is elsewhere, so the zones behind it show. */
    private static final int HAND_SUNK = 18;
    /** Zone columns tried first when a card is placed for you: the middle, then outward. */
    private static final int[] ZONE_ORDER = {2, 1, 3, 0, 4, 5, 6, 7};

    /** Actions that put a card from the hand onto the field, and so are followed by a zone choice. */
    private static final Set<Kind> PLAYS = EnumSet.of(Kind.SUMMON, Kind.SPECIAL_SUMMON, Kind.SET_MONSTER,
            Kind.SET_SPELL, Kind.ACTIVATE);
    private static final Set<Kind> COMMANDS = EnumSet.of(Kind.SUMMON, Kind.SPECIAL_SUMMON, Kind.SET_MONSTER,
            Kind.SET_SPELL, Kind.ACTIVATE, Kind.REPOSITION, Kind.ATTACK, Kind.TO_BATTLE, Kind.TO_MAIN2, Kind.TO_END);

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** The actions of one card, opened next to it. {@code drop} is the zone it was dragged onto, if any. */
    private record Menu(int x, int y, List<Choice> choices, Loc drop) {
    }

    private static final List<Hit> hits = new ArrayList<>();
    private static PromptView shown;
    private static Menu menu;
    /** The zone a card was dropped on: the next zone question is answered with it. */
    private static Loc pendingZone;
    /** A card was played from its menu: the next zone question picks a free zone for you (unless set otherwise). */
    private static boolean autoZone;
    private static int page;
    private static boolean collapsed;
    /** What was typed to narrow down a long list of choices, such as every card name to declare. */
    private static String search = "";
    private static int pressedHand = -1;
    private static double pressX;
    private static double pressY;
    private static boolean dragging;
    private static int hoveredHand = -1;
    private static boolean handRaised;
    private static int hoverCode;
    /** The card under the mouse when it's on the field, for its current stats. */
    private static CardState hoverCard;
    private static int mouseX;
    private static int mouseY;

    private DuelUi() {
    }

    /** Forgets everything about the previous duel. */
    public static void reset() {
        shown = null;
        menu = null;
        pendingZone = null;
        autoZone = false;
        pressedHand = -1;
        dragging = false;
    }

    // ---- What the current prompt is ----

    private static boolean commands(PromptView p) {
        return p.multi() == null && p.choices().stream().anyMatch(c -> COMMANDS.contains(c.kind()));
    }

    private static boolean chain(PromptView p) {
        return p.multi() == null && p.choices().stream().anyMatch(c -> c.kind() == Kind.CHAIN);
    }

    private static boolean place(PromptView p) {
        return p.multi() == null && p.choices().stream().anyMatch(c -> c.kind() == Kind.PLACE);
    }

    /** Whether a place can be clicked where it is: a zone on the mat or a card in your own hand. */
    private static boolean clickable(Loc loc) {
        if (loc == null) {
            return false;
        }
        if ((loc.location() & ~LOCATION_OVERLAY) == LOCATION_HAND) {
            return loc.controller() == ClientDuel.view().you();
        }
        return FieldLayout.slot(loc) != null && (loc.location() & (LOCATION_MZONE | LOCATION_SZONE)) != 0;
    }

    /** A pick of cards that are all on the field or in your hand is made there; anything else opens a window. */
    private static boolean pickInPlace(PromptView p) {
        if (p.multi() != null) {
            return !p.multi().locs().isEmpty() && p.multi().locs().stream().allMatch(DuelUi::clickable);
        }
        List<Choice> cards = p.choices().stream().filter(c -> c.at() != null).toList();
        return !cards.isEmpty() && cards.stream().allMatch(c -> clickable(c.at()));
    }

    /** Called once for each new prompt (and with {@code null} when there is none). */
    private static void onPrompt(PromptView p) {
        shown = p;
        menu = null;
        page = 0;
        collapsed = false;
        search = "";
        if (p == null) {
            return;
        }
        if (commands(p)) {
            // Back at the main or battle phase: whatever was being played has landed.
            pendingZone = null;
            autoZone = false;
            return;
        }
        if (place(p) && (pendingZone != null || autoZone)) {
            Choice pick = pendingZone != null ? zoneChoice(p, pendingZone) : null;
            if (pick == null && autoZone && !YgoClientConfig.CHOOSE_ZONE.get()) {
                pick = firstFreeZone(p);
            }
            pendingZone = null;
            autoZone = false;
            if (pick != null) {
                ClientDuel.answer(pick.response());
            }
        }
    }

    private static Choice zoneChoice(PromptView p, Loc zone) {
        return p.choices().stream().filter(c -> c.kind() == Kind.PLACE && FieldLayout.sameZone(c.at(), zone))
                .findFirst().orElse(null);
    }

    private static Choice firstFreeZone(PromptView p) {
        int me = ClientDuel.view().you();
        for (int seq : ZONE_ORDER) {
            for (Choice c : p.choices()) {
                if (c.kind() == Kind.PLACE && c.at().controller() == me && c.at().sequence() == seq) {
                    return c;
                }
            }
        }
        return null;
    }

    private static void choose(Choice choice, Loc drop) {
        menu = null;
        if (PLAYS.contains(choice.kind()) && choice.at() != null
                && (choice.at().location() & ~LOCATION_OVERLAY) == LOCATION_HAND) {
            pendingZone = drop;
            autoZone = drop == null;
        }
        ClientDuel.answer(choice.response());
    }

    // ---- Drawing ----

    public static void render(GuiGraphics g, int mx, int my, int w, int h) {
        DuelView view = ClientDuel.view();
        if (view == null || !ClientField.active()) {
            return;
        }
        PromptView prompt = ClientDuel.prompt();
        if (prompt != shown) {
            onPrompt(prompt);
            prompt = ClientDuel.prompt(); // may have just been answered
        }
        Font font = Minecraft.getInstance().font;
        hits.clear();
        if (DuelStaging.resultShowing()) {
            DuelStaging.renderResult(g, font, mx, my, w, h);
            return;
        }
        if (DuelStaging.introRunning()) {
            DuelStaging.renderIntro(g, font, w, h);
            return;
        }
        hoverCode = 0;
        hoverCard = null;
        mouseX = mx;
        mouseY = my;
        Loc field = ClientField.hovered();
        if (field != null) {
            CardState card = FieldRenderer.cardAt(view.board(), field);
            if (card != null && card.code() != 0) {
                hoverCode = card.code();
                hoverCard = card;
            }
        }

        hand(g, view, prompt, mx, my, w, h);
        phases(g, font, view, prompt, mx, my, w, h);
        if (prompt != null && view.result() == null) {
            if (chain(prompt)) {
                responseWindow(g, font, prompt, mx, my, w, h);
            } else if (prompt.multi() != null) {
                if (pickInPlace(prompt)) {
                    confirmBar(g, font, prompt.multi(), mx, my, w, h);
                } else {
                    cardWindow(g, font, prompt, mx, my, w, h);
                }
            } else if (!commands(prompt) && !place(prompt)) {
                if (pickInPlace(prompt)) {
                    otherButtons(g, font, prompt, mx, my, w, h);
                } else if (prompt.choices().stream().anyMatch(c -> c.code() != 0)) {
                    cardWindow(g, font, prompt, mx, my, w, h);
                } else {
                    dialog(g, font, prompt, mx, my, w, h);
                }
            }
        }
        if (logOpen) {
            log(g, font, mx, my, w, h);
        }
        if (menu != null) {
            menu(g, font, mx, my, w, h);
        }
        cardPanel(g, font, w, h);
        DuelStaging.renderBanners(g, font, w, h);
        if (dragging && pressedHand >= 0) {
            List<CardState> cards = view.board().side(view.you()).hand();
            if (pressedHand < cards.size()) {
                card(g, cards.get(pressedHand).code(), mx - HAND_WIDTH / 2, my - HAND_HEIGHT / 2, HAND_WIDTH,
                        HAND_HEIGHT);
            }
        }
    }

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, PANEL_EDGE);
        g.fill(x, y, x + w, y + h, PANEL);
    }

    static boolean button(GuiGraphics g, Font font, int x, int y, int w, String label, boolean enabled,
                                  int mx, int my, Runnable action) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + BUTTON_HEIGHT;
        g.fill(x, y, x + w, y + BUTTON_HEIGHT, !enabled ? BUTTON_OFF : hover ? BUTTON_HOVER : BUTTON);
        g.drawCenteredString(font, font.plainSubstrByWidth(label, w - 4), x + w / 2, y + 3, enabled ? TEXT : DIM);
        if (enabled) {
            hits.add(new Hit(x, y, w, BUTTON_HEIGHT, action));
        }
        return hover;
    }

    /** A card picture, or its back with the name on it until the art has downloaded. */
    static void card(GuiGraphics g, int code, int x, int y, int w, int h) {
        CardArt.Texture art = code == 0 ? null : CardArt.get(code);
        if (art != null) {
            g.blit(art.location(), x, y, w, h, 0, 0, art.width(), art.height(), art.width(), art.height());
            return;
        }
        g.blit(CARD_BACK, x, y, w, h, 0, 0, 68, 100, 68, 100);
        if (code != 0) {
            Font font = Minecraft.getInstance().font;
            List<FormattedCharSequence> lines = font.split(Component.literal(YgoData.text().cardName(code)), w - 2);
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                g.drawString(font, lines.get(i), x + 1, y + 4 + i * 9, TEXT);
            }
        }
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y, color);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        g.fill(x - 1, y, x, y + h, color);
        g.fill(x + w, y, x + w + 1, y + h, color);
    }

    // ---- Hand ----

    private static int handStep(int cards, int w) {
        int room = w - 2 * SIDE_MARGIN;
        if (cards <= 1) {
            return HAND_WIDTH + 3;
        }
        return Math.max(8, Math.min(HAND_WIDTH + 3, (room - HAND_WIDTH) / (cards - 1)));
    }

    private static int handLeft(int cards, int w) {
        return w / 2 - ((cards - 1) * handStep(cards, w) + HAND_WIDTH) / 2;
    }

    /** Top of the hand: raised while the mouse is over it or a card is held, sunk a little otherwise. */
    private static int handTop(int h) {
        return h - HAND_HEIGHT - 4 + (handRaised ? 0 : HAND_SUNK);
    }

    /** @return which hand card is under the mouse (the topmost where they overlap), or -1 */
    private static int handCardAt(int cards, double mx, double my, int w, int h) {
        int step = handStep(cards, w);
        int left = handLeft(cards, w);
        int top = handTop(h);
        for (int i = cards - 1; i >= 0; i--) {
            int x = left + i * step;
            int lift = i == hoveredHand ? HAND_LIFT : 0;
            if (mx >= x && mx < x + HAND_WIDTH && my >= top - lift && my < top + HAND_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    private static Loc handLoc(int index) {
        return new Loc(ClientDuel.view().you(), LOCATION_HAND, index, 0);
    }

    private static void hand(GuiGraphics g, DuelView view, PromptView prompt, int mx, int my, int w, int h) {
        List<CardState> cards = view.board().side(view.you()).hand();
        handRaised = pressedHand >= 0 || my >= h - HAND_HEIGHT - 4 - HAND_LIFT && mx >= handLeft(cards.size(), w)
                - 4 && mx < w - handLeft(cards.size(), w) + 4;
        hoveredHand = menu == null || dragging ? handCardAt(cards.size(), mx, my, w, h) : hoveredHand;
        if (hoveredHand >= cards.size()) {
            hoveredHand = -1;
        }
        int step = handStep(cards.size(), w);
        int left = handLeft(cards.size(), w);
        int top = handTop(h);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < cards.size(); i++) {
                // The card under the mouse is drawn last, lifted, so it's never hidden behind its neighbour.
                if ((i == hoveredHand) != (pass == 1) || (dragging && i == pressedHand)) {
                    continue;
                }
                int x = left + i * step;
                int y = top - (i == hoveredHand ? HAND_LIFT : 0);
                Loc loc = handLoc(i);
                if (prompt != null && actsOn(prompt, loc)) {
                    outline(g, x, y, HAND_WIDTH, HAND_HEIGHT, selected(prompt, loc) ? SELECTED : GOLD);
                }
                card(g, cards.get(i).code(), x, y, HAND_WIDTH, HAND_HEIGHT);
            }
        }
        if (hoveredHand >= 0 && !dragging) {
            hoverCode = cards.get(hoveredHand).code();
        }
    }

    private static boolean actsOn(PromptView p, Loc loc) {
        if (p.multi() != null) {
            return p.multi().locs().stream().anyMatch(l -> FieldLayout.sameZone(l, loc));
        }
        return p.choices().stream().anyMatch(c -> FieldLayout.sameZone(c.at(), loc));
    }

    private static boolean selected(PromptView p, Loc loc) {
        if (p.multi() == null) {
            return false;
        }
        return ClientDuel.selected().stream().anyMatch(i -> FieldLayout.sameZone(p.multi().locs().get(i), loc));
    }

    // ---- Phase buttons ----

    private record Phase(int bits, String name, Kind go) {
    }

    private static final List<Phase> PHASES = List.of(new Phase(0x01, "DP", null), new Phase(0x02, "SP", null),
            new Phase(0x04, "M1", null), new Phase(0xF8, "BP", Kind.TO_BATTLE), new Phase(0x100, "M2", Kind.TO_MAIN2),
            new Phase(0x200, "EP", Kind.TO_END));

    private static void phases(GuiGraphics g, Font font, DuelView view, PromptView prompt, int mx, int my, int w,
                               int h) {
        int x = w - PHASE_WIDTH - 6;
        int y = h / 2 - PHASES.size() * (BUTTON_HEIGHT + 2) / 2;
        boolean mine = view.board().turnPlayer() == view.you();
        for (Phase phase : PHASES) {
            Choice go = prompt == null || phase.go() == null ? null : prompt.choices().stream()
                    .filter(c -> c.kind() == phase.go()).findFirst().orElse(null);
            boolean current = (view.board().phase() & phase.bits()) != 0;
            if (current) {
                g.fill(x - 2, y - 1, x + PHASE_WIDTH + 2, y + BUTTON_HEIGHT + 1, mine ? 0xFF4090FF : 0xFFFF6060);
            }
            Choice target = go;
            button(g, font, x, y, PHASE_WIDTH, phase.name(), go != null, mx, my, () -> choose(target, null));
            y += BUTTON_HEIGHT + 2;
        }
        button(g, font, x, y + 4, PHASE_WIDTH, "Log", true, mx, my, DuelUi::toggleLog);
        if (logOpen) {
            outline(g, x, y + 4, PHASE_WIDTH, BUTTON_HEIGHT, GOLD);
        }
        int clock = ClientDuel.clockLeft();
        if (clock >= 0) {
            // The turn time limit: what's left of yours, red for the last ten seconds.
            int seconds = (clock + 19) / 20;
            String time = seconds / 60 + ":" + String.format("%02d", seconds % 60);
            g.drawCenteredString(font, time, x + PHASE_WIDTH / 2, y + BUTTON_HEIGHT + 8, seconds <= 10 ? 0xFFFF5050
                    : TEXT);
        }
    }

    // ---- The menu on a card ----

    private static void openMenu(List<Choice> choices, double x, double y, Loc drop) {
        menu = new Menu((int) x, (int) y, List.copyOf(choices), drop);
    }

    private static void menu(GuiGraphics g, Font font, int mx, int my, int w, int h) {
        int width = 60;
        for (Choice c : menu.choices()) {
            width = Math.max(width, Math.min(160, font.width(c.action()) + 10));
        }
        int height = menu.choices().size() * (BUTTON_HEIGHT + 1) + 3;
        int x = Math.min(menu.x() + 4, w - width - 4);
        int y = Math.max(4, Math.min(menu.y() - height, h - height - 4));
        panel(g, x - 2, y - 2, width + 4, height + 2);
        for (Choice c : menu.choices()) {
            Loc drop = menu.drop();
            button(g, font, x, y, width, c.action(), true, mx, my, () -> choose(c, drop));
            y += BUTTON_HEIGHT + 1;
        }
    }

    // ---- Windows ----

    private static int windowWidth(int w, int max) {
        return Math.min(max, w - 2 * (CARD_PANEL_WIDTH + 12));
    }

    /** The lowest a window reaches: it may cover the top of the hand, never all of it. */
    private static int windowBottom(int h) {
        return h - HAND_HEIGHT / 2;
    }

    /** The title bar of a window, with a button that folds it away to look at the field. */
    private static int windowTop(GuiGraphics g, Font font, String title, int x, int y, int w, int mx, int my) {
        g.drawString(font, font.plainSubstrByWidth(title, w - 24), x + 4, y + 4, GOLD);
        button(g, font, x + w - 18, y + 1, 16, collapsed ? "+" : "-", true, mx, my, () -> collapsed = !collapsed);
        return y + 16;
    }

    /** A question with plain answers: yes or no, a position, an option. */
    /** A dialog with this many plain choices or more can be narrowed down by typing. */
    private static final int SEARCH_FROM = 24;

    private static boolean searchable(PromptView p) {
        return p != null && p.multi() == null && p.choices().size() >= SEARCH_FROM
                && p.choices().stream().allMatch(c -> c.code() == 0 && c.at() == null && c.kind() == Kind.OTHER);
    }

    /** Whether typed keys go to a dialog's search right now (so they don't also work as hotkeys). */
    public static boolean searching() {
        return searchable(shown) && shown == ClientDuel.prompt() && !collapsed;
    }

    /** A key typed while {@link #searching()}. */
    public static void type(char c) {
        if (searching() && c >= ' ') {
            search += c;
            page = 0;
        }
    }

    /** Backspace while {@link #searching()}. */
    public static void erase() {
        if (searching() && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            page = 0;
        }
    }

    private static void dialog(GuiGraphics g, Font font, PromptView p, int mx, int my, int w, int h) {
        int width = windowWidth(w, 260);
        int x = w / 2 - width / 2;
        int y = DuelHud.promptBottom(w);
        List<FormattedCharSequence> title = font.split(Component.literal(p.title()), width - (p.card() != 0 ? 58 : 8));
        int artHeight = p.card() != 0 ? 72 : 0;
        int textHeight = Math.max(artHeight, title.size() * 10 + 4);
        boolean searchable = searchable(p);
        int searchHeight = searchable ? 14 : 0;
        int perPage = Math.max(2, (windowBottom(h) - y - 6 - textHeight - searchHeight - 18) / (BUTTON_HEIGHT + 2));
        String needle = search.toLowerCase(java.util.Locale.ROOT);
        List<Choice> choices = searchable && !needle.isEmpty() ? p.choices().stream()
                .filter(c -> c.label().toLowerCase(java.util.Locale.ROOT).contains(needle)).toList() : p.choices();
        int pages = Math.max(1, (choices.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int shownCount = Math.min(perPage, choices.size() - page * perPage);
        int height = collapsed ? 16
                : 6 + textHeight + searchHeight + shownCount * (BUTTON_HEIGHT + 2) + (pages > 1 ? 18 : 0);
        panel(g, x, y, width, height);
        if (collapsed) {
            windowTop(g, font, p.title(), x, y, width, mx, my);
            return;
        }
        button(g, font, x + width - 18, y + 1, 16, "-", true, mx, my, () -> collapsed = true);
        int textX = x + 4;
        if (p.card() != 0) {
            card(g, p.card(), x + 4, y + 4, 50, 72);
            textX = x + 58;
            if (mx >= x + 4 && mx < x + 54 && my >= y + 4 && my < y + 76) {
                hoverCode = p.card();
            }
        }
        for (int i = 0; i < title.size(); i++) {
            g.drawString(font, title.get(i), textX, y + 4 + i * 10, GOLD);
        }
        int by = y + 6 + textHeight;
        if (searchable) {
            g.fill(x + 4, by, x + width - 4, by + 11, 0xC0000000);
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            g.drawString(font, search.isEmpty() ? "Type to search (" + p.choices().size() + ")"
                    : search + (blink ? "_" : ""), x + 7, by + 2, search.isEmpty() ? DIM : 0xFFFFFFFF, false);
            by += searchHeight;
        }
        for (int i = page * perPage; i < page * perPage + shownCount; i++) {
            Choice c = choices.get(i);
            button(g, font, x + 4, by, width - 8, c.label(), true, mx, my, () -> choose(c, null));
            by += BUTTON_HEIGHT + 2;
        }
        pager(g, font, pages, x + 4, by, mx, my);
    }

    private static void pager(GuiGraphics g, Font font, int pages, int x, int y, int mx, int my) {
        if (pages <= 1) {
            return;
        }
        button(g, font, x, y, 16, "<", page > 0, mx, my, () -> page--);
        String label = (page + 1) + "/" + pages;
        g.drawString(font, label, x + 22, y + 3, DIM);
        button(g, font, x + Math.max(48, 28 + font.width(label)), y, 16, ">", page < pages - 1, mx, my,
                () -> page++);
    }

    /** Buttons for a pick made on the field go under the phase buttons, clear of the zones. */
    private static int sideButtonsTop(int h) {
        return h / 2 + PHASES.size() * (BUTTON_HEIGHT + 2) / 2 + BUTTON_HEIGHT + 24;
    }

    /** Confirm and cancel for a pick made on the field or in the hand. */
    private static void confirmBar(GuiGraphics g, Font font, PromptView.MultiSelect multi, int mx, int my, int w,
                                   int h) {
        List<Integer> picked = ClientDuel.selected();
        int count = multi.cancel() != null ? 2 : 1;
        int x = w - SIDE_BUTTON_WIDTH - 6;
        int y = sideButtonsTop(h);
        panel(g, x - 2, y - 2, SIDE_BUTTON_WIDTH + 4, count * (BUTTON_HEIGHT + 2) + 2);
        String label = "Confirm (" + picked.size() + ")";
        button(g, font, x, y, SIDE_BUTTON_WIDTH, label, multi.canConfirm(picked), mx, my,
                () -> ClientDuel.answer(multi.encode(List.copyOf(picked))));
        if (multi.cancel() != null) {
            button(g, font, x, y + BUTTON_HEIGHT + 2, SIDE_BUTTON_WIDTH, "Cancel", true, mx, my,
                    () -> ClientDuel.answer(multi.cancel().response()));
        }
    }

    /** Buttons for the choices of an on-field pick that aren't cards ("Done", "Cancel"). */
    private static void otherButtons(GuiGraphics g, Font font, PromptView p, int mx, int my, int w, int h) {
        List<Choice> rest = p.choices().stream().filter(c -> c.at() == null).toList();
        if (rest.isEmpty()) {
            return;
        }
        int x = w - SIDE_BUTTON_WIDTH - 6;
        int y = sideButtonsTop(h);
        panel(g, x - 2, y - 2, SIDE_BUTTON_WIDTH + 4, rest.size() * (BUTTON_HEIGHT + 2) + 2);
        for (Choice c : rest) {
            button(g, font, x, y, SIDE_BUTTON_WIDTH, c.label(), true, mx, my, () -> choose(c, null));
            y += BUTTON_HEIGHT + 2;
        }
    }

    /** Cards to pick from piles (graveyard, deck, banished...), shown big. */
    private static void cardWindow(GuiGraphics g, Font font, PromptView p, int mx, int my, int w, int h) {
        PromptView.MultiSelect multi = p.multi();
        List<Integer> codes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Runnable> picks = new ArrayList<>();
        List<Boolean> ticked = new ArrayList<>();
        List<Choice> extra = new ArrayList<>();
        if (multi != null) {
            for (int i = 0; i < multi.options().size(); i++) {
                int index = i;
                codes.add(multi.codes().get(i));
                names.add(multi.options().get(i));
                ticked.add(ClientDuel.selected().contains(i));
                picks.add(() -> ClientDuel.toggle(index));
            }
        } else {
            for (Choice c : p.choices()) {
                if (c.code() == 0) {
                    extra.add(c);
                    continue;
                }
                codes.add(c.code());
                names.add(c.label());
                ticked.add(false);
                picks.add(() -> choose(c, null));
            }
        }
        int cw = 40;
        int ch = 58;
        int width = windowWidth(w, 300);
        int perRow = Math.max(1, (width - 8) / (cw + 4));
        int rows = Math.max(1, Math.min(3, (windowBottom(h) - DuelHud.promptBottom(w) - 16 - 22) / (ch + 4)));
        int perPage = perRow * rows;
        int pages = Math.max(1, (codes.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int x = w / 2 - width / 2;
        int y = DuelHud.promptBottom(w);
        int height = collapsed ? 16 : 16 + rows * (ch + 4) + 22;
        panel(g, x, y, width, height);
        String title = p.title() + (multi != null ? "  (" + ClientDuel.selected().size() + ")" : "");
        int top = windowTop(g, font, title, x, y, width, mx, my);
        if (collapsed) {
            return;
        }
        for (int i = page * perPage; i < Math.min(codes.size(), (page + 1) * perPage); i++) {
            int slot = i - page * perPage;
            int cx = x + 4 + (slot % perRow) * (cw + 4);
            int cy = top + (slot / perRow) * (ch + 4);
            if (ticked.get(i)) {
                outline(g, cx, cy, cw, ch, SELECTED);
                outline(g, cx - 1, cy - 1, cw + 2, ch + 2, SELECTED);
            }
            card(g, codes.get(i), cx, cy, cw, ch);
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + ch) {
                hoverCode = codes.get(i);
                outline(g, cx, cy, cw, ch, GOLD);
            }
            hits.add(new Hit(cx, cy, cw, ch, picks.get(i)));
        }
        int by = top + rows * (ch + 4) + 2;
        int bx = x + 4;
        pager(g, font, pages, bx, by, mx, my);
        if (pages > 1) {
            bx += 70;
        }
        if (multi != null) {
            List<Integer> picked = ClientDuel.selected();
            button(g, font, bx, by, 70, "Confirm", multi.canConfirm(picked), mx, my,
                    () -> ClientDuel.answer(multi.encode(List.copyOf(picked))));
            bx += 74;
            if (multi.cancel() != null) {
                button(g, font, bx, by, 60, "Cancel", true, mx, my,
                        () -> ClientDuel.answer(multi.cancel().response()));
            }
        }
        for (Choice c : extra) {
            button(g, font, bx, by, 60, c.label(), true, mx, my, () -> choose(c, null));
            bx += 64;
        }
    }

    /** A chance to respond: what just happened, the cards that can answer it, and "Don't respond". */
    private static void responseWindow(GuiGraphics g, Font font, PromptView p, int mx, int my, int w, int h) {
        int width = windowWidth(w, 280);
        int x = w / 2 - width / 2;
        int y = DuelHud.promptBottom(w);
        List<Choice> chains = p.choices().stream().filter(c -> c.kind() == Kind.CHAIN).toList();
        Choice pass = p.choices().stream().filter(c -> c.kind() == Kind.PASS).findFirst().orElse(null);
        ClientDuel.Trigger trigger = ClientDuel.lastTrigger();
        int rowHeight = 26;
        int triggerHeight = trigger != null ? 50 : 0;
        int perPage = Math.max(1, Math.min(4, (windowBottom(h) - y - 16 - triggerHeight - BUTTON_HEIGHT - 8)
                / rowHeight));
        int pages = Math.max(1, (chains.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int shown = Math.min(perPage, chains.size() - page * perPage);
        int height = collapsed ? 16 : 16 + triggerHeight + shown * rowHeight + BUTTON_HEIGHT + 8;
        panel(g, x, y, width, height);
        int top = windowTop(g, font, "Respond?", x, y, width, mx, my);
        if (collapsed) {
            return;
        }
        if (trigger != null) {
            card(g, trigger.code(), x + 4, top, 32, 46);
            if (mx >= x + 4 && mx < x + 36 && my >= top && my < top + 46) {
                hoverCode = trigger.code();
            }
            List<FormattedCharSequence> what = font.split(Component.literal(trigger.what()), width - 44);
            int ty = top;
            for (int i = 0; i < Math.min(2, what.size()); i++, ty += 10) {
                g.drawString(font, what.get(i), x + 40, ty, TEXT);
            }
            CardInfo info = trigger.code() == 0 ? null : YgoData.cards().card(trigger.code());
            if (info != null) {
                List<FormattedCharSequence> lines = font.split(
                        Component.literal(info.description().replace("\r", "")), width - 44);
                for (int i = 0; i < Math.min(4 - Math.min(2, what.size()), lines.size()); i++, ty += 10) {
                    g.drawString(font, lines.get(i), x + 40, ty + 2, DIM);
                }
            }
            top += triggerHeight;
        }
        for (int i = page * perPage; i < page * perPage + shown; i++) {
            Choice c = chains.get(i);
            int ry = top + (i - page * perPage) * rowHeight;
            boolean hover = mx >= x + 4 && mx < x + width - 4 && my >= ry && my < ry + rowHeight - 2;
            g.fill(x + 4, ry, x + width - 4, ry + rowHeight - 2, hover ? BUTTON_HOVER : BUTTON);
            card(g, c.code(), x + 6, ry + 1, 16, 23);
            g.drawString(font, font.plainSubstrByWidth(YgoData.text().cardName(c.code()), width - 34), x + 26,
                    ry + 3, GOLD);
            g.drawString(font, font.plainSubstrByWidth(c.action(), width - 34), x + 26, ry + 13, TEXT);
            if (hover) {
                hoverCode = c.code();
            }
            hits.add(new Hit(x + 4, ry, width - 8, rowHeight - 2, () -> choose(c, null)));
        }
        int by = top + shown * rowHeight + 2;
        if (pass != null) {
            button(g, font, x + width - 104, by, 100, pass.label(), true, mx, my, () -> choose(pass, null));
        }
        pager(g, font, pages, x + 4, by, mx, my);
    }

    // ---- Card panel ----

    /** The card under the mouse, big, with its stats and text, on the left between the life panels. */
    private static void cardPanel(GuiGraphics g, Font font, int w, int h) {
        if (hoverCode == 0) {
            return;
        }
        CardInfo info = YgoData.cards().card(hoverCode);
        if (info == null) {
            return;
        }
        int x = 6;
        int y = 50;
        int bottom = h - 76;
        if (bottom - y < 60) {
            return;
        }
        int width = CARD_PANEL_WIDTH;
        panel(g, x, y, width, bottom - y);
        int ty = y + 3;
        for (FormattedCharSequence line : font.split(Component.literal(info.name()), width - 6)) {
            g.drawString(font, line, x + 3, ty, GOLD);
            ty += 9;
        }
        String type = CardItem.typeLine(info);
        int stats = type.indexOf(" · ATK");
        for (String part : stats >= 0 ? new String[]{type.substring(0, stats), type.substring(stats + 3)}
                : new String[]{type}) {
            for (FormattedCharSequence line : font.split(Component.literal(part), width - 6)) {
                if (ty + 9 > bottom) {
                    return;
                }
                g.drawString(font, line, x + 3, ty, DIM);
                ty += 9;
            }
        }
        if (hoverCard != null && info.is(TYPE_MONSTER) && hoverCard.code() == hoverCode && ty + 9 <= bottom) {
            g.drawString(font, "Now " + hoverCard.attack() + (info.is(TYPE_LINK) ? "" : " / " + hoverCard.defense()),
                    x + 3, ty, GOLD);
            ty += 9;
            if (!hoverCard.overlayCodes().isEmpty() && ty + 9 <= bottom) {
                g.drawString(font, "Materials: " + hoverCard.overlayCodes().size(), x + 3, ty, GOLD);
                ty += 9;
            }
        }
        List<FormattedCharSequence> text = font.split(Component.literal(info.description().replace("\r", "")),
                width - 6);
        int textRoom = Math.min(text.size(), 6) * 9;
        int artHeight = Math.min(58, bottom - ty - 4 - textRoom);
        if (artHeight >= 30) {
            int artWidth = artHeight * 40 / 58;
            card(g, hoverCode, x + width / 2 - artWidth / 2, ty + 2, artWidth, artHeight);
            ty += artHeight + 4;
        }
        for (FormattedCharSequence line : text) {
            if (ty + 9 > bottom) {
                break;
            }
            g.drawString(font, line, x + 3, ty, TEXT);
            ty += 9;
        }
    }

    // ---- Input ----

    public static boolean mouseClicked(double mx, double my, int w, int h) {
        DuelView view = ClientDuel.view();
        if (view == null || !ClientField.active()) {
            return false;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(mx, my)) {
                hits.get(i).action().run();
                return true;
            }
        }
        if (DuelStaging.introRunning() || DuelStaging.resultShowing()) {
            return true;
        }
        if (menu != null) {
            menu = null;
            return true;
        }
        int handCard = handCardAt(view.board().side(view.you()).hand().size(), mx, my, w, h);
        if (handCard >= 0) {
            pressedHand = handCard;
            pressX = mx;
            pressY = my;
            dragging = false;
            return true;
        }
        Loc zone = zoneUnder(mx, my, w, h);
        if (zone != null) {
            clickCard(zone, mx, my);
        }
        return true;
    }

    public static void mouseDragged(double mx, double my) {
        if (pressedHand >= 0 && !dragging && Math.hypot(mx - pressX, my - pressY) > DRAG_START) {
            dragging = true;
            menu = null;
        }
    }

    public static void mouseReleased(double mx, double my, int w, int h) {
        if (pressedHand < 0) {
            return;
        }
        int index = pressedHand;
        boolean dropped = dragging;
        pressedHand = -1;
        dragging = false;
        if (ClientDuel.view() == null || ClientDuel.prompt() == null) {
            return;
        }
        if (dropped) {
            Loc zone = zoneUnder(mx, my, w, h);
            if (zone != null) {
                drop(index, zone, mx, my);
            }
        } else {
            clickCard(handLoc(index), mx, my);
        }
    }

    private static Loc zoneUnder(double mx, double my, int w, int h) {
        if (!ClientField.active()) {
            return null;
        }
        Vec3[] ray = DuelMode.rayThrough(mx / w, my / h);
        ClientField.hoverRay(ray[0], ray[1]);
        return ClientField.hovered();
    }

    /** A click on a card or zone: its menu while you're choosing an action, else it picks that card or zone. */
    private static void clickCard(Loc loc, double mx, double my) {
        PromptView p = ClientDuel.prompt();
        if (p == null) {
            return;
        }
        if (p.multi() == null && (commands(p) || chain(p))) {
            List<Choice> here = p.choices().stream()
                    .filter(c -> c.at() != null && FieldLayout.sameZone(c.at(), loc)).toList();
            if (!here.isEmpty()) {
                openMenu(here, mx, my, null);
            }
            return;
        }
        ClientField.clickAt(loc);
    }

    /** A hand card dropped on a zone: plays it there, asking first when it could go there more than one way. */
    private static void drop(int index, Loc zone, double mx, double my) {
        PromptView p = ClientDuel.prompt();
        int me = ClientDuel.view().you();
        if (!commands(p) || zone.controller() != me) {
            return;
        }
        int location = zone.location() & ~LOCATION_OVERLAY;
        Loc hand = handLoc(index);
        List<Choice> fits = new ArrayList<>();
        for (Choice c : p.choices()) {
            if (c.at() == null || !FieldLayout.sameZone(c.at(), hand)) {
                continue;
            }
            boolean monsterZone = location == LOCATION_MZONE;
            boolean spellZone = location == LOCATION_SZONE;
            boolean fits1 = switch (c.kind()) {
                case SUMMON, SPECIAL_SUMMON, SET_MONSTER -> monsterZone;
                case SET_SPELL -> spellZone;
                case ACTIVATE -> spellZone && isSpellOrTrap(c.code());
                default -> false;
            };
            if (fits1) {
                fits.add(c);
            }
        }
        if (fits.size() == 1) {
            choose(fits.getFirst(), zone);
        } else if (fits.size() > 1) {
            openMenu(fits, mx, my, zone);
        }
    }

    private static boolean isSpellOrTrap(int code) {
        CardInfo info = YgoData.cards().card(code);
        return info != null && (info.is(TYPE_SPELL) || info.is(TYPE_TRAP));
    }

    /** The hint under the prompt's title: where to make this choice. */
    public static String help(PromptView p) {
        if (chain(p)) {
            return "Respond in the window, or let it pass";
        }
        if (p.multi() != null) {
            String count = " (" + ClientDuel.selected().size() + "/" + p.multi().max() + ")";
            return pickInPlace(p) ? "Click glowing cards, then Confirm" + count : "Pick cards in the window" + count;
        }
        if (commands(p)) {
            return "Click a glowing card, or drag one from your hand onto a zone";
        }
        if (place(p)) {
            return "Click a glowing zone";
        }
        return pickInPlace(p) ? "Click a glowing card" : "Choose in the window";
    }

    /** {@link #help} for a narrow screen. */
    public static String shortHelp(PromptView p) {
        if (p.multi() != null) {
            return "Pick " + ClientDuel.selected().size() + "/" + p.multi().max();
        }
        if (chain(p)) {
            return "Respond or pass";
        }
        return commands(p) ? "Click or drag a glowing card" : help(p);
    }

    /** Several choices are about the card just clicked: lists them next to the mouse. */
    public static void offer(List<Choice> choices) {
        openMenu(choices, mouseX, mouseY, null);
    }

    // ---- Duel log ----

    private static final int LOG_WIDTH = 130;
    private static boolean logOpen;
    /** How many lines up from the newest the log is scrolled. */
    private static int logScroll;

    public static void toggleLog() {
        logOpen = !logOpen;
        logScroll = 0;
    }

    private static int[] logBox(int w, int h) {
        int x = w - PHASE_WIDTH - 12 - LOG_WIDTH;
        int y = DuelHud.promptBottom(w) - 2;
        return new int[]{x, y, LOG_WIDTH, Math.max(40, h - HAND_HEIGHT - 10 - y)};
    }

    /** Everything that happened this duel, newest at the bottom; the mouse wheel scrolls it. */
    private static void log(GuiGraphics g, Font font, int mx, int my, int w, int h) {
        int[] box = logBox(w, h);
        panel(g, box[0], box[1], box[2], box[3]);
        g.drawString(font, "Duel log", box[0] + 4, box[1] + 3, GOLD);
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (int e = 0; e < ClientDuel.log().size(); e++) {
            for (FormattedCharSequence line : font.split(Component.literal(ClientDuel.log().get(e)), box[2] - 8)) {
                lines.add(line);
                colors.add(e % 2 == 0 ? TEXT : DIM); // every other entry dimmed, so long ones stay together
            }
        }
        int rows = (box[3] - 16) / 9;
        logScroll = Math.max(0, Math.min(logScroll, lines.size() - rows));
        int end = lines.size() - logScroll;
        int first = Math.max(0, end - rows);
        int y = box[1] + 14;
        for (int i = first; i < end; i++) {
            g.drawString(font, lines.get(i), box[0] + 4, y, colors.get(i));
            y += 9;
        }
        if (logScroll > 0) {
            g.drawString(font, "v " + logScroll + " more", box[0] + box[2] - 50, box[1] + 3, DIM);
        }
        hits.add(new Hit(box[0], box[1], box[2], box[3], () -> {
        }));
    }

    /** The mouse wheel over the log scrolls it. */
    public static boolean scroll(double mx, double my, double amount, int w, int h) {
        if (!logOpen) {
            return false;
        }
        int[] box = logBox(w, h);
        if (mx < box[0] || mx >= box[0] + box[2] || my < box[1] || my >= box[1] + box[3]) {
            return false;
        }
        logScroll += (int) Math.signum(amount) * 3;
        logScroll = Math.max(0, logScroll);
        return true;
    }

    /** Escape closes an open menu first, then the result screen. */
    public static boolean closeMenu() {
        if (DuelStaging.resultShowing()) {
            DuelStaging.closeResult();
            return true;
        }
        if (menu == null) {
            return false;
        }
        menu = null;
        return true;
    }
}
