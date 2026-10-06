package io.github.zancrow321.minecraftygo.duel;

import io.github.zancrow321.minecraftygo.engine.Ruleset;
import io.github.zancrow321.minecraftygo.engine.data.Banlist;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.entity.DuelistNpc;

import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * An organized 1v1 duel, such as a tournament game: who plays with which deck, under which rules, and who hears
 * how it ended. Both people must stand on the podiums of one duel arena.
 *
 * @param seats     team 0 first, then team 1
 * @param firstTeam the team that goes first, or -1 for a coin toss
 * @param npc       an NPC standing in for a bot seat, only for show (it walks onto the free podium), or {@code null}
 * @param onEnd     told the winning team (0 or 1), 2 for a draw, or -1 if the duel broke off with an error
 */
public record MatchSetup(List<Seat> seats, Rules rules, int firstTeam, DuelistNpc npc, IntConsumer onEnd) {
    /**
     * @param player {@code null} for a bot
     * @param deck   the deck to play, or {@code null} for the person's deck box (or a starter deck)
     */
    public record Seat(UUID player, String name, Deck deck) {
    }

    /**
     * Rules that differ from the server's; {@code null} (or 0, or -1 for the time limit) keeps the server's.
     *
     * @param turnTimeLimit seconds per person and turn, 0 for none
     * @param maxTurns      the duel ends after this many turns, on life points; 0 for no limit
     */
    public record Rules(int lifePoints, Ruleset ruleset, Banlist banlist, int turnTimeLimit, int maxTurns) {
        public static final Rules SERVER = new Rules(0, null, null, -1, 0);
    }
}
