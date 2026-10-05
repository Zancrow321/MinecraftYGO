package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.DuelLogHandler;
import io.github.zancrow321.minecraftygo.engine.DuelSettings;
import io.github.zancrow321.minecraftygo.engine.OcgCoreTest;
import io.github.zancrow321.minecraftygo.engine.Ruleset;
import io.github.zancrow321.minecraftygo.engine.ai.DuelistAi;
import io.github.zancrow321.minecraftygo.engine.ai.RandomResponder;
import io.github.zancrow321.minecraftygo.engine.ai.Responder;
import io.github.zancrow321.minecraftygo.engine.data.Banlist;
import io.github.zancrow321.minecraftygo.engine.data.BundledScripts;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.data.DeckBuilder;
import io.github.zancrow321.minecraftygo.engine.text.DuelText;
import io.github.zancrow321.minecraftygo.engine.text.PromptChoices;
import io.github.zancrow321.minecraftygo.engine.text.PromptView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A long playtest: many duels with random decks, every ruleset, 1v1, tag and Battle City, the heuristic AI and the
 * random bot, with some seats played through the same censored views a person gets (and the network codec). Off by
 * default; run with {@code ./gradlew :engine:test -PengineOnly -Pplaytest=<duels> --tests '*PlaytestTest'}. Writes
 * a report to {@code build/playtest.txt}.
 */
@EnabledIfSystemProperty(named = "ygo.playtest", matches = "\\d+")
class PlaytestTest {
    private static final int MAX_CALLS = 20_000;
    private static final int MAX_HUMAN_RETRIES = 500;
    /** A duel this slow is stuck in a loop somewhere; ordinary ones take well under a second. */
    private static final long MAX_NANOS = 30_000_000_000L;

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    private record Outcome(String setup, int winner, String problem) {
    }

    @Test
    void playMany() throws IOException {
        int duels = Integer.parseInt(System.getProperty("ygo.playtest"));
        long firstSeed = Long.getLong("ygo.playtest.seed", 1);
        CardDatabase cards = CardDatabase.loadBundled();
        CardPool pool = CardPool.loadBundled();
        Banlist banlist = Banlist.loadBundled();
        DuelText text = DuelText.loadBundled(cards);
        List<Outcome> outcomes = new ArrayList<>();
        Map<String, Integer> scriptErrors = new TreeMap<>();
        long started = System.nanoTime();
        for (long seed = firstSeed; seed < firstSeed + duels; seed++) {
            Outcome outcome = play(seed, cards, pool, banlist, text, scriptErrors);
            outcomes.add(outcome);
            if (outcome.problem() != null) {
                System.out.println(outcome.setup() + "\n" + outcome.problem());
            }
        }
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;

        StringBuilder report = new StringBuilder();
        List<Outcome> problems = outcomes.stream().filter(o -> o.problem() != null).toList();
        report.append(String.format("%d duels in %d s, %d with problems, %d distinct script errors%n%n", duels,
                seconds, problems.size(), scriptErrors.size()));
        for (Outcome o : problems) {
            report.append(o.setup()).append('\n').append(o.problem()).append("\n\n");
        }
        report.append("Script errors (count, message):\n");
        scriptErrors.forEach((message, count) -> report.append(count).append("  ").append(message).append('\n'));
        Path out = Path.of("build/playtest.txt");
        Files.writeString(out, report);
        System.out.println(report.substring(0, Math.min(report.length(), 4000)));
        assertTrue(problems.isEmpty() && scriptErrors.isEmpty(), "see " + out.toAbsolutePath());
    }

    /**
     * Checks that a person could give {@code answer} on the duel screen: a multi-select must allow that many cards
     * (or cancelling), and a list of choices must not be empty.
     *
     * @return what the screen can't do, or {@code null}
     */
    private static String throughUi(PromptView ui, byte[] answer) {
        if (ui.multi() == null) {
            return ui.choices().isEmpty() ? "screen shows no choices" : null;
        }
        PromptView.MultiSelect multi = ui.multi();
        ByteBuffer b = ByteBuffer.wrap(answer).order(ByteOrder.LITTLE_ENDIAN);
        if (answer.length == 4 && b.getInt(0) == -1) {
            return multi.cancel() != null ? null : "screen can't cancel";
        }
        if (answer.length < 8 || b.getInt(0) != 0 || answer.length != 8 + 4 * b.getInt(4)) {
            return "answer isn't a card list";
        }
        List<Integer> picked = new ArrayList<>();
        for (int i = 0; i < b.getInt(4); i++) {
            picked.add(b.getInt(8 + 4 * i));
        }
        if (picked.stream().anyMatch(i -> i < 0 || i >= multi.options().size())) {
            return "screen doesn't list card " + picked;
        }
        return multi.canConfirm(picked) ? null : "screen won't confirm " + picked.size() + " card(s) (allows "
                + multi.min() + "-" + multi.max() + ")";
    }

    private static boolean matchesChoice(PromptView ui, byte[] answer) {
        return ui.choices().stream().anyMatch(c -> Arrays.equals(c.response(), answer));
    }

    /** Where a stuck duel stands. */
    private static String describe(DuelTable table) {
        try {
            var field = DuelTable.class.getDeclaredField("duel");
            field.setAccessible(true);
            DuelController duel = (DuelController) field.get(table);
            return "turn " + duel.turn() + ", waiting for seat " + table.waitingFor() + ", prompt "
                    + duel.pendingPrompt();
        } catch (ReflectiveOperationException e) {
            return e.toString();
        }
    }

    private static Outcome play(long seed, CardDatabase cards, CardPool pool, Banlist banlist, DuelText text,
                                Map<String, Integer> scriptErrors) {
        Random random = new Random(seed * 1_000_003L);
        Ruleset ruleset = Ruleset.values()[random.nextInt(Ruleset.values().length)];
        int mode = random.nextInt(4); // 0, 1: 1v1; 2: tag; 3: Battle City
        int seatCount = mode < 2 ? 2 : 4;
        List<DuelTable.Seat> seats = new ArrayList<>();
        List<Deck> decks = new ArrayList<>();
        Responder[] players = new Responder[seatCount];
        boolean[] human = new boolean[seatCount];
        StringBuilder setup = new StringBuilder("seed " + seed + ", " + ruleset + ", "
                + (mode < 2 ? "1v1" : mode == 2 ? "tag" : "battle city") + ":");
        for (int seat = 0; seat < seatCount; seat++) {
            int team = seatCount == 2 ? seat : seat / 2;
            boolean ai = random.nextInt(3) > 0;
            players[seat] = ai ? new DuelistAi(seed * 10 + seat, cards) : new RandomResponder(seed * 10 + seat, cards);
            human[seat] = random.nextInt(3) == 0;
            boolean npcDeck = random.nextBoolean();
            decks.add(npcDeck ? DeckBuilder.random("npc", seed * 100 + seat, cards, pool, banlist)
                    : BotDuelTest.randomDeck(cards, pool, new Random(seed * 100 + seat)));
            seats.add(new DuelTable.Seat(team, "P" + seat, human[seat] ? null : players[seat]));
            setup.append(String.format(" [%d %s%s %s]", seat, ai ? "ai" : "random", human[seat] ? " as person" : "",
                    npcDeck ? "npc deck" : "pool deck"));
        }
        List<String> errors = new ArrayList<>();
        DuelLogHandler log = (type, message) -> {
            if (type == DuelLogHandler.LogType.ERROR) {
                errors.add(message);
            }
        };
        DuelSettings settings = DuelSettings.standard(new long[]{seed, seed * 31 + 1, seed * 17 + 2, 99}, ruleset.flags());
        try (DuelTable table = new DuelTable(text, new BundledScripts(), settings, seats, decks, log)
                .splitField(mode == 3)) {
            PromptChoices choices = new PromptChoices(text);
            Map<Integer, DuelView> views = table.start();
            int retries = 0;
            long deadline = System.nanoTime() + MAX_NANOS;
            DuelView lastPromptView = null;
            for (int calls = 0; !table.finished(); calls++) {
                if (calls > MAX_CALLS || System.nanoTime() > deadline) {
                    return new Outcome(setup.toString(), -1, "did not finish after " + calls + " calls: "
                            + describe(table));
                }
                for (DuelView view : views.values()) {
                    DuelView decoded = ViewCodec.decode(ViewCodec.encode(view));
                    if (decoded.log().size() != view.log().size()) {
                        return new Outcome(setup.toString(), -1, "view codec lost log lines");
                    }
                }
                int waiting = table.waitingFor();
                if (waiting >= 0 && human[waiting]) {
                    DuelView view = views.get(waiting);
                    if (view == null || view.prompt() == null) {
                        return new Outcome(setup.toString(), -1, "person " + waiting + " is asked but got no prompt");
                    }
                    retries = lastPromptView != null && view.log().contains("That choice isn't allowed, try again")
                            ? retries + 1 : 0;
                    if (retries > MAX_HUMAN_RETRIES) {
                        return new Outcome(setup.toString(), -1, "person stuck on " + view.prompt());
                    }
                    lastPromptView = view;
                    byte[] answer = players[waiting].respond(view.prompt(), view.board(), retries);
                    PromptView ui = choices.build(view.prompt(), view.hint());
                    String unclickable = throughUi(ui, answer);
                    if (unclickable == null && ui.title().matches("(.*(of|for) )?#\\d+.*")) {
                        unclickable = "unknown card in the title \"" + ui.title() + "\" (hint " + view.hint() + ")";
                    }
                    if (unclickable != null) {
                        return new Outcome(setup.toString(), -1, unclickable + " for " + view.prompt());
                    }
                    if (ui.multi() == null && !matchesChoice(ui, answer)) {
                        // The screen offers fewer choices than the core allows (one order, one counter pick...).
                        answer = ui.choices().get(random.nextInt(ui.choices().size())).response();
                    }
                    views = table.respond(waiting, answer);
                } else {
                    views = table.pump();
                }
            }
            for (String error : errors) {
                scriptErrors.merge(error.replaceAll("\\s+", " "), 1, Integer::sum);
            }
            return new Outcome(setup.toString(), table.winner(), errors.isEmpty() ? null
                    : errors.size() + " script error(s), first: " + errors.getFirst());
        } catch (RuntimeException e) {
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            return new Outcome(setup.toString(), -1, trace.toString().lines().limit(12)
                    .reduce((a, b) -> a + "\n" + b).orElse(""));
        }
    }
}
