package io.github.zancrow321.minecraftygo.engine.ffi;

/** Failure reported by, or while talking to, the native ocgcore library. */
public class OcgException extends RuntimeException {
	public OcgException(String message) {
		super(message);
	}

	public OcgException(String message, Throwable cause) {
		super(message, cause);
	}
}
