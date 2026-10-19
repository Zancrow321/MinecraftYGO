package io.github.zancrow321.jadm.engine.ai.windbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * WindBot's {@code GameAI}: turns each prompt into questions for the {@link Executor} and falls back to sensible
 * defaults when it has no opinion. The executor can also choose in advance ({@link #selectCard} and friends) what
 * the next prompts of an effect it activates should pick.
 */
public final class GameAI {
    public final Duel duel;
    public Executor executor;
    final Random random;

    /** How often each card was activated this turn, so a bot can't loop forever. */
    private final Map<Integer, Integer> activatedCards = new HashMap<>();
    private boolean selectingPendulumSummon;
    private ClientCard pendingAttacker;
    private ClientCard pendingAttackTarget;
    private final Set<ClientCard> attackersWithInvalidPreselectedTarget = new HashSet<>();

    private CardSelector materialSelector;
    private long materialSelectorHint;
    private int place;
    private int option = -1;
    private int number = -1;
    private int announce;
    private int yesno = -1;
    private final List<Integer> attributes = new ArrayList<>();
    private final List<CardSelector> selector = new ArrayList<>();
    private final List<Integer> positions = new ArrayList<>();
    private int selectorPointer = -1;
    private final List<Long> races = new ArrayList<>();

    public GameAI(Duel duel, Random random) {
        this.duel = duel;
        this.random = random;
    }

    // --- Events ----------------------------------------------------------------------------------------------------

    public void onDraw(int player) {
        executor.onDraw(player);
    }

    public void onNewTurn() {
        activatedCards.clear();
        executor.onNewTurn();
    }

    public void onNewPhase() {
        pendingAttacker = null;
        pendingAttackTarget = null;
        attackersWithInvalidPreselectedTarget.clear();
        clearSelections();
        selectingPendulumSummon = false;
        executor.onNewPhase();
    }

    public void onMove(ClientCard card, int previousControler, int previousLocation, int currentControler,
                       int currentLocation) {
        executor.onMove(card, previousControler, previousLocation, currentControler, currentLocation);
    }

    public void onPosChange(ClientCard card, int previousPosition, int currentPosition) {
        executor.onPosChange(card, previousPosition, currentPosition);
    }

    public void onAttack() {
        pendingAttacker = null;
        pendingAttackTarget = null;
        attackersWithInvalidPreselectedTarget.clear();
    }

    public void onChaining(ClientCard card, int player) {
        executor.onChaining(player, card);
    }

    public void onSummoning() {
        executor.onSummoning();
    }

    public void onChainSolved(int chainIndex) {
        executor.onChainSolved(chainIndex);
    }

    public void onSpSummoned() {
        executor.onSpSummoned();
    }

    public void onSpSummoning() {
        selectingPendulumSummon = false;
        executor.onSpSummoning();
    }

    public void onChainEnd() {
        clearSelections();
        executor.onChainEnd();
    }

    private void clearSelections() {
        selector.clear();
        positions.clear();
        selectorPointer = -1;
        materialSelector = null;
        materialSelectorHint = 0;
        place = 0;
        option = -1;
        number = -1;
        announce = 0;
        yesno = -1;
        attributes.clear();
        races.clear();
    }

    public void onHintZone(int player, long zone) {
        executor.onHintZone(player, zone);
    }

    public void onReceivingAnnouce(int player, long data) {
        executor.onReceivingAnnouce(player, data);
    }

    // --- Prompts ---------------------------------------------------------------------------------------------------

    public int onRockPaperScissors() {
        return executor.onRockPaperScissors();
    }

    public BattlePhaseAction onSelectBattleCmd(BattlePhase battle) {
        pendingAttacker = null;
        pendingAttackTarget = null;

        for (CardExecutor exec : executor.executors) {
            if (exec.type == ExecutorType.GO_TO_MAIN_PHASE2 && battle.canMainPhaseTwo && exec.func.getAsBoolean()) {
                return toMainPhase2();
            }
            if (exec.type == ExecutorType.GO_TO_END_PHASE && battle.canEndPhase && exec.func.getAsBoolean()) {
                return toEndPhase();
            }
            for (int i = 0; i < battle.activableCards.size(); ++i) {
                ClientCard card = battle.activableCards.get(i);
                if (shouldExecute(exec, card, ExecutorType.ACTIVATE, battle.activableDescs.get(i), -1)) {
                    return new BattlePhaseAction(BattlePhaseAction.BattleAction.ACTIVATE, card.actionIndex);
                }
            }
        }

        // Stronger attackers first.
        List<ClientCard> attackers = new ArrayList<>(battle.attackableCards.stream()
                .filter(card -> !attackersWithInvalidPreselectedTarget.contains(card)).toList());
        attackers.sort(CardContainer::compareCardAttackPower);
        java.util.Collections.reverse(attackers);

        List<ClientCard> defenders = new ArrayList<>(duel.fields[1].getMonsters());
        defenders.sort(CardContainer::compareDefensePower);
        java.util.Collections.reverse(defenders);

        if (!attackers.isEmpty()) {
            ClientCard selected = executor.onSelectAttacker(attackers, defenders);
            if (selected != null && attackers.contains(selected)) {
                attackers.remove(selected);
                attackers.add(0, selected);
            }
            BattlePhaseAction result = executor.onBattle(attackers, defenders);
            if (result != null) {
                return result;
            }
            if (defenders.isEmpty()) {
                // The weakest attacker goes first when the attacks are direct.
                return attack(attackers.get(attackers.size() - 1), null);
            }
            for (int k = 0; k < attackers.size(); ++k) {
                ClientCard attacker = attackers.get(k);
                attacker.isLastAttacker = k == attackers.size() - 1;
                result = executor.onSelectAttackTarget(attacker, defenders);
                if (result != null) {
                    return result;
                }
            }
        }

        if (battle.canMainPhaseTwo) {
            return toMainPhase2();
        }
        if (battle.canEndPhase) {
            return toEndPhase();
        }
        BattlePhaseAction fallback = attack(battle.attackableCards.get(0), defenders.isEmpty() ? null
                : defenders.get(0));
        // Not through the preselected target logic, which could cancel the attack and loop.
        pendingAttacker = null;
        pendingAttackTarget = null;
        return fallback;
    }

    public List<ClientCard> onSelectCard(List<ClientCard> cards, int min, int max, long hint, boolean cancelable) {
        List<ClientCard> result;

        if (hint == HintMsg.SP_SUMMON && selectingPendulumSummon) {
            result = validateCardSelection(executor.onSelectPendulumSummon(cards, min, max), cards, min, max,
                    cancelable);
            if (result != null) {
                return result;
            }
        }

        // Attack targets are picked with a card selection; some scripts use the same title, hence the check.
        if (hint == HintMsg.ATTACK_TARGET && pendingAttacker != null) {
            if (pendingAttackTarget != null && cards.contains(pendingAttackTarget)) {
                return new ArrayList<>(List.of(pendingAttackTarget));
            }
            if (cancelable) {
                attackersWithInvalidPreselectedTarget.add(pendingAttacker);
                pendingAttacker = null;
                pendingAttackTarget = null;
                return new ArrayList<>();
            }
        }

        result = validateCardSelection(executor.onSelectCard(cards, min, max, hint, cancelable), cards, min, max,
                cancelable);
        if (result != null) {
            return result;
        }

        CardSelector chosen;
        if (hint == HintMsg.FUSION_MATERIAL || hint == HintMsg.SYNCHRO_MATERIAL || hint == HintMsg.XYZ_MATERIAL
                || hint == HintMsg.LINK_MATERIAL) {
            if (materialSelector != null) {
                chosen = materialSelector;
            } else {
                if (hint == HintMsg.FUSION_MATERIAL) {
                    result = executor.onSelectFusionMaterial(cards, min, max);
                }
                if (hint == HintMsg.SYNCHRO_MATERIAL) {
                    result = executor.onSelectSynchroMaterial(cards, new ArrayList<>(), 0, min, max);
                }
                if (hint == HintMsg.XYZ_MATERIAL) {
                    result = executor.onSelectXyzMaterial(cards, min, max);
                }
                if (hint == HintMsg.LINK_MATERIAL) {
                    result = executor.onSelectLinkMaterial(cards, min, max);
                }
                result = validateCardSelection(result, cards, min, max, cancelable);
                if (result != null) {
                    return result;
                }
                chosen = getSelectedCards();
            }
        } else if (materialSelector != null && hint == materialSelectorHint) {
            chosen = materialSelector;
        } else {
            chosen = getSelectedCards();
        }

        if (chosen != null) {
            return chosen.select(cards, min, max);
        }

        // Otherwise the first cards, as few as allowed.
        List<ClientCard> selected = new ArrayList<>();
        if (cards.size() >= min) {
            for (int i = 0; i < min; ++i) {
                selected.add(cards.get(i));
            }
        }
        return selected;
    }

    private static List<ClientCard> validateCardSelection(List<ClientCard> selected, List<ClientCard> cards, int min,
                                                          int max, boolean cancelable) {
        if (selected == null) {
            return null;
        }
        boolean validCount = selected.size() >= min && selected.size() <= max;
        if (cancelable && selected.isEmpty()) {
            validCount = true;
        }
        if (!validCount || new HashSet<>(selected).size() != selected.size()
                || selected.stream().anyMatch(card -> card == null || !cards.contains(card))) {
            return null;
        }
        return selected;
    }

    /** @return the index of the effect to chain, or -1 to pass */
    public int onSelectChain(List<ClientCard> cards, List<Long> descs, List<Boolean> forces, int timing) {
        executor.onSelectChain(cards);
        for (CardExecutor exec : executor.executors) {
            for (int i = 0; i < cards.size(); ++i) {
                if (shouldExecute(exec, cards.get(i), ExecutorType.ACTIVATE, descs.get(i), timing)) {
                    return i;
                }
            }
        }
        for (int i = 0; i < forces.size(); ++i) {
            if (forces.get(i)) {
                return i;
            }
        }
        return -1;
    }

    /** @return how many counters to take from each card, the first ones first */
    public int[] onSelectCounter(int type, int quantity, List<ClientCard> cards, List<Integer> counters) {
        int[] used = new int[counters.size()];
        int i = 0;
        while (quantity > 0 && i < counters.size()) {
            if (counters.get(i) >= quantity) {
                used[i] = quantity;
                quantity = 0;
            } else {
                used[i] = counters.get(i);
                quantity -= counters.get(i);
            }
            i++;
        }
        return used;
    }

    public List<ClientCard> onCardSorting(List<ClientCard> cards) {
        List<ClientCard> result = executor.onCardSorting(cards);
        return result != null ? result : new ArrayList<>(cards);
    }

    public boolean onSelectEffectYn(ClientCard card, long desc) {
        for (CardExecutor exec : executor.executors) {
            if (shouldExecute(exec, card, ExecutorType.ACTIVATE, desc, -1)) {
                return true;
            }
        }
        return false;
    }

    public MainPhaseAction onSelectIdleCmd(MainPhase main) {
        selectingPendulumSummon = false;
        for (CardExecutor exec : executor.executors) {
            // Going to the End or Battle Phase comes first; those rules have no card.
            if (exec.type == ExecutorType.GO_TO_END_PHASE && main.canEndPhase && exec.func.getAsBoolean()) {
                return new MainPhaseAction(MainPhaseAction.MainAction.TO_END_PHASE);
            }
            if (exec.type == ExecutorType.GO_TO_BATTLE_PHASE && main.canBattlePhase && exec.func.getAsBoolean()) {
                return new MainPhaseAction(MainPhaseAction.MainAction.TO_BATTLE_PHASE);
            }
            for (int i = 0; i < main.activableCards.size(); ++i) {
                ClientCard card = main.activableCards.get(i);
                long desc = main.activableDescs.get(i);
                if (shouldExecute(exec, card, ExecutorType.ACTIVATE, desc, -1)) {
                    return new MainPhaseAction(MainPhaseAction.MainAction.ACTIVATE,
                            card.actionActivateIndex.getOrDefault(desc, i));
                }
            }
            for (ClientCard card : main.monsterSetableCards) {
                if (shouldExecute(exec, card, ExecutorType.MONSTER_SET, -1, -1)) {
                    return new MainPhaseAction(MainPhaseAction.MainAction.SET_MONSTER, card.actionIndex);
                }
            }
            for (ClientCard card : main.reposableCards) {
                if (shouldExecute(exec, card, ExecutorType.REPOS, -1, -1)) {
                    return new MainPhaseAction(MainPhaseAction.MainAction.REPOS, card.actionIndex);
                }
            }
            for (ClientCard card : main.specialSummonableCards) {
                if (shouldExecute(exec, card, ExecutorType.SP_SUMMON, -1, -1)) {
                    ClientCard leftScale = executor.util.getPZone(0, 0);
                    ClientCard rightScale = executor.util.getPZone(0, 1);
                    selectingPendulumSummon = card.hasType(CardType.PENDULUM)
                            && (card == leftScale || card == rightScale);
                    return new MainPhaseAction(MainPhaseAction.MainAction.SP_SUMMON, card.actionIndex);
                }
            }
            for (ClientCard card : main.summonableCards) {
                if (shouldExecute(exec, card, ExecutorType.SUMMON, -1, -1)) {
                    return new MainPhaseAction(MainPhaseAction.MainAction.SUMMON, card.actionIndex);
                }
                if (shouldExecute(exec, card, ExecutorType.SUMMON_OR_SET, -1, -1)) {
                    if (main.monsterSetableCards.contains(card) && executor.onSelectMonsterSummonOrSet(card)) {
                        return new MainPhaseAction(MainPhaseAction.MainAction.SET_MONSTER, card.actionIndex);
                    }
                    return new MainPhaseAction(MainPhaseAction.MainAction.SUMMON, card.actionIndex);
                }
            }
            for (ClientCard card : main.spellSetableCards) {
                if (shouldExecute(exec, card, ExecutorType.SPELL_SET, -1, -1)) {
                    return new MainPhaseAction(MainPhaseAction.MainAction.SET_SPELL, card.actionIndex);
                }
            }
        }
        if (main.canBattlePhase && (duel.fields[0].hasAttackingMonster() || !main.canEndPhase)) {
            return new MainPhaseAction(MainPhaseAction.MainAction.TO_BATTLE_PHASE);
        }
        return new MainPhaseAction(MainPhaseAction.MainAction.TO_END_PHASE);
    }

    /** System strings 60 and 61: Heads and Tails. */
    private static final long HEADS = 60;
    private static final long TAILS = 61;

    public int onSelectOption(List<Long> options) {
        int selectorSelected = option;
        option = -1;
        int result = executor.onSelectOption(options);
        if (result >= 0 && result < options.size()) {
            return result;
        }
        if (selectorSelected >= 0 && selectorSelected < options.size()) {
            return selectorSelected;
        }
        // Calling a coin toss is a guess, not always heads.
        if (options.size() == 2 && options.get(0) == HEADS && options.get(1) == TAILS) {
            return random.nextInt(2);
        }
        return 0;
    }

    /**
     * @param available the zones that may be picked: bits 0-6 the bot's Monster Zones, 8-15 its Spell &amp; Trap
     *                  slots, 16-31 the same for the opponent
     * @return one zone bit
     */
    public int onSelectPlace(int cardId, int count, int available) {
        if (count > 1) {
            place = 0;
            return onSelectDisfield(cardId, count, available);
        }
        int player;
        int location;
        int filter;
        if ((available & 0x7f) != 0) {
            player = 0;
            location = CardLocation.MONSTER_ZONE;
            filter = available & Zones.MONSTER_ZONES;
        } else if ((available & 0x1f00) != 0) {
            player = 0;
            location = CardLocation.SPELL_ZONE;
            filter = (available >> 8) & Zones.SPELL_ZONES;
        } else if ((available & 0x2000) != 0) {
            player = 0;
            location = CardLocation.FIELD_ZONE;
            filter = Zones.FIELD_ZONE;
        } else if ((available & 0xc000) != 0) {
            player = 0;
            location = CardLocation.PENDULUM_ZONE;
            filter = (available >> 14) & Zones.PENDULUM_ZONES;
        } else if ((available & 0x7f0000) != 0) {
            player = 1;
            location = CardLocation.MONSTER_ZONE;
            filter = (available >> 16) & Zones.MONSTER_ZONES;
        } else if ((available & 0x1f000000) != 0) {
            player = 1;
            location = CardLocation.SPELL_ZONE;
            filter = (available >> 24) & Zones.SPELL_ZONES;
        } else if ((available & 0x20000000) != 0) {
            player = 1;
            location = CardLocation.FIELD_ZONE;
            filter = Zones.FIELD_ZONE;
        } else {
            player = 1;
            location = CardLocation.PENDULUM_ZONE;
            filter = (available >>> 30) & Zones.PENDULUM_ZONES;
        }

        int selectorSelected = place;
        place = 0;
        int executorSelected = executor.onSelectPlace(cardId, player, location, filter);
        if ((executorSelected & filter) > 0) {
            filter &= executorSelected;
        } else if ((selectorSelected & filter) > 0) {
            filter &= selectorSelected;
        }

        int sequence = 0;
        if (location != CardLocation.PENDULUM_ZONE && location != CardLocation.FIELD_ZONE) {
            if ((filter & Zones.Z2) != 0) {
                sequence = 2;
            } else if ((filter & Zones.Z1) != 0) {
                sequence = 1;
            } else if ((filter & Zones.Z3) != 0) {
                sequence = 3;
            } else if ((filter & Zones.Z0) != 0) {
                sequence = 0;
            } else if ((filter & Zones.Z4) != 0) {
                sequence = 4;
            } else if ((filter & Zones.Z6) != 0) {
                sequence = 6;
            } else if ((filter & Zones.Z5) != 0) {
                sequence = 5;
            }
        } else {
            if (location == CardLocation.FIELD_ZONE) {
                sequence = 5;
            } else {
                sequence = (filter & Zones.Z0) != 0 ? 6 : 7;
            }
            location = CardLocation.SPELL_ZONE;
        }
        return 1 << (player * 16 + (location == CardLocation.MONSTER_ZONE ? 0 : 8) + sequence);
    }

    /** @return {@code count} zone bits from {@code available} */
    public int onSelectDisfield(long hint, int count, int available) {
        int required = Math.max(1, count);
        int executorSelected = executor.onSelectDisfield(hint, count, available) & available;
        boolean mirroredExtraZones = (executorSelected & ((1 << 5) | (1 << 22))) == ((1 << 5) | (1 << 22))
                || (executorSelected & ((1 << 6) | (1 << 21))) == ((1 << 6) | (1 << 21));
        if (Integer.bitCount(executorSelected) == required && !mirroredExtraZones) {
            return executorSelected;
        }

        // Central zones first, the opponent's before the bot's.
        List<Integer> zones = new ArrayList<>();
        int[] monsterZoneOrder = {2, 1, 3, 0, 4, 6, 5};
        int[] spellZoneOrder = {2, 1, 3, 0, 4};
        for (int player : new int[]{1, 0}) {
            int offset = player * 16;
            for (int sequence : monsterZoneOrder) {
                int zone = 1 << (offset + sequence);
                if ((available & zone) != 0) {
                    zones.add(zone);
                }
            }
            for (int sequence : spellZoneOrder) {
                int zone = 1 << (offset + 8 + sequence);
                if ((available & zone) != 0) {
                    zones.add(zone);
                }
            }
        }
        for (int player : new int[]{0, 1}) {
            int offset = player * 16 + 8;
            for (int sequence : new int[]{5, 7, 6}) {
                int zone = 1 << (offset + sequence);
                if ((available & zone) != 0) {
                    zones.add(zone);
                }
            }
        }

        int selected = 0;
        int remaining = required;
        for (int zone : zones) {
            if (remaining == 0) {
                break;
            }
            // Each Extra Monster Zone has a bit on both sides; picking both is not allowed.
            if (zone == (1 << 5) && (selected & (1 << 22)) != 0 || zone == (1 << 22) && (selected & (1 << 5)) != 0
                    || zone == (1 << 6) && (selected & (1 << 21)) != 0
                    || zone == (1 << 21) && (selected & (1 << 6)) != 0) {
                continue;
            }
            selected |= zone;
            --remaining;
        }
        return selected;
    }

    /** @return one of {@code positions} */
    public int onSelectPosition(int cardId, List<Integer> positions) {
        int executorSelected = executor.onSelectPosition(cardId, positions);
        int selectorSelected = getSelectedPosition();
        if (positions.contains(executorSelected)) {
            return executorSelected;
        }
        if (positions.contains(selectorSelected)) {
            return selectorSelected;
        }
        return positions.get(0);
    }

    /** For Synchro materials and Ritual tributes: cards whose values add up to {@code sum}. */
    public List<ClientCard> onSelectSum(List<ClientCard> cards, List<ClientCard> mandatoryCards, int sum, int min,
                                        int max, long hint, boolean exactEqual) {
        List<ClientCard> selected = executor.onSelectSum(cards, mandatoryCards, sum, min, max, hint, exactEqual);
        if (isValidSumSelection(selected, cards, mandatoryCards, sum, min, max, exactEqual)) {
            return selected;
        }

        if (hint == HintMsg.RELEASE || hint == HintMsg.SYNCHRO_MATERIAL) {
            if (materialSelector != null) {
                selected = materialSelector.select(cards, min, max);
            } else if (hint == HintMsg.SYNCHRO_MATERIAL) {
                selected = executor.onSelectSynchroMaterial(cards, mandatoryCards, sum, min, max);
            } else {
                selected = executor.onSelectRitualTribute(cards, mandatoryCards, sum, min, max, exactEqual);
            }
            if (isValidSumSelection(selected, cards, mandatoryCards, sum, min, max, exactEqual)) {
                return selected;
            }
        }

        List<ClientCard> orderedCards = new ArrayList<>();
        if (selected != null) {
            for (ClientCard card : selected) {
                if (card != null && cards.contains(card) && !orderedCards.contains(card)) {
                    orderedCards.add(card);
                }
            }
        }
        for (ClientCard card : cards) {
            if (!orderedCards.contains(card)) {
                orderedCards.add(card);
            }
        }
        selected = findSumSelection(orderedCards, mandatoryCards, sum, min, max, exactEqual);
        return selected != null ? selected : new ArrayList<>();
    }

    private static boolean canReachSum(List<ClientCard> cards, int index, long currentSum, int min, int max) {
        if (currentSum > max) {
            return false;
        }
        if (index == cards.size()) {
            return currentSum >= min && currentSum <= max;
        }
        ClientCard card = cards.get(index);
        if (canReachSum(cards, index + 1, currentSum + card.opParam1, min, max)) {
            return true;
        }
        return card.opParam2 > 0 && card.opParam2 != card.opParam1
                && canReachSum(cards, index + 1, currentSum + card.opParam2, min, max);
    }

    private static boolean isValidSumSelection(List<ClientCard> selected, List<ClientCard> cards,
                                               List<ClientCard> mandatoryCards, int sum, int min, int max,
                                               boolean exactEqual) {
        if (selected == null || new HashSet<>(selected).size() != selected.size()
                || selected.stream().anyMatch(card -> card == null || !cards.contains(card))) {
            return false;
        }
        if (exactEqual && (selected.size() < min || selected.size() > max)) {
            return false;
        }
        List<ClientCard> allSelected = new ArrayList<>(mandatoryCards);
        allSelected.addAll(selected);
        if (exactEqual) {
            return canReachSum(allSelected, 0, 0, sum, sum);
        }
        // The core's "at least" mode only takes a minimal set: the largest possible sum reaches the target, but
        // without the smallest contribution it would not.
        if (allSelected.isEmpty()) {
            return sum <= 0;
        }
        long minimumSum = 0;
        long maximumSum = 0;
        int smallestMinimum = Integer.MAX_VALUE;
        for (ClientCard card : allSelected) {
            int minimum = card.opParam2 > 0 ? Math.min(card.opParam1, card.opParam2) : card.opParam1;
            minimumSum += minimum;
            maximumSum += Math.max(card.opParam1, card.opParam2);
            smallestMinimum = Math.min(smallestMinimum, minimum);
        }
        return isValidGreaterSum(minimumSum, maximumSum, smallestMinimum, sum);
    }

    public List<ClientCard> findSumSelection(List<ClientCard> cards, List<ClientCard> mandatoryCards, int sum, int min,
                                             int max, boolean exactEqual) {
        if (!exactEqual) {
            long minimumSum = 0;
            long maximumSum = 0;
            int smallestMinimum = Integer.MAX_VALUE;
            for (ClientCard card : mandatoryCards) {
                int minimum = card.opParam2 > 0 ? Math.min(card.opParam1, card.opParam2) : card.opParam1;
                minimumSum += minimum;
                maximumSum += Math.max(card.opParam1, card.opParam2);
                smallestMinimum = Math.min(smallestMinimum, minimum);
            }
            List<ClientCard> result = new ArrayList<>();
            long[] remainingMaximums = new long[cards.size() + 1];
            for (int i = cards.size() - 1; i >= 0; --i) {
                remainingMaximums[i] = remainingMaximums[i + 1]
                        + Math.max(cards.get(i).opParam1, cards.get(i).opParam2);
            }
            return trySelectGreaterSum(cards, remainingMaximums, sum, 0, minimumSum, maximumSum, smallestMinimum,
                    mandatoryCards.size(), result) ? result : null;
        }

        Set<Long> mandatorySums = new HashSet<>(Set.of(0L));
        for (ClientCard card : mandatoryCards) {
            Set<Long> nextSums = new HashSet<>();
            for (long current : mandatorySums) {
                if (current + card.opParam1 <= sum) {
                    nextSums.add(current + card.opParam1);
                }
                if (card.opParam2 > 0 && card.opParam2 != card.opParam1 && current + card.opParam2 <= sum) {
                    nextSums.add(current + card.opParam2);
                }
            }
            mandatorySums = nextSums;
        }
        Set<Long> optionalSums = new HashSet<>();
        for (long value : mandatorySums) {
            optionalSums.add(sum - value);
        }
        long maximumOptionalSum = optionalSums.stream().mapToLong(Long::longValue).max().orElse(-1);
        int maximumCount = Math.min(max, cards.size());
        for (int count = min; count <= maximumCount; ++count) {
            List<ClientCard> result = new ArrayList<>();
            if (trySelectCardsBySum(cards, optionalSums, maximumOptionalSum, 0, count, 0, result, new HashSet<>())) {
                return result;
            }
        }
        return null;
    }

    private record SumState(int index, int remaining, long sum) {
    }

    private static boolean trySelectCardsBySum(List<ClientCard> cards, Set<Long> targetSums, long maximumTarget,
                                               int index, int remainingCount, long currentSum,
                                               List<ClientCard> result, Set<SumState> failed) {
        if (remainingCount == 0) {
            return targetSums.contains(currentSum);
        }
        if (currentSum > maximumTarget || cards.size() - index < remainingCount) {
            return false;
        }
        SumState state = new SumState(index, remainingCount, currentSum);
        if (failed.contains(state)) {
            return false;
        }
        ClientCard card = cards.get(index);
        result.add(card);
        if (card.opParam2 > 0 && card.opParam2 != card.opParam1
                && trySelectCardsBySum(cards, targetSums, maximumTarget, index + 1, remainingCount - 1,
                currentSum + card.opParam2, result, failed)) {
            return true;
        }
        if (trySelectCardsBySum(cards, targetSums, maximumTarget, index + 1, remainingCount - 1,
                currentSum + card.opParam1, result, failed)) {
            return true;
        }
        result.remove(result.size() - 1);
        if (trySelectCardsBySum(cards, targetSums, maximumTarget, index + 1, remainingCount, currentSum, result,
                failed)) {
            return true;
        }
        failed.add(state);
        return false;
    }

    private static boolean trySelectGreaterSum(List<ClientCard> cards, long[] remainingMaximums, int sum, int index,
                                               long minimumSum, long maximumSum, int smallestMinimum,
                                               int selectedCount, List<ClientCard> result) {
        if (selectedCount > 0 && isValidGreaterSum(minimumSum, maximumSum, smallestMinimum, sum)) {
            return true;
        }
        if (selectedCount > 0 && minimumSum - smallestMinimum >= sum) {
            return false;
        }
        if (index >= cards.size() || maximumSum + remainingMaximums[index] < sum) {
            return false;
        }
        ClientCard card = cards.get(index);
        int minimum = card.opParam2 > 0 ? Math.min(card.opParam1, card.opParam2) : card.opParam1;
        result.add(card);
        if (trySelectGreaterSum(cards, remainingMaximums, sum, index + 1, minimumSum + minimum,
                maximumSum + Math.max(card.opParam1, card.opParam2), Math.min(smallestMinimum, minimum),
                selectedCount + 1, result)) {
            return true;
        }
        result.remove(result.size() - 1);
        return trySelectGreaterSum(cards, remainingMaximums, sum, index + 1, minimumSum, maximumSum, smallestMinimum,
                selectedCount, result);
    }

    private static boolean isValidGreaterSum(long minimumSum, long maximumSum, int smallestMinimum, int sum) {
        return maximumSum >= sum && minimumSum - smallestMinimum < sum;
    }

    /** @param min the least tribute value, {@code max} the most; a card's value is in {@code opParam1/2} */
    public List<ClientCard> onSelectTribute(List<ClientCard> cards, int min, int max, long hint, boolean cancelable) {
        List<ClientCard> selected = executor.onSelectTribute(cards, min, max, hint, cancelable);
        boolean validCancellation = selected != null && cancelable && selected.isEmpty();
        boolean validSelection = selected != null && new HashSet<>(selected).size() == selected.size()
                && cards.containsAll(selected) && isValidTributeSelection(selected, min, max);
        if (validCancellation || validSelection) {
            return selected;
        }
        List<ClientCard> sorted = new ArrayList<>(cards);
        sorted.sort(CardContainer::compareCardAttack);
        List<ClientCard> result = findTributeSelection(sorted, min, max, false);
        return result != null ? result : new ArrayList<>();
    }

    public boolean isValidTributeSelection(List<ClientCard> selected, int min, int max) {
        return selected != null && selected.stream().allMatch(Objects::nonNull)
                && canReachSum(selected, 0, 0, min, max);
    }

    public List<ClientCard> findTributeSelection(List<ClientCard> cards, int min, int max) {
        return findTributeSelection(cards, min, max, false);
    }

    public List<ClientCard> findTributeSelection(List<ClientCard> cards, int min, int max,
                                                 boolean preferMaximumCount) {
        Set<Long> targetSums = new HashSet<>();
        for (int value = min; value <= max; ++value) {
            targetSums.add((long) value);
        }
        int maximumCount = Math.min(max, cards.size());
        for (int offset = 0; offset <= maximumCount; ++offset) {
            int count = preferMaximumCount ? maximumCount - offset : offset;
            List<ClientCard> selected = new ArrayList<>();
            if (trySelectCardsBySum(cards, targetSums, max, 0, count, 0, selected, new HashSet<>())) {
                return selected;
            }
        }
        return null;
    }

    public boolean onSelectYesNo(long desc) {
        int selected = yesno;
        yesno = -1;
        if (selected != -1) {
            return selected > 0;
        }
        return executor.onSelectYesNo(desc);
    }

    public boolean onSelectBattleReplay() {
        return executor.onSelectBattleReplay();
    }

    public boolean onSelectBattleDirectAttack() {
        return executor.onSelectBattleDirectAttack(pendingAttacker, pendingAttackTarget == null);
    }

    /** @param avail the passcodes the effect allows, never empty */
    public int onAnnounceCard(List<Integer> avail) {
        int announced = announce;
        announce = 0;
        int selected = executor.onAnnounceCard(avail);
        if (avail.contains(selected)) {
            return selected;
        }
        if (avail.contains(announced)) {
            return announced;
        }
        return avail.get(0);
    }

    /** @return the index of the number */
    public int onAnnounceNumber(List<Long> numbers) {
        int selected = number;
        number = -1;
        int index = numbers.indexOf((long) selected);
        if (selected >= 0 && index >= 0) {
            return index;
        }
        return random.nextInt(numbers.size());
    }

    public List<Integer> onAnnounceAttrib(int count, List<Integer> available) {
        List<Integer> found = new ArrayList<>(new LinkedHashSet<>(attributes.stream()
                .filter(available::contains).toList()));
        while (found.size() > count) {
            found.remove(found.size() - 1);
        }
        attributes.clear();
        for (int attribute : available) {
            if (found.size() >= count) {
                break;
            }
            if (!found.contains(attribute)) {
                found.add(attribute);
            }
        }
        return found;
    }

    public List<Long> onAnnounceRace(int count, List<Long> available) {
        List<Long> found = new ArrayList<>(new LinkedHashSet<>(races.stream().filter(available::contains).toList()));
        while (found.size() > count) {
            found.remove(found.size() - 1);
        }
        races.clear();
        for (long race : available) {
            if (found.size() >= count) {
                break;
            }
            if (!found.contains(race)) {
                found.add(race);
            }
        }
        return found;
    }

    // --- Choices made in advance -------------------------------------------------------------------------------

    private void push(CardSelector chosen) {
        selectorPointer = selector.size();
        selector.add(chosen);
    }

    private void insertNext(CardSelector chosen) {
        if (selectorPointer == -1) {
            selectorPointer = 0;
        }
        selector.add(selectorPointer, chosen);
    }

    public void selectCard(ClientCard card) {
        push(new CardSelector(card));
    }

    public void selectCard(List<ClientCard> cards) {
        push(new CardSelector(cards));
    }

    public void selectCard(int cardId) {
        push(new CardSelector(cardId));
    }

    public void selectCard(int... ids) {
        selectCardIds(Arrays.stream(ids).boxed().toList());
    }

    public void selectCardIds(List<Integer> ids) {
        push(CardSelector.ofIds(ids));
    }

    public void selectCardLocation(int location) {
        push(CardSelector.ofLocation(location));
    }

    public void selectNextCard(ClientCard card) {
        insertNext(new CardSelector(card));
    }

    public void selectNextCard(List<ClientCard> cards) {
        insertNext(new CardSelector(cards));
    }

    public void selectNextCard(int cardId) {
        insertNext(new CardSelector(cardId));
    }

    public void selectNextCard(int... ids) {
        selectNextCardIds(Arrays.stream(ids).boxed().toList());
    }

    public void selectNextCardIds(List<Integer> ids) {
        insertNext(CardSelector.ofIds(ids));
    }

    public void selectNextCardLocation(int location) {
        insertNext(CardSelector.ofLocation(location));
    }

    public void selectThirdCard(ClientCard card) {
        insertNext(new CardSelector(card));
    }

    public void selectThirdCard(List<ClientCard> cards) {
        insertNext(new CardSelector(cards));
    }

    public void selectThirdCard(int cardId) {
        insertNext(new CardSelector(cardId));
    }

    public void selectThirdCard(int... ids) {
        selectThirdCardIds(Arrays.stream(ids).boxed().toList());
    }

    public void selectThirdCardIds(List<Integer> ids) {
        insertNext(CardSelector.ofIds(ids));
    }

    public void selectThirdCardLocation(int location) {
        insertNext(CardSelector.ofLocation(location));
    }

    public void selectMaterials(ClientCard card) {
        selectMaterials(card, 0);
    }

    public void selectMaterials(ClientCard card, int hint) {
        materialSelector = new CardSelector(card);
        materialSelectorHint = hint;
    }

    public void selectMaterials(List<ClientCard> cards) {
        selectMaterials(cards, 0);
    }

    public void selectMaterials(List<ClientCard> cards, int hint) {
        materialSelector = new CardSelector(cards);
        materialSelectorHint = hint;
    }

    public void selectMaterials(int cardId) {
        selectMaterials(cardId, 0);
    }

    public void selectMaterials(int cardId, int hint) {
        materialSelector = new CardSelector(cardId);
        materialSelectorHint = hint;
    }

    public void selectMaterialsIds(List<Integer> ids) {
        selectMaterialsIds(ids, 0);
    }

    public void selectMaterialsIds(List<Integer> ids, int hint) {
        materialSelector = CardSelector.ofIds(ids);
        materialSelectorHint = hint;
    }

    public void selectMaterialsLocation(int location) {
        selectMaterialsLocation(location, 0);
    }

    public void selectMaterialsLocation(int location, int hint) {
        materialSelector = CardSelector.ofLocation(location);
        materialSelectorHint = hint;
    }

    public void cleanSelectMaterials() {
        materialSelector = null;
        materialSelectorHint = 0;
    }

    public boolean haveSelectedCards() {
        return !selector.isEmpty() || materialSelector != null;
    }

    public boolean haveSelectedPosition() {
        return !positions.isEmpty();
    }

    public CardSelector getSelectedCards() {
        if (selector.isEmpty()) {
            return null;
        }
        return selector.remove(selector.size() - 1);
    }

    public int getSelectedPosition() {
        return positions.isEmpty() ? CardPosition.FACE_UP_ATTACK : positions.remove(0);
    }

    public void selectPosition(int pos) {
        positions.add(pos);
    }

    public void selectPlace(int zones) {
        place = zones;
    }

    public void selectOption(int opt) {
        option = opt;
    }

    public void selectNumber(int number) {
        this.number = number;
    }

    public void selectAttribute(int attribute) {
        attributes.clear();
        attributes.add(attribute);
    }

    public void selectAttributes(int[] attributes) {
        this.attributes.clear();
        for (int attribute : attributes) {
            this.attributes.add(attribute);
        }
    }

    public void selectRace(long race) {
        races.clear();
        races.add(race);
    }

    public void selectRaces(long[] races) {
        this.races.clear();
        for (long race : races) {
            this.races.add(race);
        }
    }

    public void selectAnnounceID(int id) {
        announce = id;
    }

    public void selectYesNo(boolean opt) {
        yesno = opt ? 1 : 0;
    }

    public BattlePhaseAction attack(ClientCard attacker, ClientCard defender) {
        pendingAttacker = attacker;
        pendingAttackTarget = defender;
        return new BattlePhaseAction(BattlePhaseAction.BattleAction.ATTACK, attacker.actionIndex);
    }

    public BattlePhaseAction toEndPhase() {
        return new BattlePhaseAction(BattlePhaseAction.BattleAction.TO_END_PHASE);
    }

    public BattlePhaseAction toMainPhase2() {
        return new BattlePhaseAction(BattlePhaseAction.BattleAction.TO_MAIN_PHASE_TWO);
    }

    private boolean shouldExecute(CardExecutor exec, ClientCard card, ExecutorType type, long desc, int timing) {
        executor.setCard(type, card, desc, timing);
        if (card.id != 0 && type == ExecutorType.ACTIVATE) {
            if (activatedCards.getOrDefault(card.id, 0) >= 9) {
                return false;
            }
            if (!executor.onPreActivate(card)) {
                return false;
            }
        }
        boolean result = exec.type == type && (exec.cardId == -1 || card.isOriginalCode(exec.cardId))
                && (exec.func == null || exec.func.getAsBoolean());
        if (card.id != 0 && type == ExecutorType.ACTIVATE && result) {
            activatedCards.merge(card.id, card.isDisabled() ? 3 : 1, Integer::sum);
        }
        return result;
    }
}
