package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.protocol.CardRef;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.*;
import io.github.zancrow321.jadm.engine.protocol.Loc;

import java.util.List;
import java.util.function.Predicate;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * The core puts real card codes in prompts even for cards the chooser can't see (an opponent's face-down
 * monster, say). This blanks those before a prompt leaves the server. Cards in the chooser's own deck stay: being
 * asked to pick from your deck means you are searching it, and you see what you pick (as EDOPro does).
 */
public final class PromptCensor {
    private PromptCensor() {
    }

    public static Prompt forChooser(Prompt prompt) {
        return forChooser(prompt, card -> false);
    }

    /** @param revealed cards an effect has shown the chooser (a looked-at hand, say), which stay too */
    public static Prompt forChooser(Prompt prompt, Predicate<CardRef> revealed) {
        int p = prompt.player();
        return switch (prompt) {
            case SelectCard s -> new SelectCard(p, s.cancelable(), s.min(), s.max(), cards(s.cards(), p, revealed));
            case SelectUnselectCard s -> new SelectUnselectCard(p, s.finishable(), s.cancelable(), s.min(), s.max(),
                    cards(s.selectable(), p, revealed), cards(s.unselectable(), p, revealed));
            case SelectTribute s -> new SelectTribute(p, s.cancelable(), s.min(), s.max(), s.cards().stream()
                    .map(c -> new TributeCandidate(card(c.card(), p, revealed), c.releaseParam())).toList());
            case SelectSum s -> new SelectSum(p, s.atLeast(), s.target(), s.min(), s.max(),
                    s.mustSelect().stream().map(c -> new SumCandidate(card(c.card(), p, revealed), c.param()))
                            .toList(),
                    s.selectable().stream().map(c -> new SumCandidate(card(c.card(), p, revealed), c.param()))
                            .toList());
            default -> prompt;
        };
    }

    private static List<CardRef> cards(List<CardRef> cards, int viewer, Predicate<CardRef> revealed) {
        return cards.stream().map(c -> card(c, viewer, revealed)).toList();
    }

    private static CardRef card(CardRef card, int viewer, Predicate<CardRef> revealed) {
        return visible(card.loc(), viewer) || revealed.test(card) ? card : new CardRef(0, card.loc());
    }

    static boolean visible(Loc loc, int viewer) {
        if (loc.location() == 0 || loc.controller() == viewer) {
            return true; // a bare card code (declare-a-card style lists) or the chooser's own card, deck included
        }
        return switch (loc.location() & 0x7F) {
            case LOCATION_GRAVE -> true;
            case LOCATION_MZONE, LOCATION_SZONE, LOCATION_REMOVED -> loc.isFaceUp();
            default -> false; // deck, hand, extra deck
        };
    }
}
