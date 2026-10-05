package io.github.zancrow321.minecraftygo.tools.bbmodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parts of a Blockbench {@code .bbmodel} the converter needs: cubes, the group hierarchy (format 4.x nested
 * outliner groups and 5.x separate {@code groups} with an outliner of uuid references), resolution, the first texture
 * and embedded animations. Huge fields that are never needed ({@code reference_images}, {@code backgrounds}) are
 * skipped while streaming.
 */
public final class BbModel {
	private static final Set<String> SKIPPED = Set.of("reference_images", "backgrounds", "geckolib_filepath_cache");

	public record Face(double[] uv, Integer texture, int rotation) {}

	public record Cube(String uuid, String name, double[] from, double[] to, double[] origin, double[] rotation,
			double inflate, boolean boxUv, double[] uvOffset, boolean mirrorUv, Map<String, Face> faces, boolean export) {
		public double[] size() {
			return new double[] {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
		}
	}

	/** A group (bone); children are cubes (uuids) and nested groups, in outliner order. */
	public record Group(String uuid, String name, double[] origin, double[] rotation, boolean export, List<Object> children) {}

	public final String formatVersion;
	public final String modelFormat;
	public final int textureWidth;
	public final int textureHeight;
	public final Map<String, Cube> cubes;
	/** Top-level outliner entries: {@link Group}s and cube uuids (Strings). */
	public final List<Object> roots;
	public final String firstTextureSource;
	public final JsonArray animations;

	private BbModel(String formatVersion, String modelFormat, int textureWidth, int textureHeight, Map<String, Cube> cubes,
			List<Object> roots, String firstTextureSource, JsonArray animations) {
		this.formatVersion = formatVersion;
		this.modelFormat = modelFormat;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.cubes = cubes;
		this.roots = roots;
		this.firstTextureSource = firstTextureSource;
		this.animations = animations;
	}

	public boolean isFormat5OrNewer() {
		return Integer.parseInt(formatVersion.split("\\.")[0]) >= 5;
	}

	public static BbModel read(Path file) {
		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return read(in);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read " + file, e);
		}
	}

	public static BbModel read(Reader in) {
		Map<String, JsonElement> fields = new HashMap<>();
		try (JsonReader reader = new JsonReader(in)) {
			reader.beginObject();
			while (reader.hasNext()) {
				String key = reader.nextName();
				if (SKIPPED.contains(key)) {
					reader.skipValue();
				} else {
					fields.put(key, JsonParser.parseReader(reader));
				}
			}
			reader.endObject();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		JsonObject meta = fields.get("meta").getAsJsonObject();
		JsonObject resolution = fields.get("resolution").getAsJsonObject();

		Map<String, Cube> cubes = new LinkedHashMap<>();
		for (JsonElement element : fields.get("elements").getAsJsonArray()) {
			JsonObject e = element.getAsJsonObject();
			String type = e.has("type") ? e.get("type").getAsString() : "cube";
			if (!type.equals("cube")) {
				throw new IllegalArgumentException("Unsupported element type " + type);
			}
			Map<String, Face> faces = new LinkedHashMap<>();
			JsonObject faceObject = e.getAsJsonObject("faces");
			if (faceObject != null) {
				for (var entry : faceObject.entrySet()) {
					JsonObject f = entry.getValue().getAsJsonObject();
					Integer texture = f.has("texture") && !f.get("texture").isJsonNull()
							&& f.get("texture").getAsJsonPrimitive().isNumber() ? f.get("texture").getAsInt() : null;
					faces.put(entry.getKey(), new Face(vec(f.getAsJsonArray("uv"), 4), texture,
							f.has("rotation") ? f.get("rotation").getAsInt() : 0));
				}
			}
			boolean boxUv = !e.has("box_uv") || e.get("box_uv").getAsBoolean();
			Cube cube = new Cube(e.get("uuid").getAsString(), e.has("name") ? e.get("name").getAsString() : "cube",
					vec(e.getAsJsonArray("from"), 3), vec(e.getAsJsonArray("to"), 3),
					e.has("origin") ? vec(e.getAsJsonArray("origin"), 3) : new double[3],
					e.has("rotation") ? vec(e.getAsJsonArray("rotation"), 3) : new double[3],
					e.has("inflate") ? e.get("inflate").getAsDouble() : 0,
					boxUv,
					e.has("uv_offset") ? vec(e.getAsJsonArray("uv_offset"), 2) : new double[2],
					e.has("mirror_uv") && e.get("mirror_uv").getAsBoolean(),
					faces,
					!e.has("export") || e.get("export").getAsBoolean());
			cubes.put(cube.uuid(), cube);
		}

		Map<String, JsonObject> separateGroups = new HashMap<>();
		if (fields.containsKey("groups")) {
			for (JsonElement element : fields.get("groups").getAsJsonArray()) {
				JsonObject g = element.getAsJsonObject();
				separateGroups.put(g.get("uuid").getAsString(), g);
			}
		}
		List<Object> roots = new ArrayList<>();
		for (JsonElement node : fields.get("outliner").getAsJsonArray()) {
			roots.add(outlinerNode(node, separateGroups));
		}

		String textureSource = null;
		JsonElement textures = fields.get("textures");
		if (textures != null && !textures.getAsJsonArray().isEmpty()) {
			JsonObject first = textures.getAsJsonArray().get(0).getAsJsonObject();
			textureSource = first.has("source") ? first.get("source").getAsString() : null;
		}
		JsonArray animations = fields.containsKey("animations") ? fields.get("animations").getAsJsonArray() : new JsonArray();
		return new BbModel(meta.get("format_version").getAsString(),
				meta.has("model_format") ? meta.get("model_format").getAsString() : "",
				resolution.get("width").getAsInt(), resolution.get("height").getAsInt(), cubes, roots, textureSource, animations);
	}

	private static Object outlinerNode(JsonElement node, Map<String, JsonObject> separateGroups) {
		if (node.isJsonPrimitive()) {
			return node.getAsString();
		}
		JsonObject o = node.getAsJsonObject();
		String uuid = o.get("uuid").getAsString();
		// Format 5: properties live in the separate "groups" list; format 4: inline.
		JsonObject props = separateGroups.getOrDefault(uuid, o);
		List<Object> children = new ArrayList<>();
		JsonArray childArray = o.getAsJsonArray("children");
		if (childArray != null) {
			for (JsonElement child : childArray) {
				children.add(outlinerNode(child, separateGroups));
			}
		}
		return new Group(uuid, props.has("name") ? props.get("name").getAsString() : "bone",
				props.has("origin") ? vec(props.getAsJsonArray("origin"), 3) : new double[3],
				props.has("rotation") ? vec(props.getAsJsonArray("rotation"), 3) : new double[3],
				!props.has("export") || props.get("export").getAsBoolean(),
				children);
	}

	static double[] vec(JsonArray array, int size) {
		double[] values = new double[size];
		if (array != null) {
			for (int i = 0; i < size && i < array.size(); i++) {
				values[i] = number(array.get(i));
			}
		}
		return values;
	}

	/** Blockbench sometimes stores numbers as strings (keyframe values); non-numeric expressions become 0. */
	static double number(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return 0;
		}
		try {
			return element.getAsJsonPrimitive().isNumber() ? element.getAsDouble() : Double.parseDouble(element.getAsString().trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
