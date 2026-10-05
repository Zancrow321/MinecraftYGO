package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ai.DuelAgent;
import io.github.zancrow321.minecraftygo.engine.ai.HeuristicAgent;
import io.github.zancrow321.minecraftygo.engine.ai.RandomLegalAgent;
import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.data.BundledData;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.engine.data.JsonCardDatabase;
import io.github.zancrow321.minecraftygo.engine.deck.Deck;
import io.github.zancrow321.minecraftygo.engine.deck.DeckValidator;
import io.github.zancrow321.minecraftygo.engine.ffi.NativeDuel;
import io.github.zancrow321.minecraftygo.engine.ffi.NewCard;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.script.ScriptProvider;
import io.github.zancrow321.minecraftygo.engine.testing.Duels;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** Every card of the pool through the real core, using exactly the data the mod ships (bundled JSON + scripts.zip). */
class PoolCardsTest {
	private static JsonCardDatabase cards;
	private static CardPool pool;
	private static ScriptProvider scripts;

	@BeforeAll
	static void load() {
		cards = BundledData.cards();
		pool = BundledData.pool();
		scripts = BundledData.scripts();
	}

	@Test
	void everyPoolCardScriptLoadsWithoutErrors() {
		EngineDataSource data = new EngineDataSource(cards, scripts);
		DuelSetup setup = Duels.single(1);
		try (NativeDuel duel = TestEnvironment.core().createDuel(setup.toOptions(), data)) {
			for (String bootstrap : List.of("constant.lua", "utility.lua")) {
				assertThat(duel.loadScript(bootstrap, scripts.read(bootstrap).orElseThrow())).isTrue();
			}
			for (CardPool.Entry entry : pool.entries()) {
				int location = entry.extraDeck() ? OcgConstants.LOCATION_EXTRA : OcgConstants.LOCATION_DECK;
				duel.newCard(new NewCard(0, 0, entry.code(), 0, location, 0, OcgConstants.POS_FACEDOWN_DEFENSE));
			}
			assertThat(duel.queryCount(0, OcgConstants.LOCATION_DECK) + duel.queryCount(0, OcgConstants.LOCATION_EXTRA))
					.isEqualTo(pool.size());
		}
		assertThat(data.errors()).as("script errors").isEmpty();
	}

	static Deck randomDeck(Random random) {
		List<CardPool.Entry> main = new ArrayList<>();
		List<CardPool.Entry> extra = new ArrayList<>();
		for (CardPool.Entry entry : pool.entries()) {
			(entry.extraDeck() ? extra : main).add(entry);
		}
		Map<Integer, Integer> copies = new HashMap<>();
		List<Integer> mainCodes = new ArrayList<>();
		while (mainCodes.size() < 40) {
			CardPool.Entry entry = main.get(random.nextInt(main.size()));
			int canonical = cards.find(entry.code()).orElseThrow().canonicalCode();
			if (copies.merge(canonical, 1, Integer::sum) <= DeckValidator.MAX_COPIES) {
				mainCodes.add(entry.code());
			}
		}
		List<Integer> extraCodes = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			extraCodes.add(extra.get(random.nextInt(extra.size())).code());
		}
		return Deck.of(mainCodes, extraCodes);
	}

	@Test
	void randomPoolDecksPlayCleanly() {
		int duels = Integer.getInteger("ygo.poolDuels", 30);
		long base = Long.getLong("ygo.fuzzSeed", 9000);
		DeckValidator validator = new DeckValidator(cards, io.github.zancrow321.minecraftygo.engine.deck.Banlist.NONE, pool::contains);
		for (int i = 0; i < duels; i++) {
			long seed = base + i;
			Random random = new Random(seed);
			Deck first = randomDeck(random);
			Deck second = randomDeck(random);
			assertThat(validator.validate(first)).isEmpty();
			EngineDataSource data = new EngineDataSource(cards, scripts);
			try (DuelSession session = DuelSession.start(TestEnvironment.core(), Duels.single(seed, first, second), data, scripts)) {
				DuelAgent a = new HeuristicAgent(seed, cards);
				DuelAgent b = new RandomLegalAgent(seed + 1, cards);
				List<Object> unparsed = new ArrayList<>();
				HeadlessDuel.Result result = HeadlessDuel.play(session, (team, _) -> team == 0 ? a : b,
						new TagRotation(1, 1), 6000, m -> {
							if (m instanceof Event.Unparsed u) {
								unparsed.add(u);
							}
						});
				assertThat(result.finished()).as("seed %d: %s", seed, result).isTrue();
				assertThat(result.retries()).as("seed %d retries", seed).isZero();
				assertThat(unparsed).as("seed %d", seed).isEmpty();
			}
			assertThat(data.errors()).as("seed %d script errors", seed).isEmpty();
		}
	}
}
