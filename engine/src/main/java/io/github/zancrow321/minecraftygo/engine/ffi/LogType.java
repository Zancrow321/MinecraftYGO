package io.github.zancrow321.minecraftygo.engine.ffi;

/** {@code OCG_LogTypes}. */
public enum LogType {
	ERROR,
	FROM_SCRIPT,
	FOR_DEBUG,
	UNDEFINED;

	static LogType fromNative(int value) {
		return value >= 0 && value < values().length ? values()[value] : UNDEFINED;
	}
}
