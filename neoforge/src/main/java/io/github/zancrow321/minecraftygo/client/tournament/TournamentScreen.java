package io.github.zancrow321.minecraftygo.client.tournament;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.github.zancrow321.minecraftygo.engine.tournament.Bracket;
import io.github.zancrow321.minecraftygo.tournament.TournamentView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The tournament window ({@code /ygo tournament}): the bracket (or the Swiss table), the matches, the rules and
 * prizes, and buttons to join, leave, start and call off. It follows the tournament live while it is open.
 */
public final class TournamentScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int GOLD = 0xFFFFE070;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF9A9A9A;
    private static final int DARK = 0xFF5A5A5A;
    private static final int GREEN = 0xFF7CFC7C;
    private static final int AQUA = 0xFF6FE6FF;
    private static final int RED = 0xFFFF7070;
    private static final int PANEL = 0xC0101018;
    private static final int BOX = 0xFF22222E;
    private static final int BOX_EDGE = 0xFF44445A;
    private static final int LIVE = 0xFFFFC040;
    private static final int LINE = 0xFF6A6A80;
    private static final int BOX_W = 118;
    private static final int ROW_H = 11;
    private static final int BOX_H = ROW_H * 2;
    private static final int COLUMN = BOX_W + 26;
    private static final int TOP = 54;

    private static TournamentView latest;

    private enum Tab { BRACKET, TABLE, MATCHES, INFO }

    private Tab tab;
    private double scrollX;
    private double scrollY;
    private boolean dragging;

    public TournamentScreen(String tab) {
        super(Component.literal("Tournament"));
        this.tab = switch (tab) {
            case "bracket" -> Tab.BRACKET;
            case "table" -> Tab.TABLE;
            case "matches" -> Tab.MATCHES;
            case "rules", "info" -> Tab.INFO;
            default -> null;
        };
    }

    /** A tournament update from the server; opens the window if asked, refreshes it if it is open. */
    public static void receive(String open, String json) {
        TournamentView view = null;
        if (!json.isEmpty()) {
            try {
                view = GSON.fromJson(json, TournamentView.class);
            } catch (JsonParseException e) {
                return;
            }
        }
        latest = view;
        Minecraft mc = Minecraft.getInstance();
        if (!open.isEmpty()) {
            mc.setScreen(new TournamentScreen(open));
        } else if (mc.screen instanceof TournamentScreen screen) {
            screen.rebuildWidgets();
        }
    }

    private static boolean elimination(TournamentView v) {
        return v.format.equals("single") || v.format.equals("double")
                || v.matches.stream().anyMatch(m -> m.stage.equals("T"));
    }

    private String me() {
        return Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getScoreboardName();
    }

    private int myIndex() {
        return latest == null ? -1 : latest.entrants.indexOf(me());
    }

    @Override
    protected void init() {
        TournamentView v = latest;
        if (tab == Tab.BRACKET && v != null && !elimination(v)) {
            tab = Tab.TABLE;
        }
        if (tab == null) {
            tab = v != null && v.state.equals("open") ? Tab.INFO
                    : v != null && !elimination(v) ? Tab.TABLE : Tab.BRACKET;
        }
        int x = 10;
        for (Tab t : Tab.values()) {
            if (v == null || t == Tab.BRACKET && !elimination(v)) {
                continue;
            }
            String label = switch (t) {
                case BRACKET -> "Bracket";
                case TABLE -> v.format.equals("swiss") || v.format.equals("roundrobin") ? "Table" : "Duelists";
                case MATCHES -> "Matches";
                case INFO -> "Rules & prizes";
            };
            int w = font.width(label) + 16;
            Button b = addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                tab = t;
                scrollX = 0;
                scrollY = 0;
                rebuildWidgets();
            }).bounds(x, 30, w, 18).build());
            b.active = tab != t;
            x += w + 4;
        }
        int y = height - 24;
        int right = width - 10;
        right = button(right, y, "Close", this::onClose);
        if (v != null && (v.state.equals("open") || v.state.equals("running"))) {
            boolean in = myIndex() >= 0;
            if (v.state.equals("open")) {
                right = button(right, y, in ? "Leave" : "Join", () -> command(in ? "leave" : "join"));
            } else if (in && v.places.get(myIndex()) == 0) {
                right = button(right, y, "Ready", () -> command("ready"));
            }
            if (me().equals(v.host) || Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.hasPermissions(2)) {
                if (v.state.equals("open")) {
                    right = button(right, y, "Start now", () -> command("start"));
                }
                button(right, y, "Call off", () -> command("cancel"));
            }
        }
    }

    /** A button ending at {@code right}; @return where the next one ends */
    private int button(int right, int y, String label, Runnable action) {
        int w = font.width(label) + 20;
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(right - w, y, w, 18).build());
        return right - w - 4;
    }

    private static void command(String sub) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.connection.sendCommand("ygo tournament " + sub);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        TournamentView v = latest;
        g.fill(6, TOP - 2, width - 6, height - 30, PANEL);
        if (v == null) {
            g.drawCenteredString(font, "There is no tournament yet.", width / 2, height / 2 - 10, WHITE);
            g.drawCenteredString(font, "Operators open one with /ygo tournament create [format] [name].", width / 2,
                    height / 2 + 4, GRAY);
            return;
        }
        g.drawString(font, v.name, 10, 8, GOLD);
        String sub = v.formatName + (v.bestOf > 1 ? ", best of " + v.bestOf : "") + "  ·  " + v.status
                + "  ·  hosted by " + v.host;
        g.drawString(font, sub, 10, 19, GRAY);
        g.enableScissor(6, TOP - 2, width - 6, height - 30);
        switch (tab) {
            case BRACKET -> bracket(g, v, mouseX, mouseY);
            case TABLE -> table(g, v);
            case MATCHES -> matches(g, v);
            case INFO -> info(g, v);
        }
        g.disableScissor();
        if (tab == Tab.BRACKET) {
            g.drawString(font, "Drag to move around", width - 10 - font.width("Drag to move around"), 19, DARK);
        }
    }

    // ---- Bracket ------------------------------------------------------------------------------------------------

    private record Placed(Bracket.Match match, int x, int y) {
    }

    /** Lays the elimination matches out in columns, later rounds between the matches they come from. */
    private List<Placed> layout(TournamentView v) {
        List<Placed> out = new ArrayList<>();
        Map<Integer, Integer> ys = new HashMap<>();
        Map<Integer, Integer> xs = new HashMap<>();
        boolean swiss = v.format.equals("swiss");
        String main = swiss ? "T" : "W";
        int top = 14;
        int bottom = layoutStage(v, main, top, ys, xs);
        int lastColumn = v.matches.stream().filter(m -> m.stage.equals(main)).mapToInt(m -> m.round).max().orElse(1);
        if (v.format.equals("double")) {
            int losersTop = bottom + 38;
            layoutStage(v, "L", losersTop, ys, xs);
            int lastLoser = v.matches.stream().filter(m -> m.stage.equals("L")).mapToInt(m -> m.round).max()
                    .orElse(0);
            int column = Math.max(lastColumn, lastLoser);
            for (Bracket.Match m : v.matches) {
                if (m.stage.equals("F") || m.stage.equals("F2")) {
                    Integer a = ys.get(m.from[0].ref);
                    Integer b = ys.get(m.from[1].ref);
                    int y = m.stage.equals("F2") ? ys.getOrDefault(m.from[0].ref, top) + BOX_H + 16
                            : a != null && b != null ? (a + b) / 2 : top;
                    xs.put(m.id, column + (m.stage.equals("F2") ? 1 : 0));
                    ys.put(m.id, y);
                }
            }
        }
        for (Bracket.Match m : v.matches) {
            if (m.stage.equals("3")) {
                xs.put(m.id, lastColumn - 1);
                ys.put(m.id, ys.getOrDefault(m.id - 1, top) + BOX_H + 30);
            }
        }
        for (Bracket.Match m : v.matches) {
            // A grand final reset that wasn't needed isn't shown.
            if (xs.containsKey(m.id) && !(m.stage.equals("F2") && m.walkover)) {
                out.add(new Placed(m, 14 + xs.get(m.id) * COLUMN, ys.get(m.id)));
            }
        }
        return out;
    }

    /** @return the lowest y used */
    private int layoutStage(TournamentView v, String stage, int top, Map<Integer, Integer> ys,
                            Map<Integer, Integer> xs) {
        int bottom = top;
        int rounds = v.matches.stream().filter(m -> m.stage.equals(stage)).mapToInt(m -> m.round).max().orElse(0);
        for (int r = 1; r <= rounds; r++) {
            int i = 0;
            for (Bracket.Match m : v.matches) {
                if (!m.stage.equals(stage) || m.round != r) {
                    continue;
                }
                Integer a = m.from[0].kind == 'W' ? ys.get(m.from[0].ref) : null;
                Integer b = m.from[1].kind == 'W' ? ys.get(m.from[1].ref) : null;
                int y = a != null && b != null ? (a + b) / 2 : a != null ? a : b != null ? b
                        : top + i * (BOX_H + 8);
                ys.put(m.id, y);
                xs.put(m.id, r - 1);
                bottom = Math.max(bottom, y + BOX_H);
                i++;
            }
        }
        return bottom;
    }

    private void bracket(GuiGraphics g, TournamentView v, int mouseX, int mouseY) {
        if (v.matches.isEmpty()) {
            g.drawString(font, "The bracket is drawn when the tournament starts.", 14, TOP + 6, GRAY, false);
            return;
        }
        List<Placed> placed = layout(v);
        int ox = (int) scrollX + 6;
        int oy = (int) scrollY + TOP;
        Map<Integer, Placed> byId = new HashMap<>();
        placed.forEach(p -> byId.put(p.match().id, p));
        // A title over each round: at the top for the main bracket, over the first box for the rest.
        java.util.Set<String> titled = new java.util.HashSet<>();
        for (Placed p : placed) {
            Bracket.Match m = p.match();
            if (!titled.add(m.stage + m.round)) {
                continue;
            }
            boolean top = m.stage.equals("W") || m.stage.equals("T");
            g.drawString(font, short_(v.titles.get(m.id)), ox + p.x(), top ? oy + 3 : oy + p.y() - 10, GRAY);
        }
        // Lines from each match to the one its winner goes on to.
        for (Placed p : placed) {
            for (Bracket.Source s : p.match().from) {
                Placed from = s.kind == 'W' ? byId.get(s.ref) : null;
                if (from == null) {
                    continue;
                }
                int x1 = ox + from.x() + BOX_W;
                int y1 = oy + from.y() + BOX_H / 2;
                int x2 = ox + p.x();
                int y2 = oy + p.y() + BOX_H / 2;
                int mid = x1 + (x2 - x1) / 2;
                g.hLine(x1, mid, y1, LINE);
                g.vLine(mid, Math.min(y1, y2), Math.max(y1, y2), LINE);
                g.hLine(mid, x2, y2, LINE);
            }
        }
        for (Placed p : placed) {
            box(g, v, p.match(), ox + p.x(), oy + p.y());
        }
        // The champion, to the right of the last match.
        Placed last = placed.stream().filter(p -> p.match().winnerPlace == 1 || p.match().stage.equals("F"))
                .reduce((a, b) -> b).orElse(null);
        if (last != null && last.match().winner >= 0 && v.places.get(last.match().winner) == 1) {
            int x = ox + last.x() + BOX_W + 14;
            int y = oy + last.y() + BOX_H / 2;
            g.hLine(ox + last.x() + BOX_W, x - 3, y, LINE);
            g.drawString(font, "Champion", x, y - 10, GRAY);
            g.drawString(font, v.entrants.get(last.match().winner), x, y + 1, GOLD);
        }
        if (v.format.equals("double")) {
            int losersY = placed.stream().filter(p -> p.match().stage.equals("L")).mapToInt(Placed::y).min()
                    .orElse(-1);
            if (losersY >= 0) {
                g.drawString(font, "Losers' bracket", ox + 14, oy + losersY - 24, GOLD);
            }
        }
        for (Placed p : placed) {
            int x = ox + p.x();
            int y = oy + p.y();
            if (mouseX >= x && mouseX < x + BOX_W && mouseY >= y && mouseY < y + BOX_H && mouseY > TOP
                    && mouseY < height - 30) {
                g.renderTooltip(font, tooltip(v, p.match()), java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private List<Component> tooltip(TournamentView v, Bracket.Match m) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(v.titles.get(m.id)));
        if (m.a >= 0 && m.b >= 0) {
            out.add(Component.literal(name(v, m.a) + " vs " + name(v, m.b)));
            if (m.games() > 0) {
                out.add(Component.literal("Games " + m.winsA + "-" + m.winsB + (m.draws > 0 ? ", " + m.draws
                        + " drawn" : "")));
            }
            for (int e : new int[]{m.a, m.b}) {
                if (!v.decks.get(e).isEmpty()) {
                    out.add(Component.literal(v.entrants.get(e) + ": " + v.decks.get(e)).withColor(GRAY));
                }
            }
        }
        String live = v.live.get(m.id);
        if (live != null) {
            out.add(Component.literal(live).withColor(LIVE));
        }
        if (m.walkover && m.winner >= 0 && m.a >= 0 && m.b >= 0) {
            out.add(Component.literal("Decided without being played").withColor(GRAY));
        }
        return out;
    }

    /** "Winners quarterfinal" -> "Quarterfinal", "Losers round 3" -> "Losers 3", "round 2" -> "Round 2" */
    private static String short_(String title) {
        String s = title.replace("Winners ", "").replace("Top cut ", "Top cut: ").replace("Losers round", "Losers");
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void box(GuiGraphics g, TournamentView v, Bracket.Match m, int x, int y) {
        boolean live = v.live.containsKey(m.id) && !m.decided();
        g.fill(x - 1, y - 1, x + BOX_W + 1, y + BOX_H + 1, live ? LIVE : BOX_EDGE);
        g.fill(x, y, x + BOX_W, y + BOX_H, BOX);
        g.hLine(x, x + BOX_W - 1, y + ROW_H, BOX_EDGE);
        row(g, v, m, m.a, m.winsA, x, y);
        row(g, v, m, m.b, m.winsB, x, y + ROW_H);
    }

    private void row(GuiGraphics g, TournamentView v, Bracket.Match m, int entrant, int wins, int x, int y) {
        String text;
        int color;
        if (entrant == Bracket.OPEN) {
            text = "";
            color = DARK;
        } else if (entrant < 0) {
            text = "bye";
            color = DARK;
        } else {
            text = v.entrants.get(entrant);
            boolean won = m.winner == entrant;
            boolean lost = m.decided() && !won;
            color = entrant == myIndex() ? AQUA : won ? GREEN : lost ? GRAY : WHITE;
        }
        String score = entrant >= 0 && m.a >= 0 && m.b >= 0 && (m.games() > 0 || m.decided())
                ? m.walkover && m.games() == 0 ? (m.winner == entrant ? "W" : "-") : String.valueOf(wins) : "";
        int room = BOX_W - 8 - font.width(score);
        g.drawString(font, font.plainSubstrByWidth(text, room), x + 3, y + 2, color, false);
        if (!score.isEmpty()) {
            g.drawString(font, score, x + BOX_W - 3 - font.width(score), y + 2, m.winner == entrant ? GREEN : GRAY,
                    false);
        }
    }

    private static String name(TournamentView v, int entrant) {
        return entrant < 0 ? "bye" : v.entrants.get(entrant);
    }

    // ---- Table, matches, info -----------------------------------------------------------------------------------

    private void table(GuiGraphics g, TournamentView v) {
        int y = TOP + 6 + (int) scrollY;
        int[] cols = {14, 40, 230, 270, 340, 400};
        boolean points = v.format.equals("swiss") || v.format.equals("roundrobin");
        String[] head = {"#", "Duelist", points ? "Pts" : "", "W-L-D", points ? "OMW" : "", "Games", "Deck"};
        for (int i = 0; i < cols.length; i++) {
            g.drawString(font, head[i], cols[i], y, GOLD, false);
        }
        g.drawString(font, head[6], 470, y, GOLD, false);
        y += 14;
        List<Bracket.Standing> rows = new ArrayList<>(v.standings.isEmpty() ? standingsFromEntrants(v)
                : v.standings);
        int rank = 0;
        boolean done = v.state.equals("done");
        if (done) {
            rows.sort(java.util.Comparator.comparingInt(s -> v.places.get(s.entrant) > 0 ? v.places.get(s.entrant)
                    : Integer.MAX_VALUE));
        }
        for (Bracket.Standing s : rows) {
            rank++;
            int e = s.entrant;
            int color = e == myIndex() ? AQUA : s.dropped ? DARK : WHITE;
            if (e == myIndex()) {
                g.fill(10, y - 2, width - 10, y + 10, 0x40FFFFFF);
            }
            String place = done && v.places.get(e) > 0 ? String.valueOf(v.places.get(e)) : String.valueOf(rank);
            g.drawString(font, place, cols[0], y, color, false);
            g.drawString(font, font.plainSubstrByWidth(v.entrants.get(e) + (v.npc.get(e) ? " (NPC)" : "")
                    + (s.dropped ? " (out)" : ""), 185), cols[1], y, color, false);
            if (points) {
                g.drawString(font, String.valueOf(s.points), cols[2], y, color, false);
                g.drawString(font, percent(s.omw), cols[4], y, GRAY, false);
            }
            g.drawString(font, s.wins + "-" + s.losses + "-" + s.draws, cols[3], y, color, false);
            g.drawString(font, s.games == 0 ? "-" : s.gameWins + "/" + s.games, cols[5], y, GRAY, false);
            g.drawString(font, font.plainSubstrByWidth(v.decks.get(e), Math.max(40, width - 480)), 470, y, GRAY,
                    false);
            y += 12;
        }
    }

    /** Before the start: the duelists who joined, in order. */
    private static List<Bracket.Standing> standingsFromEntrants(TournamentView v) {
        List<Bracket.Standing> out = new ArrayList<>();
        for (int i = 0; i < v.entrants.size(); i++) {
            Bracket.Standing s = new Bracket.Standing();
            s.entrant = i;
            out.add(s);
        }
        return out;
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    private void matches(GuiGraphics g, TournamentView v) {
        int y = TOP + 6 + (int) scrollY;
        List<Bracket.Match> order = new ArrayList<>();
        v.matches.stream().filter(m -> v.live.containsKey(m.id) && !m.decided()).forEach(order::add);
        v.matches.stream().filter(m -> m.playable() && !v.live.containsKey(m.id)).forEach(order::add);
        List<Bracket.Match> done = new ArrayList<>(v.matches.stream()
                .filter(m -> m.decided() && m.a >= 0 && m.b >= 0 && !(m.reset && m.walkover)).toList());
        java.util.Collections.reverse(done);
        order.addAll(done);
        if (order.isEmpty()) {
            g.drawString(font, v.state.equals("open") ? "Pairings come when the tournament starts."
                    : "No matches yet.", 14, y, GRAY, false);
            return;
        }
        for (Bracket.Match m : order) {
            boolean mine = m.a == myIndex() || m.b == myIndex();
            String status = v.live.getOrDefault(m.id, m.decided() ? "" : "Up next");
            String result;
            if (m.winner == Bracket.DRAW) {
                result = "draw";
            } else if (m.decided()) {
                result = (m.winner == m.a ? name(v, m.a) : name(v, m.b)) + " won" + (m.walkover && m.games() == 0
                        ? " (no-show)" : "");
            } else {
                result = status;
            }
            g.drawString(font, font.plainSubstrByWidth(v.titles.get(m.id), 120), 14, y, GRAY, false);
            g.drawString(font, name(v, m.a) + "  " + m.winsA + " - " + m.winsB + "  " + name(v, m.b), 140, y,
                    mine ? AQUA : WHITE, false);
            g.drawString(font, result, 360, y, m.decided() ? GREEN : LIVE, false);
            y += 12;
        }
    }

    private void info(GuiGraphics g, TournamentView v) {
        int y = TOP + 6 + (int) scrollY;
        g.drawString(font, "Rules", 14, y, GOLD, false);
        y += 13;
        for (String line : v.rules) {
            y = wrapped(g, line, y, WHITE);
        }
        y += 6;
        g.drawString(font, "Prizes", 14, y, GOLD, false);
        y += 13;
        if (v.prizes.isEmpty()) {
            y = wrapped(g, "None", y, GRAY);
        }
        for (String line : v.prizes) {
            y = wrapped(g, line, y, WHITE);
        }
        if (v.arenas == 0) {
            y += 4;
            y = wrapped(g, "No arena belongs to tournaments yet: an operator stands on a Duel Arena and runs "
                    + "/ygo tournament arena add.", y, RED);
        }
        y += 6;
        g.drawString(font, "Duelists (" + v.entrants.size() + ")", 14, y, GOLD, false);
        y += 13;
        y = wrapped(g, v.entrants.isEmpty() ? "Nobody yet." : String.join(", ", v.entrants), y, WHITE);
        y += 6;
        g.drawString(font, "Latest", 14, y, GOLD, false);
        y += 13;
        for (int i = v.news.size() - 1; i >= Math.max(0, v.news.size() - 12); i--) {
            y = wrapped(g, v.news.get(i), y, GRAY);
        }
    }

    private int wrapped(GuiGraphics g, String text, int y, int color) {
        for (var line : font.split(Component.literal(text), width - 40)) {
            g.drawString(font, line, 18, y, color, false);
            y += 11;
        }
        return y;
    }

    // ---- Input --------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        dragging = tab == Tab.BRACKET && mouseY > TOP && mouseY < height - 30;
        return dragging;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            scrollX = Math.min(0, scrollX + dragX);
            scrollY = Math.min(0, scrollY + dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollXAmount, double scrollYAmount) {
        scrollY = Math.min(0, scrollY + scrollYAmount * 24);
        if (scrollXAmount != 0) {
            scrollX = Math.min(0, scrollX + scrollXAmount * 24);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public String toString() {
        return "TournamentScreen[" + (latest == null ? "none" : latest.name.toLowerCase(Locale.ROOT)) + "]";
    }
}
