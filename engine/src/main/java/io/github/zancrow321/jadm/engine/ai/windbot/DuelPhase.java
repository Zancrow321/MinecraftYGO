package io.github.zancrow321.jadm.engine.ai.windbot;

/** Phase bits ({@code PHASE_*}). */
public final class DuelPhase {
    public static final int DRAW = 0x01;
    public static final int STANDBY = 0x02;
    public static final int MAIN1 = 0x04;
    public static final int BATTLE_START = 0x08;
    public static final int BATTLE_STEP = 0x10;
    public static final int DAMAGE = 0x20;
    public static final int DAMAGE_CAL = 0x40;
    public static final int BATTLE = 0x80;
    public static final int MAIN2 = 0x100;
    public static final int END = 0x200;

    private DuelPhase() {
    }
}
