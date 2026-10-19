package io.github.zancrow321.jadm.engine.ai.windbot;

/** Monster type bits ({@code RACE_*}); the core uses 64 bits for them. */
public final class CardRace {
    public static final long WARRIOR = 0x1L;
    public static final long SPELL_CASTER = 0x2L;
    public static final long FAIRY = 0x4L;
    public static final long FIEND = 0x8L;
    public static final long ZOMBIE = 0x10L;
    public static final long MACHINE = 0x20L;
    public static final long AQUA = 0x40L;
    public static final long PYRO = 0x80L;
    public static final long ROCK = 0x100L;
    public static final long WIND_BEAST = 0x200L;
    public static final long PLANT = 0x400L;
    public static final long INSECT = 0x800L;
    public static final long THUNDER = 0x1000L;
    public static final long DRAGON = 0x2000L;
    public static final long BEAST = 0x4000L;
    public static final long BEAST_WARRIOR = 0x8000L;
    public static final long DINOSAUR = 0x10000L;
    public static final long FISH = 0x20000L;
    public static final long SEA_SERPENT = 0x40000L;
    public static final long REPTILE = 0x80000L;
    public static final long PSYCHO = 0x100000L;
    public static final long DIVINE_BEAST = 0x200000L;
    public static final long CREATOR_GOD = 0x400000L;
    public static final long WYRM = 0x800000L;
    public static final long CYBERSE = 0x1000000L;
    public static final long ILLUSION = 0x2000000L;

    private CardRace() {
    }
}
