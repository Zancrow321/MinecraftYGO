package io.github.zancrow321.minecraftygo.engine.natives;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Locates the ocgcore shared library: either an explicit path (system property {@value #PATH_PROPERTY}, used by
 * tests and for custom builds) or the library bundled under {@code natives/<platform>/} on the classpath, which is
 * extracted into {@code <extractionRoot>/<sha256>/} so different versions never overwrite each other.
 */
public final class NativeLibraryLoader {
	public static final String PATH_PROPERTY = "ygo.ocgcore.path";

	private NativeLibraryLoader() {}

	public static Path locate(Path extractionRoot) {
		String override = System.getProperty(PATH_PROPERTY);
		if (override != null && !override.isBlank()) {
			Path path = Path.of(override).toAbsolutePath();
			if (!Files.isRegularFile(path)) {
				throw new IllegalStateException(PATH_PROPERTY + " points to a missing file: " + path);
			}
			return path;
		}
		return extractBundled(Platform.current(), extractionRoot, NativeLibraryLoader.class.getClassLoader());
	}

	static Path extractBundled(Platform platform, Path extractionRoot, ClassLoader classLoader) {
		byte[] library;
		try (InputStream in = classLoader.getResourceAsStream(platform.resourcePath())) {
			if (in == null) {
				throw new IllegalStateException("No bundled ocgcore library for platform " + platform.id());
			}
			library = in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read bundled ocgcore library", e);
		}
		String hash = sha256(library);
		Path target = extractionRoot.resolve(hash).resolve(platform.libraryFileName());
		try {
			if (Files.isRegularFile(target) && sha256(Files.readAllBytes(target)).equals(hash)) {
				return target;
			}
			Files.createDirectories(target.getParent());
			Path temp = Files.createTempFile(target.getParent(), platform.libraryFileName(), ".tmp");
			Files.write(temp, library);
			try {
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return target;
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to extract ocgcore library to " + target, e);
		}
	}

	static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
