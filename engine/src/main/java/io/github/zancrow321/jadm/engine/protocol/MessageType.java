package io.github.zancrow321.jadm.engine.protocol;

/**
 * {@code MSG_*} values from {@code ocgapi_constants.h}.
 */
public final class MessageType {
    private MessageType() {
    }

    public static final int RETRY = 1;
    public static final int HINT = 2;
    public static final int WIN = 5;
    public static final int SELECT_BATTLECMD = 10;
    public static final int SELECT_IDLECMD = 11;
    public static final int SELECT_EFFECTYN = 12;
    public static final int SELECT_YESNO = 13;
    public static final int SELECT_OPTION = 14;
    public static final int SELECT_CARD = 15;
    public static final int SELECT_CHAIN = 16;
    public static final int SELECT_PLACE = 18;
    public static final int SELECT_POSITION = 19;
    public static final int SELECT_TRIBUTE = 20;
    public static final int SORT_CHAIN = 21;
    public static final int SELECT_COUNTER = 22;
    public static final int SELECT_SUM = 23;
    public static final int SELECT_DISFIELD = 24;
    public static final int SORT_CARD = 25;
    public static final int SELECT_UNSELECT_CARD = 26;
    public static final int CONFIRM_DECKTOP = 30;
    public static final int CONFIRM_CARDS = 31;
    public static final int SHUFFLE_DECK = 32;
    public static final int SHUFFLE_HAND = 33;
    public static final int SWAP_GRAVE_DECK = 35;
    public static final int SHUFFLE_SET_CARD = 36;
    public static final int REVERSE_DECK = 37;
    public static final int DECK_TOP = 38;
    public static final int SHUFFLE_EXTRA = 39;
    public static final int NEW_TURN = 40;
    public static final int NEW_PHASE = 41;
    public static final int CONFIRM_EXTRATOP = 42;
    public static final int MOVE = 50;
    public static final int POS_CHANGE = 53;
    public static final int SET = 54;
    public static final int SWAP = 55;
    public static final int FIELD_DISABLED = 56;
    public static final int SUMMONING = 60;
    public static final int SUMMONED = 61;
    public static final int SPSUMMONING = 62;
    public static final int SPSUMMONED = 63;
    public static final int FLIPSUMMONING = 64;
    public static final int FLIPSUMMONED = 65;
    public static final int CHAINING = 70;
    public static final int CHAINED = 71;
    public static final int CHAIN_SOLVING = 72;
    public static final int CHAIN_SOLVED = 73;
    public static final int CHAIN_END = 74;
    public static final int CHAIN_NEGATED = 75;
    public static final int CHAIN_DISABLED = 76;
    public static final int CARD_SELECTED = 80;
    public static final int RANDOM_SELECTED = 81;
    public static final int BECOME_TARGET = 83;
    public static final int DRAW = 90;
    public static final int DAMAGE = 91;
    public static final int RECOVER = 92;
    public static final int EQUIP = 93;
    public static final int LPUPDATE = 94;
    public static final int CARD_TARGET = 96;
    public static final int CANCEL_TARGET = 97;
    public static final int PAY_LPCOST = 100;
    public static final int ADD_COUNTER = 101;
    public static final int REMOVE_COUNTER = 102;
    public static final int ATTACK = 110;
    public static final int BATTLE = 111;
    public static final int ATTACK_DISABLED = 112;
    public static final int DAMAGE_STEP_START = 113;
    public static final int DAMAGE_STEP_END = 114;
    public static final int MISSED_EFFECT = 120;
    public static final int TOSS_COIN = 130;
    public static final int TOSS_DICE = 131;
    public static final int ROCK_PAPER_SCISSORS = 132;
    public static final int HAND_RES = 133;
    public static final int ANNOUNCE_RACE = 140;
    public static final int ANNOUNCE_ATTRIB = 141;
    public static final int ANNOUNCE_CARD = 142;
    public static final int ANNOUNCE_NUMBER = 143;
    public static final int CARD_HINT = 160;
    public static final int TAG_SWAP = 161;
    public static final int RELOAD_FIELD = 162;
    public static final int AI_NAME = 163;
    public static final int SHOW_HINT = 164;
    public static final int PLAYER_HINT = 165;
    public static final int MATCH_KILL = 170;
    public static final int REMOVE_CARDS = 190;

    // Hint types for MSG_HINT
    public static final int HINT_EVENT = 1;
    public static final int HINT_MESSAGE = 2;
    public static final int HINT_SELECTMSG = 3;
    public static final int HINT_OPSELECTED = 4;
    public static final int HINT_EFFECT = 5;
    public static final int HINT_RACE = 6;
    public static final int HINT_ATTRIB = 7;
    public static final int HINT_CODE = 8;
    public static final int HINT_NUMBER = 9;
    public static final int HINT_CARD = 10;
}
