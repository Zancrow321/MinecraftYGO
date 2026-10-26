package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.ai.Responder;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.text.DuelText;
import io.github.zancrow321.jadm.engine.text.PromptChoices;
import io.github.zancrow321.jadm.engine.text.PromptView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Lilith, Lady of Lament shows the opponent three Normal Traps from the Deck; both logs must name them. */
class RevealDuelTest {
    private static final int LILITH = 23898021;

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @AfterEach
    void clearSetup() {
        System.clearProperty("jadm.duelSetup");
    }

    @Test
    void lilithRevealIsNamedInBothLogs() throws Exception {
        Path setup = Files.createTempFile("reveal", ".lua");
        Files.writeString(setup, """
                Debug.AddCard(23898021,0,0,LOCATION_MZONE,2,POS_FACEUP_ATTACK)
                """);
        System.setProperty("jadm.duelSetup", setup.toString());
        CardDatabase cards = CardDatabase.loadBundled();
        DuelText text = DuelText.loadBundled(cards);
        PromptChoices choices = new PromptChoices(text);
        List<List<String>> logs = List.of(new ArrayList<>(), new ArrayList<>());
        boolean activated = false;
        try (DuelTable table = new DuelTable(text, new BundledScripts(),
                DuelSettings.standard(new long[]{1, 2, 3, 4}, OcgConstants.DUEL_MODE_MR1),
                Deck.bundled("starter_yugi"), Deck.bundled("starter_kaiba"), List.of("Alex", "Steve"),
                new Responder[]{null, null}, (t, m) -> { })) {
            Map<Integer, DuelView> views = table.start();
            for (int step = 0; step < 200; step++) {
                views.forEach((seat, view) -> logs.get(seat).addAll(view.log()));
                if (logs.get(0).stream().anyMatch(l -> l.startsWith("Alex Set") || l.startsWith("You Set"))) {
                    break;
                }
                Map.Entry<Integer, DuelView> waiting = views.entrySet().stream()
                        .filter(e -> e.getValue().prompt() != null).findFirst().orElse(null);
                if (waiting == null) {
                    views = table.pump();
                    continue;
                }
                PromptView prompt = choices.build(waiting.getValue().prompt(), waiting.getValue().hint());
                byte[] answer;
                if (prompt.multi() != null) {
                    PromptView.MultiSelect multi = prompt.multi();
                    answer = multi.encode(IntStream.range(0, Math.max(multi.min(), 1)).boxed().toList());
                } else {
                    PromptView.Choice lilith = activated ? null : prompt.choices().stream()
                            .filter(c -> c.code() == LILITH).findFirst()
                            .orElse(null);
                    activated |= lilith != null;
                    answer = (lilith != null ? lilith : prompt.choices().getFirst()).response();
                }
                views = table.respond(waiting.getKey(), answer);
            }
        }
        System.out.println("Alex: " + logs.get(0));
        System.out.println("Steve: " + logs.get(1));
        if (!activated) {
            fail("Lilith was never offered: " + logs.get(0));
        }
        String own = logs.get(0).stream().filter(l -> l.startsWith("You revealed ")).findFirst()
                .orElseThrow(() -> new AssertionError(logs.get(0)));
        assertTrue(own.endsWith(" from your Deck to Steve") && own.split(", ").length == 3, own);
        String cardsShown = own.substring("You revealed ".length(), own.indexOf(" from your Deck"));
        assertTrue(logs.get(1).contains("Alex revealed " + cardsShown + " from their Deck to you"),
                logs.get(1).toString());
        // Steve picked the card Alex Sets, so both logs name it and say it came from the Deck.
        String set = logs.get(0).stream().filter(l -> l.startsWith("You Set ")).findFirst()
                .orElseThrow(() -> new AssertionError(logs.get(0)));
        assertTrue(set.endsWith(" from your Deck in the Spell & Trap Zone"), set);
        String setCard = set.substring("You Set ".length(), set.indexOf(" from your Deck"));
        assertTrue(cardsShown.contains(setCard), set);
        assertTrue(logs.get(1).contains("Alex Set " + setCard + " from their Deck in the Spell & Trap Zone"),
                logs.get(1).toString());
    }
}
