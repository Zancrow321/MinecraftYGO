package io.github.zancrow321.minecraftygo.engine.prompt;

/** A response that the core would reject with {@code MSG_RETRY}. */
public class InvalidResponseException extends IllegalArgumentException {
	public InvalidResponseException(String message) {
		super(message);
	}
}
