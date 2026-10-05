package io.github.zancrow321.minecraftygo.engine.ai;

import io.github.zancrow321.minecraftygo.engine.duel.DuelSession;
import io.github.zancrow321.minecraftygo.engine.duel.EngineDataSource;
import io.github.zancrow321.minecraftygo.engine.duel.HeadlessDuel;
import io.github.zancrow321.minecraftygo.engine.duel.TagRotation;
import io.github.zancrow321.minecraftygo.engine.testing.Duels;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicAgentTest {
	private static final int DUELS = 40;

	@Test
	void heuristicBotBeatsRandomBotMostOfTheTime() {
		int heuristicWins = 0;
		for (int i = 0; i < DUELS; i++) {
			int heuristicTeam = i % 2;
			long seed = 1000 + i;
			EngineDataSource data = TestEnvironment.dataSource();
			try (DuelSession session = Duels.start(Duels.single(seed), data)) {
				DuelAgent heuristic = new HeuristicAgent(seed, TestEnvironment.database());
				DuelAgent random = new RandomLegalAgent(seed + 1, TestEnvironment.database());
				HeadlessDuel.Result result = HeadlessDuel.play(session,
						(team, _) -> team == heuristicTeam ? heuristic : random, new TagRotation(1, 1), 4000);
				assertThat(result.retries()).isZero();
				assertThat(result.finished()).as("%s", result).isTrue();
				if (result.winner() == heuristicTeam) {
					heuristicWins++;
				}
			}
			assertThat(data.errors()).isEmpty();
		}
		System.out.println("heuristic won " + heuristicWins + "/" + DUELS);
		assertThat(heuristicWins).isGreaterThanOrEqualTo(DUELS * 7 / 10);
	}
}
