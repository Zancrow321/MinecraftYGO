package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.protocol.CardRef;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.*;
import io.github.zancrow321.jadm.engine.protocol.Loc;

import java.util.List;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * The core puts real card codes in prompts even for cards the chooser can't see (an opponent's face-down
 * monster, say). This blanks those before a prompt leaves the server.
 */
public final class PromptCensor {
    private PromptCensor() {
    }

    public static Prompt forChooser(Prompt prompt) {
        int p = prompt.player();
        return switch (prompt) {
            case SelectCard s -> new SelectCard(p, s.cancelable(), s.min(), s.max(), cards(s.cards(), p));
            case SelectUnselectCard s -> new SelectUnselectCard(p, s.finishable(), s.cancelable(), s.min(), s.max(),
                    cards(s.selectable(), p), cards(s.unselectable(), p));
            case SelectTribute s -> new SelectTribute(p, s.cancelable(), s.min(), s.max(), s.cards().stream()
                    .map(c -> new TributeCandidate(card(c.card(), p), c.releaseParam())).toList());
            case SelectSum s -> new SelectSum(p, s.atLeast(), s.target(), s.min(), s.max(),
                    s.mustSelect().stream().map(c -> new SumCandidate(card(c.card(), p), c.param())).toList(),
                    s.selectable().stream().map(c -> new SumCandidate(card(c.card(), p), c.param())).toList());
            default -> prompt;
        };
    }

    private static List<CardRef> cards(List<CardRef> cards, int viewer) {
        return cards.stream().map(c -> card(c, viewer)).toList();
    }

    private static CardRef card(CardRef card, int viewer) {
        return visible(card.loc(), viewer) ? card : new CardRef(0, card.loc());
    }

    static boolean visible(Loc loc, int viewer) {
        if (loc.location() == 0 || loc.controller() == viewer && loc.location() != LOCATION_DECK) {
            return true; // a bare card code (declare-a-card style lists) or the chooser's own card
        }
        return switch (loc.location() & 0x7F) {
            case LOCATION_GRAVE -> true;
            case LOCATION_MZONE, LOCATION_SZONE, LOCATION_REMOVED -> loc.isFaceUp();
            default -> false; // deck, hand, extra deck
        };
    }
}
