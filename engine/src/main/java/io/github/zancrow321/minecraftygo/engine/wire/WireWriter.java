package io.github.zancrow321.minecraftygo.engine.wire;

import java.io.ByteArrayOutputStream;

/** Little-endian builder for responses (and test messages). */
public final class WireWriter {
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	public WireWriter u8(int value) {
		out.write(value & 0xFF);
		return this;
	}

	public WireWriter u16(int value) {
		out.write(value & 0xFF);
		out.write((value >>> 8) & 0xFF);
		return this;
	}

	public WireWriter i32(int value) {
		for (int i = 0; i < 4; i++) {
			out.write((value >>> (8 * i)) & 0xFF);
		}
		return this;
	}

	public WireWriter i64(long value) {
		for (int i = 0; i < 8; i++) {
			out.write((int) ((value >>> (8 * i)) & 0xFF));
		}
		return this;
	}

	public WireWriter bytes(byte[] value) {
		out.writeBytes(value);
		return this;
	}

	public WireWriter locInfo(LocInfo loc) {
		return u8(loc.controller()).u8(loc.location()).i32(loc.sequence()).i32(loc.position());
	}

	public int size() {
		return out.size();
	}

	public byte[] toByteArray() {
		return out.toByteArray();
	}
}
