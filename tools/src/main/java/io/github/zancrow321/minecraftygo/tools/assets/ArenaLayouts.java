package io.github.zancrow321.minecraftygo.tools.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Arena anchor positions in blocks relative to the arena origin (field centre, floor level), Minecraft axes. Team 0
 * stands at -Z facing +Z, team 1 is the 180-degree rotation. Zone sequence 0 is the duelist's leftmost zone, which for
 * someone facing +Z is at +X. Used when no Duel Arena model exists yet and to derive the 1v1/2v2 layout files.
 */
public final class ArenaLayouts {
	static final double ZONE_SPACING = 1.2;
	static final double MONSTER_ROW = -1.5;
	static final double SPELL_ROW = -2.8;
	static final double SIDE_COLUMN = 3.6;
	static final double FLOOR = 0.05;
	static final double SPOT_Z = -5.2;

	private ArenaLayouts() {}

	public static JsonObject defaultAnchors() {
		JsonObject anchors = new JsonObject();
		for (int team = 0; team < 2; team++) {
			String t = "t" + team + "_";
			for (int seq = 0; seq < 5; seq++) {
				double x = (2 - seq) * ZONE_SPACING;
				put(anchors, t + "m" + seq, team, x, FLOOR, MONSTER_ROW);
				put(anchors, t + "s" + seq, team, x, FLOOR, SPELL_ROW);
			}
			put(anchors, t + "field", team, SIDE_COLUMN, FLOOR, MONSTER_ROW);
			put(anchors, t + "extra", team, SIDE_COLUMN, FLOOR, SPELL_ROW);
			put(anchors, t + "grave", team, -SIDE_COLUMN, FLOOR, MONSTER_ROW);
			put(anchors, t + "deck", team, -SIDE_COLUMN, FLOOR, SPELL_ROW);
			put(anchors, t + "banish", team, -SIDE_COLUMN, FLOOR, MONSTER_ROW + ZONE_SPACING);
			for (int duelist = 0; duelist < 2; duelist++) {
				double x = duelist == 0 ? 0.9 : -0.9;
				put(anchors, "spot_" + t + "d" + duelist, team, x, 0, SPOT_Z);
				put(anchors, "hand_" + t + "d" + duelist, team, x, 1.3, SPOT_Z + 0.7);
				put(anchors, "prompt_" + t + "d" + duelist, team, x, 1.7, SPOT_Z + 1.6);
			}
			put(anchors, "lp_" + "t" + team, team, -4.4, 1.6, SPOT_Z + 0.4);
		}
		anchors.add("phase", vec(4.8, 1.6, 0));
		anchors.add("chain", vec(0, 0.2, 0));
		return anchors;
	}

	private static void put(JsonObject anchors, String name, int team, double x, double y, double z) {
		anchors.add(name, team == 0 ? vec(x, y, z) : vec(-x, y, -z));
	}

	/** A layout file: anchors with the duelist spots of 1v1 (both duelists centred) or 2v2. */
	public static JsonObject layout(JsonObject anchors, int duelistsPerTeam) {
		JsonObject copy = anchors.deepCopy();
		if (duelistsPerTeam == 1) {
			for (int team = 0; team < 2; team++) {
				for (String prefix : new String[] {"spot_", "hand_", "prompt_"}) {
					JsonArray d0 = copy.getAsJsonArray(prefix + "t" + team + "_d0");
					copy.add(prefix + "t" + team + "_d0", vec(0, d0.get(1).getAsDouble(), d0.get(2).getAsDouble()));
					copy.remove(prefix + "t" + team + "_d1");
				}
			}
		}
		JsonObject layout = new JsonObject();
		layout.addProperty("duelists_per_team", duelistsPerTeam);
		layout.addProperty("width", 9);
		layout.addProperty("depth", 13);
		layout.add("anchors", copy);
		return layout;
	}

	static JsonArray vec(double x, double y, double z) {
		JsonArray array = new JsonArray();
		for (double value : new double[] {x, y, z}) {
			double rounded = Math.round(value * 1000d) / 1000d;
			array.add(rounded == 0 ? 0.0 : rounded);
		}
		return array;
	}
}
