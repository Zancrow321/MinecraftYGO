package io.github.zancrow321.minecraftygo.engine.prompt;

/** An activatable effect: the card, its effect description ({@code (code << 20) | index}) and client mode. */
public record ChainOption(CardRef card, long description, int clientMode) {}
