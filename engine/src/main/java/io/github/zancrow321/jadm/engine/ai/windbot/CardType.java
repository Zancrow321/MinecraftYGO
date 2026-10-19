package io.github.zancrow321.jadm.engine.ai.windbot;

/** Card type bits ({@code TYPE_*}), as WindBot names them. */
public final class CardType {
    public static final int MONSTER = 0x1;
    public static final int SPELL = 0x2;
    public static final int TRAP = 0x4;
    public static final int NORMAL = 0x10;
    public static final int EFFECT = 0x20;
    public static final int FUSION = 0x40;
    public static final int RITUAL = 0x80;
    public static final int TRAP_MONSTER = 0x100;
    public static final int SPIRIT = 0x200;
    public static final int UNION = 0x400;
    public static final int DUAL = 0x800;
    public static final int TUNER = 0x1000;
    public static final int SYNCHRO = 0x2000;
    public static final int TOKEN = 0x4000;
    public static final int QUICK_PLAY = 0x10000;
    public static final int CONTINUOUS = 0x20000;
    public static final int EQUIP = 0x40000;
    public static final int FIELD = 0x80000;
    public static final int COUNTER = 0x100000;
    public static final int FLIP = 0x200000;
    public static final int TOON = 0x400000;
    public static final int XYZ = 0x800000;
    public static final int PENDULUM = 0x1000000;
    public static final int SP_SUMMON = 0x2000000;
    public static final int LINK = 0x4000000;

    private CardType() {
    }
}
