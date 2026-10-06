package io.github.zancrow321.jadm.engine.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decodes {@code OCG_DuelQueryLocation} results.
 *
 * <p>The buffer is {@code u32 total} then one block per slot. An empty zone is {@code u16 0}. A card is a list of
 * entries {@code u16 len, u32 flag, payload} ending with {@link #QUERY_END}.
 */
public final class QueryDecoder {
    public static final int QUERY_CODE = 0x1;
    public static final int QUERY_POSITION = 0x2;
    public static final int QUERY_ALIAS = 0x4;
    public static final int QUERY_TYPE = 0x8;
    public static final int QUERY_LEVEL = 0x10;
    public static final int QUERY_RANK = 0x20;
    public static final int QUERY_ATTRIBUTE = 0x40;
    public static final int QUERY_RACE = 0x80;
    public static final int QUERY_ATTACK = 0x100;
    public static final int QUERY_DEFENSE = 0x200;
    public static final int QUERY_OVERLAY_CARD = 0x10000;
    public static final int QUERY_OWNER = 0x40000;
    public static final int QUERY_IS_PUBLIC = 0x100000;
    public static final int QUERY_LSCALE = 0x200000;
    public static final int QUERY_RSCALE = 0x400000;
    public static final int QUERY_LINK = 0x800000;
    public static final int QUERY_IS_HIDDEN = 0x1000000;
    public static final int QUERY_END = 0x80000000;

    /** The fields the mod asks for. */
    public static final int STANDARD_FLAGS = QUERY_CODE | QUERY_POSITION | QUERY_ALIAS | QUERY_TYPE | QUERY_LEVEL
            | QUERY_RANK | QUERY_ATTRIBUTE | QUERY_RACE | QUERY_ATTACK | QUERY_DEFENSE | QUERY_OVERLAY_CARD
            | QUERY_OWNER | QUERY_IS_PUBLIC | QUERY_IS_HIDDEN | QUERY_LSCALE | QUERY_RSCALE | QUERY_LINK;

    private QueryDecoder() {
    }

    /** @return one entry per slot, {@code null} for an empty zone */
    public static List<CardState> decodeLocation(byte[] buffer) {
        if (buffer.length < 4) {
            return List.of();
        }
        ByteReader r = new ByteReader(buffer);
        int total = r.i32();
        if (total != r.remaining()) {
            throw new IllegalArgumentException("query length " + total + " but " + r.remaining() + " bytes follow");
        }
        List<CardState> slots = new ArrayList<>();
        while (r.remaining() > 0) {
            slots.add(readCard(r));
        }
        return Collections.unmodifiableList(slots);
    }

    private static CardState readCard(ByteReader r) {
        int code = 0, position = 0, alias = 0, type = 0, level = 0, rank = 0, attribute = 0, attack = 0;
        int defense = 0, owner = 0, leftScale = 0, rightScale = 0, link = 0, linkMarker = 0;
        long race = 0;
        boolean isPublic = false, isHidden = false;
        List<Integer> overlay = List.of();
        boolean first = true;
        while (true) {
            int length = r.u16();
            if (length == 0 && first) {
                return null;
            }
            first = false;
            int flag = r.i32();
            int payload = length - 4;
            switch (flag) {
                case QUERY_END -> {
                    return new CardState(code, position, alias, type, level, rank, attribute, race, attack, defense,
                            owner, isPublic, isHidden, overlay, leftScale, rightScale, link, linkMarker);
                }
                case QUERY_CODE -> code = r.i32();
                case QUERY_POSITION -> position = r.i32();
                case QUERY_ALIAS -> alias = r.i32();
                case QUERY_TYPE -> type = r.i32();
                case QUERY_LEVEL -> level = r.i32();
                case QUERY_RANK -> rank = r.i32();
                case QUERY_ATTRIBUTE -> attribute = r.i32();
                case QUERY_RACE -> race = r.u64();
                case QUERY_ATTACK -> attack = r.i32();
                case QUERY_DEFENSE -> defense = r.i32();
                case QUERY_OWNER -> owner = r.u8();
                case QUERY_IS_PUBLIC -> isPublic = r.bool();
                case QUERY_IS_HIDDEN -> isHidden = r.bool();
                case QUERY_LSCALE -> leftScale = r.i32();
                case QUERY_RSCALE -> rightScale = r.i32();
                case QUERY_LINK -> {
                    link = r.i32();
                    linkMarker = r.i32();
                }
                case QUERY_OVERLAY_CARD -> {
                    int count = r.i32();
                    List<Integer> codes = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        codes.add(r.i32());
                    }
                    overlay = List.copyOf(codes);
                }
                default -> r.skip(payload);
            }
        }
    }
}
