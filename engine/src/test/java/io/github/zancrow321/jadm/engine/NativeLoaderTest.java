package io.github.zancrow321.jadm.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeLoaderTest {
    @Test
    void mapsSupportedPlatforms() {
        assertEquals(new NativeLoader.Platform("linux-x86_64", "libocgcore.so"),
                NativeLoader.Platform.of("Linux", "amd64"));
        assertEquals(new NativeLoader.Platform("linux-aarch64", "libocgcore.so"),
                NativeLoader.Platform.of("Linux", "aarch64"));
        assertEquals(new NativeLoader.Platform("windows-x86_64", "ocgcore.dll"),
                NativeLoader.Platform.of("Windows 11", "amd64"));
        assertEquals(new NativeLoader.Platform("macos", "libocgcore.dylib"),
                NativeLoader.Platform.of("Mac OS X", "aarch64"));
        assertEquals(new NativeLoader.Platform("macos", "libocgcore.dylib"),
                NativeLoader.Platform.of("Mac OS X", "x86_64"));
    }

    @Test
    void rejectsUnsupportedPlatforms() {
        assertThrows(UnsatisfiedLinkError.class, () -> NativeLoader.Platform.of("Linux", "riscv64"));
        assertThrows(UnsatisfiedLinkError.class, () -> NativeLoader.Platform.of("SunOS", "amd64"));
    }
}
