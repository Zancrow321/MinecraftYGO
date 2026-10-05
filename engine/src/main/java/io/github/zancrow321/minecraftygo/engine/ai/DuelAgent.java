package io.github.zancrow321.minecraftygo.engine.ai;

import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse;

/** Something that answers prompts: a bot, or an adapter for a human player. */
public interface DuelAgent {
	PromptResponse respond(Prompt prompt);

	/** Observes the messages this agent is allowed to see (used for per-turn bookkeeping). */
	default void observe(CoreMessage message) {}
}
