package io.github.zancrow321.minecraftygo.tools.bbmodel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Golden tests: the same model in Blockbench 4.x (inline outliner groups) and 5.x (separate groups) format. */
class GeoConverterTest {
	private static final String ELEMENTS = """
			"elements":[
			 {"name":"body","type":"cube","uuid":"c1","from":[-4,0,-2],"to":[4,12,2],"origin":[0,0,0],"uv_offset":[0,16],"box_uv":true,"faces":{}},
			 {"name":"arm","type":"cube","uuid":"c2","from":[4,10,-1],"to":[6,12,1],"origin":[5,11,0],"rotation":[0,0,22.5],
			  "uv_offset":[24,16],"box_uv":true,"mirror_uv":true,"inflate":0.25,"faces":{}},
			 {"name":"loose","type":"cube","uuid":"c3","from":[0,0,0],"to":[1,1,1],"origin":[0,0,0],"box_uv":false,"faces":{
			  "north":{"uv":[0,0,1,1],"texture":0},"up":{"uv":[2,2,4,3],"texture":0,"rotation":90},"down":{"uv":[0,0,1,1],"texture":null}}}
			],
			"reference_images":[{"source":"data:image/png;base64,AAAA"}],
			"textures":[{"source":"data:image/png;base64,iVBOR"}],
			"resolution":{"width":64,"height":32}
			""";

	static final String FORMAT_4 = "{\"meta\":{\"format_version\":\"4.10\",\"model_format\":\"animated_entity_model\"}," + ELEMENTS + ","
			+ "\"outliner\":[{\"name\":\"torso\",\"uuid\":\"g1\",\"origin\":[0,12,0],\"rotation\":[10,20,30],\"children\":[\"c1\","
			+ "{\"name\":\"torso\",\"uuid\":\"g2\",\"origin\":[5,11,0],\"children\":[\"c2\"]}]},\"c3\"]}";

	static final String FORMAT_5 = "{\"meta\":{\"format_version\":\"5.0\",\"model_format\":\"geckolib_model\"}," + ELEMENTS + ","
			+ "\"groups\":[{\"name\":\"torso\",\"uuid\":\"g1\",\"origin\":[0,12,0],\"rotation\":[10,20,30]},"
			+ "{\"name\":\"torso\",\"uuid\":\"g2\",\"origin\":[5,11,0],\"rotation\":[0,0,0]}],"
			+ "\"outliner\":[{\"uuid\":\"g1\",\"children\":[\"c1\",{\"uuid\":\"g2\",\"children\":[\"c2\"]}]},\"c3\"]}";

	static final String EXPECTED = """
			{"format_version":"1.12.0","minecraft:geometry":[{
			 "description":{"identifier":"geometry.test","texture_width":64,"texture_height":32},
			 "bones":[
			  {"name":"ygo_root","pivot":[0,0,0],"cubes":[
			   {"origin":[-1,0,0],"size":[1,1,1],"uv":{
			    "north":{"uv":[0,0],"uv_size":[1,1]},
			    "up":{"uv":[4,3],"uv_size":[-2,-1],"uv_rotation":90}}}]},
			  {"name":"torso","parent":"ygo_root","pivot":[0,12,0],"rotation":[-10,-20,30],"cubes":[
			   {"origin":[-4,0,-2],"size":[8,12,4],"uv":[0,16]}]},
			  {"name":"torso_2","parent":"torso","pivot":[-5,11,0],"cubes":[
			   {"origin":[-6,10,-1],"size":[2,2,2],"inflate":0.25,"pivot":[-5,11,0],"rotation":[0,0,22.5],"uv":[24,16],"mirror":true}]}
			 ]}]}
			""";

	private static GeoConverter.Result convert(String bbmodel) {
		return GeoConverter.convert(BbModel.read(new StringReader(bbmodel)), "geometry.test");
	}

	@Test
	void format4MatchesBlockbenchBedrockExport() {
		GeoConverter.Result result = convert(FORMAT_4);
		assertThat(result.geometry()).isEqualTo(JsonParser.parseString(EXPECTED));
		assertThat(result.cubeCount()).isEqualTo(3);
		assertThat(result.renamedBones()).isEqualTo(Map.of("g2", "torso_2"));
	}

	@Test
	void format5ProducesTheSameGeometry() {
		JsonObject four = convert(FORMAT_4).geometry();
		JsonObject five = convert(FORMAT_5).geometry();
		assertThat(five).isEqualTo(four);
	}

	@Test
	void skippedFieldsAreNotRequired() {
		BbModel model = BbModel.read(new StringReader(FORMAT_5));
		assertThat(model.isFormat5OrNewer()).isTrue();
		assertThat(model.textureWidth).isEqualTo(64);
		assertThat(model.firstTextureSource).startsWith("data:image/png");
	}
}
