package io.github.zancrow321.minecraftygo.engine;

import com.sun.jna.ptr.IntByReference;

/**
 * Entry point to the native OCG-Core duel engine.
 */
public final class OcgCore {
    /** The {@code OCG_VERSION_MAJOR} these bindings were written against. */
    public static final int SUPPORTED_MAJOR_VERSION = 11;

    private static volatile OcgCore instance;

    final OcgCoreLibrary lib;
    private final Version version;

    private OcgCore(OcgCoreLibrary lib) {
        this.lib = lib;
        IntByReference major = new IntByReference();
        IntByReference minor = new IntByReference();
        lib.OCG_GetVersion(major, minor);
        this.version = new Version(major.getValue(), minor.getValue());
        if (version.major() != SUPPORTED_MAJOR_VERSION) {
            throw new IllegalStateException("ocgcore API " + version + " is incompatible; expected "
                    + SUPPORTED_MAJOR_VERSION + ".x");
        }
    }

    /**
     * Loads the native library on first use.
     *
     * @throws UnsatisfiedLinkError if no library is available for this platform
     */
    public static OcgCore get() {
        OcgCore core = instance;
        if (core == null) {
            synchronized (OcgCore.class) {
                core = instance;
                if (core == null) {
                    core = instance = new OcgCore(NativeLoader.load());
                }
            }
        }
        return core;
    }

    public Version version() {
        return version;
    }

    /**
     * Creates a duel. The returned duel is not thread-safe: drive it from one thread at a time.
     *
     * @throws DuelCreationException if the core rejects the options
     */
    public OcgDuel createDuel(DuelSettings settings, CardDataProvider cards, ScriptProvider scripts,
                              DuelLogHandler log) {
        return new OcgDuel(this, settings, cards, scripts, log);
    }

    public record Version(int major, int minor) {
        @Override
        public String toString() {
            return major + "." + minor;
        }
    }
}
