package io.github.zancrow321.minecraftygo.engine.protocol;

import io.github.zancrow321.minecraftygo.engine.OcgConstants;

/**
 * Where a card is: controller (0/1), location ({@code LOCATION_*}), sequence, and position ({@code POS_*}). For an
 * Xyz material the location includes {@code LOCATION_OVERLAY} and {@code position} is the index in the stack.
 */
public record Loc(int controller, int location, int sequence, int position) {
    public static final Loc NONE = new Loc(0, 0, 0, 0);

    public boolean isNone() {
        return location == 0;
    }

    public boolean isFaceUp() {
        return (position & OcgConstants.POS_FACEUP) != 0;
    }

    public boolean isOverlay() {
        return (location & OcgConstants.LOCATION_OVERLAY) != 0;
    }

    /** The same place, ignoring position; useful for matching cards across messages. */
    public Loc place() {
        return new Loc(controller, location, sequence, 0);
    }
}
