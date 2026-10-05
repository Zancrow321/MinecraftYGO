package io.github.zancrow321.minecraftygo.engine.message;

/** One decoded message from {@code OCG_DuelGetMessage}. {@link #type()} is the {@code MSG_*} id. */
public interface CoreMessage {
	int type();
}
