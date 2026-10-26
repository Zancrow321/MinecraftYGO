package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.DuelLogHandler;
import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgCore;
import io.github.zancrow321.jadm.engine.OcgDuel;
import io.github.zancrow321.jadm.engine.ScriptProvider;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.protocol.CardState;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.protocol.MessageDecoder;
import io.github.zancrow321.jadm.engine.protocol.QueryDecoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * Runs one duel, 1v1 or tag (several duelists per team taking turns): sets it up from the decks, drives the core until a player must choose, and accepts their
 * responses. Not thread-safe; the owner calls it from one thread.
 */
public final class DuelController implements AutoCloseable {
    private static final String[] BASE_SCRIPTS = {"constant.lua", "utility.lua"};

    private final OcgDuel duel;
    private final SummonStyle.Tracker summonStyles;
    private DuelMessage.Prompt pendingPrompt;
    private DuelMessage.Win result;
    private int turn;
    private int turnPlayer;
    private int phase;
    private final int[] duelists = new int[2];
    private final int[] activeDuelist = new int[2];
    private final CardDatabase cards;
    private final List<List<Deck>> teams;
    /** Per team and duelist, whether their foils are on their cards yet (see {@link #showFoils}). */
    private final boolean[][] foilsShown = new boolean[2][];

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
        this.cards = cards;
        this.teams = List.copyOf(teams);
        this.summonStyles = new SummonStyle.Tracker(cards);
        try {
            for (String base : BASE_SCRIPTS) {
                byte[] script = scripts.read(base);
                if (script == null || !duel.loadScript(base, script)) {
                    throw new IllegalStateException("Could not load " + base);
                }
            }
            SplittableRandom random = new SplittableRandom(mix(settings.seed()));
            for (int team = 0; team < 2; team++) {
                duelists[team] = teams.get(team).size();
                foilsShown[team] = new boolean[duelists[team]];
                for (int duelist = 0; duelist < duelists[team]; duelist++) {
                    addDeck(team, duelist, teams.get(team).get(duelist), random);
                }
            }
            showFoils();
            runSetupScript();
            duel.start();
        } catch (RuntimeException e) {
            duel.close();
            throw e;
        }
    }

    /**
     * For development: the Lua file named by the system property {@code jadm.duelSetup} runs before the
     * duel starts, so it can put cards on the field with {@code Debug.AddCard} (a card added to an occupied monster
     * zone becomes an Xyz material).
     */
    private void runSetupScript() {
        String path = System.getProperty("jadm.duelSetup");
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

    /**
     * The core draws the opening hands straight off the top of the deck as the cards were added and never shuffles
     * at the start itself (that is the host's job, as in EDOPro), so the main deck is shuffled here first.
     */
    private void addDeck(int team, int duelist, Deck deck, SplittableRandom random) {
        List<Integer> main = new ArrayList<>(deck.main());
        Collections.shuffle(main, random);
        for (int code : main) {
            duel.newCard(team, duelist, code, team, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
        }
        for (int code : deck.extra()) {
            duel.newCard(team, duelist, code, team, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE);
        }
    }

    /**
     * Marks each foil copy in the decks of the duelists playing now with its rarity, so the board shows it. The core
     * keeps a number per card, its cover (a Skill card's sleeve, otherwise unused), and reports it with the card
     * wherever it goes; copies of a card play the same, so which of them gets which rarity doesn't matter. A tag
     * partner's cards only reach the field once they take over, so theirs are marked then, still in hand and deck.
     */
    private void showFoils() {
        for (int team = 0; team < 2; team++) {
            int duelist = activeDuelist[team];
            if (foilsShown[team][duelist]) {
                continue;
            }
            foilsShown[team][duelist] = true;
            // By the code the core reports for the card: an alternate artwork's is its original's.
            Map<Integer, List<Integer>> covers = new TreeMap<>();
            teams.get(team).get(duelist).foils().forEach((code, rarities) -> rarities.forEach(rarity ->
                    covers.computeIfAbsent(cards.original(code), c -> new ArrayList<>()).add(rarity.ordinal())));
            if (covers.isEmpty()) {
                continue;
            }
            StringBuilder lua = new StringBuilder("local f={");
            covers.forEach((code, list) -> lua.append('[').append(code).append("]={")
                    .append(list.stream().map(String::valueOf).collect(Collectors.joining(","))).append("},"));
            lua.append("}\n")
                    .append("for c in Duel.GetFieldGroup(").append(team)
                    .append(",LOCATION_DECK|LOCATION_EXTRA|LOCATION_HAND,0):Iter() do\n")
                    .append(" local l=f[c:GetOriginalCode()]\n")
                    .append(" if l and #l>0 and c:Cover()==0 then c:Cover(table.remove(l)) end\n")
                    .append("end\n");
            // A failed script only costs the foils, never the duel.
            duel.loadScript("jadm_foils.lua", lua.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Folds the duel's four seed words into one, so the same seed always gives the same deck order. */
    private static long mix(long[] seed) {
        long mixed = 0;
        for (long word : seed) {
            mixed = mixed * 0x9E3779B97F4A7C15L + word;
        }
        return mixed;
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
            for (DuelMessage decoded : MessageDecoder.decodeAll(duel.getMessage())) {
                DuelMessage message = summonStyles.annotate(decoded);
                messages.add(message);
                track(message);
            }
            showFoils();
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
        summonStyles.responded(pendingPrompt, response);
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

    /** The cards in one of a player's locations, in sequence order (for a deck, the bottom card first). */
    List<CardState> query(int player, int location) {
        return QueryDecoder.decodeLocation(duel.queryLocation(player, location, QueryDecoder.STANDARD_FLAGS));
    }

    @Override
    public void close() {
        duel.close();
    }
}
