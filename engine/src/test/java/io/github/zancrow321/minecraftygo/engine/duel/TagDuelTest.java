package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ai.DuelAgent;
import io.github.zancrow321.minecraftygo.engine.ai.RandomLegalAgent;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse;
import io.github.zancrow321.minecraftygo.engine.testing.Duels;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 2v2 tag duels: the core rotates duelists per team and prompts go to the active duelist. */
class TagDuelTest {
	private static final class CountingAgent implements DuelAgent {
		private final RandomLegalAgent delegate;
		private int answered;

		CountingAgent(long seed) {
			delegate = new RandomLegalAgent(seed, TestEnvironment.database());
		}

		@Override
		public PromptResponse respond(Prompt prompt) {
			answered++;
			return delegate.respond(prompt);
		}

		@Override
		public void observe(io.github.zancrow321.minecraftygo.engine.message.CoreMessage message) {
			delegate.observe(message);
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {51, 52, 53})
	void tagDuelRotatesDuelists(long seed) {
		CountingAgent[][] agents = new CountingAgent[2][2];
		for (int team = 0; team < 2; team++) {
			for (int duelist = 0; duelist < 2; duelist++) {
				agents[team][duelist] = new CountingAgent(seed * 10 + team * 2 + duelist);
			}
		}
		EngineDataSource data = TestEnvironment.dataSource();
		HeadlessDuel.Result result;
		try (DuelSession session = Duels.start(Duels.tag(seed), data)) {
			result = HeadlessDuel.play(session, (team, duelist) -> agents[team][duelist], new TagRotation(2, 2), 4000);
		}
		System.out.println("tag seed " + seed + ": " + result + " answered " + agents[0][0].answered + "/"
				+ agents[0][1].answered + "/" + agents[1][0].answered + "/" + agents[1][1].answered);
		assertThat(data.errors()).isEmpty();
		assertThat(result.finished()).as("%s", result).isTrue();
		assertThat(result.retries()).isZero();
		if (result.turns() >= 4) {
			assertThat(result.tagSwaps()).isPositive();
			for (int team = 0; team < 2; team++) {
				for (int duelist = 0; duelist < 2; duelist++) {
					assertThat(agents[team][duelist].answered).as("prompts answered by %d/%d", team, duelist).isPositive();
				}
			}
		}
	}
}
