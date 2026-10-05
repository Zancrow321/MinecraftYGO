package io.github.zancrow321.minecraftygo.engine.prompt;

import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt.SelectSum;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt.SumCandidate;
import io.github.zancrow321.minecraftygo.engine.wire.WireWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates a {@link PromptResponse} against its {@link Prompt} (mirroring the checks in ygopro-core
 * {@code playerop.cpp}) and encodes it into the little-endian buffer expected by {@code OCG_DuelSetResponse}.
 * Invalid responses throw {@link InvalidResponseException} instead of provoking {@code MSG_RETRY}.
 */
public final class ResponseEncoder {
	private ResponseEncoder() {}

	public static byte[] encode(Prompt prompt, PromptResponse response) {
		WireWriter out = new WireWriter();
		switch (prompt) {
			case Prompt.IdleCommand p -> encodeIdle(p, expect(response, PromptResponse.IdleAction.class), out);
			case Prompt.BattleCommand p -> encodeBattle(p, expect(response, PromptResponse.BattleAction.class), out);
			case Prompt.EffectYesNo _, Prompt.YesNo _ -> out.i32(expect(response, PromptResponse.YesNo.class).yes() ? 1 : 0);
			case Prompt.SelectOption p -> out.i32(index(expect(response, PromptResponse.Choice.class).index(), p.options().size()));
			case Prompt.AnnounceNumber p -> out.i32(index(expect(response, PromptResponse.Choice.class).index(), p.options().size()));
			case Prompt.SelectChain p -> encodeChain(p, response, out);
			case Prompt.SelectCard p -> encodeSelectCard(p, response, out);
			case Prompt.SelectTribute p -> encodeTribute(p, response, out);
			case Prompt.SelectSum p -> encodeSum(p, expect(response, PromptResponse.Cards.class), out);
			case Prompt.SelectUnselectCard p -> encodeUnselect(p, response, out);
			case Prompt.SelectPlace p -> encodePlaces(p, expect(response, PromptResponse.Places.class), out);
			case Prompt.SelectPosition p -> encodePosition(p, expect(response, PromptResponse.Position.class), out);
			case Prompt.SelectCounter p -> encodeCounters(p, expect(response, PromptResponse.Counters.class), out);
			case Prompt.SortCards p -> encodeSort(p, response, out);
			case Prompt.AnnounceRace p -> {
				long mask = expect(response, PromptResponse.Mask.class).mask();
				checkMask(mask, p.available(), p.count());
				out.i64(mask);
			}
			case Prompt.AnnounceAttribute p -> {
				long mask = expect(response, PromptResponse.Mask.class).mask();
				checkMask(mask, Integer.toUnsignedLong(p.available()), p.count());
				out.i32((int) mask);
			}
			case Prompt.AnnounceCard _ -> {
				int code = expect(response, PromptResponse.Code.class).code();
				require(code != 0, "declared code must not be 0");
				out.i32(code);
			}
			case Prompt.RockPaperScissors _ -> {
				int hand = expect(response, PromptResponse.Choice.class).index();
				require(hand >= 1 && hand <= 3, "rock-paper-scissors hand must be 1-3");
				out.i32(hand);
			}
		}
		return out.toByteArray();
	}

	private static <T extends PromptResponse> T expect(PromptResponse response, Class<T> type) {
		if (!type.isInstance(response)) {
			throw new InvalidResponseException("Expected " + type.getSimpleName() + " but got " + response);
		}
		return type.cast(response);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new InvalidResponseException(message);
		}
	}

	private static int index(int index, int size) {
		require(index >= 0 && index < size, "index " + index + " out of range 0.." + (size - 1));
		return index;
	}

	private static void encodeIdle(Prompt.IdleCommand p, PromptResponse.IdleAction r, WireWriter out) {
		int index = r.index();
		switch (r.action()) {
			case SUMMON -> index(index, p.summonable().size());
			case SPECIAL_SUMMON -> index(index, p.specialSummonable().size());
			case REPOSITION -> index(index, p.repositionable().size());
			case MONSTER_SET -> index(index, p.monsterSettable().size());
			case SPELL_SET -> index(index, p.spellSettable().size());
			case ACTIVATE -> index(index, p.activatable().size());
			case TO_BATTLE_PHASE -> require(p.canBattlePhase(), "cannot enter battle phase");
			case TO_END_PHASE -> require(p.canEndPhase(), "cannot enter end phase");
			case SHUFFLE_HAND -> require(p.canShuffle(), "cannot shuffle hand");
		}
		out.i32((index << 16) | r.action().ordinal());
	}

	private static void encodeBattle(Prompt.BattleCommand p, PromptResponse.BattleAction r, WireWriter out) {
		int index = r.index();
		switch (r.action()) {
			case ACTIVATE -> index(index, p.activatable().size());
			case ATTACK -> index(index, p.attackers().size());
			case TO_MAIN_PHASE_2 -> require(p.canMainPhase2(), "cannot enter main phase 2");
			case TO_END_PHASE -> require(p.canEndPhase(), "cannot enter end phase");
		}
		out.i32((index << 16) | r.action().ordinal());
	}

	private static void encodeChain(Prompt.SelectChain p, PromptResponse response, WireWriter out) {
		if (response instanceof PromptResponse.Cancel) {
			require(!p.forced(), "a forced chain cannot be passed");
			out.i32(-1);
		} else {
			out.i32(index(expect(response, PromptResponse.Choice.class).index(), p.chains().size()));
		}
	}

	private static List<Integer> distinctIndices(List<Integer> indices, int size) {
		Set<Integer> seen = new HashSet<>();
		for (int index : indices) {
			index(index, size);
			require(seen.add(index), "duplicate index " + index);
		}
		return indices;
	}

	/** Mode 0 of {@code parse_response_cards}: i32 0, u32 count, u32 indices. */
	private static void writeCardIndices(List<Integer> indices, WireWriter out) {
		out.i32(0).i32(indices.size());
		for (int index : indices) {
			out.i32(index);
		}
	}

	private static void encodeSelectCard(Prompt.SelectCard p, PromptResponse response, WireWriter out) {
		if (response instanceof PromptResponse.Cancel) {
			require(p.cancelable(), "selection is not cancelable");
			out.i32(-1);
			return;
		}
		List<Integer> indices = distinctIndices(expect(response, PromptResponse.Cards.class).indices(), p.cards().size());
		require(indices.size() >= p.min() && indices.size() <= p.max(),
				"must select " + p.min() + ".." + p.max() + " cards, got " + indices.size());
		writeCardIndices(indices, out);
	}

	private static void encodeTribute(Prompt.SelectTribute p, PromptResponse response, WireWriter out) {
		if (response instanceof PromptResponse.Cancel) {
			require(p.cancelable(), "tribute selection is not cancelable");
			out.i32(-1);
			return;
		}
		List<Integer> indices = distinctIndices(expect(response, PromptResponse.Cards.class).indices(), p.cards().size());
		require(indices.size() <= p.max(), "at most " + p.max() + " tributes");
		int total = 0;
		for (int index : indices) {
			total += p.cards().get(index).releaseParam();
		}
		require(total >= p.min(), "tribute value " + total + " below " + p.min());
		writeCardIndices(indices, out);
	}

	private static void encodeSum(SelectSum p, PromptResponse.Cards r, WireWriter out) {
		List<Integer> indices = distinctIndices(r.indices(), p.selectable().size());
		List<SumCandidate> chosen = new ArrayList<>(p.mustSelect());
		for (int index : indices) {
			chosen.add(p.selectable().get(index));
		}
		require(isValidSum(p, indices.size(), chosen), "selection does not satisfy the sum of " + p.accumulate());
		writeCardIndices(indices, out);
	}

	/** Port of the acceptance checks of {@code field::process(Processors::SelectSum&)}. */
	public static boolean isValidSum(SelectSum p, int selectedCount, List<SumCandidate> chosen) {
		int acc = p.accumulate();
		if (!p.atLeast()) {
			if (selectedCount < p.min() || selectedCount > p.max()) {
				return false;
			}
			int[] params = chosen.stream().mapToInt(SumCandidate::param).toArray();
			return sumCheck(params, 0, acc);
		}
		int sum = 0;
		int max = 0;
		int min = Integer.MAX_VALUE;
		for (SumCandidate candidate : chosen) {
			int o1 = candidate.param() & 0xFFFF;
			int o2 = candidate.param() >>> 16;
			int smallest = (o2 != 0 && o2 < o1) ? o2 : o1;
			sum += smallest;
			max += Math.max(o1, o2);
			min = Math.min(min, smallest);
		}
		return !chosen.isEmpty() && max >= acc && sum - min < acc;
	}

	/** {@code select_sum_check1}: can the params (each one of two values) sum exactly to {@code acc}? */
	private static boolean sumCheck(int[] params, int index, int acc) {
		if (acc == 0 || index == params.length) {
			return false;
		}
		int o1 = params[index] & 0xFFFF;
		int o2 = params[index] >>> 16;
		if (index == params.length - 1) {
			return acc == o1 || acc == o2;
		}
		return (acc > o1 && sumCheck(params, index + 1, acc - o1))
				|| (o2 > 0 && acc > o2 && sumCheck(params, index + 1, acc - o2));
	}

	private static void encodeUnselect(Prompt.SelectUnselectCard p, PromptResponse response, WireWriter out) {
		if (response instanceof PromptResponse.Cancel) {
			require(p.cancelable() || p.finishable(), "selection can neither be finished nor canceled");
			out.i32(-1);
			return;
		}
		int index = expect(response, PromptResponse.Choice.class).index();
		index(index, p.selectable().size() + p.unselectable().size());
		out.i32(1).i32(index);
	}

	private static void encodePlaces(Prompt.SelectPlace p, PromptResponse.Places r, WireWriter out) {
		require(r.zones().size() == p.count(), "must choose " + p.count() + " zones");
		int used = p.unavailable();
		for (PromptResponse.Zone zone : r.zones()) {
			require(zone.player() == 0 || zone.player() == 1, "invalid player " + zone.player());
			require(zone.location() == OcgConstants.LOCATION_MZONE || zone.location() == OcgConstants.LOCATION_SZONE,
					"zones must be MZONE or SZONE");
			boolean monsterZone = zone.location() == OcgConstants.LOCATION_MZONE;
			require(zone.sequence() >= 0 && zone.sequence() <= (monsterZone ? 6 : 7), "invalid sequence " + zone.sequence());
			int bit = zoneBit(p.player(), zone);
			require((used & bit) == 0, "zone " + zone + " is not available");
			used |= bit;
			out.u8(zone.player()).u8(zone.location()).u8(zone.sequence());
		}
	}

	/** Bit of a zone in the {@link Prompt.SelectPlace#unavailable()} mask of {@code prompted}. */
	public static int zoneBit(int prompted, PromptResponse.Zone zone) {
		int bit = 1 << zone.sequence();
		if (zone.location() == OcgConstants.LOCATION_SZONE) {
			bit <<= 8;
		}
		if (zone.player() != prompted) {
			bit <<= 16;
		}
		return bit;
	}

	private static void encodePosition(Prompt.SelectPosition p, PromptResponse.Position r, WireWriter out) {
		int position = r.position();
		require(Integer.bitCount(position) == 1 && (position & 0xF) == position, "position must be a single POS_* value");
		require((position & p.positions()) != 0, "position not allowed");
		out.i32(position);
	}

	private static void encodeCounters(Prompt.SelectCounter p, PromptResponse.Counters r, WireWriter out) {
		require(r.counts().size() == p.cards().size(), "one count per card required");
		int total = 0;
		for (int i = 0; i < r.counts().size(); i++) {
			int count = r.counts().get(i);
			require(count >= 0 && count <= p.cards().get(i).counters(), "invalid counter count for card " + i);
			total += count;
		}
		require(total == p.count(), "must remove exactly " + p.count() + " counters");
		for (int count : r.counts()) {
			out.u16(count);
		}
	}

	private static void encodeSort(Prompt.SortCards p, PromptResponse response, WireWriter out) {
		if (response instanceof PromptResponse.Cancel) {
			out.u8(0xFF);
			return;
		}
		List<Integer> order = expect(response, PromptResponse.Order.class).order();
		require(order.size() == p.cards().size(), "one slot per card required");
		distinctIndices(order, p.cards().size());
		for (int slot : order) {
			out.u8(slot);
		}
	}

	private static void checkMask(long mask, long available, int count) {
		require((mask & ~available) == 0, "mask contains unavailable bits");
		require(Long.bitCount(mask) == count, "must choose exactly " + count);
	}
}
