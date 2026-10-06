package io.github.zancrow321.minecraftygo.engine.protocol;

import java.util.List;

/**
 * One card as reported by a location query.
 *
 * @param code     the card's passcode
 * @param position {@code POS_*} bits
 * @param isPublic whether both players may see it (e.g. a revealed card in hand)
 * @param leftScale  a Pendulum card's left scale (0 otherwise)
 * @param rightScale a Pendulum card's right scale
 * @param link       a Link monster's rating (0 otherwise)
 * @param linkMarker a Link monster's arrows, {@code LINK_MARKER_*} bits
 */
public record CardState(int code, int position, int alias, int type, int level, int rank, int attribute, long race,
                        int attack, int defense, int owner, boolean isPublic, boolean isHidden,
                        List<Integer> overlayCodes, int leftScale, int rightScale, int link, int linkMarker) {
    public CardState(int code, int position, int alias, int type, int level, int rank, int attribute, long race,
                     int attack, int defense, int owner, boolean isPublic, boolean isHidden,
                     List<Integer> overlayCodes) {
        this(code, position, alias, type, level, rank, attribute, race, attack, defense, owner, isPublic, isHidden,
                overlayCodes, 0, 0, 0, 0);
    }
}
