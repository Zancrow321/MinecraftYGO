package io.github.zancrow321.minecraftygo.engine.ai;

import io.github.zancrow321.minecraftygo.engine.duel.Board;
import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage;

/**
 * Answers prompts for a seat no person sits in.
 */
public interface Responder {
    /**
     * @param board   the board as this seat may see it
     * @param attempt 0 for the first answer, then 1, 2... after each MSG_RETRY for the same prompt
     */
    byte[] respond(DuelMessage.Prompt prompt, Board board, int attempt);
}
