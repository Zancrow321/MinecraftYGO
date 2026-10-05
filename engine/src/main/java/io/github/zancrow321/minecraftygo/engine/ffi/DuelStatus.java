package io.github.zancrow321.minecraftygo.engine.ffi;

/** Result of {@code OCG_DuelProcess}. */
public enum DuelStatus {
	/** The duel is over. */
	END,
	/** The last message is a prompt; a response must be set before processing again. */
	AWAITING,
	/** More processing is possible without a response. */
	CONTINUE;

	static DuelStatus fromNative(int value) {
		return switch (value) {
			case 0 -> END;
			case 1 -> AWAITING;
			case 2 -> CONTINUE;
			default -> throw new OcgException("Unknown OCG_DuelStatus " + value);
		};
	}
}
