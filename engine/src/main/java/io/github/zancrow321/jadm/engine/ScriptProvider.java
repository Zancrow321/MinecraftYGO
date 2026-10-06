package io.github.zancrow321.jadm.engine;

/**
 * Supplies Lua card scripts (e.g. {@code c89631139.lua}, {@code constant.lua}) to the engine.
 */
@FunctionalInterface
public interface ScriptProvider {
    /**
     * @return the script's bytes, or {@code null} if no script by that name exists
     */
    byte[] read(String name);
}
