package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage;

import java.util.List;

/**
 * Everything one player's client needs to draw the duel: their censored board, new commentary lines, and the
 * prompt waiting for them (if any).
 *
 * @param you    which player (0 or 1) the receiver is
 * @param log    commentary lines since the previous view
 * @param prompt the prompt this player must answer, already censored, or {@code null}
 * @param hint   the SELECTMSG hint for the prompt, or 0
 * @param result a final message once the duel is over, else {@code null}
 */
public record DuelView(int you, List<String> names, Board board, List<String> log, DuelMessage.Prompt prompt,
                       long hint, String result) {
}
