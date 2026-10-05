package io.github.zancrow321.minecraftygo.engine.ai;

import io.github.zancrow321.minecraftygo.engine.data.CardData;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.prompt.CardRef;
import io.github.zancrow321.minecraftygo.engine.prompt.ChainOption;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse.BattleActionType;
import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse.IdleActionType;
import io.github.zancrow321.minecraftygo.engine.query.CardInfo;
import io.github.zancrow321.minecraftygo.engine.query.FieldState;
import io.github.zancrow321.minecraftygo.engine.query.PlayerField;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;

/**
 * Rule-based bot (v1): develops its board, attacks only when it wins the battle, removes the strongest threats with
 * its effects and keeps traps set. Everything it does not handle explicitly falls back to legal random choices.
 */
public class HeuristicAgent extends RandomLegalAgent {
	private static final int UNKNOWN_MONSTER_STAT = 1500;
	private static final int MAX_ACTIVATIONS_PER_CARD_PER_TURN = 2;
	private static final int DARK_HOLE = 53129443;

	private final CardDatabase database;
	private final Map<Integer, Integer> activationsThisTurn = new HashMap<>();
	private FieldState field;
	private int pendingAttackerAttack = -1;

	public HeuristicAgent(long seed, CardDatabase database) {
		super(seed, database);
		this.database = database;
	}

	@Override
	public void observe(CoreMessage message) {
		super.observe(message);
		if (message instanceof Event.NewTurn) {
			activationsThisTurn.clear();
		}
	}

	@Override
	public void observeField(FieldState field) {
		this.field = field;
	}

	@Override
	public PromptResponse respond(Prompt prompt) {
		PromptResponse response = switch (prompt) {
			case Prompt.SelectCard p when field != null -> selectCards(p);
			case Prompt.SelectTribute p when field != null -> tribute(p);
			case Prompt.SelectPosition p -> position(p);
			case Prompt.EffectYesNo _, Prompt.YesNo _ -> new PromptResponse.YesNo(true);
			default -> null;
		};
		if (!(prompt instanceof Prompt.SelectCard)) {
			pendingAttackerAttack = -1;
		}
		return response != null ? response : super.respond(prompt);
	}

	// ---- main phase --------------------------------------------------------------------------------------

	@Override
	protected PromptResponse idle(Prompt.IdleCommand p) {
		if (field == null) {
			return super.idle(p);
		}
		int me = p.player();
		PlayerField mine = field.player(me);
		PlayerField theirs = field.player(1 - me);

		for (int i = 0; i < p.activatable().size(); i++) {
			ChainOption option = p.activatable().get(i);
			if (shouldActivate(option, mine, theirs)) {
				activationsThisTurn.merge(option.card().code(), 1, Integer::sum);
				return new PromptResponse.IdleAction(IdleActionType.ACTIVATE, i);
			}
		}
		if (!p.specialSummonable().isEmpty()) {
			return new PromptResponse.IdleAction(IdleActionType.SPECIAL_SUMMON, best(p.specialSummonable(), this::attackOf));
		}
		int threat = strongestAttack(theirs);
		if (!p.summonable().isEmpty()) {
			int index = best(p.summonable(), this::attackOf);
			int attack = attackOf(p.summonable().get(index));
			if (attack >= threat || p.monsterSettable().isEmpty()) {
				return new PromptResponse.IdleAction(IdleActionType.SUMMON, index);
			}
		}
		if (!p.monsterSettable().isEmpty()) {
			return new PromptResponse.IdleAction(IdleActionType.MONSTER_SET, best(p.monsterSettable(), this::defenseOf));
		}
		for (int i = 0; i < p.spellSettable().size(); i++) {
			CardData card = data(p.spellSettable().get(i));
			if (card != null && (card.isType(TYPE_TRAP) || card.isType(TYPE_QUICKPLAY))) {
				return new PromptResponse.IdleAction(IdleActionType.SPELL_SET, i);
			}
		}
		for (int i = 0; i < p.repositionable().size(); i++) {
			CardRef ref = p.repositionable().get(i);
			CardInfo card = zoneCard(mine, ref);
			if (card != null && card.isFaceUp() && !card.isAttackPosition() && card.attack() > threat) {
				return new PromptResponse.IdleAction(IdleActionType.REPOSITION, i);
			}
		}
		if (p.canBattlePhase() && hasAttackers(mine)) {
			return new PromptResponse.IdleAction(IdleActionType.TO_BATTLE_PHASE, 0);
		}
		if (p.canEndPhase()) {
			return new PromptResponse.IdleAction(IdleActionType.TO_END_PHASE, 0);
		}
		return super.idle(p);
	}

	private boolean shouldActivate(ChainOption option, PlayerField mine, PlayerField theirs) {
		int code = option.card().code();
		if (activationsThisTurn.getOrDefault(code, 0) >= MAX_ACTIVATIONS_PER_CARD_PER_TURN) {
			return false;
		}
		CardData card = database.find(code).orElse(null);
		if (card == null) {
			return false;
		}
		if (code == DARK_HOLE) {
			return theirs.monsterCount() > mine.monsterCount();
		}
		if (card.isType(TYPE_SPELL)) {
			// Field-wide removal and similar spells: only when the opponent has something to hit.
			return !card.isType(TYPE_EQUIP | TYPE_CONTINUOUS) || mine.monsterCount() > 0;
		}
		return !card.isType(TYPE_TRAP);
	}

	// ---- battle phase ------------------------------------------------------------------------------------

	@Override
	protected PromptResponse battle(Prompt.BattleCommand p) {
		if (field == null) {
			return super.battle(p);
		}
		PlayerField mine = field.player(p.player());
		PlayerField theirs = field.player(1 - p.player());
		int bestIndex = -1;
		int bestAttack = -1;
		for (int i = 0; i < p.attackers().size(); i++) {
			Prompt.Attacker attacker = p.attackers().get(i);
			CardInfo card = zoneCard(mine, attacker.card());
			int attack = card != null ? card.attack() : attackOf(attacker.card());
			boolean worthIt = attacker.canAttackDirectly() || theirs.monsterCount() == 0 || canBeatSomething(attack, theirs);
			if (worthIt && attack > bestAttack) {
				bestAttack = attack;
				bestIndex = i;
			}
		}
		if (bestIndex >= 0) {
			pendingAttackerAttack = bestAttack;
			return new PromptResponse.BattleAction(BattleActionType.ATTACK, bestIndex);
		}
		if (p.canMainPhase2()) {
			return new PromptResponse.BattleAction(BattleActionType.TO_MAIN_PHASE_2, 0);
		}
		if (p.canEndPhase()) {
			return new PromptResponse.BattleAction(BattleActionType.TO_END_PHASE, 0);
		}
		return super.battle(p);
	}

	private static boolean canBeatSomething(int attack, PlayerField theirs) {
		for (CardInfo target : theirs.monsterZones()) {
			if (target != null && attack > statToBeat(target)) {
				return true;
			}
		}
		return false;
	}

	private static int statToBeat(CardInfo target) {
		if (!target.isFaceUp()) {
			return UNKNOWN_MONSTER_STAT;
		}
		return target.isAttackPosition() ? target.attack() : target.defense();
	}

	// ---- chains ----------------------------------------------------------------------------------------------

	@Override
	protected PromptResponse chain(Prompt.SelectChain p) {
		if (p.chains().isEmpty()) {
			return new PromptResponse.Cancel();
		}
		if (p.forced()) {
			return new PromptResponse.Choice(0);
		}
		// Traps and quick effects are only offered when their condition is met (e.g. Mirror Force on an attack).
		for (int i = 0; i < p.chains().size(); i++) {
			CardData card = data(p.chains().get(i).card());
			if (card != null && (card.isType(TYPE_TRAP) || card.isType(TYPE_MONSTER))) {
				return new PromptResponse.Choice(i);
			}
		}
		return random.nextInt(3) == 0 ? new PromptResponse.Choice(random.nextInt(p.chains().size())) : new PromptResponse.Cancel();
	}

	// ---- selections ------------------------------------------------------------------------------------------

	private PromptResponse selectCards(Prompt.SelectCard p) {
		int me = p.player();
		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < p.cards().size(); i++) {
			indices.add(i);
		}
		int count = Math.max(Math.min(Math.max(p.min(), 1), p.max()), 0);
		if (pendingAttackerAttack >= 0) {
			// Attack target: the most valuable monster we beat, otherwise the weakest one.
			int attack = pendingAttackerAttack;
			pendingAttackerAttack = -1;
			indices.sort(Comparator.comparingInt((Integer i) -> {
				CardInfo target = fieldCard(p.cards().get(i));
				int stat = target != null ? statToBeat(target) : UNKNOWN_MONSTER_STAT;
				return stat < attack ? -stat : 100_000 + stat;
			}));
		} else {
			boolean anyOpponent = p.cards().stream().anyMatch(c -> c.controller() != me);
			// Effects aimed at the opponent take their strongest cards; costs use our weakest cards.
			indices.sort(Comparator.comparingInt((Integer i) -> {
				CardRef ref = p.cards().get(i);
				int value = valueOf(ref);
				return anyOpponent ? (ref.controller() != me ? -value : 100_000 + value) : value;
			}));
		}
		return new PromptResponse.Cards(new ArrayList<>(indices.subList(0, Math.min(count, indices.size()))));
	}

	private PromptResponse tribute(Prompt.SelectTribute p) {
		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < p.cards().size(); i++) {
			indices.add(i);
		}
		indices.sort(Comparator.comparingInt(i -> valueOf(p.cards().get(i).card())));
		List<Integer> chosen = new ArrayList<>();
		int total = 0;
		for (int index : indices) {
			if (total >= p.min() || chosen.size() >= p.max()) {
				break;
			}
			chosen.add(index);
			total += p.cards().get(index).releaseParam();
		}
		if (total >= p.min()) {
			return new PromptResponse.Cards(chosen);
		}
		return super.respond(p);
	}

	private PromptResponse position(Prompt.SelectPosition p) {
		CardData card = database.find(p.code()).orElse(null);
		int threat = field == null ? 0 : strongestAttack(field.player(1 - p.player()));
		boolean attack = card == null || card.attack() >= Math.min(card.defense(), threat) || card.attack() >= threat;
		int[] preference = attack
				? new int[] {POS_FACEUP_ATTACK, POS_FACEDOWN_DEFENSE, POS_FACEUP_DEFENSE, POS_FACEDOWN_ATTACK}
				: new int[] {POS_FACEDOWN_DEFENSE, POS_FACEUP_DEFENSE, POS_FACEUP_ATTACK, POS_FACEDOWN_ATTACK};
		for (int position : preference) {
			if ((p.positions() & position) != 0) {
				return new PromptResponse.Position(position);
			}
		}
		return super.respond(p);
	}

	// ---- evaluation helpers ----------------------------------------------------------------------------------

	private CardData data(CardRef ref) {
		return ref.code() == 0 ? null : database.find(ref.code()).orElse(null);
	}

	private int attackOf(CardRef ref) {
		CardData card = data(ref);
		return card == null ? 0 : card.attack();
	}

	private int defenseOf(CardRef ref) {
		CardData card = data(ref);
		return card == null ? 0 : card.defense();
	}

	/** Rough card value: current ATK on the field, printed ATK otherwise, unknown cards average. */
	private int valueOf(CardRef ref) {
		CardInfo onField = fieldCard(ref);
		if (onField != null && onField.code() != 0) {
			return Math.max(onField.attack(), onField.defense());
		}
		CardData card = data(ref);
		return card == null ? UNKNOWN_MONSTER_STAT : Math.max(card.attack(), card.defense());
	}

	private static int best(List<CardRef> cards, java.util.function.ToIntFunction<CardRef> score) {
		int best = 0;
		for (int i = 1; i < cards.size(); i++) {
			if (score.applyAsInt(cards.get(i)) > score.applyAsInt(cards.get(best))) {
				best = i;
			}
		}
		return best;
	}

	private CardInfo fieldCard(CardRef ref) {
		if (field == null || ref.controller() > 1) {
			return null;
		}
		return zoneCard(field.player(ref.controller()), ref);
	}

	private static CardInfo zoneCard(PlayerField side, CardRef ref) {
		List<CardInfo> zones = ref.location() == LOCATION_MZONE ? side.monsterZones()
				: ref.location() == LOCATION_SZONE ? side.spellZones() : null;
		if (zones == null || ref.sequence() < 0 || ref.sequence() >= zones.size()) {
			return null;
		}
		return zones.get(ref.sequence());
	}

	private static int strongestAttack(PlayerField side) {
		return side.monsterZones().stream().filter(Objects::nonNull)
				.mapToInt(c -> c.isFaceUp() ? c.attack() : UNKNOWN_MONSTER_STAT).max().orElse(0);
	}

	private static boolean hasAttackers(PlayerField side) {
		return side.monsterZones().stream().anyMatch(c -> c != null && c.isFaceUp() && c.isAttackPosition() && c.attack() > 0);
	}
}
