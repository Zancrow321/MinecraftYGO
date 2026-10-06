package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.DuelLogHandler;
import io.github.zancrow321.minecraftygo.engine.DuelSettings;
import io.github.zancrow321.minecraftygo.engine.OcgCore;
import io.github.zancrow321.minecraftygo.engine.OcgDuel;
import io.github.zancrow321.minecraftygo.engine.ScriptProvider;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.protocol.CardState;
import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage;
import io.github.zancrow321.minecraftygo.engine.protocol.MessageDecoder;
import io.github.zancrow321.minecraftygo.engine.protocol.QueryDecoder;

import java.util.ArrayList;
import java.util.List;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * Runs one duel, 1v1 or tag (several duelists per team taking turns): sets it up from the decks, drives the core until a player must choose, and accepts their
 * responses. Not thread-safe; the owner calls it from one thread.
 */
public final class DuelController implements AutoCloseable {
    private static final String[] BASE_SCRIPTS = {"constant.lua", "utility.lua"};

    private final OcgDuel duel;
    private DuelMessage.Prompt pendingPrompt;
    private DuelMessage.Win result;
    private int turn;
    private int turnPlayer;
    private int phase;
    private final int[] duelists = new int[2];
    private final int[] activeDuelist = new int[2];

    public DuelController(CardDatabase cards, ScriptProvider scripts, DuelSettings settings, Deck deck0, Deck deck1,
                          DuelLogHandler log) {
        this(cards, scripts, settings, List.of(List.of(deck0), List.of(deck1)), log);
    }

    /**
     * @param teams each team's decks, one per duelist in turn order; a team with two decks plays tag
     */
    public DuelController(CardDatabase cards, ScriptProvider scripts, DuelSettings settings, List<List<Deck>> teams,
                          DuelLogHandler log) {
        if (teams.size() != 2 || teams.stream().anyMatch(List::isEmpty)) {
            throw new IllegalArgumentException("Need two teams with at least one deck each");
        }
        this.duel = OcgCore.get().createDuel(settings, cards, scripts, log);
        try {
            for (String base : BASE_SCRIPTS) {
                byte[] script = scripts.read(base);
                if (script == null || !duel.loadScript(base, script)) {
                    throw new IllegalStateException("Could not load " + base);
                }
            }
            for (int team = 0; team < 2; team++) {
                duelists[team] = teams.get(team).size();
                for (int duelist = 0; duelist < duelists[team]; duelist++) {
                    addDeck(team, duelist, teams.get(team).get(duelist));
                }
            }
            runSetupScript();
            duel.start();
        } catch (RuntimeException e) {
            duel.close();
            throw e;
        }
    }

    /**
     * For development: the Lua file named by the system property {@code minecraftygo.duelSetup} runs before the
     * duel starts, so it can put cards on the field with {@code Debug.AddCard} (a card added to an occupied monster
     * zone becomes an Xyz material).
     */
    private void runSetupScript() {
        String path = System.getProperty("minecraftygo.duelSetup");
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            byte[] script = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path));
            if (!duel.loadScript("duel_setup.lua", script)) {
                throw new IllegalStateException("The duel setup script " + path + " failed");
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read the duel setup script " + path, e);
        }
    }

    private void addDeck(int team, int duelist, Deck deck) {
        for (int code : deck.main()) {
            duel.newCard(team, duelist, code, team, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
        }
        for (int code : deck.extra()) {
            duel.newCard(team, duelist, code, team, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE);
        }
    }

    /** Which of the team's duelists (in the order their decks were given) is playing now. */
    public int activeDuelist(int team) {
        return activeDuelist[team];
    }

    /** What happened since the last call, and what the duel is waiting for now. */
    public record Step(List<DuelMessage> messages, DuelMessage.Prompt prompt, DuelMessage.Win result) {
        public boolean finished() {
            return result != null;
        }
    }

    /**
     * Runs the core until it needs a player's answer or the duel ends.
     *
     * @throws IllegalStateException if a prompt is still waiting for {@link #respond}
     */
    public Step advance() {
        if (result != null) {
            return new Step(List.of(), null, result);
        }
        List<DuelMessage> messages = new ArrayList<>();
        while (true) {
            OcgDuel.Status status = duel.process();
            for (DuelMessage message : MessageDecoder.decodeAll(duel.getMessage())) {
                messages.add(message);
                track(message);
            }
            if (result != null || status == OcgDuel.Status.END) {
                pendingPrompt = null;
                return new Step(messages, null, result);
            }
            if (status == OcgDuel.Status.AWAITING) {
                if (pendingPrompt == null) {
                    throw new IllegalStateException("Core is waiting but sent no prompt");
                }
                return new Step(messages, pendingPrompt, null);
            }
        }
    }

    private void track(DuelMessage message) {
        switch (message) {
            case DuelMessage.Prompt prompt -> pendingPrompt = prompt;
            case DuelMessage.Win win -> result = win;
            case DuelMessage.NewTurn newTurn -> {
                turn++;
                turnPlayer = newTurn.player();
            }
            case DuelMessage.NewPhase newPhase -> phase = newPhase.phase();
            case DuelMessage.TagSwap swap -> activeDuelist[swap.player()] =
                    (activeDuelist[swap.player()] + 1) % duelists[swap.player()];
            // On MSG_RETRY the core does not resend the prompt, so pendingPrompt stays as it was.
            default -> {
            }
        }
    }

    /** The prompt currently waiting for an answer, or {@code null}. */
    public DuelMessage.Prompt pendingPrompt() {
        return pendingPrompt;
    }

    public void respond(byte[] response) {
        if (pendingPrompt == null) {
            throw new IllegalStateException("No prompt is waiting for a response");
        }
        duel.setResponse(response);
    }

    public int turn() {
        return turn;
    }

    /** The full, uncensored board. Use {@link Board#viewedBy(int)} before showing it to a player. */
    public Board board() {
        FieldInfo field = FieldInfo.parse(duel.queryField());
        List<Board.Side> sides = new ArrayList<>(2);
        for (int player = 0; player < 2; player++) {
            sides.add(new Board.Side(field.lifePoints[player], field.deckCounts[player],
                    query(player, LOCATION_HAND), query(player, LOCATION_MZONE), query(player, LOCATION_SZONE),
                    query(player, LOCATION_GRAVE), query(player, LOCATION_REMOVED), query(player, LOCATION_EXTRA)));
        }
        return new Board(turn, turnPlayer, phase, List.copyOf(sides), field.options);
    }

    private List<CardState> query(int player, int location) {
        return QueryDecoder.decodeLocation(duel.queryLocation(player, location, QueryDecoder.STANDARD_FLAGS));
    }

    @Override
    public void close() {
        duel.close();
    }
}
