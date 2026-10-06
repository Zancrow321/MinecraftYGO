package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.DuelSettings;
import io.github.zancrow321.minecraftygo.engine.OcgCoreTest;
import io.github.zancrow321.minecraftygo.engine.Ruleset;
import io.github.zancrow321.minecraftygo.engine.ai.RandomResponder;
import io.github.zancrow321.minecraftygo.engine.data.BundledScripts;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.protocol.CardState;
import io.github.zancrow321.minecraftygo.engine.text.DuelText;
import io.github.zancrow321.minecraftygo.engine.text.PromptChoices;
import io.github.zancrow321.minecraftygo.engine.text.PromptView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A 2v2 tag duel: two people against two bots, under each ruleset. Only the partner whose turn it is to duel for the
 * team answers prompts; both partners see the team's hand. Spectators see neither team's hand.
 */
class TagDuelTest {
    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @ParameterizedTest
    @EnumSource(Ruleset.class)
    void twoPeopleFinishATagDuelAgainstBots(Ruleset ruleset) {
        CardDatabase cards = CardDatabase.loadBundled();
        DuelText text = DuelText.loadBundled(cards);
        PromptChoices choices = new PromptChoices(text);
        Random random = new Random(ruleset.ordinal() + 3);
        List<DuelTable.Seat> seats = List.of(
                new DuelTable.Seat(0, "Steve", null),
                new DuelTable.Seat(0, "Alex", null),
                new DuelTable.Seat(1, "Rex", new RandomResponder(2, cards)),
                new DuelTable.Seat(1, "Weevil", new RandomResponder(3, cards)));
        List<Deck> decks = List.of(Deck.bundled("starter_yugi"), Deck.bundled("starter_kaiba"),
                Deck.bundled("starter_kaiba"), Deck.bundled("starter_yugi"));
        List<String> steveLog = new ArrayList<>();
        int[] prompts = new int[2];
        int benchedViews = 0;
        int spectatorLines = 0;

        try (DuelTable table = new DuelTable(text, new BundledScripts(),
                DuelSettings.standard(new long[]{9, 8, 7, 6 + ruleset.ordinal()}, ruleset.flags()), seats, decks,
                (t, m) -> { })) {
            Map<Integer, DuelView> views = table.start();
            for (int answers = 0; ; answers++) {
                if (answers > 40_000) {
                    fail("tag duel did not finish");
                }
                assertTrue(java.util.Set.of(0, 1).containsAll(views.keySet()), "only people get views");
                DuelView watched = ViewCodec.decode(ViewCodec.encode(table.spectatorView()));
                assertNull(watched.prompt());
                spectatorLines += watched.log().size();
                for (int side = 0; side < 2; side++) {
                    for (CardState card : watched.board().side(side).hand()) {
                        assertTrue(card.code() == 0 || card.isPublic(), "spectators can't see hands");
                    }
                }
                int waiting = table.waitingFor();
                DuelView answerFrom = null;
                for (Map.Entry<Integer, DuelView> entry : views.entrySet()) {
                    int seat = entry.getKey();
                    DuelView view = ViewCodec.decode(ViewCodec.encode(entry.getValue()));
                    assertEquals(List.of("Steve & Alex", "Rex & Weevil"), view.names());
                    assertEquals(0, view.you());
                    if (seat == 0) {
                        steveLog.addAll(view.log());
                    }
                    if (view.prompt() != null) {
                        assertEquals(waiting, seat, "only the waiting seat gets the prompt");
                        prompts[seat]++;
                        answerFrom = view;
                    } else if (table.activeSeat(0) != seat && !table.finished()) {
                        benchedViews++;
                        for (CardState card : view.board().side(0).hand()) {
                            assertTrue(card.code() != 0, "the benched partner sees the team's hand");
                        }
                    }
                }
                if (table.finished()) {
                    break;
                }
                views = answerFrom == null ? table.pump()
                        : table.respond(waiting, DuelTableTest.pick(choices.build(answerFrom.prompt(),
                        answerFrom.hint()), random));
            }
        }
        assertTrue(prompts[0] > 0 && prompts[1] > 0, "both partners duel: " + prompts[0] + "/" + prompts[1]);
        assertTrue(benchedViews > 0, "the benched partner keeps watching");
        assertTrue(spectatorLines > 0, "spectators get the log");
        assertTrue(steveLog.contains("Alex takes over"), steveLog.toString());
        assertTrue(steveLog.contains("Your turn to duel for your team"));
        assertTrue(steveLog.stream().anyMatch(l -> l.equals("Weevil takes over") || l.equals("Rex takes over")));
    }
}
