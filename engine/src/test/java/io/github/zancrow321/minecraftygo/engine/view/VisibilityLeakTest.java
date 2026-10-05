package io.github.zancrow321.minecraftygo.engine.view;

import io.github.zancrow321.minecraftygo.engine.ai.RandomLegalAgent;
import io.github.zancrow321.minecraftygo.engine.duel.DuelSession;
import io.github.zancrow321.minecraftygo.engine.duel.EngineDataSource;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.query.FieldState;
import io.github.zancrow321.minecraftygo.engine.query.FieldStateReader;
import io.github.zancrow321.minecraftygo.engine.testing.Duels;
import io.github.zancrow321.minecraftygo.engine.testing.LeakOracle;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Leak oracle: plays random duels and checks every redacted message and field snapshot against hidden-information
 * invariants stated independently of {@link Visibility}'s implementation.
 */
class VisibilityLeakTest {
	private static final int[] VIEWERS = {0, 1, Visibility.SPECTATOR};

	@ParameterizedTest
	@ValueSource(longs = {31, 32, 33, 34, 35, 36})
	void noHiddenInformationReachesTheWrongViewer(long seed) {
		EngineDataSource data = TestEnvironment.dataSource();
		RandomLegalAgent[] agents = {new RandomLegalAgent(seed, TestEnvironment.database()),
				new RandomLegalAgent(seed + 100, TestEnvironment.database())};
		int checkedMessages = 0;
		int checkedFields = 0;
		try (DuelSession session = Duels.start(Duels.single(seed), data)) {
			while (true) {
				DuelSession.Step step = session.advance();
				for (CoreMessage message : step.messages()) {
					for (int viewer : VIEWERS) {
						LeakOracle.checkMessage(message, viewer, Visibility.redact(message, viewer));
						checkedMessages++;
					}
				}
				if (step.ended() || step.messages().stream().anyMatch(m -> m instanceof Event.Win)) {
					break;
				}
				FieldState full = FieldStateReader.read(session.nativeDuel());
				for (int viewer : VIEWERS) {
					LeakOracle.checkField(full, viewer, Visibility.redact(full, viewer));
					checkedFields++;
				}
				session.respond(agents[step.prompt().player()].respond(step.prompt()));
			}
		}
		assertThat(checkedMessages).isGreaterThan(100);
		assertThat(checkedFields).isGreaterThan(30);
	}
}
