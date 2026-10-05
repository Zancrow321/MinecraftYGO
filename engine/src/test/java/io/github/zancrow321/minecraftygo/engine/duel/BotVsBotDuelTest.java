package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ai.RandomLegalAgent;
import io.github.zancrow321.minecraftygo.engine.testing.SampleDecks;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Milestone M1 acceptance: complete duels between random bots through the real core and scripts. */
class BotVsBotDuelTest {
	private static final int MAX_RESPONSES = 2000;

	private static HeadlessDuel.Result play(long seed) {
		long[] duelSeed = {seed, seed * 31 + 7, seed ^ 0x5DEECE66DL, 42};
		DuelSetup setup = DuelSetup.single(duelSeed, SampleDecks.dragons(), SampleDecks.magicians());
		EngineDataSource data = TestEnvironment.dataSource();
		try (DuelSession session = DuelSession.start(TestEnvironment.core(), setup, data, TestEnvironment.scripts())) {
			HeadlessDuel.Result result = HeadlessDuel.play(session,
					new RandomLegalAgent(seed, TestEnvironment.database()),
					new RandomLegalAgent(seed + 1, TestEnvironment.database()),
					MAX_RESPONSES);
			assertThat(data.errors()).as("core/script errors").isEmpty();
			return result;
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {1, 2, 3, 4, 5})
	void randomBotsFinishADuel(long seed) {
		HeadlessDuel.Result result = play(seed);
		System.out.println("seed " + seed + ": " + result);
		assertThat(result.finished()).as("duel finished within %d responses: %s", MAX_RESPONSES, result).isTrue();
		assertThat(result.winner()).isBetween(0, 2);
		assertThat(result.retries()).as("rejected responses").isZero();
		assertThat(result.turns()).isPositive();
	}

	@ParameterizedTest
	@ValueSource(longs = {11, 12})
	void sameSeedReplaysIdentically(long seed) {
		HeadlessDuel.Result first = play(seed);
		HeadlessDuel.Result second = play(seed);
		assertThat(second.streamHash()).isEqualTo(first.streamHash());
		assertThat(second.responses()).isEqualTo(first.responses());
	}
}
