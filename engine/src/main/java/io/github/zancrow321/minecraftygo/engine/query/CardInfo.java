package io.github.zancrow321.minecraftygo.engine.query;

import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.wire.LocInfo;

import java.util.List;
import java.util.Map;

/**
 * Current state of one card as returned by {@code OCG_DuelQuery*}. {@code queried} holds the {@code QUERY_*} flags
 * that were present; absent fields are 0/empty. A hidden card (from another player's point of view) has
 * {@code code == 0} and only its position.
 */
public record CardInfo(
		int queried,
		int code,
		int position,
		int alias,
		int type,
		int level,
		int rank,
		int attribute,
		long race,
		int attack,
		int defense,
		int baseAttack,
		int baseDefense,
		int reason,
		int cover,
		LocInfo reasonCard,
		LocInfo equipTarget,
		List<LocInfo> targets,
		List<Integer> overlayCodes,
		Map<Integer, Integer> counters,
		int owner,
		int status,
		boolean isPublic,
		int leftScale,
		int rightScale,
		int linkRating,
		int linkMarker,
		boolean isHidden) {

	public CardInfo {
		targets = List.copyOf(targets);
		overlayCodes = List.copyOf(overlayCodes);
		counters = Map.copyOf(counters);
	}

	public boolean isFaceUp() {
		return (position & OcgConstants.POS_FACEUP) != 0;
	}

	public boolean isAttackPosition() {
		return (position & OcgConstants.POS_ATTACK) != 0;
	}

	public boolean isType(int mask) {
		return (type & mask) != 0;
	}

	/** The same card with every identifying detail removed (what an opponent sees of a face-down card). */
	public CardInfo hidden() {
		return new CardInfo(OcgConstants.QUERY_POSITION, 0, position, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null,
				List.of(), List.of(), Map.of(), owner, 0, false, 0, 0, 0, 0, false);
	}
}
