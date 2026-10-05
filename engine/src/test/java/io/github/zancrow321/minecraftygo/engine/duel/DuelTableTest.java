package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.DuelSettings;
import io.github.zancrow321.minecraftygo.engine.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.OcgCoreTest;
import io.github.zancrow321.minecraftygo.engine.ai.RandomResponder;
import io.github.zancrow321.minecraftygo.engine.data.BundledScripts;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.protocol.CardState;
import io.github.zancrow321.minecraftygo.engine.text.DuelText;
import io.github.zancrow321.minecraftygo.engine.text.PromptChoices;
import io.github.zancrow321.minecraftygo.engine.text.PromptView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plays a person-vs-bot duel the way the mod does: the person's side only ever sees JSON-decoded views and picks
 * from the generated {@link PromptView} choices.
 */
class DuelTableTest {
    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @Test
    void personBeatsOrLosesToBotThroughViews() {
        CardDatabase cards = CardDatabase.loadBundled();
        DuelText text = DuelText.loadBundled(cards);
        PromptChoices choices = new PromptChoices(text);
        Random random = new Random(42);
        List<String> log = new ArrayList<>();
        List<FieldEvent> events = new ArrayList<>();

        try (DuelTable table = new DuelTable(text, new BundledScripts(),
                DuelSettings.standard(new long[]{5, 6, 7, 8}, OcgConstants.DUEL_MODE_MR1),
                Deck.bundled("starter_yugi"), Deck.bundled("starter_kaiba"), List.of("Steve", "Bot"),
                new RandomResponder[]{null, new RandomResponder(7, cards)}, (t, m) -> { })) {
            Map<Integer, DuelView> views = table.start();
            for (int answers = 0; ; answers++) {
                if (answers > 20_000) {
                    fail("duel did not finish");
                }
                assertEquals(java.util.Set.of(0), views.keySet(), "only the person gets views");
                DuelView view = ViewCodec.decode(ViewCodec.encode(views.get(0)));
                log.addAll(view.log());
                events.addAll(view.events());
                for (CardState card : view.board().side(1).hand()) {
                    assertEquals(0, card.code(), "opponent's hand must be hidden");
                }
                if (view.result() != null) {
                    assertTrue(table.finished());
                    break;
                }
                if (view.prompt() == null) {
                    views = table.pump();
                    continue;
                }
                PromptView prompt = choices.build(view.prompt(), view.hint());
                assertNotNull(prompt.title());
                views = table.respond(0, pick(prompt, random));
            }
        }
        assertFalse(log.isEmpty());
        assertTrue(events.stream().anyMatch(e -> e.kind() == FieldEvent.Kind.SUMMON), "summons are animated");
        assertTrue(events.stream().anyMatch(e -> e.kind() == FieldEvent.Kind.DAMAGE), "damage is animated");
        assertTrue(events.stream().filter(e -> e.kind() == FieldEvent.Kind.SET && e.player() == 1)
                .allMatch(e -> e.code() == 0), "opponent's set cards stay hidden");
        assertTrue(log.stream().anyMatch(l -> l.startsWith("You drew ")), "own draws are named");
        assertTrue(log.stream().anyMatch(l -> l.startsWith("Bot drew ")), "opponent draws are counted only");
        assertTrue(log.stream().anyMatch(l -> l.contains("wins the duel") || l.contains("You win")), log.toString());
    }

    static byte[] pick(PromptView prompt, Random random) {
        if (prompt.multi() == null) {
            // Favour the last choices (phase changes) a little so the person keeps the duel moving.
            List<PromptView.Choice> c = prompt.choices();
            return c.get(random.nextInt(4) == 0 ? c.size() - 1 : random.nextInt(c.size())).response();
        }
        PromptView.MultiSelect multi = prompt.multi();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < multi.options().size(); i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, random);
        int max = Math.min(multi.max(), indices.size());
        int count = max <= multi.min() ? max : multi.min() + random.nextInt(max - multi.min() + 1);
        return multi.encode(indices.subList(0, Math.max(count, Math.min(1, max))));
    }
}
