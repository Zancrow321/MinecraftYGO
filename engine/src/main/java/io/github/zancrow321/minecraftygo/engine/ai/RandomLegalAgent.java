package io.github.zancrow321.minecraftygo.engine.ai;

import io.github.zancrow321.minecraftygo.engine.constants.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.data.CardData;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.prompt.AnnounceFilter;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse.BattleActionType;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse.IdleActionType;
import io.github.zancrow321.minecraftygo.engine.prompt.ResponseEncoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Answers every prompt with a random but legal response. Used for fuzzing the engine and as the baseline the
 * heuristic bot has to beat. Bounded per turn so random play cannot loop forever.
 */
public class RandomLegalAgent implements DuelAgent {
	private static final int MAX_IDLE_ACTIONS_PER_TURN = 12;
	private static final int MAX_UNSELECT_STEPS = 24;

	protected final Random random;
	private final CardDatabase database;
	private int idleActionsThisTurn;
	private int unselectSteps;

	public RandomLegalAgent(long seed, CardDatabase database) {
		this.random = new Random(seed);
		this.database = database;
	}

	@Override
	public void observe(CoreMessage message) {
		if (message instanceof Event.NewTurn) {
			idleActionsThisTurn = 0;
		}
	}

	@Override
	public PromptResponse respond(Prompt prompt) {
		if (!(prompt instanceof Prompt.SelectUnselectCard)) {
			unselectSteps = 0;
		}
		return switch (prompt) {
			case Prompt.IdleCommand p -> idle(p);
			case Prompt.BattleCommand p -> battle(p);
			case Prompt.EffectYesNo _, Prompt.YesNo _ -> new PromptResponse.YesNo(random.nextInt(10) < 7);
			case Prompt.SelectOption p -> new PromptResponse.Choice(random.nextInt(p.options().size()));
			case Prompt.AnnounceNumber p -> new PromptResponse.Choice(random.nextInt(p.options().size()));
			case Prompt.SelectChain p -> chain(p);
			case Prompt.SelectCard p -> new PromptResponse.Cards(pickIndices(p.cards().size(), Math.max(p.min(), 1), p.max()));
			case Prompt.SelectTribute p -> tribute(p);
			case Prompt.SelectSum p -> sum(p);
			case Prompt.SelectUnselectCard p -> unselect(p);
			case Prompt.SelectPlace p -> places(p);
			case Prompt.SelectPosition p -> new PromptResponse.Position(randomBit(p.positions() & 0xF));
			case Prompt.SelectCounter p -> counters(p);
			case Prompt.SortCards p -> sort(p);
			case Prompt.AnnounceRace p -> new PromptResponse.Mask(randomBits(p.available(), p.count()));
			case Prompt.AnnounceAttribute p -> new PromptResponse.Mask(randomBits(Integer.toUnsignedLong(p.available()), p.count()));
			case Prompt.AnnounceCard p -> new PromptResponse.Code(announce(p));
			case Prompt.RockPaperScissors _ -> new PromptResponse.Choice(1 + random.nextInt(3));
		};
	}

	protected PromptResponse idle(Prompt.IdleCommand p) {
		List<PromptResponse.IdleAction> actions = new ArrayList<>();
		if (idleActionsThisTurn < MAX_IDLE_ACTIONS_PER_TURN) {
			addAll(actions, IdleActionType.SUMMON, p.summonable().size());
			addAll(actions, IdleActionType.SPECIAL_SUMMON, p.specialSummonable().size());
			addAll(actions, IdleActionType.MONSTER_SET, p.monsterSettable().size());
			addAll(actions, IdleActionType.SPELL_SET, p.spellSettable().size());
			addAll(actions, IdleActionType.ACTIVATE, p.activatable().size());
			if (random.nextInt(4) == 0) {
				addAll(actions, IdleActionType.REPOSITION, p.repositionable().size());
			}
		}
		boolean proceed = actions.isEmpty() || random.nextInt(4) == 0;
		if (proceed) {
			if (p.canBattlePhase() && random.nextInt(5) != 0) {
				return new PromptResponse.IdleAction(IdleActionType.TO_BATTLE_PHASE, 0);
			}
			if (p.canEndPhase()) {
				return new PromptResponse.IdleAction(IdleActionType.TO_END_PHASE, 0);
			}
			if (p.canBattlePhase()) {
				return new PromptResponse.IdleAction(IdleActionType.TO_BATTLE_PHASE, 0);
			}
		}
		if (actions.isEmpty()) {
			// Nothing else is legal; the core always offers at least one action.
			addAll(actions, IdleActionType.SUMMON, p.summonable().size());
			addAll(actions, IdleActionType.SPECIAL_SUMMON, p.specialSummonable().size());
			addAll(actions, IdleActionType.REPOSITION, p.repositionable().size());
			addAll(actions, IdleActionType.MONSTER_SET, p.monsterSettable().size());
			addAll(actions, IdleActionType.SPELL_SET, p.spellSettable().size());
			addAll(actions, IdleActionType.ACTIVATE, p.activatable().size());
			if (p.canShuffle()) {
				actions.add(new PromptResponse.IdleAction(IdleActionType.SHUFFLE_HAND, 0));
			}
		}
		idleActionsThisTurn++;
		return actions.get(random.nextInt(actions.size()));
	}

	private static void addAll(List<PromptResponse.IdleAction> actions, IdleActionType type, int count) {
		for (int i = 0; i < count; i++) {
			actions.add(new PromptResponse.IdleAction(type, i));
		}
	}

	protected PromptResponse battle(Prompt.BattleCommand p) {
		if (!p.attackers().isEmpty() && random.nextInt(10) < 8) {
			return new PromptResponse.BattleAction(BattleActionType.ATTACK, random.nextInt(p.attackers().size()));
		}
		if (!p.activatable().isEmpty() && random.nextInt(5) == 0) {
			return new PromptResponse.BattleAction(BattleActionType.ACTIVATE, random.nextInt(p.activatable().size()));
		}
		if (p.canMainPhase2() && random.nextBoolean()) {
			return new PromptResponse.BattleAction(BattleActionType.TO_MAIN_PHASE_2, 0);
		}
		if (p.canEndPhase()) {
			return new PromptResponse.BattleAction(BattleActionType.TO_END_PHASE, 0);
		}
		if (p.canMainPhase2()) {
			return new PromptResponse.BattleAction(BattleActionType.TO_MAIN_PHASE_2, 0);
		}
		if (!p.attackers().isEmpty()) {
			return new PromptResponse.BattleAction(BattleActionType.ATTACK, random.nextInt(p.attackers().size()));
		}
		return new PromptResponse.BattleAction(BattleActionType.ACTIVATE, random.nextInt(p.activatable().size()));
	}

	protected PromptResponse chain(Prompt.SelectChain p) {
		if (p.chains().isEmpty()) {
			return new PromptResponse.Cancel();
		}
		if (p.forced() || random.nextInt(10) < 4) {
			return new PromptResponse.Choice(random.nextInt(p.chains().size()));
		}
		return new PromptResponse.Cancel();
	}

	private List<Integer> pickIndices(int size, int min, int max) {
		int lower = Math.min(min, size);
		int upper = Math.min(max, size);
		int count = lower >= upper ? lower : lower + random.nextInt(upper - lower + 1);
		List<Integer> all = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			all.add(i);
		}
		Collections.shuffle(all, random);
		return new ArrayList<>(all.subList(0, count));
	}

	private PromptResponse tribute(Prompt.SelectTribute p) {
		List<Integer> order = pickIndices(p.cards().size(), p.cards().size(), p.cards().size());
		for (int attempt = 0; attempt < 2; attempt++) {
			if (attempt == 1) {
				order.sort((a, b) -> p.cards().get(b).releaseParam() - p.cards().get(a).releaseParam());
			}
			List<Integer> chosen = new ArrayList<>();
			int total = 0;
			for (int index : order) {
				if (total >= p.min() || chosen.size() >= p.max()) {
					break;
				}
				chosen.add(index);
				total += p.cards().get(index).releaseParam();
			}
			if (total >= p.min() && chosen.size() <= p.max()) {
				return new PromptResponse.Cards(chosen);
			}
		}
		if (p.cancelable()) {
			return new PromptResponse.Cancel();
		}
		throw new IllegalStateException("No legal tribute selection for " + p);
	}

	private PromptResponse sum(Prompt.SelectSum p) {
		List<Integer> order = pickIndices(p.selectable().size(), p.selectable().size(), p.selectable().size());
		List<Integer> result = new ArrayList<>();
		int[] budget = {200_000};
		if (searchSum(p, order, 0, new ArrayList<>(), result, budget)) {
			return new PromptResponse.Cards(result);
		}
		throw new IllegalStateException("No legal sum selection for " + p);
	}

	private boolean searchSum(Prompt.SelectSum p, List<Integer> order, int start, List<Integer> current,
			List<Integer> result, int[] budget) {
		if (--budget[0] < 0) {
			return false;
		}
		if (!current.isEmpty() || !p.mustSelect().isEmpty()) {
			List<Prompt.SumCandidate> chosen = new ArrayList<>(p.mustSelect());
			for (int index : current) {
				chosen.add(p.selectable().get(index));
			}
			if (ResponseEncoder.isValidSum(p, current.size(), chosen)) {
				result.addAll(current);
				return true;
			}
		}
		if (!p.atLeast() && current.size() >= p.max()) {
			return false;
		}
		for (int i = start; i < order.size(); i++) {
			current.add(order.get(i));
			if (searchSum(p, order, i + 1, current, result, budget)) {
				return true;
			}
			current.removeLast();
		}
		return false;
	}

	private PromptResponse unselect(Prompt.SelectUnselectCard p) {
		unselectSteps++;
		boolean canStop = p.finishable() || p.cancelable();
		if (canStop && (p.selectable().isEmpty() || unselectSteps > MAX_UNSELECT_STEPS || random.nextBoolean())) {
			return new PromptResponse.Cancel();
		}
		if (!p.selectable().isEmpty()) {
			return new PromptResponse.Choice(random.nextInt(p.selectable().size()));
		}
		return new PromptResponse.Choice(p.selectable().size() + random.nextInt(p.unselectable().size()));
	}

	private PromptResponse places(Prompt.SelectPlace p) {
		List<PromptResponse.Zone> free = new ArrayList<>();
		int self = p.player();
		int opponent = 1 - self;
		for (int seq = 0; seq < 7; seq++) {
			free.add(new PromptResponse.Zone(self, OcgConstants.LOCATION_MZONE, seq));
			free.add(new PromptResponse.Zone(opponent, OcgConstants.LOCATION_MZONE, seq));
		}
		for (int seq = 0; seq < 8; seq++) {
			free.add(new PromptResponse.Zone(self, OcgConstants.LOCATION_SZONE, seq));
			free.add(new PromptResponse.Zone(opponent, OcgConstants.LOCATION_SZONE, seq));
		}
		free.removeIf(zone -> (p.unavailable() & ResponseEncoder.zoneBit(self, zone)) != 0);
		Collections.shuffle(free, random);
		if (free.size() < p.count()) {
			throw new IllegalStateException("Not enough free zones for " + p);
		}
		return new PromptResponse.Places(free.subList(0, p.count()));
	}

	private PromptResponse counters(Prompt.SelectCounter p) {
		int[] counts = new int[p.cards().size()];
		int remaining = p.count();
		List<Integer> order = pickIndices(counts.length, counts.length, counts.length);
		for (int index : order) {
			int take = Math.min(remaining, p.cards().get(index).counters());
			counts[index] = take;
			remaining -= take;
		}
		List<Integer> list = new ArrayList<>();
		for (int count : counts) {
			list.add(count);
		}
		return new PromptResponse.Counters(list);
	}

	private PromptResponse sort(Prompt.SortCards p) {
		if (random.nextBoolean()) {
			return new PromptResponse.Cancel();
		}
		return new PromptResponse.Order(pickIndices(p.cards().size(), p.cards().size(), p.cards().size()));
	}

	private int randomBit(int mask) {
		List<Integer> bits = new ArrayList<>();
		for (int i = 0; i < 32; i++) {
			if ((mask & (1 << i)) != 0) {
				bits.add(1 << i);
			}
		}
		return bits.get(random.nextInt(bits.size()));
	}

	private long randomBits(long available, int count) {
		List<Long> bits = new ArrayList<>();
		for (int i = 0; i < 64; i++) {
			if ((available & (1L << i)) != 0) {
				bits.add(1L << i);
			}
		}
		Collections.shuffle(bits, random);
		long mask = 0;
		for (int i = 0; i < count && i < bits.size(); i++) {
			mask |= bits.get(i);
		}
		return mask;
	}

	private int announce(Prompt.AnnounceCard p) {
		List<Integer> codes = new ArrayList<>(database.codes());
		Collections.shuffle(codes, random);
		for (int code : codes) {
			Optional<CardData> card = database.find(code);
			if (card.isPresent() && AnnounceFilter.isDeclarable(card.get(), p.opcodes())) {
				return code;
			}
		}
		throw new IllegalStateException("No declarable card for " + p);
	}
}
