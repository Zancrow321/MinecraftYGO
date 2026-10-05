package io.github.zancrow321.minecraftygo.engine.data;

import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;

import java.util.Arrays;

/**
 * Engine-relevant numeric data of one card (mirrors {@code OCG_CardData}). {@code level} is the plain level/rank
 * (scales are split out), {@code setcodes} are the 16-bit archetype codes.
 */
public record CardData(
		int code,
		int alias,
		int[] setcodes,
		int type,
		int level,
		int attribute,
		long race,
		int attack,
		int defense,
		int leftScale,
		int rightScale,
		int linkMarker) {

	public CardData {
		setcodes = setcodes.clone();
	}

	@Override
	public int[] setcodes() {
		return setcodes.clone();
	}

	public boolean isType(int typeMask) {
		return (type & typeMask) != 0;
	}

	public boolean isMonster() {
		return isType(OcgConstants.TYPE_MONSTER);
	}

	/** Cards that live in the Extra Deck instead of the Main Deck. */
	public boolean isExtraDeckCard() {
		return isType(OcgConstants.TYPE_FUSION | OcgConstants.TYPE_SYNCHRO | OcgConstants.TYPE_XYZ | OcgConstants.TYPE_LINK);
	}

	public boolean isToken() {
		return isType(OcgConstants.TYPE_TOKEN);
	}

	/** Code identifying the card for copy limits, artwork and models (alternate artworks share their alias). */
	public int canonicalCode() {
		return alias != 0 && Math.abs(alias - code) < 10 ? alias : code;
	}

	/** Decodes the packed BabelCDB/EDOPro {@code datas} columns. */
	public static CardData fromDatabaseRow(int code, int alias, long packedSetcodes, int type, int attack, int defense,
			long packedLevel, long race, int attribute) {
		int[] codes = new int[4];
		int count = 0;
		for (int i = 0; i < 4; i++) {
			int setcode = (int) ((packedSetcodes >>> (i * 16)) & 0xFFFF);
			if (setcode != 0) {
				codes[count++] = setcode;
			}
		}
		int level = (int) (packedLevel & 0xFF);
		int leftScale = (int) ((packedLevel >>> 24) & 0xFF);
		int rightScale = (int) ((packedLevel >>> 16) & 0xFF);
		int linkMarker = 0;
		int def = defense;
		if ((type & OcgConstants.TYPE_LINK) != 0) {
			linkMarker = defense;
			def = 0;
		}
		return new CardData(code, alias, Arrays.copyOf(codes, count), type, level, attribute, race, attack, def,
				leftScale, rightScale, linkMarker);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof CardData other && code == other.code && alias == other.alias
				&& Arrays.equals(setcodes, other.setcodes) && type == other.type && level == other.level
				&& attribute == other.attribute && race == other.race && attack == other.attack
				&& defense == other.defense && leftScale == other.leftScale && rightScale == other.rightScale
				&& linkMarker == other.linkMarker;
	}

	@Override
	public int hashCode() {
		return Integer.hashCode(code) * 31 + Arrays.hashCode(setcodes);
	}

	@Override
	public String toString() {
		return "CardData[code=" + code + ", alias=" + alias + ", type=0x" + Integer.toHexString(type) + ", level="
				+ level + ", atk=" + attack + ", def=" + defense + "]";
	}
}
