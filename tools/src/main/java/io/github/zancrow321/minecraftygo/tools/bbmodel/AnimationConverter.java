package io.github.zancrow321.minecraftygo.tools.bbmodel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zancrow321.minecraftygo.tools.util.Json;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Produces the GeckoLib animation file holding the model's static {@code idle} pose: taken from the exported
 * animation json next to the model, or (if missing) from the animation embedded in the bbmodel. Embedded keyframes
 * of Blockbench 5 projects store rotations with X/Y negated and positions with X negated relative to the Bedrock
 * export, so they are converted.
 */
public final class AnimationConverter {
	public static final String IDLE = "idle";

	private AnimationConverter() {}

	/** @return the animation json, or empty when the model has no pose at all */
	public static Optional<JsonObject> idle(Optional<Path> animationFile, BbModel model, Set<String> bones,
			Map<String, String> renamedByUuid) {
		JsonObject animation = animationFile.map(file -> fromFile(Json.read(file).getAsJsonObject()))
				.orElseGet(() -> fromEmbedded(model, renamedByUuid));
		if (animation == null) {
			return Optional.empty();
		}
		JsonObject filtered = new JsonObject();
		JsonObject sourceBones = animation.has("bones") ? animation.getAsJsonObject("bones") : new JsonObject();
		for (var entry : sourceBones.entrySet()) {
			if (bones.contains(entry.getKey())) {
				filtered.add(entry.getKey(), entry.getValue());
			}
		}
		JsonObject idle = new JsonObject();
		idle.addProperty("loop", true);
		idle.add("bones", filtered);
		JsonObject animations = new JsonObject();
		animations.add(IDLE, idle);
		JsonObject root = new JsonObject();
		root.addProperty("format_version", "1.8.0");
		root.add("animations", animations);
		root.addProperty("geckolib_format_version", 2);
		return Optional.of(root);
	}

	private static JsonObject fromFile(JsonObject file) {
		JsonObject animations = file.getAsJsonObject("animations");
		if (animations == null || animations.isEmpty()) {
			return null;
		}
		if (animations.has(IDLE)) {
			return animations.getAsJsonObject(IDLE);
		}
		return animations.entrySet().iterator().next().getValue().getAsJsonObject();
	}

	private static JsonObject fromEmbedded(BbModel model, Map<String, String> renamedByUuid) {
		JsonObject chosen = null;
		for (JsonElement element : model.animations) {
			JsonObject animation = element.getAsJsonObject();
			if (chosen == null || IDLE.equals(animation.get("name").getAsString())) {
				chosen = animation;
			}
		}
		if (chosen == null || !chosen.has("animators")) {
			return null;
		}
		boolean negate = model.isFormat5OrNewer();
		JsonObject bones = new JsonObject();
		for (var entry : chosen.getAsJsonObject("animators").entrySet()) {
			JsonObject animator = entry.getValue().getAsJsonObject();
			if (animator.has("type") && !"bone".equals(animator.get("type").getAsString())) {
				continue;
			}
			String name = renamedByUuid.getOrDefault(entry.getKey(), animator.get("name").getAsString());
			JsonObject bone = new JsonObject();
			for (JsonElement keyframeElement : animator.getAsJsonArray("keyframes")) {
				JsonObject keyframe = keyframeElement.getAsJsonObject();
				String channel = keyframe.get("channel").getAsString();
				if (bone.has(channel) || !(channel.equals("rotation") || channel.equals("position") || channel.equals("scale"))) {
					continue;
				}
				JsonObject point = keyframe.getAsJsonArray("data_points").get(0).getAsJsonObject();
				double x = BbModel.number(point.get("x"));
				double y = BbModel.number(point.get("y"));
				double z = BbModel.number(point.get("z"));
				if (negate && channel.equals("rotation")) {
					x = -x;
					y = -y;
				} else if (negate && channel.equals("position")) {
					x = -x;
				}
				JsonObject value = new JsonObject();
				value.add("vector", GeoConverter.array(x, y, z));
				bone.add(channel, value);
			}
			if (!bone.isEmpty()) {
				bones.add(name, bone);
			}
		}
		JsonObject animation = new JsonObject();
		animation.add("bones", bones);
		return animation;
	}

	/** Static pose per bone in Bedrock convention (degrees / pixels). */
	public record Pose(Map<String, double[]> rotations, Map<String, double[]> positions) {
		public static final Pose NONE = new Pose(Map.of(), Map.of());
	}

	public static Pose pose(JsonObject animationJson) {
		Map<String, double[]> rotations = new HashMap<>();
		Map<String, double[]> positions = new HashMap<>();
		JsonObject idle = animationJson.getAsJsonObject("animations").getAsJsonObject(IDLE);
		for (var entry : idle.getAsJsonObject("bones").entrySet()) {
			JsonObject bone = entry.getValue().getAsJsonObject();
			vector(bone.get("rotation")).ifPresent(v -> rotations.put(entry.getKey(), v));
			vector(bone.get("position")).ifPresent(v -> positions.put(entry.getKey(), v));
		}
		return new Pose(rotations, positions);
	}

	/** Accepts {@code [x,y,z]}, {@code {"vector": [...]}}, or keyframe maps (first keyframe, pre/post forms). */
	static Optional<double[]> vector(JsonElement value) {
		if (value == null || value.isJsonNull()) {
			return Optional.empty();
		}
		if (value.isJsonArray()) {
			return Optional.of(BbModel.vec(value.getAsJsonArray(), 3));
		}
		if (value.isJsonPrimitive()) {
			double v = BbModel.number(value);
			return Optional.of(new double[] {v, v, v});
		}
		JsonObject object = value.getAsJsonObject();
		if (object.has("vector")) {
			return vector(object.get("vector"));
		}
		if (object.has("post")) {
			return vector(object.get("post"));
		}
		if (object.has("pre")) {
			return vector(object.get("pre"));
		}
		String firstKey = object.keySet().stream().min((a, b) -> Double.compare(BbModel.number(new com.google.gson.JsonPrimitive(a)),
				BbModel.number(new com.google.gson.JsonPrimitive(b)))).orElse(null);
		return firstKey == null ? Optional.empty() : vector(object.get(firstKey));
	}
}
