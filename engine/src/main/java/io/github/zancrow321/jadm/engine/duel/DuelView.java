package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.protocol.DuelMessage;

import java.util.List;

/**
 * Everything one player's client needs to draw the duel: their censored board, new commentary lines, and the
 * prompt waiting for them (if any).
 *
 * @param you    which player (0 or 1) the receiver is
 * @param log    commentary lines since the previous view
 * @param events field events since the previous view, for animation
 * @param prompt the prompt this player must answer, already censored, or {@code null}
 * @param hint   the SELECTMSG hint for the prompt, or 0
 * @param result a final message once the duel is over, else {@code null}
 * @param bosses per team, the boss monsters its duelists marked in their decks (original passcodes; empty for none)
 */
public record DuelView(int you, List<String> names, Board board, List<String> log, List<FieldEvent> events,
                       DuelMessage.Prompt prompt, long hint, String result, List<List<Integer>> bosses) {
    /** The boss monsters of {@code team}'s decks, empty if it marked none (or the view predates them). */
    public List<Integer> bossesOf(int team) {
        return bosses == null || team < 0 || team >= bosses.size() || bosses.get(team) == null ? List.of()
                : bosses.get(team);
    }
}
