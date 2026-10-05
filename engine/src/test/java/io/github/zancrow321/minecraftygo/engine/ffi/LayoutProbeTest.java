package io.github.zancrow321.minecraftygo.engine.ffi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.StructLayout;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Java struct layouts must match what the C compiler produces for ocgapi_types.h. */
class LayoutProbeTest {
	@Test
	void javaLayoutsMatchNativeLayouts() throws IOException, InterruptedException {
		String probe = System.getProperty("ygo.ocgcore.layoutProbe");
		assertThat(probe).as("ygo.ocgcore.layoutProbe").isNotBlank();
		Process process = new ProcessBuilder(probe).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes());
		assertThat(process.waitFor()).isZero();
		JsonObject native_ = JsonParser.parseString(output).getAsJsonObject();

		Map<String, StructLayout> layouts = Map.of(
				"OCG_CardData", OcgLayouts.CARD_DATA,
				"OCG_Player", OcgLayouts.PLAYER,
				"OCG_DuelOptions", OcgLayouts.DUEL_OPTIONS,
				"OCG_NewCardInfo", OcgLayouts.NEW_CARD_INFO,
				"OCG_QueryInfo", OcgLayouts.QUERY_INFO);
		for (var entry : layouts.entrySet()) {
			JsonObject expected = native_.getAsJsonObject(entry.getKey());
			StructLayout layout = entry.getValue();
			assertThat(layout.byteSize()).as(entry.getKey() + " size").isEqualTo(expected.get("size").getAsLong());
			for (String field : expected.keySet()) {
				if (field.equals("size") || field.equals("align")) {
					continue;
				}
				assertThat(layout.byteOffset(PathElement.groupElement(field)))
						.as(entry.getKey() + "." + field).isEqualTo(expected.get(field).getAsLong());
			}
			long fieldCount = layout.memberLayouts().stream().filter(m -> m.name().isPresent()).count();
			assertThat(fieldCount).as(entry.getKey() + " field count").isEqualTo(expected.size() - 2);
			assertThat(layout.byteAlignment()).as(entry.getKey() + " alignment").isEqualTo(expected.get("align").getAsLong());
		}
	}
}
