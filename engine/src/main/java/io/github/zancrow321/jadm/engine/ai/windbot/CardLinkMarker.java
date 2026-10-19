package io.github.zancrow321.jadm.engine.ai.windbot;

/** Link arrow bits ({@code LINK_MARKER_*}). */
public final class CardLinkMarker {
    public static final int BOTTOM_LEFT = 0x01;
    public static final int BOTTOM = 0x02;
    public static final int BOTTOM_RIGHT = 0x04;
    public static final int LEFT = 0x08;
    public static final int RIGHT = 0x20;
    public static final int TOP_LEFT = 0x40;
    public static final int TOP = 0x80;
    public static final int TOP_RIGHT = 0x100;

    private CardLinkMarker() {
    }
}
