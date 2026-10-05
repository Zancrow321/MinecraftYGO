package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.DuelLogHandler;
import io.github.zancrow321.minecraftygo.engine.DuelSettings;
import io.github.zancrow321.minecraftygo.engine.ScriptProvider;
import io.github.zancrow321.minecraftygo.engine.ai.RandomResponder;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.protocol.CardState;
import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage;
import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import io.github.zancrow321.minecraftygo.engine.protocol.MessageType;
import io.github.zancrow321.minecraftygo.engine.protocol.Responses;
import io.github.zancrow321.minecraftygo.engine.text.DuelLog;
import io.github.zancrow321.minecraftygo.engine.text.DuelText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * A duel between two seats, each a person or a bot. Bots answer their own prompts; for people the table produces
 * a {@link DuelView} to send to them and waits for {@link #respond}. Everything a person receives is censored for
 * them. Not thread-safe.
 */
public final class DuelTable implements AutoCloseable {
    /** Bot answers handled per call before returning, so a bot-vs-bot loop can't stall the caller. */
    private static final int BOT_STEPS_PER_CALL = 500;
    private static final int MAX_BOT_RETRIES = 500;

    private final DuelController duel;
    private final DuelText text;
    private final List<String> names;
    private final RandomResponder[] bots;
    private final List<List<String>> pendingLog = List.of(new ArrayList<>(), new ArrayList<>());
    private final List<List<FieldEvent>> pendingEvents = List.of(new ArrayList<>(), new ArrayList<>());
    private long hint;
    private int botRetries;
    private String forfeitResult;
    private DuelMessage.Win result;

    /**
     * @param bots a responder per seat, {@code null} for a person
     */
    public DuelTable(DuelText text, ScriptProvider scripts, DuelSettings settings, Deck deck0, Deck deck1,
                     List<String> names, RandomResponder[] bots, DuelLogHandler log) {
        this.text = text;
        this.names = List.copyOf(names);
        this.bots = bots.clone();
        this.duel = new DuelController(text.cards(), scripts, settings, deck0, deck1, log);
    }

    public boolean isBot(int player) {
        return bots[player] != null;
    }

    public boolean finished() {
        return result != null || forfeitResult != null;
    }

    /** @return the player who must answer next, or -1 */
    public int waitingFor() {
        DuelMessage.Prompt prompt = duel.pendingPrompt();
        return finished() || prompt == null ? -1 : prompt.player();
    }

    /** Runs the duel up to the first person's prompt. */
    public Map<Integer, DuelView> start() {
        return run(duel.advance());
    }

    /**
     * Applies a person's answer and runs on to the next person's prompt.
     *
     * @throws IllegalStateException if it isn't that player's turn to answer
     */
    public Map<Integer, DuelView> respond(int player, byte[] response) {
        if (waitingFor() != player || isBot(player)) {
            throw new IllegalStateException("Player " + player + " has nothing to answer");
        }
        duel.respond(response);
        return run(duel.advance());
    }

    /** Continues a bot-only stretch that {@link #BOT_STEPS_PER_CALL} cut short. */
    public Map<Integer, DuelView> pump() {
        int player = waitingFor();
        if (player < 0 || !isBot(player)) {
            return Map.of();
        }
        return run(null);
    }

    /** Ends the duel with {@code player} losing. */
    public Map<Integer, DuelView> forfeit(int player) {
        if (finished()) {
            return Map.of();
        }
        for (int viewer = 0; viewer < 2; viewer++) {
            pendingLog.get(viewer).add((viewer == player ? "You" : names.get(player)) + " surrendered");
        }
        forfeitResult = names.get(1 - player) + " wins the duel";
        return views(null);
    }

    private Map<Integer, DuelView> run(DuelController.Step step) {
        for (int botSteps = 0; botSteps < BOT_STEPS_PER_CALL; botSteps++) {
            if (step != null) {
                record(step);
                if (step.finished()) {
                    result = step.result();
                    return views(null);
                }
            }
            DuelMessage.Prompt prompt = duel.pendingPrompt();
            if (prompt instanceof DuelMessage.SelectChain chain && chain.chains().isEmpty() && !chain.forced()) {
                // Nothing can respond: pass automatically instead of asking (EDOPro does the same).
                duel.respond(Responses.index(-1));
                step = duel.advance();
                continue;
            }
            if (!isBot(prompt.player())) {
                return views(prompt);
            }
            boolean retried = step != null && step.messages().stream().anyMatch(m -> m instanceof DuelMessage.Retry);
            botRetries = retried ? botRetries + 1 : 0;
            if (botRetries > MAX_BOT_RETRIES) {
                throw new IllegalStateException("Bot could not answer " + prompt);
            }
            duel.respond(bots[prompt.player()].respond(prompt, botRetries));
            step = duel.advance();
        }
        return views(null);
    }

    private void record(DuelController.Step step) {
        Board board = duel.board();
        long latestHint = 0;
        for (DuelMessage message : step.messages()) {
            if (message instanceof DuelMessage.Hint h && h.hintType() == MessageType.HINT_SELECTMSG) {
                latestHint = h.data();
            }
            if (message instanceof DuelMessage.Prompt) {
                // A SELECTMSG hint right before a prompt is its title; stale hints must not carry over.
                hint = latestHint;
                latestHint = 0;
                continue;
            }
            for (int viewer = 0; viewer < 2; viewer++) {
                if (isBot(viewer)) {
                    continue;
                }
                if (message instanceof DuelMessage.Retry && waitingForHuman(viewer)) {
                    pendingLog.get(viewer).add("That choice isn't allowed, try again");
                    continue;
                }
                FieldEvent event = FieldEvent.of(message, viewer);
                if (event != null) {
                    pendingEvents.get(viewer).add(event);
                }
                String line = new DuelLog(text, names, viewer, loc -> faceUpCodeAt(board, loc)).describe(message);
                if (line != null) {
                    pendingLog.get(viewer).add(line);
                }
            }
        }
    }

    private boolean waitingForHuman(int viewer) {
        DuelMessage.Prompt prompt = duel.pendingPrompt();
        return prompt != null && prompt.player() == viewer;
    }

    private static Integer faceUpCodeAt(Board board, Loc loc) {
        List<CardState> zone = switch (loc.location()) {
            case LOCATION_MZONE -> board.side(loc.controller()).monsters();
            case LOCATION_SZONE -> board.side(loc.controller()).spells();
            default -> null;
        };
        if (zone == null || loc.sequence() >= zone.size() || zone.get(loc.sequence()) == null) {
            return null;
        }
        CardState card = zone.get(loc.sequence());
        return (card.position() & POS_FACEUP) != 0 ? card.code() : 0;
    }

    private Map<Integer, DuelView> views(DuelMessage.Prompt prompt) {
        Board board = duel.board();
        String final0 = null, final1 = null;
        if (forfeitResult != null) {
            final0 = final1 = forfeitResult;
        } else if (result != null) {
            final0 = resultFor(0);
            final1 = resultFor(1);
        }
        Map<Integer, DuelView> views = new HashMap<>();
        for (int viewer = 0; viewer < 2; viewer++) {
            if (isBot(viewer)) {
                continue;
            }
            DuelMessage.Prompt mine = prompt != null && prompt.player() == viewer ? PromptCensor.forChooser(prompt)
                    : null;
            List<String> log = List.copyOf(pendingLog.get(viewer));
            pendingLog.get(viewer).clear();
            List<FieldEvent> events = List.copyOf(pendingEvents.get(viewer));
            pendingEvents.get(viewer).clear();
            views.put(viewer, new DuelView(viewer, names, board.viewedBy(viewer), log, events, mine,
                    mine != null ? hint : 0, viewer == 0 ? final0 : final1));
        }
        return views;
    }

    private String resultFor(int viewer) {
        if (result.player() == 2) {
            return "The duel is a draw";
        }
        return result.player() == viewer ? "You win!" : "You lose";
    }

    @Override
    public void close() {
        duel.close();
    }
}
