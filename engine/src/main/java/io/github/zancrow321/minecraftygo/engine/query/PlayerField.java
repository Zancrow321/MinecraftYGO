package io.github.zancrow321.minecraftygo.engine.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * One player's side of the field. Zone lists have one entry per zone ({@code null} = empty): 7 monster zones
 * (5-6 are Extra Monster Zones) and 8 spell/trap zones (5 = field, 6-7 = pendulum). Piles list their cards in core
 * order; the deck is only counted.
 */
public record PlayerField(int lifePoints, List<CardInfo> monsterZones, List<CardInfo> spellZones, List<CardInfo> hand,
		List<CardInfo> graveyard, List<CardInfo> banished, List<CardInfo> extraDeck, int deckCount) {

	public PlayerField {
		monsterZones = nullableCopy(monsterZones);
		spellZones = nullableCopy(spellZones);
		hand = List.copyOf(hand);
		graveyard = List.copyOf(graveyard);
		banished = List.copyOf(banished);
		extraDeck = List.copyOf(extraDeck);
	}

	private static List<CardInfo> nullableCopy(List<CardInfo> zones) {
		return Collections.unmodifiableList(new ArrayList<>(zones));
	}

	/** Applies {@code mapper} to every card (null zones stay null). */
	public PlayerField map(UnaryOperator<CardInfo> mapper) {
		return new PlayerField(lifePoints, mapZones(monsterZones, mapper), mapZones(spellZones, mapper),
				hand.stream().map(mapper).toList(), graveyard.stream().map(mapper).toList(),
				banished.stream().map(mapper).toList(), extraDeck.stream().map(mapper).toList(), deckCount);
	}

	private static List<CardInfo> mapZones(List<CardInfo> zones, UnaryOperator<CardInfo> mapper) {
		List<CardInfo> mapped = new ArrayList<>(zones.size());
		for (CardInfo card : zones) {
			mapped.add(card == null ? null : mapper.apply(card));
		}
		return mapped;
	}

	public int monsterCount() {
		return (int) monsterZones.stream().filter(c -> c != null).count();
	}
}
