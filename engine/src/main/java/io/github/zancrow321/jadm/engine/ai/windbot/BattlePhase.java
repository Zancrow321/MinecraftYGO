package io.github.zancrow321.jadm.engine.ai.windbot;

import java.util.ArrayList;
import java.util.List;

/** What the last battle command offered. */
public final class BattlePhase {
    public final List<ClientCard> attackableCards = new ArrayList<>();
    public final List<ClientCard> activableCards = new ArrayList<>();
    public final List<Long> activableDescs = new ArrayList<>();
    public boolean canMainPhaseTwo;
    public boolean canEndPhase;
}
