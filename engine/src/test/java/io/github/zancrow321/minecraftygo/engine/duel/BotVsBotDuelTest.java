package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ai.RandomLegalAgent;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.testing.Duels;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Complete duels between random bots through the real core and scripts. */
class BotVsBotDuelTest {
	private static final int MAX_RESPONSES = 3000;

	private static HeadlessDuel.Result play(long seed, List<CoreMessage> tap) {
		EngineDataSource data = TestEnvironment.dataSource();
		try (DuelSession session = Duels.start(Duels.single(seed), data)) {
			RandomLegalAgent[] agents = {new RandomLegalAgent(seed * 2, TestEnvironment.database()),
					new RandomLegalAgent(seed * 2 + 1, TestEnvironment.database())};
			HeadlessDuel.Result result = HeadlessDuel.play(session, (team, _) -> agents[team],
					new TagRotation(1, 1), MAX_RESPONSES, tap::add);
			assertThat(data.errors()).as("core/script errors").isEmpty();
			return result;
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8})
	void randomBotsFinishADuel(long seed) {
		List<CoreMessage> messages = new ArrayList<>();
		HeadlessDuel.Result result = play(seed, messages);
		assertThat(result.finished()).as("duel finished within %d responses: %s", MAX_RESPONSES, result).isTrue();
		assertThat(result.winner()).isBetween(0, 2);
		assertThat(result.retries()).as("rejected responses").isZero();
		assertThat(result.turns()).isPositive();
		assertThat(messages).as("every message type has a parser")
				.noneMatch(m -> m instanceof Event.Unparsed);
	}

	@ParameterizedTest
	@ValueSource(longs = {11, 12})
	void sameSeedReplaysIdentically(long seed) {
		HeadlessDuel.Result first = play(seed, new ArrayList<>());
		HeadlessDuel.Result second = play(seed, new ArrayList<>());
		assertThat(second.streamHash()).isEqualTo(first.streamHash());
		assertThat(second.responses()).isEqualTo(first.responses());
	}

	@Test
	void recordedReplayReproducesTheStream() {
		DuelSetup setup = Duels.single(21);
		EngineDataSource data = TestEnvironment.dataSource();
		DuelReplay replay;
		try (DuelSession session = Duels.start(setup, data)) {
			HeadlessDuel.play(session, new RandomLegalAgent(1, TestEnvironment.database()),
					new RandomLegalAgent(2, TestEnvironment.database()), MAX_RESPONSES);
			replay = DuelReplay.of(setup, session);
		}
		assertThat(replay.responses()).isNotEmpty();
		assertThat(replay.replay(TestEnvironment.core(), TestEnvironment.dataSource(), TestEnvironment.scripts()))
				.isEqualTo(replay.streamHash());
	}
}
