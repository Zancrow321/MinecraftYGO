package io.github.zancrow321.minecraftygo.engine.prompt;

import io.github.zancrow321.minecraftygo.engine.wire.LocInfo;

/**
 * A card as referenced by a prompt. {@code code} may be 0 for cards whose identity is hidden from the prompted
 * player; {@code position} is 0 when the message does not carry it.
 */
public record CardRef(int code, int controller, int location, int sequence, int position) {
	public static CardRef of(int code, LocInfo loc) {
		return new CardRef(code, loc.controller(), loc.location(), loc.sequence(), loc.position());
	}
}
