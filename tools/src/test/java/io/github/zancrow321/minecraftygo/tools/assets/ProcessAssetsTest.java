package io.github.zancrow321.minecraftygo.tools.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zancrow321.minecraftygo.tools.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessAssetsTest {
	private static final Path ROOT = Path.of(System.getProperty("ygo.rootDir", "..")).toAbsolutePath().normalize();

	private static JsonObject contractAsset(String id) {
		for (JsonElement element : Json.read(ROOT.resolve("assets-src/asset-contract.json")).getAsJsonObject().getAsJsonArray("assets")) {
			if (element.getAsJsonObject().get("id").getAsString().equals(id)) {
				return element.getAsJsonObject();
			}
		}
		throw new AssertionError(id);
	}

	@Test
	void placeholdersContainEveryContractBoneAndAnimation() throws IOException {
		for (String id : List.of("duel_disk", "duel_arena", "millennium_ring")) {
			JsonObject asset = contractAsset(id);
			JsonObject geometry = ProcessAssets.placeholderGeometry(asset);
			Set<String> bones = new HashSet<>();
			for (JsonElement bone : geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
				bones.add(bone.getAsJsonObject().get("name").getAsString());
			}
			assertThat(bones).containsAll(asset.getAsJsonObject("bones").keySet());
			assertThat(ProcessAssets.placeholderAnimations(asset).getAsJsonObject("animations").keySet())
					.containsAll(asset.getAsJsonObject("animations").keySet());
			assertThat(ProcessAssets.placeholderTexture(asset)).startsWith(0x89, 'P', 'N', 'G');
		}
	}

	@Test
	void defaultArenaIsPointSymmetricAndComplete() {
		JsonObject anchors = ArenaLayouts.defaultAnchors();
		for (String bone : contractAsset("duel_arena").getAsJsonObject("bones").keySet()) {
			if (bone.startsWith("anchor_")) {
				assertThat(anchors.has(bone.substring("anchor_".length()))).as(bone).isTrue();
			}
		}
		JsonArray m0 = anchors.getAsJsonArray("t0_m0");
		JsonArray opposite = anchors.getAsJsonArray("t1_m0");
		assertThat(opposite.get(0).getAsDouble()).isEqualTo(-m0.get(0).getAsDouble());
		assertThat(opposite.get(2).getAsDouble()).isEqualTo(-m0.get(2).getAsDouble());
		// Sequence 0 is the leftmost zone for a duelist facing +Z, i.e. at +X.
		assertThat(m0.get(0).getAsDouble()).isPositive();
		JsonObject single = ArenaLayouts.layout(anchors, 1).getAsJsonObject("anchors");
		assertThat(single.has("spot_t0_d1")).isFalse();
		assertThat(single.getAsJsonArray("spot_t0_d0").get(0).getAsDouble()).isZero();
	}

	@Test
	void validationReportsContractViolations(@TempDir Path dir) throws IOException {
		Path model = dir.resolve("duel_disk.bbmodel");
		Files.writeString(model, """
				{"meta":{"format_version":"5.0"},"resolution":{"width":64,"height":64},
				 "elements":[{"uuid":"c","from":[0,0,0],"to":[1,1,1],"faces":{}}],
				 "outliner":[{"name":"root","uuid":"g","origin":[0,0,0],"children":["c"]}],
				 "reference_images":[{"name":"Pasted"}]}
				""");
		List<String> problems = ProcessAssets.validate(contractAsset("duel_disk"), model, dir.resolve("duel_disk.animation.json"));
		assertThat(problems).anyMatch(p -> p.contains("reference images"))
				.anyMatch(p -> p.contains("missing bone 'blade'"))
				.anyMatch(p -> p.contains("texture resolution"))
				.anyMatch(p -> p.contains("animation.json"));
	}
}
