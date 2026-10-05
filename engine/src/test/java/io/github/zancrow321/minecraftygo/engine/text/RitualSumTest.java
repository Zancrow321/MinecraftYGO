package io.github.zancrow321.minecraftygo.engine.text;

import io.github.zancrow321.minecraftygo.engine.ai.RandomResponder;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.protocol.CardRef;
import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage.SelectSum;
import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage.SumCandidate;
import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.LOCATION_HAND;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ritual tributes are an "at least" sum, which the core sends with no card count (min and max 0). Both the duel
 * screen and the bots must still let you pick several cards.
 */
class RitualSumTest {
    /** Levels 3, 3, 3, 2 and 4 in hand, for a level 8 Ritual Monster. */
    private static final SelectSum RITUAL = new SelectSum(0, true, 8, 0, 0, List.of(), List.of(
            candidate(0, 3), candidate(1, 3), candidate(2, 3), candidate(3, 2), candidate(4, 4)));

    private static SumCandidate candidate(int sequence, int level) {
        return new SumCandidate(new CardRef(1000 + sequence, new Loc(0, LOCATION_HAND, sequence, 0)), level);
    }

    @Test
    void screenAllowsSeveralTributes() {
        CardDatabase cards = CardDatabase.loadBundled();
        PromptView view = new PromptChoices(DuelText.loadBundled(cards)).build(RITUAL, 0);
        assertTrue(view.multi().canConfirm(List.of(0, 1, 3)), "three tributes should be confirmable");
    }

    @Test
    void botPicksTributesWithoutOneToSpare() {
        RandomResponder bot = new RandomResponder(1, CardDatabase.loadBundled());
        ByteBuffer answer = ByteBuffer.wrap(bot.respond(RITUAL, 0)).order(ByteOrder.LITTLE_ENDIAN);
        int count = answer.getInt(4);
        List<Integer> levels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            levels.add(RITUAL.selectable().get(answer.getInt(8 + 4 * i)).param());
        }
        int total = levels.stream().mapToInt(Integer::intValue).sum();
        int smallest = levels.stream().mapToInt(Integer::intValue).min().orElse(0);
        assertTrue(total >= 8 && total - smallest < 8, "tributes " + levels);
    }
}
