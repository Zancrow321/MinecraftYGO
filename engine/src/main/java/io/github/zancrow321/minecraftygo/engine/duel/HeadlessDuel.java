package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ai.DuelAgent;
import io.github.zancrow321.minecraftygo.engine.ai.SeatAgents;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.query.FieldStateReader;
import io.github.zancrow321.minecraftygo.engine.view.Visibility;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Runs a duel to completion with agents answering every prompt, without any presentation. Agents only see what their
 * team may see. Used for tests, fuzzing and bot-vs-bot simulations.
 */
public final class HeadlessDuel {
	private HeadlessDuel() {}

	/** Outcome; {@code winner} is 0/1, {@code PLAYER_NONE} (2) for a draw, or -1 if the response limit was hit. */
	public record Result(int winner, int reason, int responses, int retries, int turns, int tagSwaps, String streamHash) {
		public boolean finished() {
			return winner >= 0;
		}
	}

	public static Result play(DuelSession session, SeatAgents agents, TagRotation rotation, int maxResponses) {
		return play(session, agents, rotation, maxResponses, _ -> {});
	}

	/**
	 * @param rotation     tag rotation matching the setup (1 duelist per team for single duels)
	 * @param maxResponses safety limit; the duel is abandoned (winner -1) when exceeded
	 * @param tap          receives every unredacted message (for tests and diagnostics)
	 */
	public static Result play(DuelSession session, SeatAgents agents, TagRotation rotation, int maxResponses,
			Consumer<CoreMessage> tap) {
		int winner = -1;
		int reason = 0;
		int turns = 0;
		int tagSwaps = 0;
		while (true) {
			DuelSession.Step step = session.advance();
			for (CoreMessage message : step.messages()) {
				tap.accept(message);
				rotation.observe(message);
				for (int team = 0; team < 2; team++) {
					var visible = Visibility.redact(message, team);
					if (visible.isEmpty()) {
						continue;
					}
					for (DuelAgent agent : teamAgents(agents, rotation, team)) {
						agent.observe(visible.get());
					}
				}
				switch (message) {
					case Event.Win win -> {
						winner = win.player();
						reason = win.reason();
					}
					case Event.NewTurn _ -> turns++;
					case Event.TagSwap _ -> tagSwaps++;
					default -> {}
				}
			}
			if (step.ended() || winner >= 0 || session.responseCount() >= maxResponses) {
				break;
			}
			int team = step.prompt().player();
			DuelAgent agent = agents.agent(team, rotation.active(team));
			if (agent == null) {
				throw new IllegalStateException("No agent for team " + team + " duelist " + rotation.active(team));
			}
			agent.observeField(Visibility.redact(FieldStateReader.read(session.nativeDuel()), team));
			session.respond(agent.respond(step.prompt()));
		}
		return new Result(winner, reason, session.responseCount(), session.retries(), turns, tagSwaps,
				session.streamHash());
	}

	private static Set<DuelAgent> teamAgents(SeatAgents agents, TagRotation rotation, int team) {
		Set<DuelAgent> result = new LinkedHashSet<>();
		for (int duelist = 0; duelist < rotation.duelists(team); duelist++) {
			DuelAgent agent = agents.agent(team, duelist);
			if (agent != null) {
				result.add(agent);
			}
		}
		return result;
	}

	/** Convenience for a single duel between two agents. */
	public static Result play(DuelSession session, DuelAgent first, DuelAgent second, int maxResponses) {
		return play(session, SeatAgents.of(first, second), new TagRotation(1, 1), maxResponses);
	}
}
