package io.github.zancrow321.minecraftygo.engine.script;

import java.util.Arrays;

final class LuaSource {
	private LuaSource() {}

	/** Lua cannot parse a UTF-8 byte order mark, which some script files carry. */
	static byte[] stripBom(byte[] source) {
		if (source.length >= 3 && (source[0] & 0xFF) == 0xEF && (source[1] & 0xFF) == 0xBB && (source[2] & 0xFF) == 0xBF) {
			return Arrays.copyOfRange(source, 3, source.length);
		}
		return source;
	}
}
