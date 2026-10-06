package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.DuelLogHandler;
import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.ai.RandomResponder;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.CardPool;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.data.DeckRules;
import io.github.zancrow321.jadm.engine.data.PoolMode;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plays whole duels between two random bots with the bundled starter decks, under every supported ruleset. This
 * exercises card scripts, the message decoder and the response encoder against the real engine.
 */
class BotDuelTest {
    private static final int MAX_PROMPTS = 20_000;
    private static final int MAX_RETRIES = 200;

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8})
    void botsFinishADuel(long seed) {
        play(seed, Deck.bundled("starter_yugi"), Deck.bundled("starter_kaiba"));
    }

    /** Random decks from the whole pool, so every card script gets loaded and many get played. */
    @ParameterizedTest
    @ValueSource(longs = {11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26})
    void botsFinishAPoolDuel(long seed) {
        CardDatabase cards = CardDatabase.loadBundled();
        CardPool pool = CardPool.loadBundled();
        play(seed, randomDeck(cards, pool, new Random(seed)), randomDeck(cards, pool, new Random(seed * 7919)));
    }

    /** Random decks from every bundled card, played under the modern rules. */
    @ParameterizedTest
    @ValueSource(longs = {41, 43, 45, 47, 49, 51, 53, 55})
    void botsFinishAnAllCardsDuel(long seed) {
        CardDatabase cards = CardDatabase.loadBundled();
        CardPool pool = CardPool.of(PoolMode.ALL, cards);
        play(seed, randomDeck(cards, pool, new Random(seed)), randomDeck(cards, pool, new Random(seed * 7919)));
    }

    static Deck randomDeck(CardDatabase cards, CardPool pool, Random random) {
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        for (int code : pool.monsters()) {
            (DeckRules.isExtra(cards.card(code)) ? extra : main).add(code);
        }
        main.addAll(pool.spellsTraps());
        Collections.shuffle(main, random);
        Collections.shuffle(extra, random);
        return new Deck("random", main.subList(0, 40), extra.subList(0, 15), List.of());
    }

    private static void play(long seed, Deck first, Deck second) {
        CardDatabase cards = CardDatabase.loadBundled();
        List<String> errors = new ArrayList<>();
        DuelLogHandler log = (type, message) -> {
            if (type == DuelLogHandler.LogType.ERROR) {
                errors.add(message);
            }
        };
        long flags = seed % 2 == 0 ? OcgConstants.DUEL_MODE_MR1 : OcgConstants.DUEL_MODE_MR5;
        DuelSettings settings = DuelSettings.standard(new long[]{seed, seed * 31, seed * 17, 99}, flags);
        RandomResponder[] bots = {new RandomResponder(seed, cards), new RandomResponder(seed + 1000, cards)};
        Map<String, Integer> promptCounts = new TreeMap<>();

        try (DuelController duel = new DuelController(cards, new BundledScripts(), settings,
                first, second, log)) {
            DuelController.Step step = duel.advance();
            DuelMessage.Prompt last = null;
            int attempt = 0;
            for (int prompts = 0; !step.finished(); prompts++) {
                if (prompts > MAX_PROMPTS) {
                    fail("duel did not finish; prompts seen " + promptCounts);
                }
                for (DuelMessage message : step.messages()) {
                    if (message instanceof DuelMessage.Malformed m) {
                        fail("malformed message type " + m.type() + ": " + m.error());
                    }
                }
                DuelMessage.Prompt prompt = step.prompt();
                assertNotNull(prompt);
                boolean retried = step.messages().stream().anyMatch(m -> m instanceof DuelMessage.Retry);
                attempt = retried && prompt == last ? attempt + 1 : 0;
                if (attempt > MAX_RETRIES) {
                    fail("bot stuck on " + prompt);
                }
                last = prompt;
                promptCounts.merge(prompt.getClass().getSimpleName(), 1, Integer::sum);
                duel.respond(bots[prompt.player()].respond(prompt, attempt));
                step = duel.advance();
            }

            DuelMessage.Win win = step.result();
            assertTrue(win.player() >= 0 && win.player() <= 2, "winner " + win.player());
            assertTrue(duel.turn() > 1, "duel should last more than one turn");
            assertEquals(List.of(), errors, "script errors");
            System.out.printf("seed %d: player %d won (reason %d) on turn %d; prompts %s%n", seed, win.player(),
                    win.reason(), duel.turn(), promptCounts);
        }
    }
}
