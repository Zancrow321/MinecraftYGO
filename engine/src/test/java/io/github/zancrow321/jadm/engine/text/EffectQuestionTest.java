package io.github.zancrow321.jadm.engine.text;

import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.protocol.CardRef;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.SelectEffectYesNo;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import org.junit.jupiter.api.Test;

import static io.github.zancrow321.jadm.engine.OcgConstants.LOCATION_MZONE;
import static io.github.zancrow321.jadm.engine.OcgConstants.POS_FACEUP_ATTACK;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The core's generic "use this effect?" texts have blanks for the card and where it is. */
class EffectQuestionTest {
    @Test
    void blanksNameTheCardAndItsPlace() {
        CardDatabase cards = CardDatabase.loadBundled();
        PromptChoices choices = new PromptChoices(DuelText.loadBundled(cards));
        CardRef blackRose = new CardRef(73580471, new Loc(0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK));
        assertEquals("Use the effect of \"Black Rose Dragon\" from [Monster Zone]?",
                choices.build(new SelectEffectYesNo(0, blackRose, 200), 0).title());
    }
}
