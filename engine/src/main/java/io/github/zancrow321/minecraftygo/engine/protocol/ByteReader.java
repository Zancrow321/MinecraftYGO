package io.github.zancrow321.minecraftygo.engine.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Little-endian reader over one OCG-Core message or query payload.
 */
final class ByteReader {
    private final ByteBuffer buffer;

    ByteReader(byte[] data, int offset, int length) {
        this.buffer = ByteBuffer.wrap(data, offset, length).slice().order(ByteOrder.LITTLE_ENDIAN);
    }

    ByteReader(byte[] data) {
        this(data, 0, data.length);
    }

    int u8() {
        return Byte.toUnsignedInt(buffer.get());
    }

    boolean bool() {
        return buffer.get() != 0;
    }

    int u16() {
        return Short.toUnsignedInt(buffer.getShort());
    }

    /** A u32 or i32 as a Java int; callers treat it as unsigned where the value can exceed 2^31. */
    int i32() {
        return buffer.getInt();
    }

    long u64() {
        return buffer.getLong();
    }

    /** The core's 10-byte {@code loc_info}. */
    Loc loc() {
        return new Loc(u8(), u8(), i32(), i32());
    }

    /** The 6-byte controller/location/u32-sequence tuple, with no position. */
    Loc loc6() {
        return new Loc(u8(), u8(), i32(), 0);
    }

    /** The 3-byte controller/location/u8-sequence tuple, with no position. */
    Loc loc3() {
        return new Loc(u8(), u8(), u8(), 0);
    }

    byte[] bytes(int count) {
        byte[] out = new byte[count];
        buffer.get(out);
        return out;
    }

    void skip(int count) {
        buffer.position(buffer.position() + count);
    }

    int remaining() {
        return buffer.remaining();
    }
}
