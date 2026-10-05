package io.github.zancrow321.minecraftygo.engine.script;

import java.util.Optional;

/**
 * Supplies Lua scripts by file name as requested by ocgcore ({@code constant.lua}, {@code utility.lua},
 * {@code proc_*.lua}, {@code c<code>.lua}). Names never contain path separators. Implementations must be thread-safe.
 */
public interface ScriptProvider {
	Optional<byte[]> read(String name);
}
