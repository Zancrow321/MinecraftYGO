package io.github.zancrow321.minecraftygo.engine.script;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Scripts from a zip archive (the bundled {@code scripts.zip} of the mod), loaded into memory once. Entries are
 * looked up by file name; the folder inside the archive only decides precedence (root files first, then the given
 * folder order, then everything else).
 */
public final class ZipScriptProvider implements ScriptProvider {
	private final Map<String, byte[]> scripts;

	private ZipScriptProvider(Map<String, byte[]> scripts) {
		this.scripts = Map.copyOf(scripts);
	}

	public static ZipScriptProvider read(InputStream zip, List<String> folderOrder) {
		Map<String, byte[]> byName = new HashMap<>();
		Map<String, Integer> rankByName = new HashMap<>();
		try (ZipInputStream in = new ZipInputStream(zip)) {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				String path = entry.getName();
				if (entry.isDirectory() || !path.endsWith(".lua")) {
					continue;
				}
				int slash = path.lastIndexOf('/');
				String folder = slash < 0 ? "" : path.substring(0, slash);
				String name = path.substring(slash + 1);
				int rank;
				if (folder.isEmpty()) {
					rank = -1;
				} else {
					int index = folderOrder.indexOf(folder);
					rank = index < 0 ? Integer.MAX_VALUE : index;
				}
				byte[] content = LuaSource.stripBom(in.readAllBytes());
				Integer existing = rankByName.get(name);
				if (existing == null || rank < existing) {
					byName.put(name, content);
					rankByName.put(name, rank);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read script archive", e);
		}
		return new ZipScriptProvider(byName);
	}

	public int size() {
		return scripts.size();
	}

	@Override
	public Optional<byte[]> read(String name) {
		byte[] script = scripts.get(name);
		return script == null ? Optional.empty() : Optional.of(script.clone());
	}
}
