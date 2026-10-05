package io.github.zancrow321.minecraftygo.engine.query;

import io.github.zancrow321.minecraftygo.engine.ffi.NativeDuel;
import io.github.zancrow321.minecraftygo.engine.ffi.QueryRequest;
import io.github.zancrow321.minecraftygo.engine.wire.LocInfo;
import io.github.zancrow321.minecraftygo.engine.wire.WireReader;

import java.util.ArrayList;
import java.util.List;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;

/** Builds a complete (unredacted) {@link FieldState} from the core's query functions. */
public final class FieldStateReader {
	/** Everything the presentation and the bots need about a card. */
	public static final int CARD_FLAGS = QUERY_CODE | QUERY_POSITION | QUERY_ALIAS | QUERY_TYPE | QUERY_LEVEL
			| QUERY_RANK | QUERY_ATTRIBUTE | QUERY_RACE | QUERY_ATTACK | QUERY_DEFENSE | QUERY_BASE_ATTACK
			| QUERY_BASE_DEFENSE | QUERY_REASON | QUERY_EQUIP_CARD | QUERY_TARGET_CARD | QUERY_OVERLAY_CARD
			| QUERY_COUNTERS | QUERY_OWNER | QUERY_STATUS | QUERY_IS_PUBLIC | QUERY_LSCALE | QUERY_RSCALE | QUERY_LINK
			| QUERY_IS_HIDDEN;

	private FieldStateReader() {}

	public static FieldState read(NativeDuel duel) {
		WireReader field = new WireReader(duel.queryField());
		field.i32(); // duel options
		int[] lifePoints = new int[2];
		int[] deckCounts = new int[2];
		for (int player = 0; player < 2; player++) {
			lifePoints[player] = field.i32();
			skipZones(field, 7);
			skipZones(field, 8);
			deckCounts[player] = field.i32();
			field.skip(5 * 4); // hand, grave, removed, extra, extra face-up counts
		}
		List<FieldState.ChainEntry> chain = new ArrayList<>();
		int chainSize = field.i32();
		for (int i = 0; i < chainSize; i++) {
			int code = field.i32();
			LocInfo location = field.locInfo();
			field.skip(1 + 1 + 4); // triggering controller, location, sequence
			long description = field.i64();
			chain.add(new FieldState.ChainEntry(code, location.controller(), location.location(), location.sequence(),
					description));
		}
		List<PlayerField> players = new ArrayList<>(2);
		for (int player = 0; player < 2; player++) {
			players.add(new PlayerField(lifePoints[player],
					location(duel, player, LOCATION_MZONE),
					location(duel, player, LOCATION_SZONE),
					nonNull(location(duel, player, LOCATION_HAND)),
					nonNull(location(duel, player, LOCATION_GRAVE)),
					nonNull(location(duel, player, LOCATION_REMOVED)),
					nonNull(location(duel, player, LOCATION_EXTRA)),
					deckCounts[player]));
		}
		return new FieldState(players, chain);
	}

	private static void skipZones(WireReader reader, int zones) {
		for (int i = 0; i < zones; i++) {
			if (reader.u8() != 0) {
				reader.skip(1 + 4); // position, overlay count
			}
		}
	}

	private static List<CardInfo> location(NativeDuel duel, int player, int location) {
		return QueryParser.parseLocation(duel.queryLocation(QueryRequest.location(CARD_FLAGS, player, location)));
	}

	private static List<CardInfo> nonNull(List<CardInfo> cards) {
		return cards.stream().filter(c -> c != null).toList();
	}
}
