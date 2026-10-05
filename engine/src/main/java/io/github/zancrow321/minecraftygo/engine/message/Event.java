package io.github.zancrow321.minecraftygo.engine.message;

import io.github.zancrow321.minecraftygo.engine.wire.LocInfo;

import java.util.Arrays;
import java.util.List;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;

/** Informational core messages (everything that is not a {@link io.github.zancrow321.minecraftygo.engine.prompt.Prompt}). */
public sealed interface Event extends CoreMessage {
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

	/** {@code MSG_WIN}: {@code player} 0/1, or {@code PLAYER_NONE} for a draw. */
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

	record DrawnCard(int code, int position) {}

	record Draw(int player, List<DrawnCard> cards) implements Event {
		public Draw {
			cards = List.copyOf(cards);
		}

		@Override
		public int type() {
			return MSG_DRAW;
		}
	}

	record Move(int code, LocInfo from, LocInfo to, int reason) implements Event {
		@Override
		public int type() {
			return MSG_MOVE;
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
