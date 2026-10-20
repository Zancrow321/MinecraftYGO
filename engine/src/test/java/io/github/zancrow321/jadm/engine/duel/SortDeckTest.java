package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.ai.RandomResponder;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.SortCards;
import io.github.zancrow321.jadm.engine.text.DuelText;
import io.github.zancrow321.jadm.engine.text.PromptChoices;
import io.github.zancrow321.jadm.engine.text.PromptView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * "Look at the top cards of your Deck and put them back in any order": the duel screen must let you choose the
 * order, and the core must put the cards back the way they were clicked.
 */
class SortDeckTest {
    private static final int SPELLBOOK_ORGANIZATION = 96677818;
    /** Three different cards to sort, then filler for the opening hand on top of them. */
    private static final String SETUP = """
            Debug.AddCard(89631139,0,0,LOCATION_DECK,0,POS_FACEDOWN)
            Debug.AddCard(46986414,0,0,LOCATION_DECK,0,POS_FACEDOWN)
            Debug.AddCard(74677422,0,0,LOCATION_DECK,0,POS_FACEDOWN)
            for i=1,5 do Debug.AddCard(%d,0,0,LOCATION_DECK,0,POS_FACEDOWN) end
            """.formatted(SPELLBOOK_ORGANIZATION);

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @Test
    void clickedOrderIsTheNewDeckOrder() throws IOException {
        CardDatabase cards = CardDatabase.loadBundled();
        PromptChoices choices = new PromptChoices(DuelText.loadBundled(cards));
        RandomResponder bot = new RandomResponder(3, cards);
        Path setup = Files.createTempFile("sort-deck", ".lua");
        Files.writeString(setup, SETUP);
        System.setProperty("jadm.duelSetup", setup.toString());
        try (DuelController duel = new DuelController(cards, new BundledScripts(),
                DuelSettings.standard(new long[]{1, 2, 3, 4}, OcgConstants.DUEL_MODE_MR5),
                Deck.bundled("starter_yugi"), Deck.bundled("starter_kaiba"), (t, m) -> { })) {
            System.clearProperty("jadm.duelSetup");
            boolean activated = false;
            for (int answers = 0; answers < 2_000; answers++) {
                DuelController.Step step = duel.advance();
                assertTrue(!step.finished(), "duel ended before the cards were sorted");
                DuelMessage.Prompt prompt = step.prompt();
                if (prompt.player() == 1) {
                    duel.respond(bot.respond(prompt, 0));
                    continue;
                }
                PromptView view = choices.build(prompt, 0);
                if (prompt instanceof SortCards sort) {
                    assertNotNull(view.multi(), "sorting must let you pick an order");
                    List<Integer> before = sort.cards().stream().map(c -> c.code()).toList();
                    assertEquals(3, before.size());
                    assertEquals(3, before.stream().distinct().count(), "test needs three different cards");
                    // Click the old bottom one first (new top), then the old top one, then the middle one.
                    List<Integer> clicks = List.of(2, 0, 1);
                    assertTrue(view.multi().canConfirm(clicks));
                    duel.respond(view.multi().encode(clicks));
                    duel.advance();
                    List<Integer> deck = duel.query(0, OcgConstants.LOCATION_DECK).stream()
                            .map(c -> c.code()).toList();
                    List<Integer> top = deck.subList(deck.size() - 3, deck.size()).reversed();
                    assertEquals(List.of(before.get(2), before.get(0), before.get(1)), top);
                    return;
                }
                if (prompt instanceof DuelMessage.SelectIdleCmd && !activated) {
                    PromptView.Choice activate = view.choices().stream()
                            .filter(c -> c.kind() == PromptView.Kind.ACTIVATE && c.code() == SPELLBOOK_ORGANIZATION)
                            .findFirst().orElse(null);
                    if (activate != null) {
                        activated = true;
                        duel.respond(activate.response());
                        continue;
                    }
                }
                if (prompt instanceof DuelMessage.SelectChain) {
                    duel.respond(io.github.zancrow321.jadm.engine.protocol.Responses.cancel());
                    continue;
                }
                duel.respond(bot.respond(prompt, 0));
            }
            fail("never asked to sort the cards");
        } finally {
            System.clearProperty("jadm.duelSetup");
            Files.deleteIfExists(setup);
        }
    }
}
