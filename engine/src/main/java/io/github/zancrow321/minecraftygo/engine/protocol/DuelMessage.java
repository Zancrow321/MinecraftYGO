package io.github.zancrow321.minecraftygo.engine.protocol;

import java.util.List;

/**
 * A decoded message from {@code OCG_DuelGetMessage}. See {@link MessageDecoder} for the wire format.
 *
 * <p>Messages the mod doesn't interpret yet decode to {@link Unhandled}; anything whose bytes don't match the
 * expected layout decodes to {@link Malformed} so it can be logged instead of crashing the duel.
 */
public sealed interface DuelMessage {
    /** The {@code MSG_*} type byte. */
    int type();

    // --- Messages that need a player's response ---------------------------------------------------------------

    /** A message that blocks until {@link #player()} answers via a {@link Responses} buffer. */
    sealed interface Prompt extends DuelMessage {
        int player();
    }

    /** An effect a player may activate, from idle, battle and chain prompts. */
    record Activatable(CardRef card, long description, int clientMode) {
    }

    record Attacker(CardRef card, boolean canAttackDirectly) {
    }

    record SelectBattleCmd(int player, List<Activatable> activatable, List<Attacker> attackers, boolean canMain2,
                           boolean canEnd) implements Prompt {
        public int type() {
            return MessageType.SELECT_BATTLECMD;
        }
    }

    record SelectIdleCmd(int player, List<CardRef> summonable, List<CardRef> specialSummonable,
                         List<CardRef> repositionable, List<CardRef> monsterSettable, List<CardRef> spellSettable,
                         List<Activatable> activatable, boolean canBattle, boolean canEnd, boolean canShuffle)
            implements Prompt {
        public int type() {
            return MessageType.SELECT_IDLECMD;
        }
    }

    record SelectEffectYesNo(int player, CardRef card, long description) implements Prompt {
        public int type() {
            return MessageType.SELECT_EFFECTYN;
        }
    }

    record SelectYesNo(int player, long description) implements Prompt {
        public int type() {
            return MessageType.SELECT_YESNO;
        }
    }

    record SelectOption(int player, List<Long> options) implements Prompt {
        public int type() {
            return MessageType.SELECT_OPTION;
        }
    }

    record SelectCard(int player, boolean cancelable, int min, int max, List<CardRef> cards) implements Prompt {
        public int type() {
            return MessageType.SELECT_CARD;
        }
    }

    record SelectChain(int player, int specialCount, boolean forced, List<Activatable> chains) implements Prompt {
        public int type() {
            return MessageType.SELECT_CHAIN;
        }
    }

    /**
     * Choose {@link #count()} zones. A set bit in {@link #blockedZones()} means the zone is NOT available: bits 0-6
     * are the prompted player's monster zones, 8-15 their spell/trap zones, and 16-31 the same for the opponent.
     */
    record SelectPlace(int player, int count, int blockedZones, boolean disableField) implements Prompt {
        public int type() {
            return disableField ? MessageType.SELECT_DISFIELD : MessageType.SELECT_PLACE;
        }
    }

    record SelectPosition(int player, int code, int positions) implements Prompt {
        public int type() {
            return MessageType.SELECT_POSITION;
        }
    }

    /** @param releaseParam how many tributes this card counts as */
    record TributeCandidate(CardRef card, int releaseParam) {
    }

    record SelectTribute(int player, boolean cancelable, int min, int max, List<TributeCandidate> cards)
            implements Prompt {
        public int type() {
            return MessageType.SELECT_TRIBUTE;
        }
    }

    record SortCards(int player, List<CardRef> cards, boolean chain) implements Prompt {
        public int type() {
            return chain ? MessageType.SORT_CHAIN : MessageType.SORT_CARD;
        }
    }

    record CounterCandidate(CardRef card, int counters) {
    }

    record SelectCounter(int player, int counterType, int count, List<CounterCandidate> cards) implements Prompt {
        public int type() {
            return MessageType.SELECT_COUNTER;
        }
    }

    /** @param param low 16 bits: the card's value; high 16 bits: an alternate value (0 if none) */
    record SumCandidate(CardRef card, int param) {
    }

    /**
     * Pick cards whose values add up to {@link #target()} (or at least that much when {@link #atLeast()}). The
     * {@link #mustSelect()} cards are always included and are not part of the response.
     */
    record SelectSum(int player, boolean atLeast, int target, int min, int max, List<SumCandidate> mustSelect,
                     List<SumCandidate> selectable) implements Prompt {
        public int type() {
            return MessageType.SELECT_SUM;
        }
    }

    record SelectUnselectCard(int player, boolean finishable, boolean cancelable, int min, int max,
                              List<CardRef> selectable, List<CardRef> unselectable) implements Prompt {
        public int type() {
            return MessageType.SELECT_UNSELECT_CARD;
        }
    }

    record RockPaperScissors(int player) implements Prompt {
        public int type() {
            return MessageType.ROCK_PAPER_SCISSORS;
        }
    }

    record AnnounceRace(int player, int count, long available) implements Prompt {
        public int type() {
            return MessageType.ANNOUNCE_RACE;
        }
    }

    record AnnounceAttribute(int player, int count, int available) implements Prompt {
        public int type() {
            return MessageType.ANNOUNCE_ATTRIB;
        }
    }

    /** @param opcodes an RPN filter the announced card must satisfy */
    record AnnounceCard(int player, List<Long> opcodes) implements Prompt {
        public int type() {
            return MessageType.ANNOUNCE_CARD;
        }
    }

    record AnnounceNumber(int player, List<Long> values) implements Prompt {
        public int type() {
            return MessageType.ANNOUNCE_NUMBER;
        }
    }

    // --- Informational messages ---------------------------------------------------------------------------------

    /** The last response was invalid; ask the same prompt again. */
    record Retry() implements DuelMessage {
        public int type() {
            return MessageType.RETRY;
        }
    }

    record Hint(int hintType, int player, long data) implements DuelMessage {
        public int type() {
            return MessageType.HINT;
        }
    }

    /** @param player the winner, or 2 for a draw */
    record Win(int player, int reason) implements DuelMessage {
        public int type() {
            return MessageType.WIN;
        }
    }

    record NewTurn(int player) implements DuelMessage {
        public int type() {
            return MessageType.NEW_TURN;
        }
    }

    /** In a tag duel, {@code player}'s team handed over to its next duelist (deck, hand and Extra Deck swap). */
    record TagSwap(int player) implements DuelMessage {
        public int type() {
            return MessageType.TAG_SWAP;
        }
    }

    record NewPhase(int phase) implements DuelMessage {
        public int type() {
            return MessageType.NEW_PHASE;
        }
    }

    record ConfirmCards(int type, int player, List<CardRef> cards) implements DuelMessage {
    }

    record ShuffleDeck(int player) implements DuelMessage {
        public int type() {
            return MessageType.SHUFFLE_DECK;
        }
    }

    record ShuffleHand(int type, int player, List<Integer> codes) implements DuelMessage {
    }

    record Move(int code, Loc from, Loc to, int reason) implements DuelMessage {
        public int type() {
            return MessageType.MOVE;
        }
    }

    record PositionChange(int code, Loc loc, int previousPosition) implements DuelMessage {
        public int type() {
            return MessageType.POS_CHANGE;
        }
    }

    record Set(int code, Loc loc) implements DuelMessage {
        public int type() {
            return MessageType.SET;
        }
    }

    record Swap(CardRef first, CardRef second) implements DuelMessage {
        public int type() {
            return MessageType.SWAP;
        }
    }

    record FieldDisabled(int disabledZones) implements DuelMessage {
        public int type() {
            return MessageType.FIELD_DISABLED;
        }
    }

    /** MSG_SUMMONING, MSG_SPSUMMONING (code 0 when face-down) or MSG_FLIPSUMMONING. */
    record Summoning(int type, int code, Loc loc) implements DuelMessage {
    }

    /** MSG_SUMMONED, MSG_SPSUMMONED or MSG_FLIPSUMMONED. */
    record Summoned(int type) implements DuelMessage {
    }

    record Chaining(int code, Loc loc, int triggerController, int triggerLocation, int triggerSequence,
                    long description, int chainLink) implements DuelMessage {
        public int type() {
            return MessageType.CHAINING;
        }
    }

    /** MSG_CHAINED, MSG_CHAIN_SOLVING, MSG_CHAIN_SOLVED, MSG_CHAIN_NEGATED or MSG_CHAIN_DISABLED. */
    record ChainEvent(int type, int chainLink) implements DuelMessage {
    }

    record ChainEnd() implements DuelMessage {
        public int type() {
            return MessageType.CHAIN_END;
        }
    }

    /** MSG_CARD_SELECTED, MSG_RANDOM_SELECTED or MSG_BECOME_TARGET. */
    record CardsHighlighted(int type, List<Loc> cards) implements DuelMessage {
    }

    record Draw(int player, List<Integer> codes, List<Integer> positions) implements DuelMessage {
        public int type() {
            return MessageType.DRAW;
        }
    }

    /** MSG_DAMAGE, MSG_RECOVER, MSG_LPUPDATE (absolute LP) or MSG_PAY_LPCOST. */
    record LifePoints(int type, int player, int amount) implements DuelMessage {
    }

    record Equip(Loc card, Loc target) implements DuelMessage {
        public int type() {
            return MessageType.EQUIP;
        }
    }

    /** MSG_CARD_TARGET or MSG_CANCEL_TARGET. */
    record CardTarget(int type, Loc card, Loc target) implements DuelMessage {
    }

    /** MSG_ADD_COUNTER or MSG_REMOVE_COUNTER. */
    record Counter(int type, int counterType, Loc loc, int count) implements DuelMessage {
    }

    /** @param target {@link Loc#NONE} for a direct attack */
    record Attack(Loc attacker, Loc target) implements DuelMessage {
        public int type() {
            return MessageType.ATTACK;
        }
    }

    record Battle(Loc attacker, int attackerAtk, int attackerDef, boolean attackerDestroyed, Loc target,
                  int targetAtk, int targetDef, boolean targetDestroyed) implements DuelMessage {
        public int type() {
            return MessageType.BATTLE;
        }
    }

    /** Payload-free battle markers: MSG_ATTACK_DISABLED, MSG_DAMAGE_STEP_START, MSG_DAMAGE_STEP_END. */
    record BattleEvent(int type) implements DuelMessage {
    }

    /** MSG_TOSS_COIN or MSG_TOSS_DICE. */
    record Toss(int type, int player, List<Integer> results) implements DuelMessage {
    }

    /** @param hands player 0's and player 1's choice, 1-3 */
    record HandResult(int hand0, int hand1) implements DuelMessage {
        public int type() {
            return MessageType.HAND_RES;
        }
    }

    record MissedEffect(Loc loc, int code) implements DuelMessage {
        public int type() {
            return MessageType.MISSED_EFFECT;
        }
    }

    record DeckTop(int player, int sequenceFromTop, int code, int position) implements DuelMessage {
        public int type() {
            return MessageType.DECK_TOP;
        }
    }

    /** A known message the mod doesn't interpret yet. */
    record Unhandled(int type, byte[] payload) implements DuelMessage {
    }

    /** A message whose bytes didn't match the expected layout. */
    record Malformed(int type, byte[] payload, String error) implements DuelMessage {
    }
}
