package io.github.zancrow321.minecraftygo.tools.bbmodel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AnimationAndBoundsTest {
	private static String model(String version, String keyframeX) {
		return "{\"meta\":{\"format_version\":\"" + version + "\"},\"resolution\":{\"width\":16,\"height\":16},"
				+ "\"elements\":[{\"uuid\":\"c\",\"from\":[0,0,0],\"to\":[2,4,2],\"faces\":{}}],"
				+ "\"outliner\":[{\"name\":\"bone\",\"uuid\":\"g\",\"origin\":[0,0,0],\"children\":[\"c\"]}],"
				+ "\"animations\":[{\"name\":\"idle\",\"animators\":{\"g\":{\"name\":\"bone\",\"type\":\"bone\",\"keyframes\":["
				+ "{\"channel\":\"rotation\",\"time\":0,\"data_points\":[{\"x\":\"" + keyframeX + "\",\"y\":\"5\",\"z\":\"7\"}]},"
				+ "{\"channel\":\"position\",\"time\":0,\"data_points\":[{\"x\":1,\"y\":2,\"z\":3}]}]}}}]}";
	}

	@Test
	void embeddedFormat5KeyframesAreConvertedToBedrockConvention() {
		BbModel five = BbModel.read(new StringReader(model("5.0", "10")));
		JsonObject anim = AnimationConverter.idle(Optional.empty(), five, Set.of("bone"), Map.of()).orElseThrow();
		JsonObject bone = anim.getAsJsonObject("animations").getAsJsonObject("idle").getAsJsonObject("bones").getAsJsonObject("bone");
		assertThat(bone.getAsJsonObject("rotation").get("vector")).isEqualTo(JsonParser.parseString("[-10,-5,7]"));
		assertThat(bone.getAsJsonObject("position").get("vector")).isEqualTo(JsonParser.parseString("[-1,2,3]"));

		BbModel four = BbModel.read(new StringReader(model("4.10", "10")));
		JsonObject anim4 = AnimationConverter.idle(Optional.empty(), four, Set.of("bone"), Map.of()).orElseThrow();
		assertThat(anim4.getAsJsonObject("animations").getAsJsonObject("idle").getAsJsonObject("bones").getAsJsonObject("bone")
				.getAsJsonObject("rotation").get("vector")).isEqualTo(JsonParser.parseString("[10,5,7]"));
		assertThat(anim4.get("geckolib_format_version").getAsInt()).isEqualTo(2);
	}

	@Test
	void unknownBonesAreDroppedAndVectorFormsParse() {
		BbModel four = BbModel.read(new StringReader(model("4.10", "10")));
		assertThat(AnimationConverter.idle(Optional.empty(), four, Set.of("other"), Map.of()).orElseThrow()
				.getAsJsonObject("animations").getAsJsonObject("idle").getAsJsonObject("bones").isEmpty()).isTrue();
		assertThat(AnimationConverter.vector(JsonParser.parseString("[1,2,3]"))).hasValueSatisfying(v -> assertThat(v).containsExactly(1, 2, 3));
		assertThat(AnimationConverter.vector(JsonParser.parseString("{\"vector\":[4,5,6]}"))).hasValueSatisfying(v -> assertThat(v).containsExactly(4, 5, 6));
		assertThat(AnimationConverter.vector(JsonParser.parseString("{\"0.5\":[9,9,9],\"0.0\":{\"post\":[7,8,9]}}")))
				.hasValueSatisfying(v -> assertThat(v).containsExactly(7, 8, 9));
		assertThat(AnimationConverter.vector(JsonParser.parseString("[\"math.sin(1)\",2,3]")))
				.hasValueSatisfying(v -> assertThat(v).containsExactly(0, 2, 3));
	}

	@Test
	void boundsApplyRestAndPoseRotation() {
		BbModel.Cube cube = new BbModel.Cube("c", "c", new double[] {0, 0, 0}, new double[] {2, 4, 2}, new double[3],
				new double[3], 0, true, new double[2], false, Map.of(), true);
		GeoConverter.Bone bone = new GeoConverter.Bone("bone", null, new double[3], new double[3], List.of(cube));
		Bounds rest = Bounds.of(List.of(bone), AnimationConverter.Pose.NONE);
		assertThat(rest.height()).isCloseTo(4, within(1e-9));
		// Bedrock Z rotation of 90 degrees lays the 4 px tall cube on its side.
		Bounds posed = Bounds.of(List.of(bone), new AnimationConverter.Pose(Map.of("bone", new double[] {0, 0, 90}), Map.of()));
		assertThat(posed.height()).isCloseTo(2, within(1e-9));
		assertThat(posed.width()).isCloseTo(4, within(1e-9));
		Bounds moved = Bounds.of(List.of(bone), new AnimationConverter.Pose(Map.of(), Map.of("bone", new double[] {0, 3, 0})));
		assertThat(moved.minY()).isCloseTo(3, within(1e-9));
	}
}
