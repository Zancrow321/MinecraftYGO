package io.github.zancrow321.minecraftygo.engine.prompt;

import java.util.List;

/** Semantic answer to a {@link Prompt}; {@link ResponseEncoder} turns it into the core's byte format. */
public sealed interface PromptResponse {
	/** Answer to {@link Prompt.IdleCommand}; {@code index} points into the list belonging to {@code action}. */
	record IdleAction(IdleActionType action, int index) implements PromptResponse {}

	/** Answer to {@link Prompt.BattleCommand}. */
	record BattleAction(BattleActionType action, int index) implements PromptResponse {}

	/** Answer to {@link Prompt.EffectYesNo} and {@link Prompt.YesNo}. */
	record YesNo(boolean yes) implements PromptResponse {}

	/**
	 * An index into the prompt's options: {@link Prompt.SelectOption}, {@link Prompt.SelectChain},
	 * {@link Prompt.AnnounceNumber}, {@link Prompt.SelectUnselectCard} (selectable then unselectable), or the hand
	 * (1-3) for {@link Prompt.RockPaperScissors}.
	 */
	record Choice(int index) implements PromptResponse {}

	/** Pass / cancel / finish: chain pass, cancelable card selections, finishing an unselect prompt, default sort. */
	record Cancel() implements PromptResponse {}

	/** Indices into the candidates of {@link Prompt.SelectCard}, {@link Prompt.SelectTribute} or {@link Prompt.SelectSum} (selectable list). */
	record Cards(List<Integer> indices) implements PromptResponse {
		public Cards {
			indices = List.copyOf(indices);
		}
	}

	/** Zones for {@link Prompt.SelectPlace}; {@code player} is absolute (0/1). */
	record Places(List<Zone> zones) implements PromptResponse {
		public Places {
			zones = List.copyOf(zones);
		}
	}

	record Zone(int player, int location, int sequence) {}

	/** A single {@code POS_*} value for {@link Prompt.SelectPosition}. */
	record Position(int position) implements PromptResponse {}

	/** Counters to remove per candidate of {@link Prompt.SelectCounter}. */
	record Counters(List<Integer> counts) implements PromptResponse {
		public Counters {
			counts = List.copyOf(counts);
		}
	}

	/** New positions per card for {@link Prompt.SortCards}: {@code order.get(i)} is the slot of card {@code i}. */
	record Order(List<Integer> order) implements PromptResponse {
		public Order {
			order = List.copyOf(order);
		}
	}

	/** Bit mask for {@link Prompt.AnnounceRace} / {@link Prompt.AnnounceAttribute}. */
	record Mask(long mask) implements PromptResponse {}

	/** A declared card code for {@link Prompt.AnnounceCard}. */
	record Code(int code) implements PromptResponse {}

	enum IdleActionType {
		SUMMON, SPECIAL_SUMMON, REPOSITION, MONSTER_SET, SPELL_SET, ACTIVATE, TO_BATTLE_PHASE, TO_END_PHASE, SHUFFLE_HAND
	}

	enum BattleActionType {
		ACTIVATE, ATTACK, TO_MAIN_PHASE_2, TO_END_PHASE
	}
}
