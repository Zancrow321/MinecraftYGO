package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage;
import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import io.github.zancrow321.minecraftygo.engine.protocol.MessageType;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * Something that happened on the field that a client animates: a summon, an activation, an attack, a card leaving
 * the field, or a life point change. Already censored for one viewer.
 *
 * @param code   the card involved, or 0 when the viewer may not know it
 * @param from   where it happened (the attacker for an attack)
 * @param to     the attack target or the card's destination, else {@link Loc#NONE}
 * @param amount life points for {@link Kind#DAMAGE} and {@link Kind#RECOVER}, else 0
 */
public record FieldEvent(Kind kind, int code, Loc from, Loc to, int player, int amount) {
    public enum Kind { SUMMON, SET, ACTIVATE, ATTACK, LEAVE, POSITION, DAMAGE, RECOVER }

    /** @return the event {@code message} shows to {@code viewer}, or {@code null} if it isn't one to animate */
    public static FieldEvent of(DuelMessage message, int viewer) {
        return switch (message) {
            case DuelMessage.Summoning m -> new FieldEvent(Kind.SUMMON, m.code(), m.loc(), Loc.NONE,
                    m.loc().controller(), 0);
            case DuelMessage.Set m -> new FieldEvent(Kind.SET, m.loc().controller() == viewer ? m.code() : 0, m.loc(),
                    Loc.NONE, m.loc().controller(), 0);
            case DuelMessage.Chaining m -> new FieldEvent(Kind.ACTIVATE, m.code(), m.loc(), Loc.NONE,
                    m.loc().controller(), 0);
            case DuelMessage.Attack m -> new FieldEvent(Kind.ATTACK, 0, m.attacker(), m.target(),
                    m.attacker().controller(), 0);
            case DuelMessage.PositionChange m when onField(m.loc()) -> new FieldEvent(Kind.POSITION,
                    m.loc().isFaceUp() || m.loc().controller() == viewer ? m.code() : 0, m.loc(), Loc.NONE,
                    m.loc().controller(), 0);
            case DuelMessage.Move m when onField(m.from()) && !onField(m.to()) -> new FieldEvent(Kind.LEAVE,
                    public_(m.to()) || m.from().isFaceUp() || m.from().controller() == viewer ? m.code() : 0,
                    m.from(), m.to(), m.from().controller(), 0);
            case DuelMessage.LifePoints m when m.type() == MessageType.DAMAGE || m.type() == MessageType.PAY_LPCOST ->
                    new FieldEvent(Kind.DAMAGE, 0, Loc.NONE, Loc.NONE, m.player(), m.amount());
            case DuelMessage.LifePoints m when m.type() == MessageType.RECOVER ->
                    new FieldEvent(Kind.RECOVER, 0, Loc.NONE, Loc.NONE, m.player(), m.amount());
            default -> null;
        };
    }

    private static boolean onField(Loc loc) {
        return !loc.isOverlay() && (loc.location() & LOCATION_ONFIELD) != 0;
    }

    /** The graveyard is public, and so is a face-up banished card. */
    private static boolean public_(Loc loc) {
        return loc.location() == LOCATION_GRAVE || (loc.location() == LOCATION_REMOVED && loc.isFaceUp());
    }
}
