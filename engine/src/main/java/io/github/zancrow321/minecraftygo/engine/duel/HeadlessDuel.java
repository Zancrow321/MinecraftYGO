package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ai.DuelAgent;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;

import java.util.List;
import java.util.function.IntFunction;

/**
 * Runs a duel to completion with agents answering every prompt, without any presentation. Used for tests, fuzzing
 * and bot-vs-bot simulations.
 */
public final class HeadlessDuel {
	private HeadlessDuel() {}

	/** Outcome; {@code winner} is 0/1, {@code PLAYER_NONE} (2) for a draw, or -1 if the response limit was hit. */
	public record Result(int winner, int reason, int responses, int retries, int turns, String streamHash) {
		public boolean finished() {
			return winner >= 0;
		}
	}

	/**
	 * @param agentForPlayer agent answering prompts addressed to core player 0/1
	 * @param maxResponses   safety limit; the duel is abandoned (winner -1) when exceeded
	 */
	public static Result play(DuelSession session, IntFunction<DuelAgent> agentForPlayer, int maxResponses) {
		int winner = -1;
		int reason = 0;
		int turns = 0;
		DuelAgent[] agents = {agentForPlayer.apply(0), agentForPlayer.apply(1)};
		while (true) {
			DuelSession.Step step = session.advance();
			for (CoreMessage message : step.messages()) {
				agents[0].observe(message);
				if (agents[1] != agents[0]) {
					agents[1].observe(message);
				}
				switch (message) {
					case Event.Win win -> {
						winner = win.player();
						reason = win.reason();
					}
					case Event.NewTurn _ -> turns++;
					default -> {}
				}
			}
			if (step.ended() || winner >= 0) {
				break;
			}
			if (session.responseCount() >= maxResponses) {
				break;
			}
			DuelAgent agent = agents[step.prompt().player()];
			session.respond(agent.respond(step.prompt()));
		}
		return new Result(winner, reason, session.responseCount(), session.retries(), turns, session.streamHash());
	}

	/** Convenience for two agents. */
	public static Result play(DuelSession session, DuelAgent first, DuelAgent second, int maxResponses) {
		List<DuelAgent> agents = List.of(first, second);
		return play(session, agents::get, maxResponses);
	}
}
