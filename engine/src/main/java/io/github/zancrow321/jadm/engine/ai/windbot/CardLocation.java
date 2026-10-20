package io.github.zancrow321.jadm.engine.ai.windbot;

/** Location bits ({@code LOCATION_*}); {@link #FIELD_ZONE} and {@link #PENDULUM_ZONE} only exist in zone choices. */
public final class CardLocation {
    public static final int DECK = 0x01;
    public static final int HAND = 0x02;
    public static final int MONSTER_ZONE = 0x04;
    public static final int SPELL_ZONE = 0x08;
    public static final int GRAVE = 0x10;
    public static final int REMOVED = 0x20;
    public static final int EXTRA = 0x40;
    public static final int OVERLAY = 0x80;
    public static final int ONFIELD = 0x0C;
    public static final int FIELD_ZONE = 0x100;
    public static final int PENDULUM_ZONE = 0x200;

    private CardLocation() {
    }
}
