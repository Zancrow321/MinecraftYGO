package io.github.zancrow321.minecraftygo.tools.bbmodel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.tools.util.Json;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Structural validation of every committed, generated GeckoLib asset (the in-game load test follows with the mod). */
class GeneratedAssetsTest {
	private static final Path ROOT = Path.of(System.getProperty("ygo.rootDir", "..")).toAbsolutePath().normalize();
	private static final Path ASSETS = ROOT.resolve("neoforge/src/generated/resources/assets/minecraftygo");

	@Test
	void everyModeledPoolMonsterHasValidAssets() throws IOException {
		CardPool pool;
		try (Reader reader = Files.newBufferedReader(ROOT.resolve("engine/src/generated/resources/minecraftygo/data/pool.json"))) {
			pool = CardPool.read(reader);
		}
		JsonObject index = Json.read(ASSETS.resolve("ygo/monster_models.json")).getAsJsonObject().getAsJsonObject("models");
		int checked = 0;
		for (CardPool.Entry entry : pool.entries()) {
			if (!entry.hasModel()) {
				continue;
			}
			int code = entry.code();
			assertThat(index.has(Integer.toString(code))).as("index entry for %s", entry.name()).isTrue();
			JsonObject info = index.getAsJsonObject(Integer.toString(code));
			assertThat(info.get("scale").getAsDouble()).isBetween(0.02, 1.0);

			JsonObject geo = Json.read(ASSETS.resolve("geckolib/models/monster/" + code + ".geo.json")).getAsJsonObject();
			assertThat(geo.get("format_version").getAsString()).isEqualTo("1.12.0");
			JsonObject geometry = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
			Set<String> bones = new HashSet<>();
			for (JsonElement b : geometry.getAsJsonArray("bones")) {
				JsonObject bone = b.getAsJsonObject();
				String name = bone.get("name").getAsString();
				assertThat(bones.add(name)).as("unique bone %s in %s", name, entry.name()).isTrue();
				if (bone.has("parent")) {
					assertThat(bones).as("parent of %s declared before it", name).contains(bone.get("parent").getAsString());
				}
				assertFinite(bone, entry.name());
			}
			assertThat(bones).contains(GeoConverter.ROOT_BONE);

			Path animation = ASSETS.resolve("geckolib/animations/monster/" + code + ".animation.json");
			if (info.get("hasIdle").getAsBoolean()) {
				JsonObject idle = Json.read(animation).getAsJsonObject().getAsJsonObject("animations").getAsJsonObject("idle");
				assertThat(bones).as("animated bones of %s", entry.name()).containsAll(idle.getAsJsonObject("bones").keySet());
			} else {
				assertThat(animation).doesNotExist();
			}
			byte[] png = Files.readAllBytes(ASSETS.resolve("textures/monster/" + code + ".png"));
			assertThat(png).startsWith(0x89, 'P', 'N', 'G');
			checked++;
		}
		assertThat(checked).isEqualTo(622);
		assertThat(ASSETS.resolve("ygomcmodels/LICENSE.md")).exists();
	}

	private static void assertFinite(JsonElement element, String model) {
		if (element.isJsonArray()) {
			element.getAsJsonArray().forEach(e -> assertFinite(e, model));
		} else if (element.isJsonObject()) {
			element.getAsJsonObject().entrySet().forEach(e -> assertFinite(e.getValue(), model));
		} else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			assertThat(Double.isFinite(element.getAsDouble())).as("finite number in %s", model).isTrue();
		}
	}
}
