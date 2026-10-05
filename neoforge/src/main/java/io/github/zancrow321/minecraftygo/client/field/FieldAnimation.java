package io.github.zancrow321.minecraftygo.client.field;

import io.github.zancrow321.minecraftygo.engine.duel.FieldEvent;

/**
 * One queued or playing field effect. Events from a view play one after another, so a bot's whole turn doesn't
 * flash past at once.
 *
 * @param start the field tick it starts on
 */
public record FieldAnimation(FieldEvent event, long start, int duration) {
    /** How long each kind of event plays, and so how long the next one waits, in ticks. */
    public static int duration(FieldEvent.Kind kind) {
        return switch (kind) {
            case SUMMON -> 24;
            case ATTACK -> 22;
            case LEAVE -> 18;
            case ACTIVATE -> 26;
            case SET, POSITION -> 8;
            case DAMAGE, RECOVER -> 30;
        };
    }

    /** How long the queue waits after this event before the next starts (damage overlaps the next event). */
    public static int spacing(FieldEvent.Kind kind) {
        return switch (kind) {
            case DAMAGE, RECOVER -> 6;
            default -> duration(kind);
        };
    }

    public boolean started(long now) {
        return now >= start;
    }

    public boolean finished(long now) {
        return now >= start + duration;
    }

    /** 0 at the start, 1 at the end, clamped. */
    public float progress(long now, float partialTick) {
        return Math.max(0, Math.min(1, (now - start + partialTick) / duration));
    }
}
