package io.github.zancrow321.jadm.engine;

import com.sun.jna.Native;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Finds the ocgcore shared library for this platform and loads it through JNA.
 *
 * <p>Libraries are bundled as classpath resources at {@code natives/<platform>/<file>}. Set the system property
 * {@value #OVERRIDE_PROPERTY} to an absolute path to load a different build instead.
 */
final class NativeLoader {
    static final String OVERRIDE_PROPERTY = "jadm.ocgcore.library";

    private NativeLoader() {
    }

    static OcgCoreLibrary load() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        Path library = override != null ? Path.of(override) : extract(Platform.current());
        return Native.load(library.toAbsolutePath().toString(), OcgCoreLibrary.class);
    }

    private static Path extract(Platform platform) {
        String resource = "/natives/" + platform.directory() + "/" + platform.fileName();
        try (InputStream in = NativeLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("ocgcore is not bundled for " + platform.directory()
                        + " (missing resource " + resource + ")");
            }
            Path dir = Files.createTempDirectory("jadm-ocgcore");
            Path file = dir.resolve(platform.fileName());
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            file.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not extract " + resource, e);
        }
    }

    record Platform(String directory, String fileName) {
        static Platform current() {
            return of(System.getProperty("os.name"), System.getProperty("os.arch"));
        }

        static Platform of(String osName, String osArch) {
            String os = osName.toLowerCase(Locale.ROOT);
            String arch = switch (osArch.toLowerCase(Locale.ROOT)) {
                case "amd64", "x86_64" -> "x86_64";
                case "aarch64", "arm64" -> "aarch64";
                default -> throw new UnsatisfiedLinkError("Unsupported CPU architecture: " + osArch);
            };
            if (os.contains("win")) {
                return new Platform("windows-" + arch, "ocgcore.dll");
            }
            if (os.contains("mac") || os.contains("darwin")) {
                // A single universal binary covers both architectures.
                return new Platform("macos", "libocgcore.dylib");
            }
            if (os.contains("linux")) {
                return new Platform("linux-" + arch, "libocgcore.so");
            }
            throw new UnsatisfiedLinkError("Unsupported operating system: " + osName);
        }
    }
}
