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
 * Runs one 1v1 duel: sets it up from two decks, drives the core until a player must choose, and accepts their
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

    public DuelController(CardDatabase cards, ScriptProvider scripts, DuelSettings settings, Deck deck0, Deck deck1,
                          DuelLogHandler log) {
        this.duel = OcgCore.get().createDuel(settings, cards, scripts, log);
        try {
            for (String base : BASE_SCRIPTS) {
                byte[] script = scripts.read(base);
                if (script == null || !duel.loadScript(base, script)) {
                    throw new IllegalStateException("Could not load " + base);
                }
            }
            addDeck(0, deck0);
            addDeck(1, deck1);
            duel.start();
        } catch (RuntimeException e) {
            duel.close();
            throw e;
        }
    }

    private void addDeck(int team, Deck deck) {
        for (int code : deck.main()) {
            duel.newCard(team, 0, code, team, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
        }
        for (int code : deck.extra()) {
            duel.newCard(team, 0, code, team, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE);
        }
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
        return new Board(turn, turnPlayer, phase, List.copyOf(sides));
    }

    private List<CardState> query(int player, int location) {
        return QueryDecoder.decodeLocation(duel.queryLocation(player, location, QueryDecoder.STANDARD_FLAGS));
    }

    @Override
    public void close() {
        duel.close();
    }
}
