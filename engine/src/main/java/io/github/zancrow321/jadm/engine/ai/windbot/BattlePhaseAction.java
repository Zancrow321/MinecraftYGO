package io.github.zancrow321.jadm.engine.ai.windbot;

/** An answer to a battle command: a command type ({@code Responses.BATTLE_*}) and the index in its list. */
public final class BattlePhaseAction {
    public enum BattleAction {
        ACTIVATE, ATTACK, TO_MAIN_PHASE_TWO, TO_END_PHASE
    }

    public final BattleAction action;
    public final int index;

    public BattlePhaseAction(BattleAction action) {
        this(action, 0);
    }

    public BattlePhaseAction(BattleAction action, int index) {
        this.action = action;
        this.index = index;
    }

    /** @param indexes a card's {@link ClientCard#actionIndex} */
    public BattlePhaseAction(BattleAction action, int[] indexes) {
        this(action, indexes[action.ordinal()]);
    }
}
