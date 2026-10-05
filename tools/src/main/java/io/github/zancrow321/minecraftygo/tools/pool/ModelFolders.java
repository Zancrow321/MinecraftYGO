package io.github.zancrow321.minecraftygo.tools.pool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * One monster folder of YGOMCModels. Files are located by extension rather than by folder name because the
 * upstream naming is inconsistent (e.g. {@code Tyhone2/Tyhone.bbmodel}).
 */
public record ModelFolders(String name, Path dir, Path bbmodel, Optional<Path> texture, Optional<Path> animation) {

	/** All monster folders: top level, not starting with '_' or '.', containing a .bbmodel. */
	public static List<ModelFolders> scan(Path root) {
		try (Stream<Path> dirs = Files.list(root)) {
			return dirs.filter(Files::isDirectory)
					.filter(d -> !d.getFileName().toString().startsWith("_") && !d.getFileName().toString().startsWith("."))
					.sorted()
					.map(ModelFolders::of)
					.flatMap(Optional::stream)
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static Optional<ModelFolders> of(Path dir) {
		try (Stream<Path> files = Files.list(dir)) {
			List<Path> list = files.filter(Files::isRegularFile).sorted().toList();
			Optional<Path> bbmodel = list.stream().filter(p -> p.toString().endsWith(".bbmodel")).findFirst();
			if (bbmodel.isEmpty()) {
				return Optional.empty();
			}
			Optional<Path> texture = list.stream().filter(p -> p.getFileName().toString().equals("texture.png")).findFirst();
			Optional<Path> animation = list.stream()
					.filter(p -> p.toString().endsWith(".json"))
					.filter(ModelFolders::isAnimationFile)
					.findFirst();
			return Optional.of(new ModelFolders(dir.getFileName().toString(), dir, bbmodel.get(), texture, animation));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean isAnimationFile(Path file) {
		try {
			String content = Files.readString(file);
			return content.contains("\"animations\"");
		} catch (IOException e) {
			return false;
		}
	}
}
