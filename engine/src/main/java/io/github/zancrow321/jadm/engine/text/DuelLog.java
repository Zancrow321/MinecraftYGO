package io.github.zancrow321.jadm.engine.text;

import io.github.zancrow321.jadm.engine.duel.SummonStyle;
import io.github.zancrow321.jadm.engine.protocol.CardRef;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.*;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import io.github.zancrow321.jadm.engine.protocol.MessageType;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * Writes one line of duel commentary per message, from one viewer's point of view. Never names a card that viewer
 * isn't allowed to see.
 */
public final class DuelLog {
    private final DuelText text;
    private final List<String> names;
    private final int viewer;
    private final Function<Loc, Integer> codeAt;
    private final Predicate<CardRef> shown;

    /**
     * @param names  display names of player 0 and player 1
     * @param viewer whose log this is (0 or 1), or -1 for a spectator
     * @param codeAt looks up the card currently at a face-up location, for messages that only carry locations
     * @param shown  whether this viewer was shown that card earlier this turn, so a card revealed from the Deck is
     *               named again when it is added to the hand
     */
    public DuelLog(DuelText text, List<String> names, int viewer, Function<Loc, Integer> codeAt,
                   Predicate<CardRef> shown) {
        this.text = text;
        this.names = names;
        this.viewer = viewer;
        this.codeAt = codeAt;
        this.shown = shown;
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
                default -> switch (m.style()) {
                    case SummonStyle.FUSION -> "Fusion Summoned ";
                    case SummonStyle.SYNCHRO -> "Synchro Summoned ";
                    case SummonStyle.XYZ -> "Xyz Summoned ";
                    case SummonStyle.RITUAL -> "Ritual Summoned ";
                    case SummonStyle.LINK -> "Link Summoned ";
                    case SummonStyle.PENDULUM, SummonStyle.PENDULUM_MORE -> "Pendulum Summoned ";
                    default -> "Special Summoned ";
                };
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
            case ConfirmCards m -> confirm(m);
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
            if (to.controller() == viewer) {
                return "You added " + text.cardName(m.code()) + " to your hand";
            }
            boolean seen = m.code() != 0 && shown.test(new CardRef(m.code(), from));
            return who(to.controller()) + " added " + (seen ? text.cardName(m.code()) : "a card") + " to their hand";
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

    /**
     * A reveal. The top of a Deck is shown to everyone. Other cards are shown to the player named, and their owner
     * knows them anyway; anyone else only learns how many.
     */
    private String confirm(ConfirmCards m) {
        List<CardRef> cards = m.cards();
        if (cards.isEmpty()) {
            return null;
        }
        List<Integer> codes = cards.stream().map(CardRef::code).toList();
        if (m.type() != MessageType.CONFIRM_CARDS) {
            String pile = m.type() == MessageType.CONFIRM_DECKTOP ? "Deck" : "Extra Deck";
            return who(m.player()) + " revealed the top " + (cards.size() == 1 ? "card" : cards.size() + " cards")
                    + " of " + (m.player() == viewer ? "your " : "their ") + pile + ": " + names(codes);
        }
        int owner = cards.getFirst().loc().controller();
        boolean sameOwner = cards.stream().allMatch(c -> c.loc().controller() == owner);
        int location = cards.getFirst().loc().location() & 0x7F;
        boolean sameLocation = cards.stream().allMatch(c -> (c.loc().location() & 0x7F) == location);
        boolean visible = codes.stream().noneMatch(code -> code == 0) && (viewer == m.player()
                || (viewer >= 0 && cards.stream().allMatch(c -> c.loc().controller() == viewer)));
        String what = visible ? names(codes) : plural(cards.size(), "card");
        if (sameOwner && sameLocation && (location == LOCATION_HAND || location == LOCATION_DECK
                || location == LOCATION_EXTRA)) {
            what += " from " + (owner == viewer ? "your " : "their ") + text.location(location);
        }
        if (!sameOwner) {
            return (viewer == m.player() ? "You were shown " : "Revealed to " + who(m.player()) + ": ") + what;
        }
        if (owner == m.player()) {
            // A player looking at their own hidden cards.
            return who(owner) + " looked at " + what;
        }
        String to = m.player() == viewer ? "you" : names.get(m.player());
        return who(owner) + " revealed " + what + " to " + to;
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
