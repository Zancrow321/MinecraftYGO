package io.github.zancrow321.minecraftygo.engine.ffi;

import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.data.CardData;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmOcgCoreTest {
	private static final long[] SEED = {1, 2, 3, 4};

	@Test
	void reportsApiVersion11() {
		OcgCore core = TestEnvironment.core();
		assertThat(core.versionMajor()).isEqualTo(11);
		assertThat(core.versionMinor()).isEqualTo(0);
	}

	@Test
	void createsAndDestroysDuel() {
		NativeDuel duel = TestEnvironment.core().createDuel(options(), TestEnvironment.dataSource());
		assertThat(duel.isClosed()).isFalse();
		duel.close();
		assertThat(duel.isClosed()).isTrue();
		duel.close();
		assertThatThrownBy(duel::start).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void loadScriptReportsLuaErrorsThroughLogHandler() {
		List<String> logs = new ArrayList<>();
		DuelDataSource source = new DuelDataSource() {
			@Override
			public Optional<CardData> card(int code) {
				return Optional.empty();
			}

			@Override
			public Optional<byte[]> script(String name) {
				return Optional.empty();
			}

			@Override
			public void log(LogType type, String message) {
				logs.add(type + ": " + message);
			}
		};
		try (NativeDuel duel = TestEnvironment.core().createDuel(options(), source)) {
			assertThat(duel.loadScript("ok.lua", "x = 1".getBytes(StandardCharsets.UTF_8))).isTrue();
			assertThat(duel.loadScript("broken.lua", "this is not lua".getBytes(StandardCharsets.UTF_8))).isFalse();
		}
		assertThat(logs).anyMatch(line -> line.startsWith("ERROR"));
	}

	@Test
	void callbackExceptionsSurfaceAsOcgException() {
		DuelDataSource failing = new DuelDataSource() {
			@Override
			public Optional<CardData> card(int code) {
				throw new IllegalStateException("boom " + code);
			}

			@Override
			public Optional<byte[]> script(String name) {
				return Optional.empty();
			}
		};
		try (NativeDuel duel = TestEnvironment.core().createDuel(options(), failing)) {
			assertThatThrownBy(() -> duel.newCard(new NewCard(0, 0, 89631139, 0, OcgConstants.LOCATION_DECK, 0,
					OcgConstants.POS_FACEDOWN_DEFENSE)))
					.isInstanceOf(OcgException.class)
					.hasRootCauseMessage("boom 89631139");
			// the duel stays poisoned: every further call reports the original failure
			assertThatThrownBy(duel::start).isInstanceOf(OcgException.class);
		}
	}

	@Test
	void queryCountSeesAddedCards() {
		try (NativeDuel duel = TestEnvironment.core().createDuel(options(), TestEnvironment.dataSource())) {
			for (int i = 0; i < 3; i++) {
				duel.newCard(new NewCard(0, 0, 89631139, 0, OcgConstants.LOCATION_DECK, 0, OcgConstants.POS_FACEDOWN_DEFENSE));
			}
			assertThat(duel.queryCount(0, OcgConstants.LOCATION_DECK)).isEqualTo(3);
			assertThat(duel.queryCount(1, OcgConstants.LOCATION_DECK)).isZero();
			assertThat(duel.queryField()).isNotEmpty();
		}
	}

	private static DuelOptions options() {
		return new DuelOptions(SEED, OcgConstants.DUEL_MODE_GOAT, DuelOptions.Team.STANDARD, DuelOptions.Team.STANDARD);
	}
}
