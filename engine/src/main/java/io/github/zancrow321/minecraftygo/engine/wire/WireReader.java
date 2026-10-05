package io.github.zancrow321.minecraftygo.engine.wire;

/** Little-endian cursor over a core message or query buffer. */
public final class WireReader {
	private final byte[] data;
	private final int end;
	private int position;

	public WireReader(byte[] data) {
		this(data, 0, data.length);
	}

	public WireReader(byte[] data, int offset, int length) {
		if (offset < 0 || length < 0 || offset + length > data.length) {
			throw new IllegalArgumentException("Invalid slice " + offset + "+" + length + " of " + data.length);
		}
		this.data = data;
		this.position = offset;
		this.end = offset + length;
	}

	public int position() {
		return position;
	}

	public int remaining() {
		return end - position;
	}

	public boolean hasRemaining() {
		return position < end;
	}

	private void require(int bytes) {
		if (end - position < bytes) {
			throw new WireFormatException("Need " + bytes + " bytes at offset " + position + ", only " + (end - position) + " left");
		}
	}

	public int u8() {
		require(1);
		return data[position++] & 0xFF;
	}

	public boolean bool() {
		return u8() != 0;
	}

	public int u16() {
		require(2);
		int value = (data[position] & 0xFF) | (data[position + 1] & 0xFF) << 8;
		position += 2;
		return value;
	}

	public int i16() {
		return (short) u16();
	}

	/** Reads a uint32/int32 as a Java int (callers interpret signedness). */
	public int i32() {
		require(4);
		int value = (data[position] & 0xFF) | (data[position + 1] & 0xFF) << 8 | (data[position + 2] & 0xFF) << 16
				| (data[position + 3] & 0xFF) << 24;
		position += 4;
		return value;
	}

	public long u32() {
		return Integer.toUnsignedLong(i32());
	}

	public long i64() {
		long low = Integer.toUnsignedLong(i32());
		long high = Integer.toUnsignedLong(i32());
		return low | high << 32;
	}

	public byte[] bytes(int count) {
		require(count);
		byte[] copy = new byte[count];
		System.arraycopy(data, position, copy, 0, count);
		position += count;
		return copy;
	}

	public void skip(int count) {
		require(count);
		position += count;
	}

	/** {@code loc_info}: u8 controller, u8 location, u32 sequence, u32 position. */
	public LocInfo locInfo() {
		int controller = u8();
		int location = u8();
		int sequence = i32();
		int pos = i32();
		return new LocInfo(controller, location, sequence, pos);
	}
}
