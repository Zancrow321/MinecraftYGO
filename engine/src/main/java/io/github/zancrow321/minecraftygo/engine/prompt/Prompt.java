package io.github.zancrow321.minecraftygo.engine.prompt;

import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;

import java.util.List;

/**
 * A message that requires a response from {@link #player()} (the core's team index 0/1). Field layouts follow
 * ygopro-core {@code playerop.cpp}.
 */
public sealed interface Prompt extends CoreMessage {
	int player();

	/** {@code MSG_SELECT_IDLECMD}: main phase actions. */
	record IdleCommand(int player, List<CardRef> summonable, List<CardRef> specialSummonable,
			List<CardRef> repositionable, List<CardRef> monsterSettable, List<CardRef> spellSettable,
			List<ChainOption> activatable, boolean canBattlePhase, boolean canEndPhase, boolean canShuffle) implements Prompt {
		public IdleCommand {
			summonable = List.copyOf(summonable);
			specialSummonable = List.copyOf(specialSummonable);
			repositionable = List.copyOf(repositionable);
			monsterSettable = List.copyOf(monsterSettable);
			spellSettable = List.copyOf(spellSettable);
			activatable = List.copyOf(activatable);
		}

		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_IDLECMD;
		}
	}

	/** {@code MSG_SELECT_BATTLECMD}: battle phase actions. */
	record BattleCommand(int player, List<ChainOption> activatable, List<Attacker> attackers, boolean canMainPhase2,
			boolean canEndPhase) implements Prompt {
		public BattleCommand {
			activatable = List.copyOf(activatable);
			attackers = List.copyOf(attackers);
		}

		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_BATTLECMD;
		}
	}

	record Attacker(CardRef card, boolean canAttackDirectly) {}

	/** {@code MSG_SELECT_EFFECTYN}: activate the effect of {@code card}? */
	record EffectYesNo(int player, CardRef card, long description) implements Prompt {
		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_EFFECTYN;
		}
	}

	/** {@code MSG_SELECT_YESNO}. */
	record YesNo(int player, long description) implements Prompt {
		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_YESNO;
		}
	}

	/** {@code MSG_SELECT_OPTION}: pick one of the described options. */
	record SelectOption(int player, List<Long> options) implements Prompt {
		public SelectOption {
			options = List.copyOf(options);
		}

		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_OPTION;
		}
	}

	/** {@code MSG_SELECT_CARD}: select between {@code min} and {@code max} cards. */
	record SelectCard(int player, boolean cancelable, int min, int max, List<CardRef> cards) implements Prompt {
		public SelectCard {
			cards = List.copyOf(cards);
		}

		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_CARD;
		}
	}

	/** {@code MSG_SELECT_TRIBUTE}: tribute value sum must reach {@code min}; at most {@code max} cards. */
	record SelectTribute(int player, boolean cancelable, int min, int max, List<TributeCandidate> cards) implements Prompt {
		public SelectTribute {
			cards = List.copyOf(cards);
		}

		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_TRIBUTE;
		}
	}

	record TributeCandidate(CardRef card, int releaseParam) {}

	/**
	 * {@code MSG_SELECT_SUM}. With {@code atLeast == false} the (must + selected) params must sum exactly to
	 * {@code accumulate} using {@code min..max} selected cards; otherwise the sum must reach {@code accumulate} without
	 * any card being superfluous. Each param holds two alternative values (low/high 16 bits).
	 */
	record SelectSum(int player, boolean atLeast, int accumulate, int min, int max, List<SumCandidate> mustSelect,
			List<SumCandidate> selectable) implements Prompt {
		public SelectSum {
			mustSelect = List.copyOf(mustSelect);
			selectable = List.copyOf(selectable);
		}

		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_SUM;
		}
	}

	record SumCandidate(CardRef card, int param) {}

	/** {@code MSG_SELECT_UNSELECT_CARD}: toggle one card at a time; indices span selectable then unselectable. */
	record SelectUnselectCard(int player, boolean finishable, boolean cancelable, int min, int max,
			List<CardRef> selectable, List<CardRef> unselectable) implements Prompt {
		public SelectUnselectCard {
			selectable = List.copyOf(selectable);
			unselectable = List.copyOf(unselectable);
		}

		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_UNSELECT_CARD;
		}
	}

	/** {@code MSG_SELECT_CHAIN}: chain one of the effects or pass (not allowed if {@code forced}). */
	record SelectChain(int player, int specialCount, boolean forced, int hintTiming, int opponentHintTiming,
			List<ChainOption> chains) implements Prompt {
		public SelectChain {
			chains = List.copyOf(chains);
		}

		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_CHAIN;
		}
	}

	/**
	 * {@code MSG_SELECT_PLACE} / {@code MSG_SELECT_DISFIELD}: choose {@code count} zones. Set bits in
	 * {@code unavailable} are zones that cannot be chosen, relative to {@link #player()}: bits 0-6 own monster zones,
	 * 8-15 own spell/trap zones (13 = field, 14-15 = pendulum), 16-31 the same for the opponent.
	 */
	record SelectPlace(int player, int count, int unavailable, boolean disableField) implements Prompt {
		@Override
		public int type() {
			return disableField ? OcgConstants.MSG_SELECT_DISFIELD : OcgConstants.MSG_SELECT_PLACE;
		}
	}

	/** {@code MSG_SELECT_POSITION}: {@code positions} is a mask of allowed {@code POS_*} values. */
	record SelectPosition(int player, int code, int positions) implements Prompt {
		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_POSITION;
		}
	}

	/** {@code MSG_SELECT_COUNTER}: remove exactly {@code count} counters of {@code counterType} in total. */
	record SelectCounter(int player, int counterType, int count, List<CounterCandidate> cards) implements Prompt {
		public SelectCounter {
			cards = List.copyOf(cards);
		}

		@Override
		public int type() {
			return OcgConstants.MSG_SELECT_COUNTER;
		}
	}

	record CounterCandidate(CardRef card, int counters) {}

	/** {@code MSG_SORT_CARD} / {@code MSG_SORT_CHAIN}: order the cards. */
	record SortCards(int player, boolean chain, List<CardRef> cards) implements Prompt {
		public SortCards {
			cards = List.copyOf(cards);
		}

		@Override
		public int type() {
			return chain ? OcgConstants.MSG_SORT_CHAIN : OcgConstants.MSG_SORT_CARD;
		}
	}

	/** {@code MSG_ANNOUNCE_RACE}: choose exactly {@code count} races out of {@code available}. */
	record AnnounceRace(int player, int count, long available) implements Prompt {
		@Override
		public int type() {
			return OcgConstants.MSG_ANNOUNCE_RACE;
		}
	}

	/** {@code MSG_ANNOUNCE_ATTRIB}: choose exactly {@code count} attributes out of {@code available}. */
	record AnnounceAttribute(int player, int count, int available) implements Prompt {
		@Override
		public int type() {
			return OcgConstants.MSG_ANNOUNCE_ATTRIB;
		}
	}

	/** {@code MSG_ANNOUNCE_CARD}: declare a card name matching the postfix {@code OPCODE_*} filter. */
	record AnnounceCard(int player, List<Long> opcodes) implements Prompt {
		public AnnounceCard {
			opcodes = List.copyOf(opcodes);
		}

		@Override
		public int type() {
			return OcgConstants.MSG_ANNOUNCE_CARD;
		}
	}

	/** {@code MSG_ANNOUNCE_NUMBER}: pick one of the numbers. */
	record AnnounceNumber(int player, List<Long> options) implements Prompt {
		public AnnounceNumber {
			options = List.copyOf(options);
		}

		@Override
		public int type() {
			return OcgConstants.MSG_ANNOUNCE_NUMBER;
		}
	}

	/** {@code MSG_ROCK_PAPER_SCISSORS}: answer 1 = scissors, 2 = rock, 3 = paper. */
	record RockPaperScissors(int player) implements Prompt {
		@Override
		public int type() {
			return OcgConstants.MSG_ROCK_PAPER_SCISSORS;
		}
	}
}
