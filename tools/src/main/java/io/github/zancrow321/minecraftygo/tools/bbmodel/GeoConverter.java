package io.github.zancrow321.minecraftygo.tools.bbmodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts a {@link BbModel} into Bedrock geometry ({@code format_version 1.12.0}) as loaded by GeckoLib - a port of
 * the Blockbench Bedrock exporter (Blockbench is GPL-3.0; https://github.com/JannisX11/blockbench):
 * <ul>
 *   <li>bone pivot = group origin with X negated; bone rotation = group rotation with X and Y negated</li>
 *   <li>cube origin = {@code [-(from.x + size.x), from.y, from.z]}; cube pivot/rotation like bones</li>
 *   <li>box UV: {@code uv = uv_offset}, {@code mirror} when the cube mirrors its UV; per-face UV with
 *       {@code uv_size}, up/down faces flipped</li>
 * </ul>
 * All top-level groups and loose cubes are wrapped in the synthetic bone {@value #ROOT_BONE} (pivot 0,0,0), which the
 * shared summon/attack animations move.
 */
public final class GeoConverter {
	public static final String ROOT_BONE = "ygo_root";

	/** A converted bone in Blockbench coordinates (for bounds computation). */
	public record Bone(String name, String parent, double[] pivot, double[] rotation, List<BbModel.Cube> cubes) {}

	public record Result(JsonObject geometry, List<Bone> bones, Map<String, String> renamedBones, int cubeCount,
			List<String> warnings) {}

	private GeoConverter() {}

	public static Result convert(BbModel model, String identifier) {
		List<Bone> bones = new ArrayList<>();
		Set<String> usedNames = new HashSet<>();
		Map<String, String> renamed = new LinkedHashMap<>();
		List<String> warnings = new ArrayList<>();
		usedNames.add(ROOT_BONE);
		List<BbModel.Cube> rootCubes = new ArrayList<>();
		bones.add(new Bone(ROOT_BONE, null, new double[3], new double[3], rootCubes));
		for (Object node : model.roots) {
			collect(model, node, ROOT_BONE, bones, rootCubes, usedNames, renamed, warnings);
		}

		JsonArray boneArray = new JsonArray();
		int cubeCount = 0;
		for (Bone bone : bones) {
			JsonObject b = new JsonObject();
			b.addProperty("name", bone.name());
			if (bone.parent() != null) {
				b.addProperty("parent", bone.parent());
			}
			b.add("pivot", array(-bone.pivot()[0], bone.pivot()[1], bone.pivot()[2]));
			if (!isZero(bone.rotation())) {
				b.add("rotation", array(-bone.rotation()[0], -bone.rotation()[1], bone.rotation()[2]));
			}
			JsonArray cubes = new JsonArray();
			for (BbModel.Cube cube : bone.cubes()) {
				cubes.add(cube(cube, model, warnings));
				cubeCount++;
			}
			if (!cubes.isEmpty()) {
				b.add("cubes", cubes);
			}
			boneArray.add(b);
		}

		JsonObject description = new JsonObject();
		description.addProperty("identifier", identifier);
		description.addProperty("texture_width", model.textureWidth);
		description.addProperty("texture_height", model.textureHeight);
		JsonObject geometryEntry = new JsonObject();
		geometryEntry.add("description", description);
		geometryEntry.add("bones", boneArray);
		JsonArray geometries = new JsonArray();
		geometries.add(geometryEntry);
		JsonObject root = new JsonObject();
		root.addProperty("format_version", "1.12.0");
		root.add("minecraft:geometry", geometries);
		return new Result(root, bones, renamed, cubeCount, warnings);
	}

	private static void collect(BbModel model, Object node, String parent, List<Bone> bones, List<BbModel.Cube> parentCubes,
			Set<String> usedNames, Map<String, String> renamed, List<String> warnings) {
		if (node instanceof String uuid) {
			BbModel.Cube cube = model.cubes.get(uuid);
			if (cube == null) {
				warnings.add("outliner references missing cube " + uuid);
			} else if (cube.export()) {
				parentCubes.add(cube);
			}
			return;
		}
		BbModel.Group group = (BbModel.Group) node;
		if (!group.export()) {
			return;
		}
		String name = group.name();
		if (!usedNames.add(name)) {
			int suffix = 2;
			while (!usedNames.add(name + "_" + suffix)) {
				suffix++;
			}
			String unique = name + "_" + suffix;
			renamed.put(group.uuid(), unique);
			warnings.add("duplicate bone name " + name + " renamed to " + unique);
			name = unique;
		}
		List<BbModel.Cube> cubes = new ArrayList<>();
		bones.add(new Bone(name, parent, group.origin(), group.rotation(), cubes));
		for (Object child : group.children()) {
			collect(model, child, name, bones, cubes, usedNames, renamed, warnings);
		}
	}

	private static JsonObject cube(BbModel.Cube cube, BbModel model, List<String> warnings) {
		double[] size = cube.size();
		JsonObject c = new JsonObject();
		c.add("origin", array(-(cube.from()[0] + size[0]), cube.from()[1], cube.from()[2]));
		c.add("size", array(size[0], size[1], size[2]));
		if (cube.inflate() != 0) {
			c.addProperty("inflate", round(cube.inflate()));
		}
		if (!isZero(cube.rotation())) {
			c.add("pivot", array(-cube.origin()[0], cube.origin()[1], cube.origin()[2]));
			c.add("rotation", array(-cube.rotation()[0], -cube.rotation()[1], cube.rotation()[2]));
		}
		if (cube.boxUv()) {
			c.add("uv", array(cube.uvOffset()[0], cube.uvOffset()[1]));
			if (cube.mirrorUv()) {
				c.addProperty("mirror", true);
			}
			double width = 2 * (Math.floor(size[0]) + Math.floor(size[2]));
			double height = Math.floor(size[1]) + Math.floor(size[2]);
			if (cube.uvOffset()[0] + width > model.textureWidth || cube.uvOffset()[1] + height > model.textureHeight) {
				warnings.add("box UV of cube " + cube.name() + " exceeds the texture");
			}
		} else {
			JsonObject uv = new JsonObject();
			for (var entry : cube.faces().entrySet()) {
				BbModel.Face face = entry.getValue();
				if (face.texture() == null) {
					continue;
				}
				double[] f = face.uv();
				double u = f[0];
				double v = f[1];
				double uSize = f[2] - f[0];
				double vSize = f[3] - f[1];
				if (entry.getKey().equals("up") || entry.getKey().equals("down")) {
					u += uSize;
					v += vSize;
					uSize = -uSize;
					vSize = -vSize;
				}
				JsonObject mapping = new JsonObject();
				mapping.add("uv", array(u, v));
				mapping.add("uv_size", array(uSize, vSize));
				if (face.rotation() != 0) {
					mapping.addProperty("uv_rotation", face.rotation());
				}
				uv.add(entry.getKey(), mapping);
			}
			c.add("uv", uv);
		}
		return c;
	}

	static boolean isZero(double[] values) {
		for (double value : values) {
			if (value != 0) {
				return false;
			}
		}
		return true;
	}

	static double round(double value) {
		double rounded = Math.round(value * 10_000d) / 10_000d;
		return rounded == 0 ? 0 : rounded;
	}

	static JsonArray array(double... values) {
		JsonArray array = new JsonArray();
		for (double value : values) {
			double rounded = round(value);
			if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e9) {
				array.add((long) rounded);
			} else {
				array.add(rounded);
			}
		}
		return array;
	}

	/** Names of all bones, for filtering animations. */
	public static Set<String> boneNames(Result result) {
		Set<String> names = new HashSet<>();
		result.bones().forEach(b -> names.add(b.name()));
		return names;
	}

	static Map<String, Bone> byName(List<Bone> bones) {
		Map<String, Bone> map = new HashMap<>();
		bones.forEach(b -> map.put(b.name(), b));
		return map;
	}
}
