package io.github.zancrow321.jadm.client.quest;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.github.zancrow321.jadm.network.QuestActionPayload;
import io.github.zancrow321.jadm.network.QuestActionPayload.Action;
import io.github.zancrow321.jadm.quest.QuestView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The quest window ({@code /jadm quests} or its key): today's and this week's quests with their progress and reward,
 * a button to claim each finished one and one to swap an unfinished one for another.
 */
public final class QuestScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int WIDTH = 320;
    private static final int CARD_H = 48;
    private static final int HEADER_H = 16;
    private static final int GOLD = 0xFFFFD040;
    private static final int GREEN = 0xFF60E070;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF9AA4B0;
    private static final int DARK = 0xFF5A5A66;
    private static final int BUTTON_W = 76;
    private static final int BUTTON_H = 16;

    private QuestView view;
    private int left;
    private int top;
    private int panelH;
    private double scroll;
    private Button claimAll;

    /** A button drawn in the list (so it scrolls with it): what it does to which quest, and where it was drawn. */
    private record Hit(Action action, String id, int x, int y) {
    }

    private final List<Hit> hits = new ArrayList<>();

    private QuestScreen(QuestView view) {
        super(Component.translatable("screen.jadm.quests"));
        this.view = view;
    }

    /** The server sent the quests: pop up the ones that moved, and open or refresh the window. */
    public static void receive(boolean open, String json) {
        QuestView view;
        try {
            view = GSON.fromJson(json, QuestView.class);
        } catch (JsonParseException e) {
            return;
        }
        if (view == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (QuestView.Entry entry : view.toasts) {
            QuestToast.show(mc, entry);
        }
        if (mc.screen instanceof QuestScreen screen) {
            screen.view = view;
            screen.rebuildWidgets();
        } else if (open) {
            mc.setScreen(new QuestScreen(view));
        }
    }

    @Override
    protected void init() {
        panelH = Math.min(height - 16, contentHeight() + 64);
        left = (width - WIDTH) / 2;
        top = (height - panelH) / 2;
        claimAll = addRenderableWidget(Button.builder(Component.translatable("screen.jadm.quests.claim_all"),
                b -> send(Action.CLAIM_ALL, "")).bounds(left + 8, top + panelH - 24, 100, 18).build());
        claimAll.active = claimable() > 0;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + WIDTH - 68, top + panelH - 24, 60, 18).build());
    }

    private int contentHeight() {
        int h = 0;
        if (view.daily.isEmpty() && view.weekly.isEmpty()) {
            return 40;
        }
        if (!view.daily.isEmpty()) {
            h += HEADER_H + view.daily.size() * (CARD_H + 4);
        }
        if (!view.weekly.isEmpty()) {
            h += HEADER_H + 4 + view.weekly.size() * (CARD_H + 4);
        }
        return h + 4;
    }

    private int claimable() {
        int n = 0;
        for (List<QuestView.Entry> list : List.of(view.daily, view.weekly)) {
            for (QuestView.Entry e : list) {
                if (e.done() && !e.claimed) {
                    n++;
                }
            }
        }
        return n;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 1, top - 1, left + WIDTH + 1, top + panelH + 1, 0xFF44445A);
        g.fill(left, top, left + WIDTH, top + panelH, 0xF0181820);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + 8, top + 8, GOLD);
        if (!view.shared && view.enabled) {
            Component rerolls = Component.translatable("screen.jadm.quests.rerolls", view.rerollsLeft);
            g.drawString(font, rerolls, left + WIDTH - 8 - font.width(rerolls), top + 8, GRAY);
        }
        int listTop = top + 24;
        int listBottom = top + panelH - 30;
        hits.clear();
        if (!view.enabled) {
            g.drawCenteredString(font, Component.translatable("screen.jadm.quests.off"), left + WIDTH / 2,
                    listTop + 16, GRAY);
            return;
        }
        if (view.daily.isEmpty() && view.weekly.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.jadm.quests.none"), left + WIDTH / 2,
                    listTop + 16, GRAY);
            return;
        }
        int maxScroll = Math.max(0, contentHeight() - (listBottom - listTop));
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(left, listTop, left + WIDTH, listBottom);
        int y = listTop - (int) scroll;
        if (!view.daily.isEmpty()) {
            y = section(g, y, Component.translatable("screen.jadm.quests.daily",
                    QuestText.duration(view.dailyResetMs)), view.daily, mouseX, mouseY, listTop, listBottom);
        }
        if (!view.weekly.isEmpty()) {
            y = section(g, y + 4, Component.translatable("screen.jadm.quests.weekly",
                    QuestText.duration(view.weeklyResetMs)), view.weekly, mouseX, mouseY, listTop, listBottom);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int track = listBottom - listTop;
            int thumb = Math.max(12, track * track / (track + maxScroll));
            int thumbY = listTop + (int) ((track - thumb) * (scroll / maxScroll));
            g.fill(left + WIDTH - 4, thumbY, left + WIDTH - 2, thumbY + thumb, 0xA0FFFFFF);
        }
    }

    private int section(GuiGraphics g, int y, Component header, List<QuestView.Entry> entries, int mouseX,
                        int mouseY, int listTop, int listBottom) {
        g.drawString(font, header, left + 8, y + 4, GOLD);
        y += HEADER_H;
        for (QuestView.Entry e : entries) {
            card(g, e, left + 8, y, WIDTH - 16, mouseX, mouseY, listTop, listBottom);
            y += CARD_H + 4;
        }
        return y;
    }

    private void card(GuiGraphics g, QuestView.Entry e, int x, int y, int w, int mouseX, int mouseY, int listTop,
                      int listBottom) {
        boolean done = e.done();
        int edge = e.claimed ? 0xFF2E4A34 : done ? 0xFFB08A20 : 0xFF3A3A4C;
        g.fill(x, y, x + w, y + CARD_H, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + CARD_H - 1, e.claimed ? 0xFF1A2420 : 0xFF22222E);
        int textW = w - BUTTON_W - 18;
        int titleColor = e.claimed ? GRAY : WHITE;
        // A long title takes the second line too when there are no conditions to show there.
        String details = QuestText.details(e).getString();
        List<FormattedCharSequence> titleLines = font.split(QuestText.title(e), textW);
        int titleRows = details.isEmpty() ? Math.min(2, titleLines.size()) : 1;
        if (titleRows == 1 && titleLines.size() > 1) {
            g.drawString(font, QuestText.fit(font, QuestText.title(e).getString(), textW), x + 6, y + 5, titleColor);
        } else {
            for (int i = 0; i < titleRows; i++) {
                g.drawString(font, titleLines.get(i), x + 6, y + 5 + i * 11, titleColor);
            }
        }
        if (!details.isEmpty()) {
            g.drawString(font, QuestText.fit(font, details, textW), x + 6, y + 16, GRAY);
        }
        // Progress.
        int barX = x + 6;
        int barY = y + 29;
        int barW = 110;
        g.fill(barX, barY, barX + barW, barY + 5, 0xFF0A0A10);
        int filled = (int) (barW * Mth.clamp(e.progress / (float) Math.max(1, e.goal), 0, 1));
        g.fill(barX, barY, barX + filled, barY + 5, done ? GREEN : 0xFF5090E0);
        g.drawString(font, e.progress + "/" + e.goal, barX + barW + 6, barY - 1, done ? GREEN : GRAY);
        String reward = QuestText.reward(e.reward).getString();
        if (!reward.isEmpty()) {
            g.drawString(font, QuestText.fit(font, reward, w - 12), x + 6, y + 37, e.claimed ? DARK : GOLD);
        }
        // The button on the right: claim, reroll, or what became of it.
        int bx = x + w - BUTTON_W - 6;
        int by = y + 6;
        if (e.claimed) {
            Component label = Component.translatable("screen.jadm.quests.claimed");
            g.drawString(font, label, bx + (BUTTON_W - font.width(label)) / 2, by + 4, GREEN);
        } else if (done) {
            button(g, bx, by, Component.translatable("screen.jadm.quests.claim"), 0xFFE0B030, 0xFFFFE070, 0xFF201800,
                    mouseX, mouseY, listTop, listBottom);
            hits.add(new Hit(Action.CLAIM, e.id, bx, by));
        } else if (!view.shared && view.rerollsLeft > 0) {
            button(g, bx, by, Component.translatable("screen.jadm.quests.reroll"), 0xFF3A3A50, 0xFF505070, WHITE,
                    mouseX, mouseY, listTop, listBottom);
            hits.add(new Hit(Action.REROLL, e.id, bx, by));
        }
    }

    private void button(GuiGraphics g, int x, int y, Component label, int color, int hover, int textColor,
                        int mouseX, int mouseY, int listTop, int listBottom) {
        boolean over = over(mouseX, mouseY, x, y) && mouseY >= listTop && mouseY < listBottom;
        g.fill(x, y, x + BUTTON_W, y + BUTTON_H, over ? hover : color);
        g.drawString(font, label, x + (BUTTON_W - font.width(label)) / 2, y + 4, textColor, false);
    }

    private static boolean over(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BUTTON_W && mouseY >= y && mouseY < y + BUTTON_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= top + 24 && mouseY < top + panelH - 30) {
            for (Hit hit : hits) {
                if (over(mouseX, mouseY, hit.x(), hit.y())) {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
                    send(hit.action(), hit.id());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 20;
        return true;
    }

    private static void send(Action action, String id) {
        PacketDistributor.sendToServer(new QuestActionPayload(action, id));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
