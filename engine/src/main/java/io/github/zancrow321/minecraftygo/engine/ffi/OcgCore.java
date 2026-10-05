package io.github.zancrow321.minecraftygo.engine.ffi;

import java.nio.file.Path;

/** The ocgcore C API with Java types. Implementations are thread-safe; individual duels are not. */
public interface OcgCore {
	int versionMajor();

	int versionMinor();

	NativeDuel createDuel(DuelOptions options, DuelDataSource dataSource);

	/** Loads the shared library at {@code library} and verifies the API version. */
	static OcgCore load(Path library) {
		return FfmOcgCore.load(library);
	}
}
