package io.github.zancrow321.jadm.engine.ai.windbot;

/** An answer to an idle command: a command type ({@code Responses.IDLE_*}) and the index in its list. */
public final class MainPhaseAction {
    public enum MainAction {
        SUMMON, SP_SUMMON, REPOS, SET_MONSTER, SET_SPELL, ACTIVATE, TO_BATTLE_PHASE, TO_END_PHASE
    }

    public final MainAction action;
    public final int index;

    public MainPhaseAction(MainAction action) {
        this(action, 0);
    }

    public MainPhaseAction(MainAction action, int index) {
        this.action = action;
        this.index = index;
    }

    /** @param indexes a card's {@link ClientCard#actionIndex} */
    public MainPhaseAction(MainAction action, int[] indexes) {
        this(action, indexes[action.ordinal()]);
    }
}
