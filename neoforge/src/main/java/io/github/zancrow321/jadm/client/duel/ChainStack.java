package io.github.zancrow321.jadm.client.duel;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.JadmClientConfig;
import io.github.zancrow321.jadm.client.field.ClientField;
import io.github.zancrow321.jadm.engine.duel.DuelView;
import io.github.zancrow321.jadm.engine.duel.FieldEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The chain as it builds and resolves, like Master Duel: once a second card is chained, every link shows as a numbered
 * card in a column beside the phase buttons, newest on top. Then the links resolve one by one from the top: the one
 * resolving glows, a resolved one fades out, a negated one is crossed out in red. The steps play in time with the
 * field's animation queue, and resolving waits a moment per link so you can follow it (only for chains of two or more,
 * so single activations play as fast as before). Client setting {@code chainDisplay}.
 */
public final class ChainStack {
    /** How long each link of a chain of two or more holds when it starts to resolve, in ticks. */
    private static final int SOLVE_PAUSE = 12;
    /** How long a negated link holds, so the red cross can be read. */
    private static final int NEGATE_PAUSE = 14;
    /** How long a resolved link takes to fade out, and the column after the chain is done. */
    private static final int FADE = 10;
    private static final int CARD_WIDTH = 30;
    private static final int CARD_HEIGHT = 44;
    private static final int GOLD = 0xFFFFD040;
    private static final int MINE = 0xFF4090FF;
    private static final int THEIRS = 0xFFFF6060;

    private enum State { WAITING, SOLVING, SOLVED, NEGATED }

    private static final class Link {
        final int number;
        final int code;
        final int player;
        State state = State.WAITING;
        long since;

        Link(int number, int code, int player, long since) {
            this.number = number;
            this.code = code;
            this.player = player;
            this.since = since;
        }
    }

    /** A chain event waiting for its turn in the animation queue. */
    private record Step(long at, FieldEvent event) {
    }

    private static final List<Step> steps = new ArrayList<>();
    private static final List<Link> links = new ArrayList<>();
    /** When the chain was done (field tick), or -1 while it builds or resolves. */
    private static long endedAt = -1;
    /** Whether the chain on show has had two links, which is when the column appears. */
    private static boolean shown;
    /** The length of the chain as its events are queued (ahead of what is on show). */
    private static int queuedLinks;

    private ChainStack() {
    }

    /** A new duel, or the field went away. */
    public static void reset() {
        steps.clear();
        links.clear();
        endedAt = -1;
        shown = false;
        queuedLinks = 0;
    }

    private static boolean enabled() {
        return JadmClientConfig.CHAIN_DISPLAY.get();
    }

    private static boolean chainKind(FieldEvent e) {
        return switch (e.kind()) {
            case ACTIVATE, CHAIN_SOLVING, CHAIN_SOLVED, CHAIN_NEGATED, CHAIN_END -> true;
            default -> false;
        };
    }

    /**
     * {@code event} was queued to play at field tick {@code at}: remembers chain events for then.
     *
     * @param spacing how long the queue would wait after it anyway
     * @return how long the queue waits after it, with the pause a resolving link of a long chain gets
     */
    public static int queue(FieldEvent event, long at, int spacing) {
        if (!chainKind(event)) {
            return spacing;
        }
        steps.add(new Step(at, event));
        boolean pauses = enabled() && queuedLinks >= 2;
        return switch (event.kind()) {
            case ACTIVATE -> {
                if (event.amount() > 0) {
                    queuedLinks = event.amount();
                }
                yield spacing;
            }
            case CHAIN_SOLVING -> pauses ? SOLVE_PAUSE : spacing;
            case CHAIN_NEGATED -> pauses ? NEGATE_PAUSE : spacing;
            case CHAIN_END -> {
                queuedLinks = 0;
                yield spacing;
            }
            default -> spacing;
        };
    }

    /** Plays the chain events whose time has come; called every client tick. */
    public static void tick(long now) {
        while (!steps.isEmpty() && steps.getFirst().at() <= now) {
            apply(steps.removeFirst().event(), now);
        }
        links.removeIf(l -> l.state == State.SOLVED && now - l.since > FADE);
        if (endedAt >= 0 && now - endedAt > FADE) {
            links.clear();
            shown = false;
            endedAt = -1;
        }
    }

    private static void apply(FieldEvent e, long now) {
        int n = e.amount();
        switch (e.kind()) {
            case ACTIVATE -> {
                if (n <= 0) {
                    return;
                }
                if (n == 1 || endedAt >= 0) {
                    links.clear();
                    shown = false;
                    endedAt = -1;
                }
                links.removeIf(l -> l.number >= n);
                links.add(new Link(n, e.code(), e.player(), now));
                shown |= n >= 2;
            }
            case CHAIN_SOLVING -> {
                // The core tells a link is done only after the next one has started: the later links are done.
                for (Link l : links) {
                    if (l.number > n) {
                        solved(l, now);
                    }
                }
                set(n, State.SOLVING, now);
            }
            case CHAIN_SOLVED -> {
                Link link = link(n);
                if (link != null) {
                    solved(link, now);
                }
            }
            case CHAIN_NEGATED -> set(n, State.NEGATED, now);
            case CHAIN_END -> endedAt = now;
            default -> {
            }
        }
    }

    private static Link link(int number) {
        for (Link l : links) {
            if (l.number == number) {
                return l;
            }
        }
        return null;
    }

    /** A link that resolved starts to fade, unless it already is or was negated (its cross stays until the end). */
    private static void solved(Link link, long now) {
        if (link.state == State.WAITING || link.state == State.SOLVING) {
            link.state = State.SOLVED;
            link.since = now;
        }
    }

    private static void set(int number, State state, long now) {
        Link link = link(number);
        if (link != null) {
            link.state = state;
            link.since = now;
        }
    }

    /**
     * Draws the column with its right edge at {@code right}.
     *
     * @return the card under the mouse, for the card panel, or 0
     */
    static int render(GuiGraphics g, Font font, DuelView view, int right, int mx, int my, int h) {
        if (!enabled() || !shown || links.isEmpty()) {
            return 0;
        }
        float now = ClientField.tick() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float out = endedAt < 0 ? 1 : Math.max(0, 1 - (now - endedAt) / FADE);
        if (links.stream().allMatch(l -> l.state == State.SOLVED && now - l.since >= FADE)) {
            return 0; // everything has resolved and faded: no title on its own
        }
        int count = links.size();
        int room = h - 120;
        int step = Math.max(12, Math.min(CARD_HEIGHT + 6, (room - CARD_HEIGHT) / Math.max(1, count - 1)));
        int height = CARD_HEIGHT + (count - 1) * step;
        int x = right - CARD_WIDTH;
        int top = h / 2 - height / 2;
        int titleAlpha = Math.max(8, (int) (255 * out));
        g.drawCenteredString(font, "CHAIN", x + CARD_WIDTH / 2, top - 12, titleAlpha << 24 | (GOLD & 0xFFFFFF));
        int hovered = 0;
        // Link 1 at the bottom, the newest on top, drawn bottom first so the newer ones overlap the older.
        for (int i = 0; i < count; i++) {
            Link link = links.get(i);
            int y = top + (count - 1 - i) * step;
            float age = now - link.since;
            int dx = link.state == State.SOLVING ? -6 : 0;
            float fade = out * (link.state == State.SOLVED ? Math.max(0, 1 - age / FADE) : 1);
            if (fade <= 0.02f) {
                continue;
            }
            int edge = edge(link, view, fade);
            if (link.state == State.SOLVING) {
                // A pulsing glow around the link that is resolving.
                int glow = blend(GOLD, 0.25f + 0.25f * (float) Math.sin(age * 0.5f), fade);
                g.fill(x + dx - 4, y - 4, x + dx + CARD_WIDTH + 4, y + CARD_HEIGHT + 4, glow);
            }
            g.fill(x + dx - 2, y - 2, x + dx + CARD_WIDTH + 2, y + CARD_HEIGHT + 2, edge);
            DuelUi.card(g, link.code, x + dx, y, CARD_WIDTH, CARD_HEIGHT);
            if (fade < 1) {
                g.fill(x + dx, y, x + dx + CARD_WIDTH, y + CARD_HEIGHT, (int) (0xE0 * (1 - fade)) << 24 | 0x101828);
            }
            if (link.state == State.NEGATED) {
                g.fill(x + dx, y, x + dx + CARD_WIDTH, y + CARD_HEIGHT, (int) (0x90 * fade) << 24 | 0x600000);
                for (int t = 0; t < CARD_WIDTH; t++) {
                    int ly = y + t * CARD_HEIGHT / CARD_WIDTH;
                    g.fill(x + dx + t, ly, x + dx + t + 2, ly + 2, blend(THEIRS, 1, fade));
                    g.fill(x + dx + CARD_WIDTH - t - 2, ly, x + dx + CARD_WIDTH - t, ly + 2, blend(THEIRS, 1, fade));
                }
            }
            boolean top_ = i == count - 1;
            int visible = top_ ? CARD_HEIGHT : step;
            if (mx >= x + dx && mx < x + dx + CARD_WIDTH && my >= y && my < y + visible) {
                hovered = link.code;
            }
        }
        // The links' numbers on top of all the cards, so a newer card never hides an older one's number.
        for (int i = 0; i < count; i++) {
            Link link = links.get(i);
            float fade = out * (link.state == State.SOLVED ? Math.max(0, 1 - (now - link.since) / FADE) : 1);
            if (fade <= 0.02f) {
                continue;
            }
            int bx = x + (link.state == State.SOLVING ? -6 : 0) - 8;
            int y = top + (count - 1 - i) * step + 2;
            String number = String.valueOf(link.number);
            int bw = Math.max(12, font.width(number) + 5);
            g.fill(bx - 1, y - 1, bx + bw + 1, y + 12, edge(link, view, fade));
            g.fill(bx, y, bx + bw, y + 11, blend(0xFF101828, 1, fade));
            g.drawCenteredString(font, number, bx + bw / 2, y + 2,
                    blend(link.state == State.SOLVING ? GOLD : 0xFFFFFFFF, 1, fade));
        }
        Link solving = links.stream().filter(l -> l.state == State.SOLVING || l.state == State.NEGATED)
                .reduce((a, b) -> b).orElse(null);
        if (solving != null && solving.code != 0 && out > 0.5f) {
            String what = (solving.state == State.NEGATED ? "Negated: " : "Resolving: ")
                    + JadmData.text().cardName(solving.code);
            int tw = font.width(what);
            g.drawString(font, what, x + CARD_WIDTH - tw, top + height + 6,
                    solving.state == State.NEGATED ? THEIRS : GOLD);
        }
        return hovered;
    }

    /** The frame of a link: gold while it resolves, else blue for yours and red for your opponent's. */
    private static int edge(Link link, DuelView view, float fade) {
        return blend(link.state == State.SOLVING ? GOLD : link.player == view.you() ? MINE : THEIRS, 1, fade);
    }

    /** {@code color} with its alpha scaled by {@code a * fade}. */
    private static int blend(int color, float a, float fade) {
        int alpha = (int) (((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, a * fade)));
        return Math.max(4, alpha) << 24 | (color & 0xFFFFFF);
    }
}
