package io.github.zancrow321.minecraftygo.engine.testing;

import io.github.zancrow321.minecraftygo.engine.duel.EngineDataSource;
import io.github.zancrow321.minecraftygo.engine.ffi.OcgCore;
import io.github.zancrow321.minecraftygo.engine.natives.NativeLibraryLoader;
import io.github.zancrow321.minecraftygo.engine.script.DirectoryScriptProvider;
import io.github.zancrow321.minecraftygo.engine.script.ScriptProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared, lazily initialised test resources: the host-built ocgcore (system property {@code ygo.ocgcore.path}) and
 * the pinned BabelCDB / CardScripts checkouts under {@code ygo.upstream.dir} (prepared by the Gradle test task).
 */
public final class TestEnvironment {
	private static OcgCore core;
	private static SqliteCardDatabase database;
	private static ScriptProvider scripts;

	private TestEnvironment() {}

	public static synchronized OcgCore core() {
		if (core == null) {
			core = OcgCore.load(NativeLibraryLoader.locate(Path.of("build", "natives-extracted")));
		}
		return core;
	}

	public static Path upstream(String name) {
		String dir = System.getProperty("ygo.upstream.dir");
		if (dir == null) {
			throw new IllegalStateException("System property ygo.upstream.dir is not set (run tests through Gradle)");
		}
		Path path = Path.of(dir, name);
		if (!Files.isDirectory(path)) {
			throw new IllegalStateException("Upstream checkout missing: " + path + " (run ./gradlew syncUpstreams)");
		}
		return path;
	}

	public static synchronized SqliteCardDatabase database() {
		if (database == null) {
			Path cdb = upstream("babelCdb");
			database = new SqliteCardDatabase(List.of(cdb.resolve("cards.cdb"), cdb.resolve("goat-entries.cdb")));
		}
		return database;
	}

	public static synchronized ScriptProvider scripts() {
		if (scripts == null) {
			scripts = new DirectoryScriptProvider(upstream("cardScripts"), DirectoryScriptProvider.DEFAULT_ORDER);
		}
		return scripts;
	}

	public static EngineDataSource dataSource() {
		return new EngineDataSource(database(), scripts());
	}
}
