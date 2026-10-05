package io.github.zancrow321.minecraftygo.engine.protocol;

/**
 * A card named in a message: its passcode (0 when hidden) and location.
 */
public record CardRef(int code, Loc loc) {
}
