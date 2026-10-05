package io.github.zancrow321.minecraftygo.engine.text;

import io.github.zancrow321.minecraftygo.engine.protocol.CardRef;
import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage;
import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage.*;
import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import io.github.zancrow321.minecraftygo.engine.protocol.MessageType;

import java.util.List;
import java.util.function.Function;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * Writes one line of duel commentary per message, from one viewer's point of view. Never names a card that viewer
 * isn't allowed to see.
 */
public final class DuelLog {
    private final DuelText text;
    private final List<String> names;
    private final int viewer;
    private final Function<Loc, Integer> codeAt;

    /**
     * @param names  display names of player 0 and player 1
     * @param viewer whose log this is (0 or 1), or -1 for a spectator
     * @param codeAt looks up the card currently at a face-up location, for messages that only carry locations
     */
    public DuelLog(DuelText text, List<String> names, int viewer, Function<Loc, Integer> codeAt) {
        this.text = text;
        this.names = names;
        this.viewer = viewer;
        this.codeAt = codeAt;
    }

    /** @return the commentary line, or {@code null} if the message isn't worth a line */
    public String describe(DuelMessage message) {
        return switch (message) {
            case NewTurn m -> "--- " + possessive(m.player()) + " turn ---";
            case NewPhase m -> text.phase(m.phase());
            case Draw m -> m.player() == viewer
                    ? "You drew " + names(m.codes())
                    : who(m.player()) + " drew " + plural(m.codes().size(), "card");
            case Summoning m -> who(m.loc().controller()) + " " + switch (m.type()) {
                case MessageType.SUMMONING -> "Normal Summoned ";
                case MessageType.FLIPSUMMONING -> "Flip Summoned ";
                default -> "Special Summoned ";
            } + text.cardName(m.code());
            case DuelMessage.Set m -> who(m.loc().controller()) + " Set a card in the " + text.location(m.loc().location());
            case Chaining m -> who(m.loc().controller()) + " activated " + visibleName(m.code(), m.loc())
                    + " (chain link " + m.chainLink() + ")";
            case ChainEvent m when m.type() == MessageType.CHAIN_NEGATED -> "Chain link " + m.chainLink() + " was negated";
            case Move m -> move(m);
            // Spells and Traps "change position" when flipped to activate; the activation line covers that.
            case PositionChange m when m.loc().location() == LOCATION_SZONE -> null;
            case PositionChange m -> visibleName(m.code(), m.loc()) + " changed to "
                    + ((m.loc().position() & POS_ATTACK) != 0 ? "Attack" : "Defense") + " Position";
            case Attack m -> m.target().isNone()
                    ? nameAt(m.attacker()) + " attacks directly"
                    : nameAt(m.attacker()) + " attacks " + nameAt(m.target());
            case LifePoints m when m.type() == MessageType.DAMAGE -> who(m.player()) + " took " + m.amount() + " damage";
            case LifePoints m when m.type() == MessageType.RECOVER -> who(m.player()) + " gained " + m.amount() + " LP";
            case LifePoints m when m.type() == MessageType.PAY_LPCOST -> who(m.player()) + " paid " + m.amount() + " LP";
            case ConfirmCards m when m.player() == viewer -> "Revealed to you: " + names(m.cards().stream()
                    .map(CardRef::code).toList());
            case ShuffleDeck m -> who(m.player()) + " shuffled " + (m.player() == viewer ? "your" : "their") + " Deck";
            case Toss m -> who(m.player()) + (m.type() == MessageType.TOSS_COIN ? " tossed a coin: " : " rolled: ")
                    + m.results().stream().map(r -> m.type() == MessageType.TOSS_COIN ? (r == 1 ? "heads" : "tails")
                    : String.valueOf(r)).toList();
            case Win m -> m.player() == 2 ? "The duel is a draw" : (m.player() == viewer ? "You win the duel!"
                    : who(m.player()) + " wins the duel");
            default -> null;
        };
    }

    private String move(Move m) {
        Loc to = m.to();
        Loc from = m.from();
        String name = visibleName(m.code(), to.isNone() ? from : to);
        int dest = to.location();
        if (from.location() == LOCATION_DECK && dest == LOCATION_HAND) {
            return to.controller() == viewer ? "You added " + text.cardName(m.code()) + " to your hand"
                    : who(to.controller()) + " added a card to their hand";
        }
        if (dest == LOCATION_GRAVE) {
            boolean destroyed = (m.reason() & 0x1) != 0;
            return name + (destroyed ? " was destroyed" : " was sent to the GY");
        }
        if (dest == LOCATION_REMOVED) {
            return name + " was banished";
        }
        if (dest == LOCATION_HAND && from.location() != LOCATION_DECK && from.location() != 0) {
            return name + " returned to the hand";
        }
        if (dest == LOCATION_DECK && from.location() != LOCATION_DECK) {
            return name + " returned to the Deck";
        }
        return null;
    }

    /** The card's name if this viewer may see it at {@code loc}, else "a card". */
    private String visibleName(int code, Loc loc) {
        boolean publicPlace = loc.location() == LOCATION_GRAVE
                || ((loc.location() & LOCATION_ONFIELD) != 0 && loc.isFaceUp())
                || (loc.location() == LOCATION_REMOVED && loc.isFaceUp());
        boolean own = loc.controller() == viewer && loc.location() != LOCATION_DECK;
        return code != 0 && (publicPlace || own) ? text.cardName(code) : "a card";
    }

    private String nameAt(Loc loc) {
        Integer code = codeAt.apply(loc.place());
        return code == null || code == 0 ? "a face-down monster" : text.cardName(code);
    }

    private String who(int player) {
        return player == viewer ? "You" : names.get(player);
    }

    private String possessive(int player) {
        return player == viewer ? "Your" : names.get(player) + "'s";
    }

    private String names(List<Integer> codes) {
        return String.join(", ", codes.stream().map(text::cardName).toList());
    }

    private static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
