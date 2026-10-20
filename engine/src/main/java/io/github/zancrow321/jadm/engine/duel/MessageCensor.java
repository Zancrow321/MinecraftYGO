package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.protocol.CardRef;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.*;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import io.github.zancrow321.jadm.engine.protocol.MessageType;

import java.util.ArrayList;
import java.util.List;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * The core's messages name every card, even ones a player can't see (the card an opponent draws or sets). This
 * blanks those codes for one team, the way EDOPro's server does before it sends a message to a client, so a bot
 * that reads the messages knows no more than a person in its seat.
 */
public final class MessageCensor {
    private MessageCensor() {
    }

    /** @return the message as {@code team} would get it, or {@code null} if they don't get it at all */
    public static DuelMessage forTeam(DuelMessage message, int team) {
        return switch (message) {
            case Prompt p -> null;
            case Draw d -> d.player() == team ? d : new Draw(d.player(), drawCodes(d), d.positions());
            case Move m -> m.to().controller() == team || moveVisible(m.to())
                    ? m : new Move(0, m.from(), m.to(), m.reason());
            case DuelMessage.Set s -> s.loc().controller() == team ? s : new DuelMessage.Set(0, s.loc());
            case ShuffleHand s -> s.player() == team ? s
                    : new ShuffleHand(s.type(), s.player(), s.codes().stream().map(c -> 0).toList());
            case ConfirmCards c -> c.type() == MessageType.CONFIRM_CARDS && c.player() != team ? null : c;
            case Hint h -> h.hintType() == MessageType.HINT_SELECTMSG && h.player() != team ? null : h;
            case PositionChange p -> p.loc().controller() == team || p.loc().isFaceUp()
                    ? p : new PositionChange(0, p.loc(), p.previousPosition());
            case Swap s -> new Swap(card(s.first(), team), card(s.second(), team));
            case DeckTop d -> d.player() == team || (d.position() & POS_FACEUP) != 0 ? d : null;
            case Unhandled u -> u;
            case Malformed m -> null;
            default -> message;
        };
    }

    private static List<Integer> drawCodes(Draw d) {
        List<Integer> codes = new ArrayList<>(d.codes().size());
        for (int i = 0; i < d.codes().size(); i++) {
            boolean faceUp = i < d.positions().size() && (d.positions().get(i) & POS_FACEUP) != 0;
            codes.add(faceUp ? d.codes().get(i) : 0);
        }
        return codes;
    }

    /** Where a card moved to the opponent sees it: the Graveyard, under an Xyz, or face-up anywhere but the hand. */
    private static boolean moveVisible(Loc to) {
        int location = to.location() & 0x7F;
        if (to.isOverlay() || location == LOCATION_GRAVE) {
            return true;
        }
        return location != LOCATION_DECK && location != LOCATION_HAND && to.isFaceUp();
    }

    private static CardRef card(CardRef card, int team) {
        Loc loc = card.loc();
        return loc.controller() == team || loc.isFaceUp() ? card : new CardRef(0, loc);
    }
}
