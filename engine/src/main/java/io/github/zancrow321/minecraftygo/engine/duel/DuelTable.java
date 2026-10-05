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
 * A duel between two teams of one or more seats, each a person or a bot. With two seats on a team it is a tag duel:
 * the team's duelists take turns, and only the one playing answers prompts and sees the team's hand. Bots answer
 * their own prompts; for people the table produces a {@link DuelView} per seat and waits for {@link #respond}.
 * Everything a person receives is censored for them. Not thread-safe.
 */
public final class DuelTable implements AutoCloseable {
    /** Bot answers handled per call before returning, so a bot-vs-bot loop can't stall the caller. */
    private static final int BOT_STEPS_PER_CALL = 500;
    private static final int MAX_BOT_RETRIES = 500;

    /**
     * @param team 0 or 1; a team's seats play in the order they are listed
     * @param bot  answers for this seat, {@code null} for a person
     */
    public record Seat(int team, String name, RandomResponder bot) {
    }

    private final DuelController duel;
    private final DuelText text;
    private final List<Seat> seats;
    /** Team display names, e.g. "Alex & Steve". */
    private final List<String> names;
    /** Seat indices per team, in turn order. */
    private final List<List<Integer>> teams = List.of(new ArrayList<>(), new ArrayList<>());
    /** Which duelist of each team plays, as of the message being recorded. */
    private final int[] recordedActive = new int[2];
    private final List<List<String>> pendingLog = new ArrayList<>();
    private final List<List<FieldEvent>> pendingEvents = new ArrayList<>();
    private long hint;
    private int botRetries;
    private String forfeitResult;
    private int forfeitWinner = -1;
    private DuelMessage.Win result;

    /** A 1v1 duel. @param bots a responder per seat, {@code null} for a person */
    public DuelTable(DuelText text, ScriptProvider scripts, DuelSettings settings, Deck deck0, Deck deck1,
                     List<String> names, RandomResponder[] bots, DuelLogHandler log) {
        this(text, scripts, settings, List.of(new Seat(0, names.get(0), bots[0]), new Seat(1, names.get(1), bots[1])),
                List.of(deck0, deck1), log);
    }

    /** @param decks one per seat */
    public DuelTable(DuelText text, ScriptProvider scripts, DuelSettings settings, List<Seat> seats, List<Deck> decks,
                     DuelLogHandler log) {
        if (seats.size() != decks.size()) {
            throw new IllegalArgumentException("Need one deck per seat");
        }
        this.text = text;
        this.seats = List.copyOf(seats);
        List<List<Deck>> teamDecks = List.of(new ArrayList<>(), new ArrayList<>());
        for (int i = 0; i < seats.size(); i++) {
            teams.get(seats.get(i).team()).add(i);
            teamDecks.get(seats.get(i).team()).add(decks.get(i));
            pendingLog.add(new ArrayList<>());
            pendingEvents.add(new ArrayList<>());
        }
        List<String> teamNames = new ArrayList<>();
        for (List<Integer> team : teams) {
            teamNames.add(String.join(" & ", team.stream().map(i -> seats.get(i).name()).toList()));
        }
        this.names = List.copyOf(teamNames);
        this.duel = new DuelController(text.cards(), scripts, settings, teamDecks, log);
    }

    public List<Seat> seats() {
        return seats;
    }

    public boolean isBot(int seat) {
        return seats.get(seat).bot() != null;
    }

    public boolean finished() {
        return result != null || forfeitResult != null;
    }

    /** @return the winning team, 2 for a draw, or -1 while the duel runs */
    public int winner() {
        if (result != null) {
            return result.player();
        }
        return forfeitResult != null ? forfeitWinner : -1;
    }

    /** The seat playing for {@code team} right now. */
    public int activeSeat(int team) {
        return teams.get(team).get(duel.activeDuelist(team));
    }

    /** @return the seat that must answer next, or -1 */
    public int waitingFor() {
        DuelMessage.Prompt prompt = duel.pendingPrompt();
        return finished() || prompt == null ? -1 : activeSeat(prompt.player());
    }

    /** Runs the duel up to the first person's prompt. */
    public Map<Integer, DuelView> start() {
        return run(duel.advance());
    }

    /**
     * Applies a person's answer and runs on to the next person's prompt.
     *
     * @throws IllegalStateException if it isn't that seat's turn to answer
     */
    public Map<Integer, DuelView> respond(int seat, byte[] response) {
        if (waitingFor() != seat || isBot(seat)) {
            throw new IllegalStateException("Seat " + seat + " has nothing to answer");
        }
        duel.respond(response);
        return run(duel.advance());
    }

    /** Continues a bot-only stretch that {@link #BOT_STEPS_PER_CALL} cut short. */
    public Map<Integer, DuelView> pump() {
        int seat = waitingFor();
        if (seat < 0 || !isBot(seat)) {
            return Map.of();
        }
        return run(null);
    }

    /** Ends the duel with {@code seat}'s team losing. */
    public Map<Integer, DuelView> forfeit(int seat) {
        if (finished()) {
            return Map.of();
        }
        int team = seats.get(seat).team();
        for (int viewer = 0; viewer < seats.size(); viewer++) {
            pendingLog.get(viewer).add((viewer == seat ? "You" : seats.get(seat).name()) + " surrendered");
        }
        forfeitWinner = 1 - team;
        forfeitResult = names.get(1 - team) + " win" + (teams.get(1 - team).size() == 1 ? "s" : "") + " the duel";
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
            int seat = activeSeat(prompt.player());
            if (!isBot(seat)) {
                return views(prompt);
            }
            boolean retried = step != null && step.messages().stream().anyMatch(m -> m instanceof DuelMessage.Retry);
            botRetries = retried ? botRetries + 1 : 0;
            if (botRetries > MAX_BOT_RETRIES) {
                throw new IllegalStateException("Bot could not answer " + prompt);
            }
            duel.respond(seats.get(seat).bot().respond(prompt, botRetries));
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
            if (message instanceof DuelMessage.TagSwap swap) {
                List<Integer> team = teams.get(swap.player());
                recordedActive[swap.player()] = (recordedActive[swap.player()] + 1) % team.size();
                String next = seats.get(team.get(recordedActive[swap.player()])).name();
                for (int viewer = 0; viewer < seats.size(); viewer++) {
                    pendingLog.get(viewer).add(viewer == team.get(recordedActive[swap.player()])
                            ? "Your turn to duel for your team" : next + " takes over");
                }
                continue;
            }
            for (int viewer = 0; viewer < seats.size(); viewer++) {
                if (isBot(viewer)) {
                    continue;
                }
                if (message instanceof DuelMessage.Retry && waitingForHuman(viewer)) {
                    pendingLog.get(viewer).add("That choice isn't allowed, try again");
                    continue;
                }
                // A partner who isn't playing sees the duel like a spectator: no private card names.
                int team = seats.get(viewer).team();
                int as = teams.get(team).get(recordedActive[team]) == viewer ? team : -1;
                FieldEvent event = FieldEvent.of(message, as);
                if (event != null) {
                    pendingEvents.get(viewer).add(event);
                }
                String line = new DuelLog(text, names, as, loc -> faceUpCodeAt(board, loc)).describe(message);
                if (line != null) {
                    pendingLog.get(viewer).add(line);
                }
            }
        }
    }

    private boolean waitingForHuman(int seat) {
        return waitingFor() == seat;
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
        int waiting = prompt == null ? -1 : activeSeat(prompt.player());
        Map<Integer, DuelView> views = new HashMap<>();
        for (int seat = 0; seat < seats.size(); seat++) {
            if (isBot(seat)) {
                continue;
            }
            int team = seats.get(seat).team();
            String finalText = null;
            if (forfeitResult != null) {
                finalText = forfeitResult;
            } else if (result != null) {
                finalText = resultFor(team);
            }
            DuelMessage.Prompt mine = seat == waiting ? PromptCensor.forChooser(prompt) : null;
            List<String> log = List.copyOf(pendingLog.get(seat));
            pendingLog.get(seat).clear();
            List<FieldEvent> events = List.copyOf(pendingEvents.get(seat));
            pendingEvents.get(seat).clear();
            Board seen = board.viewedBy(team, activeSeat(team) == seat);
            views.put(seat, new DuelView(team, names, seen, log, events, mine, mine != null ? hint : 0, finalText));
        }
        return views;
    }

    private String resultFor(int team) {
        if (result.player() == 2) {
            return "The duel is a draw";
        }
        return result.player() == team ? "You win!" : "You lose";
    }

    @Override
    public void close() {
        duel.close();
    }
}
