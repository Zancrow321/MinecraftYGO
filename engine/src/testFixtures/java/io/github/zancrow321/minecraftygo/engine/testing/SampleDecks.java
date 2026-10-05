package io.github.zancrow321.minecraftygo.engine.testing;

import io.github.zancrow321.minecraftygo.engine.deck.Deck;

import java.util.ArrayList;
import java.util.List;

/** Classic-era 40 card decks (normal monsters + era staples) for engine tests. */
public final class SampleDecks {
	private SampleDecks() {}

	// Monsters
	public static final int BLUE_EYES_WHITE_DRAGON = 89631139;
	public static final int DARK_MAGICIAN = 46986414;
	public static final int SUMMONED_SKULL = 70781052;
	public static final int CELTIC_GUARDIAN = 91152256;
	public static final int GAIA_THE_FIERCE_KNIGHT = 6368038;
	public static final int MYSTICAL_ELF = 15025844;
	public static final int FERAL_IMP = 41392891;
	public static final int GIANT_SOLDIER_OF_STONE = 13039848;
	public static final int BATTLE_OX = 5053103;
	public static final int LA_JINN = 97590747;
	public static final int ALPHA_THE_MAGNET_WARRIOR = 99785935;
	public static final int KURIBOH = 40640057;
	public static final int SANGAN = 26202165;
	public static final int MAN_EATER_BUG = 54652250;
	public static final int THOUSAND_DRAGON = 41462083;
	public static final int BABY_DRAGON = 88819587;
	public static final int TIME_WIZARD = 71625222;
	public static final int FLAME_SWORDSMAN = 45231177;
	public static final int FLAME_MANIPULATOR = 34460851;
	public static final int MASAKI = 44287299;
	// Spells / traps
	public static final int DARK_HOLE = 53129443;
	public static final int RAIGEKI = 12580477;
	public static final int POT_OF_GREED = 55144522;
	public static final int MONSTER_REBORN = 83764718;
	public static final int MIRROR_FORCE = 44095762;
	public static final int TRAP_HOLE = 4206964;
	public static final int POLYMERIZATION = 24094653;
	public static final int CHANGE_OF_HEART = 4031928;

	private static List<Integer> repeat(int count, int... codes) {
		List<Integer> result = new ArrayList<>();
		for (int code : codes) {
			for (int i = 0; i < count; i++) {
				result.add(code);
			}
		}
		return result;
	}

	/** Kaiba-style beatdown with tributes. */
	public static Deck dragons() {
		List<Integer> main = new ArrayList<>();
		main.addAll(repeat(2, BLUE_EYES_WHITE_DRAGON, SUMMONED_SKULL));
		main.addAll(repeat(3, BATTLE_OX, GIANT_SOLDIER_OF_STONE, LA_JINN, ALPHA_THE_MAGNET_WARRIOR, CELTIC_GUARDIAN,
				MYSTICAL_ELF, FERAL_IMP, BABY_DRAGON, KURIBOH));
		main.addAll(repeat(1, SANGAN, MAN_EATER_BUG, DARK_HOLE, RAIGEKI, POT_OF_GREED, MONSTER_REBORN, MIRROR_FORCE,
				CHANGE_OF_HEART));
		main.addAll(repeat(2, TRAP_HOLE));
		return Deck.of(main, List.of());
	}

	/** Yugi-style deck with a Fusion in the Extra Deck. */
	public static Deck magicians() {
		List<Integer> main = new ArrayList<>();
		main.addAll(repeat(2, DARK_MAGICIAN, GAIA_THE_FIERCE_KNIGHT));
		main.addAll(repeat(3, CELTIC_GUARDIAN, MYSTICAL_ELF, FERAL_IMP, BATTLE_OX, LA_JINN, BABY_DRAGON, KURIBOH,
				FLAME_MANIPULATOR, MASAKI));
		main.addAll(repeat(1, TIME_WIZARD, SANGAN, DARK_HOLE, RAIGEKI, POT_OF_GREED, MONSTER_REBORN, MIRROR_FORCE,
				CHANGE_OF_HEART));
		main.addAll(repeat(2, TRAP_HOLE, POLYMERIZATION));
		return Deck.of(main, repeat(2, THOUSAND_DRAGON, FLAME_SWORDSMAN));
	}
}
