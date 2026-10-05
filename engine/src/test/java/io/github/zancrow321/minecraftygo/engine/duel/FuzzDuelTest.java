package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ai.DuelAgent;
import io.github.zancrow321.minecraftygo.engine.ai.HeuristicAgent;
import io.github.zancrow321.minecraftygo.engine.ai.RandomLegalAgent;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.query.FieldState;
import io.github.zancrow321.minecraftygo.engine.query.FieldStateReader;
import io.github.zancrow321.minecraftygo.engine.testing.Duels;
import io.github.zancrow321.minecraftygo.engine.testing.LeakOracle;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import io.github.zancrow321.minecraftygo.engine.view.Visibility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Many duels with mixed bots, single and tag, all checked by the leak oracle. The count comes from
 * {@code -Pygo.fuzzDuels} (nightly CI runs 1000).
 */
class FuzzDuelTest {
	private static final int[] VIEWERS = {0, 1, Visibility.SPECTATOR};

	@Test
	void fuzz() {
		int duels = Integer.getInteger("ygo.fuzzDuels", 12);
		long baseSeed = Long.getLong("ygo.fuzzSeed", System.nanoTime());
		for (int i = 0; i < duels; i++) {
			long seed = baseSeed + i;
			try {
				runOne(seed, i % 4 == 3);
			} catch (AssertionError | RuntimeException e) {
				throw new AssertionError("fuzz duel failed, reproduce with -Pygo.fuzzSeed=" + seed + " -Pygo.fuzzDuels=1", e);
			}
		}
	}

	private static void runOne(long seed, boolean tag) {
		DuelSetup setup = tag ? Duels.tag(seed) : Duels.single(seed);
		int duelists = tag ? 2 : 1;
		DuelAgent[][] agents = new DuelAgent[2][duelists];
		for (int team = 0; team < 2; team++) {
			for (int duelist = 0; duelist < duelists; duelist++) {
				long agentSeed = seed * 7 + team * 3 + duelist;
				agents[team][duelist] = (agentSeed & 1) == 0
						? new RandomLegalAgent(agentSeed, TestEnvironment.database())
						: new HeuristicAgent(agentSeed, TestEnvironment.database());
			}
		}
		TagRotation rotation = new TagRotation(duelists, duelists);
		EngineDataSource data = TestEnvironment.dataSource();
		try (DuelSession session = Duels.start(setup, data)) {
			boolean over = false;
			while (!over) {
				DuelSession.Step step = session.advance();
				for (CoreMessage message : step.messages()) {
					assertThat(message).isNotInstanceOf(Event.Unparsed.class);
					rotation.observe(message);
					for (int viewer : VIEWERS) {
						var visible = Visibility.redact(message, viewer);
						LeakOracle.checkMessage(message, viewer, visible);
						if (viewer >= 0 && visible.isPresent()) {
							for (DuelAgent agent : agents[viewer]) {
								agent.observe(visible.get());
							}
						}
					}
					over |= message instanceof Event.Win;
				}
				over |= step.ended();
				if (over) {
					break;
				}
				assertThat(session.responseCount()).as("response limit").isLessThan(6000);
				FieldState full = FieldStateReader.read(session.nativeDuel());
				for (int viewer : VIEWERS) {
					LeakOracle.checkField(full, viewer, Visibility.redact(full, viewer));
				}
				int team = step.prompt().player();
				DuelAgent agent = agents[team][rotation.active(team)];
				agent.observeField(Visibility.redact(full, team));
				session.respond(agent.respond(step.prompt()));
			}
			assertThat(session.retries()).isZero();
		}
		assertThat(data.errors()).isEmpty();
	}
}
