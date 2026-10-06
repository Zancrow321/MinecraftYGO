package io.github.zancrow321.minecraftygo.client.field;

import io.github.zancrow321.minecraftygo.engine.duel.FieldEvent;

/**
 * One queued or playing field effect. Events from a view play one after another, so a bot's whole turn doesn't
 * flash past at once.
 *
 * @param start the field tick it starts on
 * @param actor the attacking monster's card for an attack (attacks carry no code of their own), else the event's
 */
public record FieldAnimation(FieldEvent event, long start, int duration, int actor) {
    /** How long {@code event} plays: some spells with a signature effect take longer than the rest. */
    public static int duration(FieldEvent event) {
        return event.kind() == FieldEvent.Kind.ACTIVATE ? SignatureEffects.activateDuration(event.code())
                : duration(event.kind());
    }

    /** How long the queue waits after {@code event} before the next one starts. */
    public static int spacing(FieldEvent event) {
        return event.kind() == FieldEvent.Kind.ACTIVATE ? duration(event) : spacing(event.kind());
    }

    /** How long each kind of event plays, and so how long the next one waits, in ticks. */
    public static int duration(FieldEvent.Kind kind) {
        return switch (kind) {
            case SUMMON -> 24;
            case ATTACK -> 28;
            case LEAVE -> 18;
            case ACTIVATE -> 26;
            case SET, POSITION -> 8;
            case DAMAGE, RECOVER -> 30;
            case TURN -> 32;
            case PHASE -> 20;
        };
    }

    /** How long the queue waits after this event before the next starts (damage overlaps the next event). */
    public static int spacing(FieldEvent.Kind kind) {
        return switch (kind) {
            case DAMAGE, RECOVER -> 6;
            // The turn banner gets a moment of its own; a phase banner doesn't hold anything up.
            case TURN -> 16;
            case PHASE -> 0;
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
