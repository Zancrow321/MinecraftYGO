package io.github.zancrow321.jadm.engine.ai.windbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * A deck's decision rules (WindBot's {@code Executor}): a list of {@link CardExecutor}s tried in order, plus hooks
 * the AI calls for choices the rules don't cover. While a rule's function runs, {@link #card},
 * {@link #activateDescription} and {@link #type} name what it is deciding about.
 */
public abstract class Executor {
    public final Duel duel;
    public final List<CardExecutor> executors = new ArrayList<>();
    public final GameAI ai;
    public final AIUtil util;
    protected final Random random;

    protected ExecutorType type;
    protected ClientCard card;
    protected long activateDescription;
    protected int currentTiming;

    protected final ClientField bot;
    protected final ClientField enemy;

    protected Executor(GameAI ai, Duel duel) {
        this.duel = duel;
        this.ai = ai;
        this.random = ai.random;
        util = new AIUtil(duel);
        bot = duel.fields[0];
        enemy = duel.fields[1];
    }

    /** @return 1 scissors, 2 rock, 3 paper */
    public int onRockPaperScissors() {
        return random.nextInt(3) + 1;
    }

    /** @return whether to go first after winning the hand */
    public boolean onSelectHand() {
        return random.nextBoolean();
    }

    public BattlePhaseAction onBattle(List<ClientCard> attackers, List<ClientCard> defenders) {
        return null;
    }

    public ClientCard onSelectAttacker(List<ClientCard> attackers, List<ClientCard> defenders) {
        return null;
    }

    public BattlePhaseAction onSelectAttackTarget(ClientCard attacker, List<ClientCard> defenders) {
        return null;
    }

    public boolean onPreBattleBetween(ClientCard attacker, ClientCard defender) {
        return true;
    }

    public boolean onPreActivate(ClientCard card) {
        return true;
    }

    public void onChaining(int player, ClientCard card) {
    }

    public void onChainSolved(int chainIndex) {
    }

    public void onChainEnd() {
    }

    public void onReceivingAnnouce(int player, long data) {
    }

    public void onNewPhase() {
    }

    public void onNewTurn() {
    }

    public void onDraw(int player) {
    }

    public void onPlayerHint(int player, int hintType, long description) {
    }

    public void onHintZone(int player, long zone) {
    }

    public void onMove(ClientCard card, int previousControler, int previousLocation, int currentControler,
                       int currentLocation) {
    }

    public void onPosChange(ClientCard card, int previousPosition, int currentPosition) {
    }

    public List<ClientCard> onSelectCard(List<ClientCard> cards, int min, int max, long hint, boolean cancelable) {
        return null;
    }

    public List<ClientCard> onSelectTribute(List<ClientCard> cards, int min, int max, long hint,
                                            boolean cancelable) {
        return null;
    }

    public List<ClientCard> onSelectSum(List<ClientCard> cards, List<ClientCard> mandatoryCards, int sum, int min,
                                        int max, long hint, boolean exactEqual) {
        return null;
    }

    public List<ClientCard> onSelectFusionMaterial(List<ClientCard> cards, int min, int max) {
        return null;
    }

    public List<ClientCard> onSelectSynchroMaterial(List<ClientCard> cards, List<ClientCard> mandatoryCards,
                                                    int sum, int min, int max) {
        return null;
    }

    public List<ClientCard> onSelectXyzMaterial(List<ClientCard> cards, int min, int max) {
        return null;
    }

    public List<ClientCard> onSelectLinkMaterial(List<ClientCard> cards, int min, int max) {
        return null;
    }

    public List<ClientCard> onSelectRitualTribute(List<ClientCard> cards, List<ClientCard> mandatoryCards, int sum,
                                                  int min, int max, boolean exactEqual) {
        return null;
    }

    public List<ClientCard> onSelectPendulumSummon(List<ClientCard> cards, int min, int max) {
        return null;
    }

    public List<ClientCard> onCardSorting(List<ClientCard> cards) {
        return null;
    }

    public void onSelectChain(List<ClientCard> cards) {
    }

    public boolean onSelectYesNo(long desc) {
        return true;
    }

    /** @return the index of the option, or -1 to leave it to the AI */
    public int onSelectOption(List<Long> options) {
        return -1;
    }

    /** @return a mask of preferred zones within {@code available}, or 0 for no preference */
    public int onSelectPlace(int cardId, int player, int location, int available) {
        return 0;
    }

    public int onSelectDisfield(long hint, int count, int available) {
        return 0;
    }

    /** @return a {@link CardPosition} bit, or 0 for no preference */
    public int onSelectPosition(int cardId, List<Integer> positions) {
        return 0;
    }

    public boolean onSelectBattleReplay() {
        return false;
    }

    public boolean onSelectBattleDirectAttack(ClientCard attacker, boolean preselectedAnswer) {
        return preselectedAnswer;
    }

    /** @return whether to set the monster instead of summoning it face-up */
    public boolean onSelectMonsterSummonOrSet(ClientCard card) {
        return false;
    }

    /** @return a passcode from {@code avail}, or anything else to leave it to the AI */
    public int onAnnounceCard(List<Integer> avail) {
        return 0;
    }

    public void onSpSummoned() {
    }

    public void onSpSummoning() {
    }

    public void onSummoning() {
    }

    public void setCard(ExecutorType type, ClientCard card, long description, int timing) {
        this.type = type;
        this.card = card;
        this.activateDescription = description;
        this.currentTiming = timing;
    }

    public void addExecutor(ExecutorType type, int cardId, BooleanSupplier func) {
        executors.add(new CardExecutor(type, cardId, func));
    }

    public void addExecutor(ExecutorType type, int cardId) {
        executors.add(new CardExecutor(type, cardId, null));
    }

    public void addExecutor(ExecutorType type, BooleanSupplier func) {
        executors.add(new CardExecutor(type, -1, func));
    }

    /** A rule for every card that no other rule of this kind names. */
    public void addExecutor(ExecutorType type) {
        executors.add(new CardExecutor(type, -1, this::defaultNoExecutor));
    }

    private boolean defaultNoExecutor() {
        return executors.stream().allMatch(exec -> exec.type != type || !card.isOriginalCode(exec.cardId));
    }
}
