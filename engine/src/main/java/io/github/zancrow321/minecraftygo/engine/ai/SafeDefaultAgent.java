package io.github.zancrow321.minecraftygo.engine.ai;

import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse.BattleActionType;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse.IdleActionType;

/**
 * Answers on behalf of a player whose decision timed out: always the most passive legal choice (pass, cancel, end the
 * phase, decline optional effects). Mandatory choices fall back to a legal random answer.
 */
public final class SafeDefaultAgent extends RandomLegalAgent {
	public SafeDefaultAgent(CardDatabase database) {
		super(0, database);
	}

	@Override
	public PromptResponse respond(Prompt prompt) {
		return switch (prompt) {
			case Prompt.IdleCommand p when p.canEndPhase() -> new PromptResponse.IdleAction(IdleActionType.TO_END_PHASE, 0);
			case Prompt.BattleCommand p when p.canMainPhase2() -> new PromptResponse.BattleAction(BattleActionType.TO_MAIN_PHASE_2, 0);
			case Prompt.BattleCommand p when p.canEndPhase() -> new PromptResponse.BattleAction(BattleActionType.TO_END_PHASE, 0);
			case Prompt.SelectChain p when !p.forced() -> new PromptResponse.Cancel();
			case Prompt.EffectYesNo _, Prompt.YesNo _ -> new PromptResponse.YesNo(false);
			case Prompt.SelectCard p when p.cancelable() -> new PromptResponse.Cancel();
			case Prompt.SelectTribute p when p.cancelable() -> new PromptResponse.Cancel();
			case Prompt.SelectUnselectCard p when p.finishable() || p.cancelable() -> new PromptResponse.Cancel();
			case Prompt.SortCards _ -> new PromptResponse.Cancel();
			default -> super.respond(prompt);
		};
	}
}
