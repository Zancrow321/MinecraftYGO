package io.github.zancrow321.minecraftygo.engine.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Builds the byte buffers passed to {@code OCG_DuelSetResponse} for each kind of {@link DuelMessage.Prompt}.
 * All indices refer to the order of entries in the prompt message.
 */
public final class Responses {
    private Responses() {
    }

    // Idle command types (SELECT_IDLECMD)
    public static final int IDLE_SUMMON = 0;
    public static final int IDLE_SPECIAL_SUMMON = 1;
    public static final int IDLE_REPOSITION = 2;
    public static final int IDLE_SET_MONSTER = 3;
    public static final int IDLE_SET_SPELL = 4;
    public static final int IDLE_ACTIVATE = 5;
    public static final int IDLE_TO_BATTLE = 6;
    public static final int IDLE_TO_END = 7;
    public static final int IDLE_SHUFFLE = 8;

    // Battle command types (SELECT_BATTLECMD)
    public static final int BATTLE_ACTIVATE = 0;
    public static final int BATTLE_ATTACK = 1;
    public static final int BATTLE_TO_MAIN2 = 2;
    public static final int BATTLE_TO_END = 3;

    /** SELECT_IDLECMD and SELECT_BATTLECMD. Use index 0 for phase changes and shuffling. */
    public static byte[] command(int type, int index) {
        return int32((index << 16) | type);
    }

    /** SELECT_EFFECTYN and SELECT_YESNO. */
    public static byte[] yesNo(boolean yes) {
        return int32(yes ? 1 : 0);
    }

    /** SELECT_OPTION, SELECT_CHAIN (-1 to pass), ANNOUNCE_NUMBER (index of the value), SELECT_POSITION. */
    public static byte[] index(int index) {
        return int32(index);
    }

    /** SELECT_POSITION: a single {@code POS_*} bit. */
    public static byte[] position(int position) {
        return int32(position);
    }

    /** SELECT_CARD, SELECT_TRIBUTE, SELECT_SUM: the chosen candidate indices. */
    public static byte[] cards(List<Integer> indices) {
        ByteBuffer b = buffer(8 + 4 * indices.size());
        b.putInt(0).putInt(indices.size());
        indices.forEach(b::putInt);
        return b.array();
    }

    /** SELECT_CARD, SELECT_TRIBUTE and SELECT_UNSELECT_CARD: cancel or finish. */
    public static byte[] cancel() {
        return int32(-1);
    }

    /** SELECT_UNSELECT_CARD: index into selectable cards followed by unselectable ones. */
    public static byte[] toggleCard(int index) {
        return buffer(8).putInt(1).putInt(index).array();
    }

    public record Zone(int player, int location, int sequence) {
    }

    /** SELECT_PLACE and SELECT_DISFIELD. The player is absolute (0 or 1), not relative to the chooser. */
    public static byte[] zones(List<Zone> zones) {
        ByteBuffer b = buffer(3 * zones.size());
        for (Zone zone : zones) {
            b.put((byte) zone.player()).put((byte) zone.location()).put((byte) zone.sequence());
        }
        return b.array();
    }

    /** SELECT_COUNTER: counters to remove from each candidate, in prompt order. */
    public static byte[] counters(List<Integer> perCard) {
        ByteBuffer b = buffer(2 * perCard.size());
        perCard.forEach(c -> b.putShort((short) (int) c));
        return b.array();
    }

    /** SORT_CARD and SORT_CHAIN: keep the default order. */
    public static byte[] defaultOrder() {
        return new byte[]{-1};
    }

    /** SORT_CARD and SORT_CHAIN: {@code order[i]} is the new position of card i. */
    public static byte[] order(List<Integer> order) {
        byte[] out = new byte[order.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (int) order.get(i);
        }
        return out;
    }

    /** ANNOUNCE_RACE. */
    public static byte[] race(long mask) {
        return buffer(8).putLong(mask).array();
    }

    /** ANNOUNCE_ATTRIB. */
    public static byte[] attribute(int mask) {
        return int32(mask);
    }

    /** ANNOUNCE_CARD. */
    public static byte[] cardCode(int code) {
        return int32(code);
    }

    /** ROCK_PAPER_SCISSORS: 1 scissors, 2 rock, 3 paper. */
    public static byte[] hand(int hand) {
        return int32(hand);
    }

    private static byte[] int32(int value) {
        return buffer(4).putInt(value).array();
    }

    private static ByteBuffer buffer(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }
}
