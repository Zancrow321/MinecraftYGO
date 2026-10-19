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
import io.github.zancrow321.jadm.engine.duel.Board;
import io.github.zancrow321.jadm.engine.duel.DuelTable;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.text.DuelText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
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

    private record Result(int played, int wins, int answered, int fallbacks, int retries) {
    }

    /** Duels WindBot against {@code other}, alternating who goes first. */
    private static Result play(int duels, long mode, IntFunction<Deck[]> decks, IntFunction<Responder> other,
                               double mistakes) {
        int wins = 0;
        int answered = 0;
        int fallbacks = 0;
        int retries = 0;
        int played = 0;
        List<String> crashes = new ArrayList<>();
        for (int i = 0; i < duels; i++) {
            boolean first = i % 2 == 0;
            Deck[] pair = decks.apply(i);
            WindBotAi bot = new WindBotAi(i, CARDS, STYLES[i % STYLES.length], mistakes, pair[0].main());
            Recorder us = new Recorder(bot);
            Recorder them = new Recorder(other.apply(i));
            try {
                int winner = duel(i, mode, first, pair, us, them);
                played++;
                if (winner == (first ? 0 : 1)) {
                    wins++;
                }
            } catch (Error e) {
                // A crash inside the native core: say where, and whether the same duel crashes without WindBot.
                boolean coreBug;
                try {
                    duel(i, mode, first, pair, new DuelistAi(i, CARDS), other.apply(i));
                    coreBug = false;
                } catch (Error again) {
                    coreBug = true;
                }
                String report = "duel " + i + " (" + STYLES[i % STYLES.length] + ") crashed: " + e.getMessage()
                        + "\n  WindBot's last answers: " + us.last()
                        + "\n  opponent's last answers: " + them.last()
                        + "\n  the same duel with DuelistAi in WindBot's seat "
                        + (coreBug ? "crashes too, so the core is at fault" : "finishes");
                // The Windows core crashes in some duels whoever plays them; only crashes WindBot causes count.
                System.out.println(report);
                if (!coreBug) {
                    crashes.add(report);
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
        assertTrue(crashes.isEmpty(), String.join("\n", crashes));
        return new Result(played, wins, answered, fallbacks, retries);
    }

    /** Plays one duel to the end and returns the winning seat. */
    private static int duel(int i, long mode, boolean first, Deck[] pair, Responder us, Responder them) {
        Responder[] bots = first ? new Responder[]{us, them} : new Responder[]{them, us};
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
            return table.winner();
        }
    }

    /** Passes everything on to a bot and keeps its last few prompts and answers, for crash reports. */
    private static final class Recorder implements Responder {
        private final Responder bot;
        private final Deque<String> recent = new ArrayDeque<>();

        Recorder(Responder bot) {
            this.bot = bot;
        }

        @Override
        public byte[] respond(DuelMessage.Prompt prompt, Board board, int attempt) {
            byte[] answer = bot.respond(prompt, board, attempt);
            String line = "turn " + board.turn() + " " + prompt + " -> " + Arrays.toString(answer);
            recent.addLast(line.length() > 400 ? line.substring(0, 400) + "..." : line);
            if (recent.size() > 4) {
                recent.removeFirst();
            }
            return answer;
        }

        @Override
        public void observe(int team, DuelMessage message) {
            bot.observe(team, message);
        }

        String last() {
            return String.join("\n    ", recent);
        }
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
        System.out.println("WindBot won " + result.wins() + " of " + result.played()
                + " against the random bot, answering " + result.answered() + " prompts itself, handing over "
                + result.fallbacks() + ", retried "
                + result.retries());
        assertTrue(result.wins() >= result.played() * 8 / 10, "won only " + result.wins());
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
        System.out.println("WindBot won " + result.wins() + " of " + result.played()
                + " against DuelistAi, answering " + result.answered() + " prompts itself, handing over "
                + result.fallbacks() + ", retried "
                + result.retries());
        assertTrue(result.played() >= duels - 2, "only " + result.played() + " duels finished");
        assertTrue(result.wins() >= result.played() / 2, "won only " + result.wins());
        assertTrue(result.fallbacks() * 20 < result.answered(), "handed over " + result.fallbacks());
    }

    @Test
    void mistakesStillFinishDuels() {
        Result result = play(4, OcgConstants.DUEL_MODE_MR1, WindBotAiTest::starters,
                i -> new DuelistAi(3000 + i, CARDS), 1.0);
        System.out.println("A bot that always slips won " + result.wins() + " of " + result.played());
    }
}
