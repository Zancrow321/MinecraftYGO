package io.github.zancrow321.jadm.engine.ai.windbot;

/** Zone masks for one side: bits 0-6 are the Monster Zones (5 and 6 the Extra Monster Zones). */
public final class Zones {
    public static final int Z0 = 0x1;
    public static final int Z1 = 0x2;
    public static final int Z2 = 0x4;
    public static final int Z3 = 0x8;
    public static final int Z4 = 0x10;
    public static final int Z5 = 0x20;
    public static final int Z6 = 0x40;
    public static final int FIELD_ZONE = 0x20;
    public static final int MONSTER_ZONES = 0x7f;
    public static final int MAIN_MONSTER_ZONES = 0x1f;
    public static final int EXTRA_MONSTER_ZONES = 0x60;
    public static final int SPELL_ZONES = 0x1f;
    public static final int PENDULUM_ZONES = 0x3;
    public static final int LINKED_ZONES = 0x10000;
    public static final int NOT_LINKED_ZONES = 0x20000;

    private Zones() {
    }
}
