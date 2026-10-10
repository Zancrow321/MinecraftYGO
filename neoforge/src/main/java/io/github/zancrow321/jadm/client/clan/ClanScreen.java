package io.github.zancrow321.jadm.client.clan;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.platform.Lighting;
import io.github.zancrow321.jadm.clan.ClanCrests;
import io.github.zancrow321.jadm.clan.ClanView;
import io.github.zancrow321.jadm.clan.Clans;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.List;

/**
 * The clan window ({@code /jadm clan} or its key): your clan with its crest, members and treasury; its wars and how
 * they went; and the clan ranking. Every button runs the matching {@code /jadm clan} command.
 */
public final class ClanScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int GOLD = 0xFFFFE070;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF9A9A9A;
    private static final int DARK = 0xFF5A5A5A;
    private static final int GREEN = 0xFF7CFC7C;
    private static final int RED = 0xFFFF7070;
    private static final int PANEL = 0xC0101018;
    private static final int BOX = 0xFF22222E;
    private static final int BOX_EDGE = 0xFF44445A;
    private static final int MINE = 0x40FFE070;
    private static final int PICKED = 0x606FB7FF;
    private static final int ROW_H = 12;
    private static final int TOP = 34;
    private static final int LEFT_W = 150;
    private static final int WAR_H = 58;

    private enum Tab { CLAN, WARS, RANKING }

    private static Tab lastTab = Tab.CLAN;

    private ClanView view;
    private Tab tab = lastTab;
    private double scroll;
    /** The member (clan tab) or clan (ranking tab) row clicked last, or -1. */
    private int picked = -1;
    private boolean surrenderAsked;
    private ModelPart flag;
    private EditBox textA;
    private EditBox textB;

    private ClanScreen(ClanView view) {
        super(Component.literal("Clans"));
        this.view = view;
    }

    /** The server sent the clan window's contents: open it, or refresh it if it is open. */
    public static void receive(boolean open, String json) {
        ClanView view;
        try {
            view = GSON.fromJson(json, ClanView.class);
        } catch (JsonParseException e) {
            return;
        }
        if (view == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ClanScreen screen) {
            screen.view = view;
            screen.picked = Math.min(screen.picked, screen.rows() - 1);
            screen.rebuildWidgets();
        } else if (open) {
            mc.setScreen(new ClanScreen(view));
        }
    }

    // ---- Layout and buttons ----

    private int rightX() {
        return 10 + LEFT_W + 8;
    }

    private int rightW() {
        return width - rightX() - 10;
    }

    private int panelBottom() {
        return height - 54;
    }

    @Override
    protected void init() {
        flag = minecraft.getEntityModels().bakeLayer(ModelLayers.BANNER).getChild("flag");
        String keepA = textA == null ? "" : textA.getValue();
        String keepB = textB == null ? "" : textB.getValue();
        textA = null;
        textB = null;
        int tx = width - 10;
        Tab[] tabs = Tab.values();
        for (int i = tabs.length - 1; i >= 0; i--) {
            Tab t = tabs[i];
            String label = switch (t) {
                case CLAN -> view.clan == null ? "Join or found" : "My clan";
                case WARS -> "Wars";
                case RANKING -> "Clan ranking";
            };
            int w = font.width(label) + 12;
            tx -= w;
            Button b = addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                tab = t;
                lastTab = t;
                scroll = 0;
                picked = -1;
                surrenderAsked = false;
                rebuildWidgets();
            }).bounds(tx, 8, w, 18).build());
            b.active = tab != t;
            tx -= 4;
        }
        if (tab == Tab.WARS && view.clan == null) {
            tab = Tab.CLAN;
        }
        Row row1 = new Row(rightX(), height - 48);
        Row row2 = new Row(rightX(), height - 26);
        if (view.clan == null) {
            noClanWidgets(row1, row2, keepA, keepB);
        } else {
            switch (tab) {
                case CLAN -> clanWidgets(row1, row2, keepA);
                case WARS -> warWidgets(row1, row2);
                case RANKING -> rankingWidgets(row1, keepA);
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(10, height - 26, 60, 18).build());
    }

    /** Lays buttons out left to right. */
    private final class Row {
        int x;
        final int y;

        Row(int x, int y) {
            this.x = x;
            this.y = y;
        }

        Button button(String label, Runnable action) {
            return button(label, null, action);
        }

        Button button(String label, String tooltip, Runnable action) {
            int w = font.width(label) + 12;
            Button.Builder builder = Button.builder(Component.literal(label), btn -> action.run()).bounds(x, y, w, 18);
            if (tooltip != null) {
                builder.tooltip(Tooltip.create(Component.literal(tooltip)));
            }
            Button b = addRenderableWidget(builder.build());
            x += w + 4;
            return b;
        }

        EditBox box(int w, String hint, String value) {
            EditBox box = new EditBox(font, x, y + 1, w, 16, Component.literal(hint));
            box.setHint(Component.literal(hint).withStyle(s -> s.withColor(0x707070)));
            box.setMaxLength(60);
            box.setValue(value);
            addRenderableWidget(box);
            x += w + 4;
            return box;
        }
    }

    private void noClanWidgets(Row row1, Row row2, String keepA, String keepB) {
        if (!view.enabled) {
            return;
        }
        textA = row1.box(40, "Tag", keepA);
        textA.setMaxLength(5);
        textB = row1.box(120, "Clan name", keepB);
        textB.setMaxLength(24);
        row1.button("Found" + (view.createPrice > 0 ? " (" + points(view.createPrice) + ")" : ""), () -> {
            if (!textA.getValue().isBlank() && !textB.getValue().isBlank()) {
                command("create " + textA.getValue().trim() + " " + textB.getValue().trim());
            }
        });
        for (String tag : view.invites) {
            row2.button("Join [" + tag + "]", () -> command("join " + tag));
        }
        if (tab != Tab.CLAN) {
            ClanView.ClanRow c = pickedClan();
            if (c != null && c.open && !view.invites.contains(c.tag)) {
                row2.button("Join [" + c.tag + "]", () -> command("join " + c.tag));
            }
        }
    }

    private void clanWidgets(Row row1, Row row2, String keepA) {
        boolean manages = manages();
        boolean leads = leads();
        if (manages) {
            textA = row1.box(70, "Player", keepA);
            textA.setMaxLength(16);
            row1.button("Invite", "Invite this player into the clan", () -> {
                if (!textA.getValue().isBlank()) {
                    command("invite " + textA.getValue().trim());
                }
            });
            row1.button("Crest", "Make the banner in your hand (from a loom) the clan's crest", () -> command("crest"));
            row1.button(view.clan.open ? "Open" : "Invite only", view.clan.open
                    ? "Anyone can join. Click to take only invited duelists."
                    : "Only invited duelists can join. Click to open the clan to all.",
                    () -> command("open " + !view.clan.open));
        }
        ClanView.MemberRow m = pickedMember();
        if (m != null && !m.name.equals(me())) {
            if (leads && m.role.equals("Member")) {
                row2.button("Promote", () -> command("promote " + m.name));
            }
            if (leads && m.role.equals("Officer")) {
                row2.button("Demote", () -> command("demote " + m.name));
            }
            if (leads) {
                row2.button("Make leader", () -> command("leader " + m.name));
            }
            if (manages && (leads || m.role.equals("Member"))) {
                row2.button("Remove", () -> command("kick " + m.name));
            }
        } else {
            if (view.clan.crest.base >= 0) {
                row2.button("Banner" + (view.bannerPrice > 0 ? " (" + points(view.bannerPrice) + ")" : ""),
                        "A banner with the clan's crest for you", () -> command("banner"));
            }
            if (view.points) {
                textB = row2.box(44, "DP", "");
                row2.button("Pay in", "Pay Duel Points into the clan treasury", () -> {
                    String amount = textB.getValue().trim();
                    if (amount.matches("\\d{1,12}")) {
                        command("deposit " + amount);
                    }
                });
            }
            if (!leads) {
                row2.button("Leave", () -> command("leave"));
            }
        }
    }

    private void warWidgets(Row row1, Row row2) {
        ClanView.WarRow w = shownWars().isEmpty() ? null : shownWars().get(Mth.clamp(picked, 0,
                shownWars().size() - 1));
        if (w == null) {
            return;
        }
        boolean manages = manages();
        if (!w.running) {
            if (w.incoming && manages) {
                row1.button("Accept the war", () -> command("war accept " + w.otherTag));
                row1.button("Deny", () -> command("war deny " + w.otherTag));
            } else if (!w.incoming && manages) {
                row1.button("Take the declaration back", () -> command("war cancel"));
            }
            return;
        }
        if (w.battle && !w.begun) {
            if (w.inLineup) {
                row1.button("Leave the lineup", () -> command("war leave"));
            } else if (w.myLineup.size() < view.battleDuelists) {
                row1.button("Fight in the battle", () -> command("war join"));
            }
            if (manages && !w.myReady && w.myLineup.size() >= view.battleDuelists) {
                row1.button("We are ready", () -> command("war ready"));
            }
        }
        if (leads()) {
            row2.button(surrenderAsked ? "Really surrender?" : "Surrender", () -> {
                if (surrenderAsked) {
                    command("war surrender confirm");
                    surrenderAsked = false;
                } else {
                    surrenderAsked = true;
                    rebuildWidgets();
                }
            });
        }
    }

    private void rankingWidgets(Row row1, String keepA) {
        ClanView.ClanRow c = pickedClan();
        if (c == null || view.clan == null || c.tag.equals(view.clan.tag) || !manages()) {
            return;
        }
        if (view.maxStake > 0) {
            textA = row1.box(56, "Stake", keepA);
        }
        row1.button("Race for points vs [" + c.tag + "]", () -> declare(c, "race"));
        row1.button("Arena battle vs [" + c.tag + "]", () -> declare(c, "battle"));
    }

    private void declare(ClanView.ClanRow c, String format) {
        String stake = textA == null ? "" : textA.getValue().trim();
        command("war declare " + c.tag + " " + format + (stake.matches("\\d{1,12}") ? " " + stake : ""));
    }

    private void command(String sub) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand("jadm clan " + sub);
        }
    }

    // ---- Drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, PANEL);
        g.drawString(font, "Clans", 10, 10, GOLD);
        g.drawString(font, "Season " + view.season, 10 + font.width("Clans") + 8, 10, GRAY);
        if (!view.enabled) {
            g.drawString(font, "Clans are turned off on this server.", 10, 22, RED);
        }
        drawLeft(g);
        int x = rightX();
        int w = rightW();
        box(g, x, TOP, w, panelBottom() - TOP);
        switch (tab) {
            case CLAN -> {
                if (view.clan == null) {
                    drawOpenClans(g, x, w);
                } else {
                    drawMembers(g, x, w);
                }
            }
            case WARS -> drawWars(g, x, w);
            case RANKING -> drawRanking(g, x, w);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The panel drawn in render is the background.
    }

    private void drawLeft(GuiGraphics g) {
        int x = 10;
        int y = TOP;
        int h = panelBottom() - TOP;
        box(g, x, y, LEFT_W, h);
        int cx = x + LEFT_W / 2;
        if (view.clan == null) {
            g.drawCenteredString(font, "You aren't in a clan", cx, y + 10, WHITE);
            int ty = y + 26;
            for (String line : wrap("Found your own below: a tag of 2 to 5 letters and a name. Or join a clan that "
                    + "invited you, or one that is open to all.", LEFT_W - 16)) {
                g.drawString(font, line, x + 8, ty, GRAY);
                ty += 10;
            }
            ty += 6;
            if (!view.invites.isEmpty()) {
                g.drawString(font, "Invitations", x + 8, ty, GOLD);
                ty += 12;
                for (String tag : view.invites) {
                    g.drawString(font, "[" + tag + "]", x + 12, ty, WHITE);
                    ty += 10;
                }
            }
            return;
        }
        ClanView.ClanRow c = view.clan;
        int color = 0xFF000000 | c.color;
        // The crest gets what the name, motto and the seven lines below leave over.
        int mottoLines = view.motto.isEmpty() ? 0 : wrap("\"" + view.motto + "\"", LEFT_W - 16).size();
        int crestH = Mth.clamp(h - 8 - 26 - mottoLines * 10 - 6 - (view.points ? 7 : 6) * 11 - 4, 20, 60);
        crest(g, c.crest, c.tag, color, cx - crestH / 4, y + 6, crestH);
        int ty = y + 6 + crestH + 4;
        g.drawCenteredString(font, "[" + c.tag + "]", cx, ty, color);
        g.drawCenteredString(font, font.plainSubstrByWidth(c.name, LEFT_W - 8), cx, ty + 12, WHITE);
        ty += 24;
        if (!view.motto.isEmpty()) {
            for (String line : wrap("\"" + view.motto + "\"", LEFT_W - 16)) {
                g.drawCenteredString(font, line, cx, ty, GRAY);
                ty += 10;
            }
        }
        ty += 6;
        ty = stat(g, x, ty, "Rating", c.rating + (view.peak > c.rating ? " (best " + view.peak + ")" : ""), GOLD);
        ty = stat(g, x, ty, "Place", view.place + " of " + view.total, WHITE);
        ty = stat(g, x, ty, "Wars", c.won + " won, " + c.lost + " lost" + (c.drawn > 0 ? ", " + c.drawn + " drawn"
                : ""), WHITE);
        ty = stat(g, x, ty, "Members", c.members + (view.maxMembers > 0 ? " of " + view.maxMembers : ""), WHITE);
        if (view.points) {
            ty = stat(g, x, ty, "Treasury", points(view.treasury), GOLD);
        }
        ty = stat(g, x, ty, "Joining", c.open ? "open to all" : "by invitation", GRAY);
        stat(g, x, ty, "You", view.role, GRAY);
    }

    private int stat(GuiGraphics g, int x, int y, String label, String value, int color) {
        if (y + 10 > panelBottom()) {
            return y;
        }
        g.drawString(font, label, x + 8, y, GRAY);
        String shown = font.plainSubstrByWidth(value, LEFT_W - 70);
        g.drawString(font, shown, x + LEFT_W - 8 - font.width(shown), y, color);
        return y + 11;
    }

    private void drawMembers(GuiGraphics g, int x, int w) {
        g.drawString(font, "Members", x + 8, TOP + 6, GOLD);
        g.drawString(font, "Role", x + 8, TOP + 20, GRAY);
        g.drawString(font, "Duelist", x + 66, TOP + 20, GRAY);
        String war = "War duels W-L";
        g.drawString(font, war, x + w - 8 - font.width(war), TOP + 20, GRAY);
        g.fill(x + 4, TOP + 31, x + w - 4, TOP + 32, BOX_EDGE);
        int listTop = TOP + 36;
        int visible = visibleRows(listTop);
        clampScroll(view.members.size(), visible);
        int first = (int) scroll;
        for (int i = first; i < Math.min(view.members.size(), first + visible); i++) {
            ClanView.MemberRow m = view.members.get(i);
            int ry = listTop + (i - first) * ROW_H;
            if (i == picked) {
                g.fill(x + 4, ry - 2, x + w - 4, ry + ROW_H - 2, PICKED);
            } else if (m.name.equals(me())) {
                g.fill(x + 4, ry - 2, x + w - 4, ry + ROW_H - 2, MINE);
            }
            g.drawString(font, m.role, x + 8, ry, m.role.equals("Leader") ? GOLD : m.role.equals("Officer")
                    ? 0xFF6FB7FF : GRAY);
            g.fill(x + 58, ry + 2, x + 62, ry + 6, m.online ? GREEN : DARK);
            g.drawString(font, m.name, x + 66, ry, WHITE);
            String record = m.warWins + "-" + m.warLosses;
            g.drawString(font, record, x + w - 8 - font.width(record), ry, GRAY);
        }
        if (manages()) {
            g.drawString(font, "Click a member for more.", x + 8, panelBottom() - 12, DARK);
        }
    }

    private void drawOpenClans(GuiGraphics g, int x, int w) {
        g.drawString(font, "Clans open to all", x + 8, TOP + 6, GOLD);
        List<ClanView.ClanRow> open = view.ranking.stream().filter(c -> c.open).toList();
        if (open.isEmpty()) {
            g.drawString(font, "None right now. Ask a clan's leader for an invitation.", x + 8, TOP + 22, GRAY);
            return;
        }
        int ry = TOP + 22;
        for (ClanView.ClanRow c : open) {
            if (ry + 16 > panelBottom()) {
                break;
            }
            crest(g, c.crest, c.tag, 0xFF000000 | c.color, x + 8, ry - 2, 16);
            g.drawString(font, "[" + c.tag + "]", x + 20, ry + 2, 0xFF000000 | c.color);
            g.drawString(font, c.name + "  " + c.members + (c.members == 1 ? " member" : " members") + ", rating "
                    + c.rating, x + 56, ry + 2, WHITE);
            g.drawString(font, "/jadm clan join " + c.tag, x + w - 8 - font.width("/jadm clan join " + c.tag),
                    ry + 2, DARK);
            ry += 18;
        }
    }

    private List<ClanView.WarRow> shownWars() {
        return view.wars;
    }

    private void drawWars(GuiGraphics g, int x, int w) {
        int y = TOP + 6;
        if (view.wars.isEmpty()) {
            g.drawString(font, "Your clan isn't at war.", x + 8, y, WHITE);
            for (String line : wrap("The leader or an officer declares one in the clan ranking: pick a clan, then a "
                    + "race for points (every duel won against them counts) or an arena battle (" + view.battleDuelists
                    + (view.battleDuelists == 1 ? " duelist" : " duelists") + " a side fight one bout after another on a tournament arena).", w - 16)) {
                y += 11;
                g.drawString(font, line, x + 8, y, GRAY);
            }
            y += 20;
        } else {
            for (int i = 0; i < view.wars.size() && y + WAR_H < panelBottom(); i++) {
                drawWar(g, view.wars.get(i), x + 4, y, w - 8, i == Mth.clamp(picked, 0, view.wars.size() - 1)
                        && view.wars.size() > 1);
                y += WAR_H + 4;
            }
        }
        if (view.history.isEmpty() || y + 24 > panelBottom()) {
            return;
        }
        g.drawString(font, "Past wars", x + 8, y, GOLD);
        y += 12;
        for (ClanView.HistoryRow h : view.history) {
            if (y + 10 > panelBottom()) {
                break;
            }
            String result = h.result > 0 ? "Won" : h.result < 0 ? "Lost" : "Drew";
            g.drawString(font, result, x + 8, y, h.result > 0 ? GREEN : h.result < 0 ? RED : GRAY);
            g.drawString(font, h.mine + " : " + h.theirs, x + 36, y, WHITE);
            g.drawString(font, "[" + h.otherTag + "]", x + 70, y, 0xFF000000 | h.otherColor);
            String how = switch (h.how) {
                case "surrender" -> ", surrendered";
                case "disband" -> ", disbanded";
                case "battle" -> ", arena battle";
                default -> "";
            };
            String rest = font.plainSubstrByWidth(h.otherName + how + (h.mvp.isEmpty() ? "" : ", best: " + h.mvp),
                    w - 200);
            g.drawString(font, rest, x + 70 + font.width("[" + h.otherTag + "] "), y, GRAY);
            String right = signed(h.ratingChange) + "  " + ago(h.millisAgo);
            g.drawString(font, right, x + w - 8 - font.width(right), y, DARK);
            y += 11;
        }
    }

    private void drawWar(GuiGraphics g, ClanView.WarRow r, int x, int y, int w, boolean highlight) {
        g.fill(x, y, x + w, y + WAR_H, highlight ? 0xFF2C3446 : 0xFF1A1A24);
        ClanView.ClanRow mine = view.clan;
        crest(g, mine.crest, mine.tag, 0xFF000000 | mine.color, x + 6, y + 6, 32);
        crest(g, r.otherCrest, r.otherTag, 0xFF000000 | r.otherColor, x + w - 22, y + 6, 32);
        int cx = x + w / 2;
        String kind = r.battle ? "Arena battle" : "Race for points";
        if (!r.running) {
            g.drawCenteredString(font, r.incoming ? "[" + r.otherTag + "] declared war on you"
                    : "You declared war on [" + r.otherTag + "]", cx, y + 6, GOLD);
            g.drawCenteredString(font, kind + (r.stake > 0 ? ", " + points(r.stake) + " each" : ""), cx, y + 18,
                    WHITE);
            g.drawCenteredString(font, (r.incoming ? "Answer within " : "They have ") + duration(r.millisLeft)
                    + (r.incoming ? "" : " to accept"), cx, y + 30, GRAY);
            return;
        }
        g.drawString(font, "[" + mine.tag + "]", x + 30, y + 8, 0xFF000000 | mine.color);
        String theirs = "[" + r.otherTag + "]";
        g.drawString(font, theirs, x + w - 30 - font.width(theirs), y + 8, 0xFF000000 | r.otherColor);
        g.pose().pushPose();
        g.pose().translate(cx, y + 5, 0);
        g.pose().scale(2, 2, 1);
        int scoreColor = r.mine > r.theirs ? GREEN : r.mine < r.theirs ? RED : WHITE;
        g.drawCenteredString(font, r.mine + " : " + r.theirs, 0, 0, scoreColor);
        g.pose().popPose();
        String line = kind + (r.battle ? "" : r.target > 0 ? ", first to " + r.target : "")
                + (r.stake > 0 ? ", " + points(2 * r.stake) + " at stake" : "");
        g.drawCenteredString(font, line, cx, y + 26, WHITE);
        if (r.battle) {
            String status = r.begun ? r.status : "Lineups: you " + r.myLineup.size() + "/" + view.battleDuelists
                    + (r.myReady ? " ready" : "") + ", they " + r.theirLineup.size() + "/" + view.battleDuelists
                    + (r.theirReady ? " ready" : "");
            g.drawCenteredString(font, font.plainSubstrByWidth(status, w - 60), cx, y + 37, GOLD);
            String names = String.join(", ", r.myLineup) + "  vs  " + String.join(", ", r.theirLineup);
            g.drawCenteredString(font, font.plainSubstrByWidth(names, w - 60), cx, y + 47, GRAY);
        } else {
            g.drawCenteredString(font, duration(r.millisLeft) + " left" + (r.best.isEmpty() ? ""
                    : ", your best: " + r.best), cx, y + 38, GRAY);
        }
    }

    private void drawRanking(GuiGraphics g, int x, int w) {
        int place = x + 8;
        int tag = x + 44;
        int name = x + 82;
        int record = x + w - 52;
        int members = x + w - 100;
        int rating = x + w - 146;
        g.drawString(font, "#", place, TOP + 6, GRAY);
        g.drawString(font, "Clan", tag, TOP + 6, GRAY);
        g.drawString(font, "Rating", rating, TOP + 6, GRAY);
        g.drawString(font, "Size", members, TOP + 6, GRAY);
        g.drawString(font, "W-L-D", record, TOP + 6, GRAY);
        g.fill(x + 4, TOP + 17, x + w - 4, TOP + 18, BOX_EDGE);
        if (view.ranking.isEmpty()) {
            g.drawCenteredString(font, "There are no clans yet.", x + w / 2, TOP + 40, GRAY);
            return;
        }
        int listTop = TOP + 22;
        int rowH = 16;
        int visible = Math.max(1, (panelBottom() - listTop - 4) / rowH);
        clampScroll(view.ranking.size(), visible);
        int first = (int) scroll;
        for (int i = first; i < Math.min(view.ranking.size(), first + visible); i++) {
            ClanView.ClanRow c = view.ranking.get(i);
            int ry = listTop + (i - first) * rowH;
            if (i == picked) {
                g.fill(x + 4, ry - 2, x + w - 4, ry + rowH - 2, PICKED);
            } else if (view.clan != null && c.tag.equals(view.clan.tag)) {
                g.fill(x + 4, ry - 2, x + w - 4, ry + rowH - 2, MINE);
            }
            int color = 0xFF000000 | c.color;
            g.drawString(font, (i + 1) + ".", place, ry + 3, i < 3 ? GOLD : WHITE);
            crest(g, c.crest, c.tag, color, place + 18, ry - 1, 14);
            g.drawString(font, "[" + c.tag + "]", tag, ry + 3, color);
            String shown = font.plainSubstrByWidth(c.name, rating - name - 20);
            g.drawString(font, shown, name, ry + 3, WHITE);
            if (c.atWar) {
                g.drawString(font, "at war", name + font.width(shown) + 6, ry + 3, RED);
            }
            g.drawString(font, String.valueOf(c.rating), rating, ry + 3, GOLD);
            g.drawString(font, String.valueOf(c.members), members, ry + 3, GRAY);
            g.drawString(font, c.won + "-" + c.lost + "-" + c.drawn, record, ry + 3, GRAY);
        }
        if (view.total > view.ranking.size()) {
            g.drawString(font, "Showing the best " + view.ranking.size() + " of " + view.total, x + 8,
                    panelBottom() - 12, DARK);
        } else if (view.clan != null && manages() && picked < 0) {
            g.drawString(font, "Click a clan to declare war on it.", x + 8, panelBottom() - 12, DARK);
        }
    }

    /**
     * A clan's crest as a banner {@code height} pixels high (half as wide), or a plain shield with its tag while it
     * has none.
     */
    private void crest(GuiGraphics g, ClanView.CrestView crest, String tag, int color, int x, int y, int height) {
        int width = height / 2;
        if (crest == null || crest.base < 0 || minecraft.level == null) {
            g.fill(x, y, x + width, y + height, 0xFF0A0A10);
            g.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
            g.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x80000000);
            if (height >= 30) {
                g.drawCenteredString(font, tag.substring(0, Math.min(2, tag.length())), x + width / 2,
                        y + height / 2 - 4, WHITE);
            }
            return;
        }
        List<Clans.Crest.Layer> layers = new ArrayList<>();
        for (int i = 0; i < crest.patterns.size() && i < crest.colors.size(); i++) {
            layers.add(new Clans.Crest.Layer(crest.patterns.get(i), crest.colors.get(i)));
        }
        BannerPatternLayers patterns = ClanCrests.layers(minecraft.level.registryAccess(),
                new Clans.Crest(crest.base, layers));
        // The flag model is 20 by 40; at scale s it is drawn s * 5/6 wide and s * 5/3 high.
        float s = height * 0.6f;
        Lighting.setupForFlatItems();
        g.pose().pushPose();
        g.pose().translate(x - s * 0.0833f + (width - s * 0.8333f) / 2, y + s * 1.8333f, 0);
        g.pose().scale(s, s, 1);
        g.pose().translate(0.5f, -0.5f, 0.5f);
        g.pose().scale(0.6666667f, 0.6666667f, -0.6666667f);
        flag.xRot = 0;
        flag.y = -32;
        BannerRenderer.renderPatterns(g.pose(), g.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY, flag,
                ModelBakery.BANNER_BASE, true, DyeColor.byId(crest.base), patterns);
        g.pose().popPose();
        g.flush();
        Lighting.setupFor3DItems();
    }

    private static void box(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BOX_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BOX);
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int x = rightX();
        if (mouseX < x || mouseX > x + rightW() || mouseY > panelBottom()) {
            return false;
        }
        int hit = -1;
        if (tab == Tab.CLAN && view.clan != null && manages()) {
            int listTop = TOP + 36;
            int row = (int) ((mouseY - listTop + 2) / ROW_H);
            if (mouseY >= listTop - 2 && row >= 0 && row < visibleRows(listTop)) {
                hit = row + (int) scroll;
            }
            if (hit >= view.members.size()) {
                hit = -1;
            }
        } else if (tab == Tab.RANKING) {
            int listTop = TOP + 22;
            int row = (int) ((mouseY - listTop + 2) / 16);
            if (mouseY >= listTop - 2 && row >= 0) {
                hit = row + (int) scroll;
            }
            if (hit >= view.ranking.size()) {
                hit = -1;
            }
        } else if (tab == Tab.WARS && view.wars.size() > 1) {
            int row = (int) ((mouseY - TOP - 6) / (WAR_H + 4));
            hit = row >= 0 && row < view.wars.size() ? row : -1;
        } else {
            return false;
        }
        picked = hit == picked ? -1 : hit;
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 3;
        return true;
    }

    private void clampScroll(int rows, int visible) {
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows - visible));
    }

    private int visibleRows(int listTop) {
        return Math.max(1, (panelBottom() - listTop - 16) / ROW_H);
    }

    private int rows() {
        return switch (tab) {
            case CLAN -> view.members.size();
            case WARS -> view.wars.size();
            case RANKING -> view.ranking.size();
        };
    }

    // ---- Helpers ----

    private ClanView.MemberRow pickedMember() {
        return tab == Tab.CLAN && picked >= 0 && picked < view.members.size() ? view.members.get(picked) : null;
    }

    private ClanView.ClanRow pickedClan() {
        return tab == Tab.RANKING && picked >= 0 && picked < view.ranking.size() ? view.ranking.get(picked) : null;
    }

    private boolean manages() {
        return view.role.equals("Leader") || view.role.equals("Officer");
    }

    private boolean leads() {
        return view.role.equals("Leader");
    }

    private String me() {
        return minecraft == null || minecraft.player == null ? "" : minecraft.player.getScoreboardName();
    }

    private String points(long amount) {
        return String.format("%,d %s", amount, view.symbol);
    }

    private List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        font.getSplitter().splitLines(text, width, net.minecraft.network.chat.Style.EMPTY)
                .forEach(line -> out.add(line.getString()));
        return out;
    }

    private static String signed(int n) {
        return n >= 0 ? "+" + n : String.valueOf(n);
    }

    private static String duration(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        long days = minutes / (60 * 24);
        long hours = minutes / 60 % 24;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes % 60 + "min";
        }
        return Math.max(1, minutes) + " min";
    }

    private static String ago(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        if (minutes < 60) {
            return minutes + " min ago";
        }
        if (minutes < 60 * 24) {
            return minutes / 60 + "h ago";
        }
        return minutes / (60 * 24) + "d ago";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
