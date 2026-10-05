package io.github.zancrow321.minecraftygo.engine.protocol;

import java.util.List;

/**
 * One card as reported by a location query.
 *
 * @param code     the card's passcode
 * @param position {@code POS_*} bits
 * @param isPublic whether both players may see it (e.g. a revealed card in hand)
 */
public record CardState(int code, int position, int alias, int type, int level, int rank, int attribute, long race,
                        int attack, int defense, int owner, boolean isPublic, boolean isHidden,
                        List<Integer> overlayCodes) {
}
