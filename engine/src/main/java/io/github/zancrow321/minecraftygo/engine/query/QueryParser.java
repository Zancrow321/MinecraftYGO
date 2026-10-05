package io.github.zancrow321.minecraftygo.engine.query;

import io.github.zancrow321.minecraftygo.engine.wire.LocInfo;
import io.github.zancrow321.minecraftygo.engine.wire.WireFormatException;
import io.github.zancrow321.minecraftygo.engine.wire.WireReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;

/**
 * Decodes query buffers. A card is a list of {@code [u16 size][u32 QUERY_*][data]} records terminated by
 * {@code QUERY_END}; an empty slot is a single {@code u16 0}. Unknown flags are skipped using their size.
 */
public final class QueryParser {
	private QueryParser() {}

	/** {@code OCG_DuelQuery}: one card, or null if the slot was empty. */
	public static CardInfo parseCard(byte[] buffer) {
		if (buffer.length == 0) {
			return null;
		}
		WireReader reader = new WireReader(buffer);
		CardInfo info = readCard(reader);
		if (reader.hasRemaining()) {
			throw new WireFormatException("Trailing bytes after card query");
		}
		return info;
	}

	/** {@code OCG_DuelQueryLocation}: one entry per slot (null for empty zones). */
	public static List<CardInfo> parseLocation(byte[] buffer) {
		if (buffer.length == 0) {
			return List.of();
		}
		WireReader reader = new WireReader(buffer);
		int total = reader.i32();
		if (total != reader.remaining()) {
			throw new WireFormatException("Location query size " + total + " != " + reader.remaining());
		}
		List<CardInfo> cards = new ArrayList<>();
		while (reader.hasRemaining()) {
			cards.add(readCard(reader));
		}
		return Collections.unmodifiableList(cards);
	}

	private static CardInfo readCard(WireReader r) {
		int firstSize = r.u16();
		if (firstSize == 0) {
			return null;
		}
		Builder b = new Builder();
		int size = firstSize;
		while (true) {
			if (size < 4) {
				throw new WireFormatException("Query record too small: " + size);
			}
			int flag = r.i32();
			if (flag == QUERY_END) {
				break;
			}
			int dataSize = size - 4;
			int start = r.position();
			b.queried |= flag;
			switch (flag) {
				case QUERY_CODE -> b.code = r.i32();
				case QUERY_POSITION -> b.position = r.i32();
				case QUERY_ALIAS -> b.alias = r.i32();
				case QUERY_TYPE -> b.type = r.i32();
				case QUERY_LEVEL -> b.level = r.i32();
				case QUERY_RANK -> b.rank = r.i32();
				case QUERY_ATTRIBUTE -> b.attribute = r.i32();
				case QUERY_RACE -> b.race = r.i64();
				case QUERY_ATTACK -> b.attack = r.i32();
				case QUERY_DEFENSE -> b.defense = r.i32();
				case QUERY_BASE_ATTACK -> b.baseAttack = r.i32();
				case QUERY_BASE_DEFENSE -> b.baseDefense = r.i32();
				case QUERY_REASON -> b.reason = r.i32();
				case QUERY_COVER -> b.cover = r.i32();
				case QUERY_REASON_CARD -> b.reasonCard = optionalLocation(r);
				case QUERY_EQUIP_CARD -> b.equipTarget = optionalLocation(r);
				case QUERY_TARGET_CARD -> {
					int count = r.i32();
					for (int i = 0; i < count; i++) {
						b.targets.add(r.locInfo());
					}
				}
				case QUERY_OVERLAY_CARD -> {
					int count = r.i32();
					for (int i = 0; i < count; i++) {
						b.overlayCodes.add(r.i32());
					}
				}
				case QUERY_COUNTERS -> {
					int count = r.i32();
					for (int i = 0; i < count; i++) {
						int packed = r.i32();
						b.counters.merge(packed & 0xFFFF, packed >>> 16, Integer::sum);
					}
				}
				case QUERY_OWNER -> b.owner = r.u8();
				case QUERY_STATUS -> b.status = r.i32();
				case QUERY_IS_PUBLIC -> b.isPublic = r.bool();
				case QUERY_LSCALE -> b.leftScale = r.i32();
				case QUERY_RSCALE -> b.rightScale = r.i32();
				case QUERY_LINK -> {
					b.linkRating = r.i32();
					b.linkMarker = r.i32();
				}
				case QUERY_IS_HIDDEN -> b.isHidden = r.bool();
				default -> r.skip(dataSize);
			}
			if (r.position() - start != dataSize) {
				throw new WireFormatException("Query flag 0x" + Integer.toHexString(flag) + " consumed "
						+ (r.position() - start) + " of " + dataSize + " bytes");
			}
			size = r.u16();
		}
		return b.build();
	}

	/** A loc_info that is all zeroes when no card is referenced. */
	private static LocInfo optionalLocation(WireReader r) {
		LocInfo loc = r.locInfo();
		return loc.location() == 0 ? null : loc;
	}

	private static final class Builder {
		int queried;
		int code;
		int position;
		int alias;
		int type;
		int level;
		int rank;
		int attribute;
		long race;
		int attack;
		int defense;
		int baseAttack;
		int baseDefense;
		int reason;
		int cover;
		LocInfo reasonCard;
		LocInfo equipTarget;
		final List<LocInfo> targets = new ArrayList<>();
		final List<Integer> overlayCodes = new ArrayList<>();
		final Map<Integer, Integer> counters = new HashMap<>();
		int owner;
		int status;
		boolean isPublic;
		int leftScale;
		int rightScale;
		int linkRating;
		int linkMarker;
		boolean isHidden;

		CardInfo build() {
			return new CardInfo(queried, code, position, alias, type, level, rank, attribute, race, attack, defense,
					baseAttack, baseDefense, reason, cover, reasonCard, equipTarget, targets, overlayCodes, counters,
					owner, status, isPublic, leftScale, rightScale, linkRating, linkMarker, isHidden);
		}
	}
}
