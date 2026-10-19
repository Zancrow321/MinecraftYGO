package io.github.zancrow321.jadm.engine.ai.windbot;

import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.ai.DuelistAi;
import io.github.zancrow321.jadm.engine.ai.RandomResponder;
import io.github.zancrow321.jadm.engine.ai.Responder;
import io.github.zancrow321.jadm.engine.data.Banlist;
import io.github.zancrow321.jadm.engine.data.Banlists;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.CardPool;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.data.DeckBuilder;
import io.github.zancrow321.jadm.engine.data.PoolMode;
import io.github.zancrow321.jadm.engine.duel.DuelTable;
import io.github.zancrow321.jadm.engine.text.DuelText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WindBot with Astra's executor plays whole duels, mostly answering prompts itself, and beats the older bots.
 */
class WindBotAiTest {
    private static final CardDatabase CARDS = CardDatabase.loadBundled();
    private static final DuelText TEXT = DuelText.loadBundled(CARDS);
    private static final AstraExecutor.Style[] STYLES = AstraExecutor.Style.values();

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    private record Result(int wins, int answered, int fallbacks, int retries) {
    }

    /** Duels WindBot against {@code other}, alternating who goes first. */
    private static Result play(int duels, long mode, IntFunction<Deck[]> decks, IntFunction<Responder> other,
                               double mistakes) {
        int wins = 0;
        int answered = 0;
        int fallbacks = 0;
        int retries = 0;
        for (int i = 0; i < duels; i++) {
            boolean first = i % 2 == 0;
            Deck[] pair = decks.apply(i);
            WindBotAi bot = new WindBotAi(i, CARDS, STYLES[i % STYLES.length], mistakes, pair[0].main());
            Responder opponent = other.apply(i);
            Responder[] bots = first ? new Responder[]{bot, opponent} : new Responder[]{opponent, bot};
            Deck[] seated = first ? pair : new Deck[]{pair[1], pair[0]};
            try (DuelTable table = new DuelTable(TEXT, new BundledScripts(),
                    DuelSettings.standard(new long[]{i + 11, 3, 5, 7}, mode), seated[0], seated[1],
                    List.of("A", "B"), bots, (t, m) -> { })) {
                table.start();
                for (int pumps = 0; !table.finished(); pumps++) {
                    if (pumps > 10_000) {
                        fail("duel " + i + " did not finish");
                    }
                    table.pump();
                }
                if (table.winner() == (first ? 0 : 1)) {
                    wins++;
                }
            }
            answered += bot.answered();
            fallbacks += bot.fallbacks();
            retries += bot.retries();
            if (bot.lastError() != null) {
                System.out.println("duel " + i + ": " + bot.lastError());
                bot.lastError().printStackTrace(System.out);
            }
        }
        return new Result(wins, answered, fallbacks, retries);
    }

    private static Deck[] starters(int i) {
        String deck = (i / 2) % 2 == 0 ? "starter_kaiba" : "starter_yugi";
        String other = deck.equals("starter_kaiba") ? "starter_yugi" : "starter_kaiba";
        return new Deck[]{Deck.bundled(deck), Deck.bundled(other)};
    }

    @Test
    void beatsTheRandomBot() {
        int duels = 12;
        Result result = play(duels, OcgConstants.DUEL_MODE_MR1, WindBotAiTest::starters,
                i -> new RandomResponder(1000 + i, CARDS), 0);
        System.out.println("WindBot won " + result.wins() + " of " + duels + " against the random bot, answering "
                + result.answered() + " prompts itself, handing over " + result.fallbacks() + ", retried "
                + result.retries());
        assertTrue(result.wins() >= duels * 8 / 10, "won only " + result.wins());
        assertTrue(result.fallbacks() * 20 < result.answered(), "handed over " + result.fallbacks());
    }

    @Test
    void holdsItsOwnAgainstTheOldDuelistWithModernDecks() {
        int duels = 20;
        CardPool pool = CardPool.of(PoolMode.ALL, CARDS);
        Banlist banlist = Banlists.loadBundled().latest();
        IntFunction<Deck[]> decks = i -> new Deck[]{
                DeckBuilder.random("a", 100 + i / 2, CARDS, pool, banlist),
                DeckBuilder.random("b", 500 + i / 2, CARDS, pool, banlist)};
        Result result = play(duels, OcgConstants.DUEL_MODE_MR5, decks, i -> new DuelistAi(2000 + i, CARDS), 0);
        System.out.println("WindBot won " + result.wins() + " of " + duels + " against DuelistAi, answering "
                + result.answered() + " prompts itself, handing over " + result.fallbacks() + ", retried "
                + result.retries());
        assertTrue(result.wins() >= duels / 2, "won only " + result.wins());
        assertTrue(result.fallbacks() * 20 < result.answered(), "handed over " + result.fallbacks());
    }

    @Test
    void mistakesStillFinishDuels() {
        Result result = play(4, OcgConstants.DUEL_MODE_MR1, WindBotAiTest::starters,
                i -> new DuelistAi(3000 + i, CARDS), 1.0);
        System.out.println("A bot that always slips won " + result.wins() + " of 4");
    }
}
