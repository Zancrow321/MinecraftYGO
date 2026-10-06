package io.github.zancrow321.minecraftygo.client.duel;

import io.github.zancrow321.minecraftygo.client.ClientDuel;
import io.github.zancrow321.minecraftygo.client.field.ClientField;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.network.DuelResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The show around a duel: the start (the camera sweeps over the field, a big DUEL! and the coin toss), the opponent's
 * activations and summons shown big with a banner before their effect plays, and the result screen at the end.
 * Only touched on the client thread.
 */
public final class DuelStaging {
    /** How long the camera takes to sweep in over the field, from when the field starts to grow. */
    public static final int SWEEP_TICKS = 60;
    private static final int DUEL_AT = 50;
    private static final int DUEL_TICKS = 30;
    private static final int COIN_AT = 80;
    private static final int COIN_TICKS = 44;
    /** The whole start; the field's effects wait for it. */
    public static final int INTRO_TICKS = COIN_AT + COIN_TICKS;
    /** How long an opponent's card is shown before its effect plays. */
    public static final int BANNER_TICKS = 34;
    /** How long the field stays still after the last effect before the result screen opens. */
    private static final int RESULT_DELAY = 20;

    private static final int GOLD = 0xFFFFD040;
    private static final int RED = 0xFFFF5050;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFB0C4D8;

    private record Banner(int code, String text, long start) {
    }

    private static final List<Banner> banners = new ArrayList<>();
    private static DuelResultPayload result;
    private static long settledAt = -1;
    private static boolean closed;

    private DuelStaging() {
    }

    /** Forgets everything about the previous duel. */
    public static void reset() {
        banners.clear();
        result = null;
        settledAt = -1;
        closed = false;
    }

    /** What the server says this duel brought you; shown on the result screen. */
    public static void result(DuelResultPayload payload) {
        result = payload;
    }

    /** Shows the opponent's {@code code} big with {@code text} from field tick {@code start} on. */
    public static void banner(int code, String text, long start) {
        banners.add(new Banner(code, text, start));
    }

    private static float now() {
        return ClientField.tick() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static float introTime() {
        return now() - ClientField.revealAt();
    }

    /** Whether the start is still playing; nothing can be clicked until it's over. */
    public static boolean introRunning() {
        return ClientField.active() && !ClientField.watching() && introTime() < INTRO_TICKS;
    }

    /** How far the camera has swept in, 0 (high over the far end) to 1 (in place behind you), eased. */
    public static double sweep() {
        double t = Mth.clamp(introTime() / SWEEP_TICKS, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Whether the duel is over and its result screen is open (or about to), which keeps the field up. */
    public static boolean holdsField() {
        DuelView view = ClientDuel.view();
        return view != null && view.result() != null && !closed;
    }

    /** Whether the result screen is showing now. */
    public static boolean resultShowing() {
        if (!holdsField() || !ClientField.active()) {
            return false;
        }
        if (settledAt < 0 && ClientField.idle()) {
            settledAt = ClientField.tick();
        }
        return settledAt >= 0 && ClientField.tick() >= settledAt + RESULT_DELAY;
    }

    /** Closes the result screen: the field goes and duel mode ends. */
    public static void closeResult() {
        closed = true;
        ClientField.clear();
    }

    // ---- Drawing ----

    static void renderIntro(GuiGraphics g, Font font, int w, int h) {
        float t = introTime();
        if (t >= DUEL_AT && t < DUEL_AT + DUEL_TICKS) {
            float p = (t - DUEL_AT) / DUEL_TICKS;
            // Slams in big, settles, then fades.
            float scale = 4 + 6 * (float) Math.pow(Math.max(0, 1 - p * 5), 2);
            int alpha = (int) (255 * Mth.clamp((1 - p) * 4, 0, 1));
            big(g, font, "DUEL!", w / 2, h / 2 - 20, scale, (alpha << 24) | (GOLD & 0xFFFFFF));
        }
        if (t >= COIN_AT && t < INTRO_TICKS) {
            float p = (t - COIN_AT) / COIN_TICKS;
            int r = 14;
            int cx = w / 2;
            int cy = h / 2 - 20;
            float spin = Math.min(1, p / 0.55f);
            // Up and back down while it spins, then it lies still.
            int lift = (int) (Math.sin(spin * Math.PI) * 30);
            double face = Math.abs(Math.cos(spin * 7 * Math.PI));
            coin(g, cx, cy - lift, r, Math.max(0.08, face));
            if (spin >= 1) {
                DuelView view = ClientDuel.view();
                String who = view == null ? "" : view.you() == 0 ? "You go first!"
                        : view.names().get(1 - view.you()) + " goes first!";
                big(g, font, who, cx, cy + r + 8, 1.5f, TEXT);
            }
        }
    }

    private static void coin(GuiGraphics g, int cx, int cy, int r, double squash) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(r * r - dy * dy) * squash);
            g.fill(cx - half - 1, cy + dy, cx + half + 1, cy + dy + 1, 0xFF8A6A10);
            if (half > 1 && Math.abs(dy) < r - 1) {
                g.fill(cx - half + 1, cy + dy, cx + half - 1, cy + dy + 1, 0xFFF0C030);
            }
        }
    }

    private static void big(GuiGraphics g, Font font, String text, int cx, int cy, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, -4, color);
        g.pose().popPose();
    }

    /** The opponent's card that is about to take effect, big in the middle with a line saying what it does. */
    static void renderBanners(GuiGraphics g, Font font, int w, int h) {
        float now = now();
        banners.removeIf(b -> now >= b.start() + BANNER_TICKS);
        for (Banner b : banners) {
            float t = now - b.start();
            if (t < 0) {
                continue;
            }
            float in = Mth.clamp(t / 5, 0, 1);
            float out = Mth.clamp((BANNER_TICKS - t) / 5, 0, 1);
            int cw = (int) (64 * (0.7f + 0.3f * in));
            int ch = cw * 87 / 60;
            int strip = 18;
            int y = h / 2 + 12;
            int alpha = (int) (0xD0 * out);
            g.fill(0, y, w, y + strip, alpha << 24 | 0x101828);
            g.fill(0, y, w, y + 1, (int) (255 * out) << 24 | 0xFFD040);
            g.fill(0, y + strip - 1, w, y + strip, (int) (255 * out) << 24 | 0xFFD040);
            if (out > 0.3f) {
                DuelUi.card(g, b.code(), w / 2 - cw / 2, y - ch - 4, cw, ch);
            }
            int textAlpha = Math.max(8, (int) (255 * out));
            g.drawCenteredString(font, b.text(), w / 2, y + 5, textAlpha << 24 | 0xFFFFFF);
        }
    }

    /** Victory or defeat, how it ended, what it brought, and a button back to the game. */
    static void renderResult(GuiGraphics g, Font font, int mx, int my, int w, int h) {
        DuelView view = ClientDuel.view();
        int outcome = result != null ? result.outcome() : DuelResultPayload.DRAW;
        String title = result == null ? "DUEL OVER" : switch (outcome) {
            case DuelResultPayload.WON -> "VICTORY";
            case DuelResultPayload.LOST -> "DEFEAT";
            default -> "DRAW";
        };
        int color = result == null ? TEXT : outcome == DuelResultPayload.WON ? GOLD
                : outcome == DuelResultPayload.LOST ? RED : DIM;
        int width = Math.min(240, w - 40);
        List<FormattedCharSequence> reason = font.split(Component.literal(view.result()), width - 12);
        List<String> rewards = result == null ? List.of() : result.rewards();
        boolean watched = result == null;
        int height = 44 + reason.size() * 10 + (watched ? 0 : 14 + Math.max(1, rewards.size()) * 10) + 24;
        int x = w / 2 - width / 2;
        int y = Math.max(4, h / 2 - height / 2);
        DuelUi.panel(g, x, y, width, height);
        big(g, font, title, w / 2, y + 18, 3, color);
        int ty = y + 38;
        for (FormattedCharSequence line : reason) {
            g.drawCenteredString(font, line, w / 2, ty, TEXT);
            ty += 10;
        }
        ty += 4;
        if (!watched) {
            g.drawString(font, "Rewards", x + 8, ty, GOLD);
            ty += 10;
        }
        if (rewards.isEmpty() && !watched) {
            g.drawString(font, "Nothing this time", x + 8, ty, DIM);
            ty += 10;
        }
        for (String reward : rewards) {
            g.drawString(font, font.plainSubstrByWidth(reward, width - 16), x + 8, ty, TEXT);
            ty += 10;
        }
        DuelUi.button(g, font, w / 2 - 40, ty + 6, 80, "Continue", true, mx, my, DuelStaging::closeResult);
    }
}
