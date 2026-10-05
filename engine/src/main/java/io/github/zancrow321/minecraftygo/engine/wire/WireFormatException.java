package io.github.zancrow321.minecraftygo.engine.wire;

/** A core buffer did not have the expected layout. */
public class WireFormatException extends RuntimeException {
	public WireFormatException(String message) {
		super(message);
	}

	public WireFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
