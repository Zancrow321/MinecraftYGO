package io.github.zancrow321.minecraftygo.tools.bbmodel;

import com.google.gson.JsonObject;
import io.github.zancrow321.minecraftygo.engine.data.CardData;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.engine.data.JsonCardDatabase;
import io.github.zancrow321.minecraftygo.tools.pool.ModelFolders;
import io.github.zancrow321.minecraftygo.tools.util.Json;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Converts the YGOMCModels figures of the card pool to GeckoLib assets and computes their presentation transform:
 * every figure is scaled down (never up) to fit a zone footprint and a height limit, its feet placed on y = 0.
 */
public final class ConvertModels {
	static final String NAMESPACE = "minecraftygo";
	/** Max footprint (width/depth) in blocks; figures may reach into neighbouring zones (they hover as holograms). */
	static final double FOOTPRINT = 2.2;
	/** Max height in blocks; level 7+ monsters may be taller. */
	static final double HEIGHT = 2.5;
	static final double BIG_MONSTER_FACTOR = 1.3;
	static final int HEAVY_CUBES = 1500;

	public static void main(String[] args) throws IOException {
		if (args.length != 4) {
			throw new IllegalArgumentException("usage: ConvertModels <ygomcmodelsDir> <dataDir> <pool.json> <modGeneratedResources>");
		}
		Path modelsDir = Path.of(args[0]);
		Path dataDir = Path.of(args[1]);
		Path poolJson = Path.of(args[2]);
		Path assets = Path.of(args[3]).resolve("assets").resolve(NAMESPACE);

		CardPool pool;
		try (Reader reader = Files.newBufferedReader(poolJson)) {
			pool = CardPool.read(reader);
		}
		JsonCardDatabase cards;
		try (Reader reader = Files.newBufferedReader(poolJson.resolveSibling("cards.json"))) {
			cards = JsonCardDatabase.read(reader);
		}
		JsonObject overrides = Json.read(dataDir.resolve("model-overrides.json")).getAsJsonObject().getAsJsonObject("folders");

		Path geoDir = assets.resolve("geckolib/models/monster");
		Path animDir = assets.resolve("geckolib/animations/monster");
		Path textureDir = assets.resolve("textures/monster");
		for (Path dir : List.of(geoDir, animDir, textureDir)) {
			deleteDirectory(dir);
			Files.createDirectories(dir);
		}

		java.util.Map<String, ModelFolders> folders = new java.util.HashMap<>();
		ModelFolders.scan(modelsDir).forEach(f -> folders.put(f.name(), f));
		TreeMap<Integer, JsonObject> index = new TreeMap<>();
		List<String> warnings = new ArrayList<>();
		StringBuilder attribution = new StringBuilder();
		for (CardPool.Entry entry : pool.entries()) {
			if (!entry.hasModel()) {
				continue;
			}
			ModelFolders folder = Optional.ofNullable(folders.get(entry.model()))
					.orElseThrow(() -> new IllegalStateException("Model folder missing: " + entry.model()));
			JsonObject override = overrides.has(folder.name()) ? overrides.getAsJsonObject(folder.name()) : new JsonObject();
			int code = entry.code();
			BbModel model = BbModel.read(folder.bbmodel());
			GeoConverter.Result geo = GeoConverter.convert(model, "geometry." + NAMESPACE + "." + code);
			geo.warnings().forEach(w -> warnings.add(folder.name() + ": " + w));

			Optional<JsonObject> animation = AnimationConverter.idle(folder.animation(), model, GeoConverter.boneNames(geo),
					geo.renamedBones());
			AnimationConverter.Pose pose = animation.map(AnimationConverter::pose).orElse(AnimationConverter.Pose.NONE);
			Bounds bounds = Bounds.of(geo.bones(), pose);

			CardData data = cards.find(code).orElseThrow();
			double heightLimit = HEIGHT * (data.level() >= 7 ? BIG_MONSTER_FACTOR : 1) * 16;
			double footprint = Math.max(bounds.width(), bounds.depth());
			double scale = Math.min(1.0, Math.min(footprint > 0 ? FOOTPRINT * 16 / footprint : 1, bounds.height() > 0 ? heightLimit / bounds.height() : 1));
			if (override.has("scaleMul")) {
				scale *= override.get("scaleMul").getAsDouble();
			}
			scale = Math.max(0.02, Math.min(1.0, scale));

			JsonObject description = geo.geometry().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
					.getAsJsonObject("description");
			description.addProperty("visible_bounds_width", GeoConverter.round(footprint / 16 + 0.5));
			description.addProperty("visible_bounds_height", GeoConverter.round(bounds.height() / 16 + 0.5));
			description.add("visible_bounds_offset", GeoConverter.array(0, bounds.height() / 32, 0));

			Json.write(geoDir.resolve(code + ".geo.json"), Json.COMPACT.toJson(geo.geometry()) + "\n");
			animation.ifPresent(a -> Json.write(animDir.resolve(code + ".animation.json"), Json.PRETTY.toJson(a) + "\n"));
			writeTexture(folder, model, textureDir.resolve(code + ".png"));

			JsonObject info = new JsonObject();
			info.addProperty("folder", folder.name());
			info.addProperty("name", entry.name());
			info.addProperty("scale", GeoConverter.round(scale));
			// Render space (Bedrock): X is mirrored relative to Blockbench, hence +center for X.
			info.add("offset", GeoConverter.array((bounds.minX() + bounds.maxX()) / 2, -bounds.minY(), -(bounds.minZ() + bounds.maxZ()) / 2));
			info.add("size", GeoConverter.array(bounds.width() / 16, bounds.height() / 16, bounds.depth() / 16));
			info.addProperty("cubes", geo.cubeCount());
			info.addProperty("hasIdle", animation.isPresent());
			if (geo.cubeCount() > HEAVY_CUBES) {
				info.addProperty("heavy", true);
			}
			if (override.has("yawOffset")) {
				info.addProperty("yawOffset", override.get("yawOffset").getAsDouble());
			}
			index.put(code, info);
			attribution.append("| ").append(folder.name()).append(" | ").append(entry.name().replace("|", "\\|")).append(" | ")
					.append(code).append(" |\n");
		}

		JsonObject root = new JsonObject();
		root.addProperty("_comment", "Generated by :tools:convertModels. Render transform: scale(scale), then translate(offset / 16) "
				+ "in GeckoLib model space; size is the unscaled idle-pose bounding box in blocks.");
		root.addProperty("format", 1);
		JsonObject models = new JsonObject();
		index.forEach((code, info) -> models.add(Integer.toString(code), info));
		root.add("models", models);
		Json.write(assets.resolve("ygo/monster_models.json"), Json.PRETTY.toJson(root) + "\n");

		Path license = modelsDir.resolve("LICENSE.md");
		Files.createDirectories(assets.resolve("ygomcmodels"));
		Files.copy(license, assets.resolve("ygomcmodels/LICENSE.md"), StandardCopyOption.REPLACE_EXISTING);
		String commit = Files.readString(modelsDir.resolve(".git/ygo-commit")).trim();
		Json.write(assets.resolve("ygomcmodels/ATTRIBUTION.md"), "# YGOMCModels attribution\n\n"
				+ "The monster figures are converted from [YGOMCModels](https://github.com/iconmaster5326/YGOMCModels) by iconmaster "
				+ "(MIT License, see LICENSE.md), commit `" + commit + "`.\n\n| Folder | Card | Code |\n|---|---|---|\n" + attribution);

		warnings.forEach(w -> System.out.println("WARN " + w));
		System.out.println("Converted " + index.size() + " models (" + warnings.size() + " warnings)");
	}

	private static void writeTexture(ModelFolders folder, BbModel model, Path target) throws IOException {
		if (folder.texture().isPresent()) {
			Files.copy(folder.texture().get(), target, StandardCopyOption.REPLACE_EXISTING);
			return;
		}
		String source = model.firstTextureSource;
		if (source == null || !source.startsWith("data:image/png;base64,")) {
			throw new IllegalStateException("No texture for " + folder.name());
		}
		Files.write(target, Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1)));
	}

	private static void deleteDirectory(Path dir) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> files = Files.walk(dir)) {
			files.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.delete(path);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
