package io.github.zancrow321.jadm.engine.ai;

import io.github.zancrow321.jadm.engine.duel.Board;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;

/**
 * Answers prompts for a seat no person sits in.
 */
public interface Responder {
    /**
     * @param board   the board as this seat may see it
     * @param attempt 0 for the first answer, then 1, 2... after each MSG_RETRY for the same prompt
     */
    byte[] respond(DuelMessage.Prompt prompt, Board board, int attempt);

    /**
     * A message of the duel as {@code team} may see it, in order and before the prompt it leads to. Prompts are not
     * passed here. Responders that only look at the board can ignore it.
     */
    default void observe(int team, DuelMessage message) {
    }
}
