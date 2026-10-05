package io.github.zancrow321.minecraftygo.engine.ai;

import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse;
import io.github.zancrow321.minecraftygo.engine.query.FieldState;

/**
 * Something that answers prompts: a bot, or an adapter for a human player. Agents only ever receive information their
 * team may see (messages and field snapshots are redacted by the caller).
 */
public interface DuelAgent {
	PromptResponse respond(Prompt prompt);

	/** Observes a message visible to this agent's team (used for per-turn bookkeeping). */
	default void observe(CoreMessage message) {}

	/** The field as this agent's team sees it, delivered right before {@link #respond}. */
	default void observeField(FieldState field) {}
}
