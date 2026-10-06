package io.github.zancrow321.minecraftygo.client.duel;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.client.field.ClientField;
import io.github.zancrow321.minecraftygo.client.field.FieldAnimation;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.engine.duel.FieldEvent;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Makes the course of a duel easy to follow: life points count down or up instead of jumping, their panel flashes
 * red or green, a banner and a chime mark each new turn, a smaller banner each Battle, Main 2 and End Phase, and a
 * ping says when the duel waits for your response. Only touched on the client thread.
 */
public final class DuelFeedback {
    private static final long TURN_BANNER_MS = 1600;
    private static final long PHASE_BANNER_MS = 1000;
    private static final long FLASH_MS = 700;

    /** Life points as shown, per player, counting towards the real value; -1 before the first view. */
    private static final double[] shownLp = {-1, -1};
    private static final long[] flashAt = {0, 0};
    private static final boolean[] flashDamage = {false, false};
    /** Turn, phase and life point events already acted on, so each banner and flash comes once. */
    private static final Set<FieldAnimation> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private static long lastFrame;
    private static String banner;
    private static boolean bannerBig;
    private static boolean bannerMine;
    private static long bannerAt;

    private DuelFeedback() {
    }

    /** Forgets everything about the previous duel. */
    public static void reset() {
        shownLp[0] = shownLp[1] = -1;
        flashAt[0] = flashAt[1] = 0;
        seen.clear();
        banner = null;
    }

    /**
     * Called each frame with the current view, before anything is drawn. Follows the field's queue of effects, so a
     * banner or a change of life points comes when its moment plays, not when the server already got there.
     */
    public static void update(DuelView view) {
        long now = Util.getMillis();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        long tick = ClientField.tick();
        List<FieldAnimation> animations = ClientField.animations();
        seen.retainAll(animations);
        // Life point changes still waiting in the queue haven't happened yet as far as the screen goes.
        int[] pending = new int[2];
        for (FieldAnimation a : animations) {
            FieldEvent e = a.event();
            if (e.player() < 0 || e.player() > 1) {
                continue;
            }
            if (!a.started(tick)) {
                if (e.kind() == FieldEvent.Kind.DAMAGE) {
                    pending[e.player()] += e.amount();
                } else if (e.kind() == FieldEvent.Kind.RECOVER) {
                    pending[e.player()] -= e.amount();
                }
                continue;
            }
            if (!seen.add(a)) {
                continue;
            }
            boolean mine = e.player() == view.you();
            switch (e.kind()) {
                case TURN -> {
                    show(mine ? "YOUR TURN" : view.names().get(e.player()).toUpperCase(Locale.ROOT) + "'S TURN",
                            true, mine);
                    sound(mine ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
                            mine ? 1.2f : 0.8f, 0.9f);
                }
                case PHASE -> {
                    // Battle Phase start, Main Phase 2, End Phase; not the Draw, Standby or each battle step.
                    if ((e.amount() & (0x08 | 0x100 | 0x200)) != 0) {
                        show(YgoData.text().phase(e.amount()), false,
                                view.board().turnPlayer() == view.you());
                        sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.4f);
                    }
                }
                case DAMAGE, RECOVER -> {
                    flashAt[e.player()] = now;
                    flashDamage[e.player()] = e.kind() == FieldEvent.Kind.DAMAGE;
                }
                default -> {
                }
            }
        }
        for (int p = 0; p < 2; p++) {
            int lp = view.board().side(p).lifePoints() + pending[p];
            if (shownLp[p] < 0) {
                shownLp[p] = lp;
                continue;
            }
            double diff = lp - shownLp[p];
            if (Math.abs(diff) < 0.5) {
                shownLp[p] = lp;
                continue;
            }
            // About a second for any change, never slower than 200 points a second near the end.
            double step = Math.max(Math.abs(diff) * Math.min(1, dt * 4), 200 * dt);
            shownLp[p] = diff > 0 ? Math.min(lp, shownLp[p] + step) : Math.max(lp, shownLp[p] - step);
        }
    }

    /** A chance to respond has opened for you. */
    static void responseOpened() {
        sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.6f);
    }

    /** The life points to show for {@code player} now. */
    public static int lifePoints(int player, int real) {
        return shownLp[player] < 0 ? real : (int) Math.round(shownLp[player]);
    }

    /** The colour the life panel of {@code player} flashes now (alpha 0 when it doesn't). */
    public static int flash(int player) {
        long t = Util.getMillis() - flashAt[player];
        if (flashAt[player] == 0 || t > FLASH_MS) {
            return 0;
        }
        int alpha = (int) (0x90 * (1 - (float) t / FLASH_MS));
        return alpha << 24 | (flashDamage[player] ? 0xFF3030 : 0x30FF70);
    }

    private static void show(String text, boolean big, boolean mine) {
        banner = text;
        bannerBig = big;
        bannerMine = mine;
        bannerAt = Util.getMillis();
    }

    private static void sound(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** The turn or phase banner, across the middle of the screen, sliding in and fading out. */
    static void render(GuiGraphics g, Font font, int w, int h) {
        if (banner == null) {
            return;
        }
        long length = bannerBig ? TURN_BANNER_MS : PHASE_BANNER_MS;
        long t = Util.getMillis() - bannerAt;
        if (t > length) {
            banner = null;
            return;
        }
        float in = Mth.clamp(t / 200f, 0, 1);
        float out = Mth.clamp((length - t) / 300f, 0, 1);
        int color = bannerMine ? 0x4090FF : 0xFF6060;
        int strip = bannerBig ? 30 : 18;
        int y = h / 2 - 40 - strip / 2;
        int half = (int) (w / 2f * (0.3f + 0.7f * in));
        g.fill(w / 2 - half, y, w / 2 + half, y + strip, (int) (0xC0 * out) << 24 | 0x081828);
        g.fill(w / 2 - half, y, w / 2 + half, y + 1, (int) (255 * out) << 24 | color);
        g.fill(w / 2 - half, y + strip - 1, w / 2 + half, y + strip, (int) (255 * out) << 24 | color);
        int alpha = Math.max(8, (int) (255 * out * in));
        float scale = bannerBig ? 2f : 1f;
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + strip / 2f, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, banner, -font.width(banner) / 2, -4, alpha << 24 | 0xFFFFFF);
        g.pose().popPose();
    }
}
