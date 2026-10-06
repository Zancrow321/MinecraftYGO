package io.github.zancrow321.jadm.engine.data;

import io.github.zancrow321.jadm.engine.ScriptProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Serves the Lua scripts bundled under {@value #ROOT}. The core may ask for paths like {@code ./script/c123.lua};
 * only the file name is used.
 */
public final class BundledScripts implements ScriptProvider {
    public static final String ROOT = "/jadm/scripts/";

    @Override
    public byte[] read(String name) {
        String file = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        if (file.isEmpty() || file.contains("..")) {
            return null;
        }
        try (InputStream in = BundledScripts.class.getResourceAsStream(ROOT + file)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
