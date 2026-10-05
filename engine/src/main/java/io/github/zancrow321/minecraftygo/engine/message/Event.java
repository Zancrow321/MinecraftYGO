package io.github.zancrow321.minecraftygo.engine.message;

import io.github.zancrow321.minecraftygo.engine.wire.LocInfo;

import java.util.Arrays;
import java.util.List;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;

/**
 * Informational core messages (everything that is not a {@link io.github.zancrow321.minecraftygo.engine.prompt.Prompt}).
 * Field layouts follow the writers in ygopro-core; a {@code code} of 0 means "unknown/hidden".
 */
public sealed interface Event extends CoreMessage {
	// ---- flow ----------------------------------------------------------------------------------------

	/** The previous response was rejected; the last prompt must be answered again. */
	record Retry() implements Event {
		@Override
		public int type() {
			return MSG_RETRY;
		}
	}

	/** {@code MSG_HINT}: {@code hintType} is a {@code HINT_*} constant; {@code data} depends on it. */
	record Hint(int hintType, int player, long data) implements Event {
		@Override
		public int type() {
			return MSG_HINT;
		}
	}

	/** {@code MSG_WIN}: {@code player} 0/1, or {@code PLAYER_NONE} (2) for a draw. */
	record Win(int player, int reason) implements Event {
		@Override
		public int type() {
			return MSG_WIN;
		}
	}

	record NewTurn(int player) implements Event {
		@Override
		public int type() {
			return MSG_NEW_TURN;
		}
	}

	/** {@code phase} is a {@code PHASE_*} constant. */
	record NewPhase(int phase) implements Event {
		@Override
		public int type() {
			return MSG_NEW_PHASE;
		}
	}

	/** Result of a rock-paper-scissors round: hands of player 0 and 1 (1 scissors, 2 rock, 3 paper). */
	record HandResult(int hand0, int hand1) implements Event {
		@Override
		public int type() {
			return MSG_HAND_RES;
		}
	}

	/** Tag duel: {@code player}'s team switched to its next duelist; codes of hidden cards are 0 for others. */
	record TagSwap(int player, int deckCount, int extraCount, int extraFaceUpCount, int deckTopCode,
			List<CodePosition> hand, List<CodePosition> extra) implements Event {
		public TagSwap {
			hand = List.copyOf(hand);
			extra = List.copyOf(extra);
		}

		@Override
		public int type() {
			return MSG_TAG_SWAP;
		}
	}

	// ---- card movement -----------------------------------------------------------------------------

	record CodePosition(int code, int position) {}

	record Draw(int player, List<CodePosition> cards) implements Event {
		public Draw {
			cards = List.copyOf(cards);
		}

		@Override
		public int type() {
			return MSG_DRAW;
		}
	}

	/** A card changed location/position; {@code reason} is a {@code REASON_*} mask. */
	record Move(int code, LocInfo from, LocInfo to, int reason) implements Event {
		@Override
		public int type() {
			return MSG_MOVE;
		}
	}

	/** Position change on the field (e.g. flip, change to defense). */
	record PositionChange(int code, int controller, int location, int sequence, int previousPosition,
			int currentPosition) implements Event {
		@Override
		public int type() {
			return MSG_POS_CHANGE;
		}
	}

	/** A card was set face-down. */
	record SetCard(int code, LocInfo location) implements Event {
		@Override
		public int type() {
			return MSG_SET;
		}
	}

	/** Two cards swapped places/controllers. */
	record Swap(int code1, LocInfo location1, int code2, LocInfo location2) implements Event {
		@Override
		public int type() {
			return MSG_SWAP;
		}
	}

	record CodeLocation(int code, int controller, int location, int sequence) {}

	/** {@code MSG_SHUFFLE_HAND} / {@code MSG_SHUFFLE_EXTRA}: new order of the cards. */
	record ShuffleCards(int player, boolean extra, List<Integer> codes) implements Event {
		public ShuffleCards {
			codes = List.copyOf(codes);
		}

		@Override
		public int type() {
			return extra ? MSG_SHUFFLE_EXTRA : MSG_SHUFFLE_HAND;
		}
	}

	record ShuffleDeck(int player) implements Event {
		@Override
		public int type() {
			return MSG_SHUFFLE_DECK;
		}
	}

	/** Set cards were shuffled among their zones; {@code overlay} holds the material locations (or empty ones). */
	record ShuffleSetCards(int location, List<LocInfo> cards, List<LocInfo> overlay) implements Event {
		public ShuffleSetCards {
			cards = List.copyOf(cards);
			overlay = List.copyOf(overlay);
		}

		@Override
		public int type() {
			return MSG_SHUFFLE_SET_CARD;
		}
	}

	record ReverseDeck() implements Event {
		@Override
		public int type() {
			return MSG_REVERSE_DECK;
		}
	}

	/** A deck card counted {@code sequenceFromTop} from the top is face-up. */
	record DeckTop(int player, int sequenceFromTop, int code, int position) implements Event {
		@Override
		public int type() {
			return MSG_DECK_TOP;
		}
	}

	/** Grave and deck were exchanged; {@code extraMask} marks cards that went to the Extra Deck instead. */
	record SwapGraveDeck(int player, int extraInsertPosition, byte[] extraMask) implements Event {
		public SwapGraveDeck {
			extraMask = extraMask.clone();
		}

		@Override
		public byte[] extraMask() {
			return extraMask.clone();
		}

		@Override
		public int type() {
			return MSG_SWAP_GRAVE_DECK;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof SwapGraveDeck other && player == other.player
					&& extraInsertPosition == other.extraInsertPosition && Arrays.equals(extraMask, other.extraMask);
		}

		@Override
		public int hashCode() {
			return player * 31 + Arrays.hashCode(extraMask);
		}

		@Override
		public String toString() {
			return "SwapGraveDeck[player=" + player + "]";
		}
	}

	/** Cards removed from the duel entirely (Debug/skill functionality). */
	record RemoveCards(List<LocInfo> cards) implements Event {
		public RemoveCards {
			cards = List.copyOf(cards);
		}

		@Override
		public int type() {
			return MSG_REMOVE_CARDS;
		}
	}

	// ---- reveals -----------------------------------------------------------------------------------

	/**
	 * {@code MSG_CONFIRM_DECKTOP}, {@code MSG_CONFIRM_EXTRATOP}, {@code MSG_CONFIRM_CARDS}: {@code player} is shown the
	 * cards (for deck/hand reveals everyone is shown them, see the visibility rules).
	 */
	record Confirm(int messageType, int player, List<CodeLocation> cards) implements Event {
		public Confirm {
			cards = List.copyOf(cards);
		}

		@Override
		public int type() {
			return messageType;
		}
	}

	// ---- summons -------------------------------------------------------------------------------------

	/** {@code MSG_SUMMONING}, {@code MSG_SPSUMMONING}, {@code MSG_FLIPSUMMONING}. */
	record Summoning(int messageType, int code, LocInfo location) implements Event {
		@Override
		public int type() {
			return messageType;
		}
	}

	/** {@code MSG_SUMMONED}, {@code MSG_SPSUMMONED}, {@code MSG_FLIPSUMMONED}: the summon resolved. */
	record Summoned(int messageType) implements Event {
		@Override
		public int type() {
			return messageType;
		}
	}

	// ---- chains --------------------------------------------------------------------------------------

	record Chaining(int code, LocInfo location, int triggeringController, int triggeringLocation,
			int triggeringSequence, long description, int chainSize) implements Event {
		@Override
		public int type() {
			return MSG_CHAINING;
		}
	}

	/**
	 * {@code MSG_CHAINED}, {@code MSG_CHAIN_SOLVING}, {@code MSG_CHAIN_SOLVED}, {@code MSG_CHAIN_NEGATED},
	 * {@code MSG_CHAIN_DISABLED}: state change of chain link {@code chainLink}.
	 */
	record ChainLink(int messageType, int chainLink) implements Event {
		@Override
		public int type() {
			return messageType;
		}
	}

	record ChainEnd() implements Event {
		@Override
		public int type() {
			return MSG_CHAIN_END;
		}
	}

	/** {@code MSG_CARD_SELECTED}, {@code MSG_BECOME_TARGET}: cards were selected / targeted. */
	record CardsSelected(int messageType, List<LocInfo> cards) implements Event {
		public CardsSelected {
			cards = List.copyOf(cards);
		}

		@Override
		public int type() {
			return messageType;
		}
	}

	record RandomSelected(int player, List<LocInfo> cards) implements Event {
		public RandomSelected {
			cards = List.copyOf(cards);
		}

		@Override
		public int type() {
			return MSG_RANDOM_SELECTED;
		}
	}

	/** An optional trigger effect missed its timing. */
	record MissedEffect(LocInfo location, int code) implements Event {
		@Override
		public int type() {
			return MSG_MISSED_EFFECT;
		}
	}

	// ---- life points -------------------------------------------------------------------------------

	/** {@code MSG_DAMAGE}, {@code MSG_RECOVER}, {@code MSG_PAY_LPCOST}, {@code MSG_LPUPDATE} (new value). */
	record LifePoints(int messageType, int player, int amount) implements Event {
		@Override
		public int type() {
			return messageType;
		}
	}

	// ---- card relations ----------------------------------------------------------------------------

	/** {@code MSG_EQUIP}, {@code MSG_CARD_TARGET}, {@code MSG_CANCEL_TARGET}: {@code source} relates to {@code target}. */
	record CardRelation(int messageType, LocInfo source, LocInfo target) implements Event {
		@Override
		public int type() {
			return messageType;
		}
	}

	/** {@code MSG_ADD_COUNTER} / {@code MSG_REMOVE_COUNTER}. */
	record Counter(int messageType, int counterType, int controller, int location, int sequence, int count)
			implements Event {
		@Override
		public int type() {
			return messageType;
		}
	}

	record FieldDisabled(int disabledZones) implements Event {
		@Override
		public int type() {
			return MSG_FIELD_DISABLED;
		}
	}

	// ---- battle --------------------------------------------------------------------------------------

	/** {@code target} has location 0 for a direct attack. */
	record Attack(LocInfo attacker, LocInfo target) implements Event {
		public boolean isDirect() {
			return target.location() == 0;
		}

		@Override
		public int type() {
			return MSG_ATTACK;
		}
	}

	record Battle(LocInfo attacker, int attackerAttack, int attackerDefense, boolean attackerDestroyed,
			LocInfo target, int targetAttack, int targetDefense, boolean targetDestroyed) implements Event {
		@Override
		public int type() {
			return MSG_BATTLE;
		}
	}

	/** {@code MSG_ATTACK_DISABLED}, {@code MSG_DAMAGE_STEP_START}, {@code MSG_DAMAGE_STEP_END}. */
	record BattleStep(int messageType) implements Event {
		@Override
		public int type() {
			return messageType;
		}
	}

	// ---- randomness ----------------------------------------------------------------------------------

	/** {@code MSG_TOSS_COIN} / {@code MSG_TOSS_DICE}. */
	record Toss(int messageType, int player, List<Integer> results) implements Event {
		public Toss {
			results = List.copyOf(results);
		}

		@Override
		public int type() {
			return messageType;
		}
	}

	// ---- hints ---------------------------------------------------------------------------------------

	/** {@code type} is a {@code CHINT_*} constant. */
	record CardHint(LocInfo location, int hintType, long value) implements Event {
		@Override
		public int type() {
			return MSG_CARD_HINT;
		}
	}

	/** {@code type} is a {@code PHINT_*} constant. */
	record PlayerHint(int player, int hintType, long description) implements Event {
		@Override
		public int type() {
			return MSG_PLAYER_HINT;
		}
	}

	/** {@code MSG_AI_NAME} / {@code MSG_SHOW_HINT}. */
	record Text(int messageType, String text) implements Event {
		@Override
		public int type() {
			return messageType;
		}
	}

	record MatchKill(int code) implements Event {
		@Override
		public int type() {
			return MSG_MATCH_KILL;
		}
	}

	/** A message without a dedicated parser; {@code payload} excludes the type byte. */
	record Unparsed(int type, byte[] payload) implements Event {
		public Unparsed {
			payload = payload.clone();
		}

		@Override
		public byte[] payload() {
			return payload.clone();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Unparsed other && type == other.type && Arrays.equals(payload, other.payload);
		}

		@Override
		public int hashCode() {
			return type * 31 + Arrays.hashCode(payload);
		}

		@Override
		public String toString() {
			return "Unparsed[type=" + type + ", " + payload.length + " bytes]";
		}
	}
}
