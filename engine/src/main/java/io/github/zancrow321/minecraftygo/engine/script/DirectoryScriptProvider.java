package io.github.zancrow321.minecraftygo.engine.script;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Serves scripts from a ProjectIgnis/CardScripts checkout (or an override folder). The root folder and the given
 * sub folders are indexed once; when a name exists in several folders, the earlier folder in {@code searchOrder}
 * wins (the root folder always comes first).
 */
public final class DirectoryScriptProvider implements ScriptProvider {
	/** Folder order for standard duels. */
	public static final List<String> DEFAULT_ORDER = List.of("official", "goat", "pre-errata", "unofficial", "skill");

	private final Map<String, Path> index;

	public DirectoryScriptProvider(Path root, List<String> searchOrder) {
		Map<String, Path> files = new HashMap<>();
		indexFolder(root, files);
		for (String folder : searchOrder) {
			Path dir = root.resolve(folder);
			if (Files.isDirectory(dir)) {
				indexFolder(dir, files);
			}
		}
		this.index = Map.copyOf(files);
	}

	private static void indexFolder(Path dir, Map<String, Path> files) {
		try (Stream<Path> stream = Files.list(dir)) {
			stream.filter(p -> p.getFileName().toString().endsWith(".lua") && Files.isRegularFile(p))
					.forEach(p -> files.putIfAbsent(p.getFileName().toString(), p));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to index scripts in " + dir, e);
		}
	}

	public int size() {
		return index.size();
	}

	@Override
	public Optional<byte[]> read(String name) {
		Path path = index.get(name);
		if (path == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(LuaSource.stripBom(Files.readAllBytes(path)));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read script " + path, e);
		}
	}
}
