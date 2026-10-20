package io.github.zancrow321.jadm.engine.ai.windbot;

import io.github.zancrow321.jadm.engine.data.CardInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One card as the bot knows it (WindBot's {@code ClientCard}, with its {@code CardExtension} helpers). Controllers
 * and owners are local: 0 is the bot, 1 its opponent. Objects keep their identity while a card moves, so WindBot's
 * lists of chain cards and targets keep pointing at the right card.
 */
public final class ClientCard {
    /** The passcode, 0 while the card is hidden. Change it with {@link #setId}. */
    public int id;
    /** The card's printed data, {@code null} while it is hidden or unknown. */
    public CardInfo data;
    public String name;

    public int position;
    public int sequence;
    public int location;
    public int lastLocation;
    public int alias;
    public int level;
    public int rank;
    public int type;
    public int attribute;
    public long race;
    public int attack;
    public int defense;
    public int lScale;
    public int rScale;
    public int linkCount;
    public int linkMarker;
    public int baseAttack;
    public int baseDefense;
    public int realPower;
    public final List<Integer> overlays = new ArrayList<>();
    public int owner = -1;
    public int controller;
    public int disabled;
    public int procCompleted;
    public int selectSeq;
    public int opParam1;
    public int opParam2;

    public final List<ClientCard> equipCards = new ArrayList<>();
    public ClientCard equipTarget;
    public final List<ClientCard> ownTargets = new ArrayList<>();
    public final List<ClientCard> targetCards = new ArrayList<>();

    public boolean canDirectAttack;
    public boolean attacked;
    public boolean isLastAttacker;
    public boolean isSpecialSummoned;

    /** The index of this card in each list of the last idle or battle command (by command type). */
    public final int[] actionIndex = new int[16];
    /** For each effect description, its index among the last idle command's activatable effects. */
    public final Map<Long, Integer> actionActivateIndex = new HashMap<>();

    public ClientCard(int id, int location, int sequence) {
        this(id, location, sequence, 0);
    }

    public ClientCard(int id, int location, int sequence, int position) {
        setId(id);
        this.sequence = sequence;
        this.position = position;
        this.location = location;
    }

    public void setId(int id) {
        if (this.id == id && (id == 0 || data != null)) {
            return;
        }
        this.id = id;
        data = NamedCards.get(id);
        if (data != null) {
            name = data.name();
            alias = data.data().alias();
            // Until the core reports the card's current values, use the printed ones.
            type = data.data().type();
            level = data.data().level();
            attribute = data.data().attribute();
            race = data.data().race();
            attack = data.data().attack();
            defense = data.data().defense();
            baseAttack = attack;
            baseDefense = defense;
            lScale = data.data().lscale();
            rScale = data.data().rscale();
            linkMarker = data.data().linkMarker();
            linkCount = hasType(CardType.LINK) ? level : 0;
        } else {
            name = null;
            alias = 0;
        }
    }

    /**
     * Sets the monster equipped by this card and keeps both sides of the equip relation in sync: the equip card stores
     * the monster in {@link #equipTarget}, the monster stores the equip card in {@link #equipCards}.
     */
    public void setEquipTarget(ClientCard target) {
        if (equipTarget == target) {
            if (target != null && !target.equipCards.contains(this)) {
                target.equipCards.add(this);
            }
            return;
        }
        if (equipTarget != null) {
            equipTarget.equipCards.removeIf(card -> card == this);
        }
        equipTarget = target;
        if (target != null && !target.equipCards.contains(this)) {
            target.equipCards.add(this);
        }
    }

    /** Clears every equip relation involving this card, in both directions. */
    public void clearEquipRelations() {
        setEquipTarget(null);
        for (ClientCard equipCard : List.copyOf(equipCards)) {
            equipCard.setEquipTarget(null);
        }
        equipCards.clear();
    }

    /** Adds a lasting card-target relation (not an equip) and keeps the target's side in sync. */
    public void addCardTarget(ClientCard target) {
        if (target == null) {
            return;
        }
        if (!targetCards.contains(target)) {
            targetCards.add(target);
        }
        if (!target.ownTargets.contains(this)) {
            target.ownTargets.add(this);
        }
    }

    public void removeCardTarget(ClientCard target) {
        if (target == null) {
            return;
        }
        targetCards.removeIf(card -> card == target);
        target.ownTargets.removeIf(card -> card == this);
    }

    /** Clears only the targets this card holds, keeping the cards that target it. */
    void clearOutgoingCardTargets() {
        for (ClientCard card : List.copyOf(targetCards)) {
            removeCardTarget(card);
        }
        targetCards.clear();
    }

    /** Clears the lasting target relations in both directions; equips are kept. */
    public void clearCardTargets() {
        clearOutgoingCardTargets();
        for (ClientCard card : List.copyOf(ownTargets)) {
            card.removeCardTarget(this);
        }
        ownTargets.clear();
    }

    public boolean hasLinkMarker(int dir) {
        return (linkMarker & dir) != 0;
    }

    /** The zones this Link monster points to: bits 0-6 the bot's Monster Zones, 16-22 the opponent's. */
    public int getLinkedZones() {
        if (!hasType(CardType.LINK) || location != CardLocation.MONSTER_ZONE) {
            return 0;
        }
        int zones = 0;
        if (sequence > 0 && sequence <= 4 && hasLinkMarker(CardLinkMarker.LEFT)) {
            zones |= 1 << (sequence - 1);
        }
        if (sequence <= 3 && hasLinkMarker(CardLinkMarker.RIGHT)) {
            zones |= 1 << (sequence + 1);
        }
        if (sequence == 0 && hasLinkMarker(CardLinkMarker.TOP_RIGHT)
                || sequence == 1 && hasLinkMarker(CardLinkMarker.TOP)
                || sequence == 2 && hasLinkMarker(CardLinkMarker.TOP_LEFT)) {
            zones |= (1 << 5) | (1 << (16 + 6));
        }
        if (sequence == 2 && hasLinkMarker(CardLinkMarker.TOP_RIGHT)
                || sequence == 3 && hasLinkMarker(CardLinkMarker.TOP)
                || sequence == 4 && hasLinkMarker(CardLinkMarker.TOP_LEFT)) {
            zones |= (1 << 6) | (1 << (16 + 5));
        }
        if (sequence == 5) {
            if (hasLinkMarker(CardLinkMarker.BOTTOM_LEFT)) {
                zones |= 1;
            }
            if (hasLinkMarker(CardLinkMarker.BOTTOM)) {
                zones |= 1 << 1;
            }
            if (hasLinkMarker(CardLinkMarker.BOTTOM_RIGHT)) {
                zones |= 1 << 2;
            }
            if (hasLinkMarker(CardLinkMarker.TOP_LEFT)) {
                zones |= 1 << (16 + 4);
            }
            if (hasLinkMarker(CardLinkMarker.TOP)) {
                zones |= 1 << (16 + 3);
            }
            if (hasLinkMarker(CardLinkMarker.TOP_RIGHT)) {
                zones |= 1 << (16 + 2);
            }
        }
        if (sequence == 6) {
            if (hasLinkMarker(CardLinkMarker.BOTTOM_LEFT)) {
                zones |= 1 << 2;
            }
            if (hasLinkMarker(CardLinkMarker.BOTTOM)) {
                zones |= 1 << 3;
            }
            if (hasLinkMarker(CardLinkMarker.BOTTOM_RIGHT)) {
                zones |= 1 << 4;
            }
            if (hasLinkMarker(CardLinkMarker.TOP_LEFT)) {
                zones |= 1 << (16 + 2);
            }
            if (hasLinkMarker(CardLinkMarker.TOP)) {
                zones |= 1 << (16 + 1);
            }
            if (hasLinkMarker(CardLinkMarker.TOP_RIGHT)) {
                zones |= 1 << 16;
            }
        }
        return zones;
    }

    public boolean hasType(int type) {
        return (this.type & type) != 0;
    }

    public boolean hasPosition(int position) {
        return (this.position & position) != 0;
    }

    public boolean hasAttribute(int attribute) {
        return (this.attribute & attribute) != 0;
    }

    public boolean hasRace(long race) {
        return (this.race & race) != 0;
    }

    public boolean hasSetcode(int setcode) {
        if (data == null) {
            return false;
        }
        int settype = setcode & 0xfff;
        int setsubtype = setcode & 0xf000;
        for (int check : data.data().setcodes()) {
            if ((check & 0xfff) == settype && (check & 0xf000 & setsubtype) == setsubtype) {
                return true;
            }
        }
        return false;
    }

    public boolean isMonster() {
        return hasType(CardType.MONSTER);
    }

    public boolean isTuner() {
        return hasType(CardType.TUNER);
    }

    public boolean isSpell() {
        return hasType(CardType.SPELL);
    }

    public boolean isTrap() {
        return hasType(CardType.TRAP);
    }

    public boolean isExtraCard() {
        return hasType(CardType.FUSION) || hasType(CardType.SYNCHRO) || hasType(CardType.XYZ)
                || hasType(CardType.LINK);
    }

    public boolean isFaceup() {
        return hasPosition(CardPosition.FACE_UP);
    }

    public boolean isFacedown() {
        return hasPosition(CardPosition.FACE_DOWN);
    }

    public boolean isAttack() {
        return hasPosition(CardPosition.ATTACK);
    }

    public boolean isDefense() {
        return hasPosition(CardPosition.DEFENCE);
    }

    public boolean isDisabled() {
        return disabled != 0;
    }

    public boolean isCanRevive() {
        return procCompleted != 0 || !(isExtraCard() || hasType(CardType.RITUAL) || hasType(CardType.SP_SUMMON));
    }

    public boolean isCode(int id) {
        return this.id == id || alias != 0 && alias == id;
    }

    public boolean isCode(int... ids) {
        for (int id : ids) {
            if (isCode(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCode(Collection<Integer> ids) {
        return ids.contains(id) || alias != 0 && ids.contains(alias);
    }

    public boolean isOriginalCode(int id) {
        return this.id == id || alias == id && NamedCards.isAltartAlias(this.id, alias);
    }

    public boolean isOnField() {
        return location == CardLocation.MONSTER_ZONE || location == CardLocation.SPELL_ZONE
                || location == CardLocation.PENDULUM_ZONE || location == CardLocation.FIELD_ZONE;
    }

    public boolean hasXyzMaterial() {
        return !overlays.isEmpty();
    }

    public boolean hasXyzMaterial(int count) {
        return overlays.size() >= count;
    }

    public boolean hasXyzMaterial(int count, int cardId) {
        return overlays.size() >= count && overlays.contains(cardId);
    }

    public int getDefensePower() {
        return isAttack() ? attack : defense;
    }

    /** The database alias when there is one (alternate artworks and rule names alike), else the card's own code. */
    public int getOriginCode() {
        if (data != null) {
            return data.data().alias() > 0 ? data.data().alias() : data.code();
        }
        return id;
    }

    /** The original artwork's code for an alternate artwork, the card's own code otherwise. */
    public int getNonAltartCode() {
        return NamedCards.isAltartAlias(id, alias) ? alias : id;
    }

    // --- WindBot's CardExtension ---------------------------------------------------------------------------------

    public boolean isMonsterInvincible() {
        return !isDisabled() && (controller == 0 && CardLists.INVINCIBLE_BOT_MONSTER.contains(id)
                || controller == 1 && CardLists.INVINCIBLE_ENEMY_MONSTER.contains(id));
    }

    public boolean isMonsterDangerous() {
        return !isDisabled() && (CardLists.DANGEROUS_MONSTER.contains(id)
                || hasSetcode(0x18d) && (hasType(CardType.RITUAL) || !equipCards.isEmpty()));
    }

    public boolean isMonsterHasPreventActivationEffectInBattle() {
        return !isDisabled() && CardLists.PREVENT_ACTIVATION_EFFECT_IN_BATTLE.contains(id);
    }

    public boolean isMonsterAttackWhileInDefPos() {
        return isFaceup() && isDefense() && !isDisabled() && CardLists.DEFENSE_ATTACK_MONSTER.contains(id);
    }

    public int getAttackPower() {
        if (isMonsterAttackWhileInDefPos() && !CardLists.DEFENSE_ATTACK_WITH_ATTACK_VALUE_MONSTER.contains(id)) {
            return defense;
        }
        return attack;
    }

    public boolean isShouldNotBeTarget() {
        return isFaceup() && !isDisabled() && !hasType(CardType.NORMAL)
                && (CardLists.SHOULD_NOT_BE_TARGET.contains(id) || overlays.contains(91025875));
    }

    public boolean isShouldNotBeMonsterTarget() {
        return isFaceup() && !isDisabled() && CardLists.SHOULD_NOT_BE_MONSTER_TARGET.contains(id)
                || equipCards.stream().anyMatch(c -> c.isCode(89812483) && !c.isDisabled());
    }

    public boolean isShouldNotBeSpellTrapTarget() {
        return isFaceup() && !isDisabled() && CardLists.SHOULD_NOT_BE_SPELL_TRAP_TARGET.contains(id)
                || equipCards.stream().anyMatch(c -> c.isCode(89812483) && !c.isDisabled());
    }

    public boolean isMonsterShouldBeDisabledBeforeItUseEffect() {
        return !isDisabled() && CardLists.SHOULD_BE_DISABLED_BEFORE_IT_USE_EFFECT_MONSTER.contains(id);
    }

    public boolean isFloodgate() {
        return CardLists.FLOODGATE.contains(id);
    }

    public boolean isOneForXyz() {
        return CardLists.ONE_FOR_XYZ.contains(id);
    }

    public boolean isFusionSpell() {
        return CardLists.FUSION_SPELL.contains(id);
    }

    public boolean isMonsterNotBeSynchroMaterial() {
        return CardLists.NOT_BE_SYNCHRO_MATERIAL_MONSTER.contains(id);
    }

    public boolean isMonsterNotBeXyzMaterial() {
        return CardLists.NOT_BE_XYZ_MATERIAL_MONSTER.contains(id);
    }

    public boolean isMonsterNotBeSummonTribute() {
        return CardLists.NOT_BE_SUMMON_TRIBUTE_MONSTER.contains(id);
    }

    @Override
    public String toString() {
        return (name != null ? name : id == 0 ? "?" : String.valueOf(id)) + "@" + location + ":" + sequence;
    }
}
