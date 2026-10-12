package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.ai.RandomResponder;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.protocol.CardState;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import io.github.zancrow321.jadm.engine.text.DuelText;
import io.github.zancrow321.jadm.engine.text.PromptChoices;
import io.github.zancrow321.jadm.engine.text.PromptView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Detaching Xyz materials: the person picks which ones, and a cost of 2 takes exactly the 2 picked. */
class XyzDetachTest {
    private static final int TORPEDO = 94151981; // Xyz Armor Torpedo: detach 2 materials; draw 1 card
    private static final List<Integer> MATERIALS = List.of(46986414, 89631139, 40640057);

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @Test
    void personPicksWhichTwoMaterialsToDetach(@TempDir Path dir) throws IOException {
        StringBuilder lua = new StringBuilder("Debug.AddCard(" + TORPEDO + ",0,0,LOCATION_MZONE,2,POS_FACEUP_ATTACK)\n");
        for (int code : MATERIALS) {
            lua.append("Debug.AddCard(").append(code).append(",0,0,LOCATION_MZONE,2,POS_FACEUP_ATTACK)\n");
        }
        Path setup = dir.resolve("setup.lua");
        Files.writeString(setup, lua);

        CardDatabase cards = CardDatabase.loadBundled();
        DuelText text = DuelText.loadBundled(cards);
        PromptChoices choices = new PromptChoices(text);
        System.setProperty("jadm.duelSetup", setup.toString());
        try (DuelTable table = new DuelTable(text, new BundledScripts(),
                DuelSettings.standard(new long[]{1, 2, 3, 4}, OcgConstants.DUEL_MODE_MR1),
                Deck.bundled("starter_yugi"), Deck.bundled("starter_kaiba"), List.of("Steve", "Bot"),
                new RandomResponder[]{null, new RandomResponder(3, cards)}, (t, m) -> { })) {
            System.clearProperty("jadm.duelSetup");
            Map<Integer, DuelView> views = table.start();
            boolean activated = false;
            boolean detached = false;
            for (int answers = 0; answers < 2_000; answers++) {
                DuelView view = views.get(0);
                if (view == null || view.prompt() == null) {
                    views = table.pump();
                    continue;
                }
                assertTrue(view.result() == null, "duel ended before the detach");
                PromptView prompt = choices.build(view.prompt(), view.hint());
                if (detached && prompt.multi() == null && prompt.choices().stream()
                        .anyMatch(c -> c.kind() == PromptView.Kind.TO_END)) {
                    CardState torpedo = view.board().side(0).monsters().stream()
                            .filter(c -> c != null && c.code() == TORPEDO).findFirst().orElse(null);
                    assertNotNull(torpedo, "Xyz Armor Torpedo is still on the field");
                    assertEquals(List.of(MATERIALS.get(0)), torpedo.overlayCodes(), "the unpicked material stays");
                    List<Integer> graveyard = view.board().side(0).graveyard().stream().map(CardState::code).toList();
                    assertTrue(graveyard.containsAll(MATERIALS.subList(1, 3)), "picked materials go to the GY: "
                            + graveyard);
                    return;
                }
                byte[] answer;
                if (!activated && prompt.multi() == null) {
                    PromptView.Choice activate = prompt.choices().stream()
                            .filter(c -> c.kind() == PromptView.Kind.ACTIVATE && c.code() == TORPEDO)
                            .findFirst().orElse(null);
                    if (activate != null) {
                        activated = true;
                        views = table.respond(0, activate.response());
                        continue;
                    }
                }
                if (activated && !detached && prompt.multi() != null
                        && prompt.multi().locs().stream().allMatch(l -> l != null && l.isOverlay())) {
                    PromptView.MultiSelect multi = prompt.multi();
                    assertEquals(2, multi.min());
                    assertEquals(2, multi.max());
                    assertEquals(3, multi.options().size());
                    assertTrue(multi.options().stream().allMatch(o -> o.contains("your Xyz material")),
                            multi.options().toString());
                    // Pick the 2nd and 3rd material by their place in the stack, not by list order.
                    List<Integer> pick = new ArrayList<>();
                    for (int i = 0; i < multi.locs().size(); i++) {
                        Loc at = multi.locs().get(i);
                        if (multi.codes().get(i) != MATERIALS.get(0).intValue()) {
                            pick.add(i);
                        }
                        assertEquals(2, at.sequence(), "materials sit in their monster's zone");
                    }
                    assertEquals(2, pick.size(), multi.codes().toString());
                    detached = true;
                    views = table.respond(0, multi.encode(pick));
                    continue;
                }
                answer = passive(prompt);
                views = table.respond(0, Objects.requireNonNull(answer, prompt.title()));
            }
            fail("never got to detach (activated " + activated + ", detached " + detached + ")");
        } finally {
            System.clearProperty("jadm.duelSetup");
        }
    }

    /** An answer that keeps the duel moving without doing anything: pass, go to the end, or the first option. */
    private static byte[] passive(PromptView prompt) {
        if (prompt.multi() != null) {
            List<Integer> first = new ArrayList<>();
            for (int i = 0; i < Math.max(prompt.multi().min(), 1) && i < prompt.multi().options().size(); i++) {
                first.add(i);
            }
            return prompt.multi().encode(first);
        }
        for (PromptView.Kind kind : List.of(PromptView.Kind.PASS, PromptView.Kind.TO_END)) {
            for (PromptView.Choice c : prompt.choices()) {
                if (c.kind() == kind) {
                    return c.response();
                }
            }
        }
        return prompt.choices().getFirst().response();
    }
}
