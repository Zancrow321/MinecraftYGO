package io.github.zancrow321.minecraftygo.engine.deck;

import io.github.zancrow321.minecraftygo.engine.testing.SampleDecks;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeckValidatorTest {
	private final DeckValidator validator = new DeckValidator(TestEnvironment.database());

	@Test
	void sampleDecksAreLegal() {
		assertThat(validator.validate(SampleDecks.dragons())).isEmpty();
		assertThat(validator.validate(SampleDecks.magicians())).isEmpty();
	}

	@Test
	void reportsSizeCopiesAndPlacementProblems() {
		List<Integer> main = new ArrayList<>(SampleDecks.dragons().main());
		main.add(SampleDecks.BLUE_EYES_WHITE_DRAGON);
		main.add(SampleDecks.BLUE_EYES_WHITE_DRAGON); // 4 copies
		main.add(SampleDecks.THOUSAND_DRAGON);        // fusion in main deck
		Deck deck = new Deck(main, List.of(SampleDecks.DARK_HOLE), List.of());
		List<DeckProblem> problems = validator.validate(deck);
		assertThat(problems).extracting(DeckProblem::kind).contains(
				DeckProblem.Kind.TOO_MANY_COPIES, DeckProblem.Kind.EXTRA_DECK_CARD_IN_MAIN,
				DeckProblem.Kind.MAIN_DECK_CARD_IN_EXTRA);

		Deck small = Deck.of(main.subList(0, 20), List.of());
		assertThat(validator.validate(small)).extracting(DeckProblem::kind).contains(DeckProblem.Kind.MAIN_TOO_SMALL);
	}

	@Test
	void appliesBanlistAndPool() {
		DeckValidator strict = new DeckValidator(TestEnvironment.database(),
				new Banlist("test", Map.of(SampleDecks.RAIGEKI, 0)),
				code -> code != SampleDecks.CHANGE_OF_HEART);
		assertThat(strict.validate(SampleDecks.dragons())).extracting(DeckProblem::kind)
				.containsExactlyInAnyOrder(DeckProblem.Kind.TOO_MANY_COPIES, DeckProblem.Kind.NOT_IN_POOL);
		assertThat(Set.copyOf(strict.validate(SampleDecks.dragons()).stream().map(DeckProblem::code).toList()))
				.containsExactlyInAnyOrder(SampleDecks.RAIGEKI, SampleDecks.CHANGE_OF_HEART);
	}

	@Test
	void ydkRoundTrip() {
		Deck deck = new Deck(SampleDecks.magicians().main(), SampleDecks.magicians().extra(), List.of(SampleDecks.KURIBOH));
		String text = YdkCodec.format(deck, "MinecraftYGO");
		assertThat(text).startsWith("#created by MinecraftYGO").contains("#main", "#extra", "!side");
		assertThat(YdkCodec.parse(text)).isEqualTo(deck);
		assertThat(YdkCodec.parse("#main\r\n89631139\r\n\r\n#extra\r\n!side\r\n40640057\r\n"))
				.isEqualTo(new Deck(List.of(89631139), List.of(), List.of(40640057)));
	}
}
