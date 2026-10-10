package io.github.zancrow321.jadm.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A duel ended, after the mod's own rewards and before everyone sees the result screen. Lines added with
 * {@link #addNote} show there under the rewards.
 */
public final class DuelEndEvent extends Event {
    private final DuelInfo duel;
    private final int winningTeam;
    private final boolean finished;
    private final int turns;
    private final int[] lifePoints;
    private final Map<UUID, List<String>> notes = new HashMap<>();

    /**
     * @param winningTeam 0 or 1, 2 for a draw, -1 if the duel broke off
     * @param finished    whether the duel was played to the end (a surrender counts) rather than cut short by an error
     */
    public DuelEndEvent(DuelInfo duel, int winningTeam, boolean finished, int turns, int[] lifePoints) {
        this.duel = duel;
        this.winningTeam = winningTeam;
        this.finished = finished;
        this.turns = turns;
        this.lifePoints = lifePoints.clone();
    }

    public DuelInfo getDuel() {
        return duel;
    }

    /** 0 or 1, 2 for a draw, -1 if the duel broke off. */
    public int getWinningTeam() {
        return winningTeam;
    }

    public boolean isDraw() {
        return winningTeam == 2;
    }

    /** Whether one team won. */
    public boolean isDecided() {
        return winningTeam == 0 || winningTeam == 1;
    }

    public boolean isFinished() {
        return finished;
    }

    public int getTurns() {
        return turns;
    }

    /** {@code team}'s life points at the end. */
    public int getLifePoints(int team) {
        return lifePoints[team];
    }

    /** The online people who won; empty for a draw. */
    public List<ServerPlayer> getWinners() {
        return isDecided() ? duel.players(winningTeam) : List.of();
    }

    /** The online people who lost; empty for a draw. */
    public List<ServerPlayer> getLosers() {
        return isDecided() ? duel.players(1 - winningTeam) : List.of();
    }

    public boolean isWinner(ServerPlayer player) {
        return isDecided() && duel.teamOf(player.getUUID()) == winningTeam;
    }

    /** The names of the winning team, or an empty list for a draw. */
    public List<String> getWinnerNames() {
        return isDecided() ? duel.names(winningTeam) : List.of();
    }

    /** Adds a line to {@code player}'s result screen, under the rewards, e.g. "+50 DP: first win of the day". */
    public void addNote(ServerPlayer player, String line) {
        notes.computeIfAbsent(player.getUUID(), u -> new ArrayList<>()).add(line);
    }

    public List<String> notes(UUID player) {
        return notes.getOrDefault(player, List.of());
    }
}
