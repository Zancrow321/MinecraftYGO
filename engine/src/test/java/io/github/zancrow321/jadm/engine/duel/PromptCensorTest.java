package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.protocol.CardRef;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.SelectCard;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptCensorTest {
    private static List<Integer> codes(SelectCard prompt) {
        return prompt.cards().stream().map(CardRef::code).toList();
    }

    @Test
    void searchingYourOwnDeckShowsTheCards() {
        SelectCard search = new SelectCard(0, false, 1, 1, List.of(
                new CardRef(46986414, new Loc(0, LOCATION_DECK, 3, POS_FACEDOWN_DEFENSE)),
                new CardRef(89631139, new Loc(1, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE)),
                new CardRef(70903634, new Loc(1, LOCATION_HAND, 2, POS_FACEDOWN_DEFENSE))));
        assertEquals(List.of(46986414, 0, 0), codes((SelectCard) PromptCensor.forChooser(search)),
                "your deck shows, the opponent's deck and hand don't");
    }

    @Test
    void cardsShownToYouStayVisible() {
        CardRef shown = new CardRef(70903634, new Loc(1, LOCATION_HAND, 2, POS_FACEDOWN_DEFENSE));
        CardRef hidden = new CardRef(89631139, new Loc(1, LOCATION_HAND, 0, POS_FACEDOWN_DEFENSE));
        SelectCard pick = new SelectCard(0, false, 1, 1, List.of(shown, hidden));
        assertEquals(List.of(70903634, 0),
                codes((SelectCard) PromptCensor.forChooser(pick, card -> card.equals(shown))));
    }
}
