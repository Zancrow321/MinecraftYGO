package io.github.zancrow321.minecraftygo.engine.ffi;

import io.github.zancrow321.minecraftygo.engine.data.CardData;

import java.util.Optional;

/**
 * Callbacks ocgcore makes while a duel runs (card data, scripts, log). They are invoked on the thread that is
 * currently calling into the duel. Exceptions thrown here are caught at the native boundary and re-thrown from the
 * duel call that triggered them.
 */
public interface DuelDataSource {
	Optional<CardData> card(int code);

	Optional<byte[]> script(String name);

	default void log(LogType type, String message) {}
}
