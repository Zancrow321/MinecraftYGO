package io.github.zancrow321.minecraftygo.engine;

/**
 * Constants from {@code ocgapi_constants.h}. Only what the mod uses so far; extend as needed.
 */
public final class OcgConstants {
    private OcgConstants() {
    }

    // Locations
    public static final int TYPE_MONSTER = 0x1;
    public static final int TYPE_SPELL = 0x2;
    public static final int TYPE_TRAP = 0x4;
    public static final int TYPE_NORMAL = 0x10;
    public static final int TYPE_FUSION = 0x40;
    public static final int TYPE_RITUAL = 0x80;
    public static final int TYPE_SYNCHRO = 0x2000;
    public static final int TYPE_TOKEN = 0x4000;
    public static final int TYPE_EQUIP = 0x40000;
    public static final int TYPE_XYZ = 0x800000;
    public static final int TYPE_PENDULUM = 0x1000000;
    public static final int TYPE_LINK = 0x4000000;
    /** The types that go in the extra deck. */
    public static final int TYPES_EXTRA_DECK = TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK;

    public static final int LOCATION_DECK = 0x01;
    public static final int LOCATION_HAND = 0x02;
    public static final int LOCATION_MZONE = 0x04;
    public static final int LOCATION_SZONE = 0x08;
    public static final int LOCATION_GRAVE = 0x10;
    public static final int LOCATION_REMOVED = 0x20;
    public static final int LOCATION_EXTRA = 0x40;
    public static final int LOCATION_OVERLAY = 0x80;
    public static final int LOCATION_ONFIELD = LOCATION_MZONE | LOCATION_SZONE;

    // Positions
    public static final int POS_FACEUP_ATTACK = 0x1;
    public static final int POS_FACEDOWN_ATTACK = 0x2;
    public static final int POS_FACEUP_DEFENSE = 0x4;
    public static final int POS_FACEDOWN_DEFENSE = 0x8;
    public static final int POS_FACEUP = POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE;
    public static final int POS_FACEDOWN = POS_FACEDOWN_ATTACK | POS_FACEDOWN_DEFENSE;
    public static final int POS_ATTACK = POS_FACEUP_ATTACK | POS_FACEDOWN_ATTACK;
    public static final int POS_DEFENSE = POS_FACEUP_DEFENSE | POS_FACEDOWN_DEFENSE;

    // Duel flags
    public static final long DUEL_TEST_MODE = 0x01L;
    public static final long DUEL_ATTACK_FIRST_TURN = 0x02L;
    public static final long DUEL_USE_TRAPS_IN_NEW_CHAIN = 0x04L;
    public static final long DUEL_6_STEP_BATLLE_STEP = 0x08L;
    public static final long DUEL_PSEUDO_SHUFFLE = 0x10L;
    public static final long DUEL_TRIGGER_WHEN_PRIVATE_KNOWLEDGE = 0x20L;
    public static final long DUEL_SIMPLE_AI = 0x40L;
    public static final long DUEL_RELAY = 0x80L;
    public static final long DUEL_OCG_OBSOLETE_IGNITION = 0x100L;
    public static final long DUEL_1ST_TURN_DRAW = 0x200L;
    public static final long DUEL_1_FACEUP_FIELD = 0x400L;
    public static final long DUEL_PZONE = 0x800L;
    public static final long DUEL_SEPARATE_PZONE = 0x1000L;
    public static final long DUEL_EMZONE = 0x2000L;
    public static final long DUEL_FSX_MMZONE = 0x4000L;
    public static final long DUEL_TRAP_MONSTERS_NOT_USE_ZONE = 0x8000L;
    public static final long DUEL_RETURN_TO_DECK_TRIGGERS = 0x10000L;
    public static final long DUEL_TRIGGER_ONLY_IN_LOCATION = 0x20000L;
    public static final long DUEL_SPSUMMON_ONCE_OLD_NEGATE = 0x40000L;
    public static final long DUEL_CANNOT_SUMMON_OATH_OLD = 0x80000L;
    public static final long DUEL_EQUIP_NOT_SENT_IF_MISSING_TARGET = 0x8000000L;
    public static final long DUEL_0_ATK_DESTROYED = 0x10000000L;
    public static final long DUEL_STORE_ATTACK_REPLAYS = 0x20000000L;
    public static final long DUEL_SINGLE_CHAIN_IN_DAMAGE_SUBSTEP = 0x40000000L;
    public static final long DUEL_CAN_REPOS_IF_NON_SUMPLAYER = 0x80000000L;
    public static final long DUEL_TCG_SEGOC_NONPUBLIC = 0x100000000L;
    public static final long DUEL_TCG_SEGOC_FIRSTTRIGGER = 0x200000000L;
    public static final long DUEL_TCG_FAST_EFFECT_IGNITION = 0x400000000L;

    /** Master Rule 1: the original rules, the mod's default ruleset. */
    public static final long DUEL_MODE_MR1 = DUEL_OCG_OBSOLETE_IGNITION | DUEL_1ST_TURN_DRAW | DUEL_1_FACEUP_FIELD
            | DUEL_SPSUMMON_ONCE_OLD_NEGATE | DUEL_RETURN_TO_DECK_TRIGGERS | DUEL_CANNOT_SUMMON_OATH_OLD;
    /** GOAT format: MR1 with the TCG rulings of 2005. */
    public static final long DUEL_MODE_GOAT = DUEL_MODE_MR1 | DUEL_TCG_FAST_EFFECT_IGNITION
            | DUEL_USE_TRAPS_IN_NEW_CHAIN | DUEL_6_STEP_BATLLE_STEP | DUEL_TRIGGER_WHEN_PRIVATE_KNOWLEDGE
            | DUEL_EQUIP_NOT_SENT_IF_MISSING_TARGET | DUEL_0_ATK_DESTROYED | DUEL_STORE_ATTACK_REPLAYS
            | DUEL_SINGLE_CHAIN_IN_DAMAGE_SUBSTEP | DUEL_CAN_REPOS_IF_NON_SUMPLAYER | DUEL_TCG_SEGOC_NONPUBLIC
            | DUEL_TCG_SEGOC_FIRSTTRIGGER;
    /** Master Rule 2: Xyz monsters, ignition effects the modern way. */
    public static final long DUEL_MODE_MR2 = DUEL_1ST_TURN_DRAW | DUEL_1_FACEUP_FIELD | DUEL_SPSUMMON_ONCE_OLD_NEGATE
            | DUEL_RETURN_TO_DECK_TRIGGERS | DUEL_CANNOT_SUMMON_OATH_OLD;
    /** Master Rule 3: Pendulum Zones of their own. */
    public static final long DUEL_MODE_MR3 = DUEL_PZONE | DUEL_SEPARATE_PZONE | DUEL_SPSUMMON_ONCE_OLD_NEGATE
            | DUEL_RETURN_TO_DECK_TRIGGERS | DUEL_CANNOT_SUMMON_OATH_OLD;
    /** Master Rule 4: Extra Monster Zones, Pendulum Zones in the outer spell and trap zones. */
    public static final long DUEL_MODE_MR4 = DUEL_PZONE | DUEL_EMZONE | DUEL_SPSUMMON_ONCE_OLD_NEGATE
            | DUEL_RETURN_TO_DECK_TRIGGERS | DUEL_CANNOT_SUMMON_OATH_OLD;
    /** Master Rule 5: current rules. */
    public static final long DUEL_MODE_MR5 = DUEL_PZONE | DUEL_EMZONE | DUEL_FSX_MMZONE
            | DUEL_TRAP_MONSTERS_NOT_USE_ZONE | DUEL_TRIGGER_ONLY_IN_LOCATION;
}
