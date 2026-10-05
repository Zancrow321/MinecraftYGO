package io.github.zancrow321.minecraftygo.engine.wire;

/** A card location as encoded by the core ({@code loc_info}). */
public record LocInfo(int controller, int location, int sequence, int position) {}
