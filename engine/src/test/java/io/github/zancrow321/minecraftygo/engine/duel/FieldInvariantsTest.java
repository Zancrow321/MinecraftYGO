package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.ai.RandomLegalAgent;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.query.CardInfo;
import io.github.zancrow321.minecraftygo.engine.query.FieldState;
import io.github.zancrow321.minecraftygo.engine.query.FieldStateReader;
import io.github.zancrow321.minecraftygo.engine.query.PlayerField;
import io.github.zancrow321.minecraftygo.engine.testing.Duels;
import io.github.zancrow321.minecraftygo.engine.testing.TestEnvironment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Objects;
import java.util.stream.Stream;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-checks the query parser and the event parser against each other: every card stays owned by its player
 * somewhere, and life points tracked from LP events match the queried values.
 */
class FieldInvariantsTest {
	@ParameterizedTest
	@ValueSource(longs = {41, 42, 43, 44})
	void cardsAreConservedAndLifePointsMatchEvents(long seed) {
		DuelSetup setup = Duels.single(seed);
		int[] deckSizes = new int[2];
		for (int team = 0; team < 2; team++) {
			deckSizes[team] = setup.teams().get(team).getFirst().main().size() + setup.teams().get(team).getFirst().extra().size();
		}
		int[] trackedLp = {8000, 8000};
		RandomLegalAgent[] agents = {new RandomLegalAgent(seed, TestEnvironment.database()),
				new RandomLegalAgent(seed + 1, TestEnvironment.database())};
		int checks = 0;
		try (DuelSession session = Duels.start(setup, TestEnvironment.dataSource())) {
			while (true) {
				DuelSession.Step step = session.advance();
				boolean won = false;
				for (CoreMessage message : step.messages()) {
					if (message instanceof Event.LifePoints lp) {
						switch (lp.messageType()) {
							case MSG_DAMAGE, MSG_PAY_LPCOST -> trackedLp[lp.player()] = Math.max(0, trackedLp[lp.player()] - lp.amount());
							case MSG_RECOVER -> trackedLp[lp.player()] += lp.amount();
							case MSG_LPUPDATE -> trackedLp[lp.player()] = lp.amount();
							default -> throw new AssertionError(lp);
						}
					}
					won |= message instanceof Event.Win;
				}
				if (step.ended() || won) {
					break;
				}
				FieldState field = FieldStateReader.read(session.nativeDuel());
				for (int player = 0; player < 2; player++) {
					assertThat(field.player(player).lifePoints()).as("LP of %d", player).isEqualTo(trackedLp[player]);
					assertThat(ownedCards(field, player)).as("cards owned by %d", player).isEqualTo(deckSizes[player]);
				}
				checks++;
				session.respond(agents[step.prompt().player()].respond(step.prompt()));
			}
		}
		assertThat(checks).isGreaterThan(20);
	}

	private static int ownedCards(FieldState field, int owner) {
		int count = field.player(owner).deckCount();
		for (int side = 0; side < 2; side++) {
			PlayerField pf = field.player(side);
			count += (int) Stream.of(pf.monsterZones(), pf.spellZones(), pf.hand(), pf.graveyard(), pf.banished(), pf.extraDeck())
					.flatMap(java.util.List::stream)
					.filter(Objects::nonNull)
					.filter(card -> card.owner() == owner && !card.isType(TYPE_TOKEN))
					.count();
			count += overlayCount(pf, owner);
		}
		return count;
	}

	private static int overlayCount(PlayerField field, int owner) {
		return field.monsterZones().stream().filter(Objects::nonNull).filter(c -> c.owner() == owner)
				.mapToInt((CardInfo c) -> c.overlayCodes().size()).sum();
	}
}
