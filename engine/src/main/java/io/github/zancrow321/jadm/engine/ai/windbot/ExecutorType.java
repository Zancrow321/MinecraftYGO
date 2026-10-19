package io.github.zancrow321.jadm.engine.ai.windbot;

/** What a {@link CardExecutor} decides about. */
public enum ExecutorType {
    SUMMON,
    SP_SUMMON,
    REPOS,
    MONSTER_SET,
    SPELL_SET,
    ACTIVATE,
    SUMMON_OR_SET,
    GO_TO_BATTLE_PHASE,
    GO_TO_MAIN_PHASE2,
    GO_TO_END_PHASE,
    SURRENDER
}
