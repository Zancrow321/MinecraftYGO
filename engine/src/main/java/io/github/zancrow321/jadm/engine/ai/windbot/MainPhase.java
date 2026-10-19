package io.github.zancrow321.jadm.engine.ai.windbot;

import java.util.ArrayList;
import java.util.List;

/** What the last idle command (Main Phase) offered. */
public final class MainPhase {
    public final List<ClientCard> summonableCards = new ArrayList<>();
    public final List<ClientCard> specialSummonableCards = new ArrayList<>();
    public final List<ClientCard> reposableCards = new ArrayList<>();
    public final List<ClientCard> monsterSetableCards = new ArrayList<>();
    public final List<ClientCard> spellSetableCards = new ArrayList<>();
    public final List<ClientCard> activableCards = new ArrayList<>();
    public final List<Long> activableDescs = new ArrayList<>();
    public boolean canBattlePhase;
    public boolean canEndPhase;
}
