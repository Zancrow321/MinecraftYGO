package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ai.RandomLegalAgent;
import io.github.zancrow321.minecraftygo.engine.data.GoatVariantResolver;
import io.github.zancrow321.minecraftygo.engine.deck.Deck;
import io.github.zancrow321.minecraftygo.engine.testing.Duels;
import io.github.zancrow321.minecraftygo.engine.testing.SampleDecks;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoatFormatTest {
	@Test
	void resolverMapsOriginalsToGoatVariants() {
		GoatVariantResolver resolver = GoatVariantResolver.fromDatabase(TestEnvironment.database());
		assertThat(resolver.size()).isGreaterThan(150);
		int goatSangan = resolver.toGoat(SampleDecks.SANGAN);
		assertThat(goatSangan).isBetween(GoatVariantResolver.FIRST_GOAT_CODE, GoatVariantResolver.LAST_GOAT_CODE);
		assertThat(resolver.original(goatSangan)).isEqualTo(SampleDecks.SANGAN);
		assertThat(resolver.toGoat(SampleDecks.BLUE_EYES_WHITE_DRAGON)).isEqualTo(SampleDecks.BLUE_EYES_WHITE_DRAGON);
	}

	@Test
	void duelWithGoatVariantsRuns() {
		GoatVariantResolver resolver = GoatVariantResolver.fromDatabase(TestEnvironment.database());
		Deck first = resolver.toGoat(SampleDecks.dragons());
		Deck second = resolver.toGoat(SampleDecks.magicians());
		assertThat(first.main()).contains(resolver.toGoat(SampleDecks.SANGAN));
		EngineDataSource data = TestEnvironment.dataSource();
		try (DuelSession session = Duels.start(Duels.single(61, first, second), data)) {
			HeadlessDuel.Result result = HeadlessDuel.play(session, new RandomLegalAgent(1, TestEnvironment.database()),
					new RandomLegalAgent(2, TestEnvironment.database()), 3000);
			assertThat(result.finished()).isTrue();
			assertThat(result.retries()).isZero();
		}
		assertThat(data.errors()).isEmpty();
	}
}
