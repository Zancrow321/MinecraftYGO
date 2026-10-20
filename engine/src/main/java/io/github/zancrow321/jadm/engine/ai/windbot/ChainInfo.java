package io.github.zancrow321.jadm.engine.ai.windbot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** One link of the chain being built or resolved, with the card as it was when it was activated. */
public final class ChainInfo {
    public final ClientCard relatedCard;
    public final int activatePlayer;
    public final int activateId;
    public final int activateAlias;
    public final int activateController;
    public final int activatePosition;
    public final int activateSequence;
    public final int activateLocation;
    public final int activateLevel;
    public final int activateRank;
    public final int activateType;
    public final long activateRace;
    public final int activateAttack;
    public final int activateDefense;
    public final boolean isSpecialSummoned;
    public final long activateDescription;
    public final List<ClientCard> targets = new ArrayList<>();
    /** The options announced for this link after it was activated (HINT_OPSELECTED). */
    public final List<Long> announces = new ArrayList<>();

    public ChainInfo(ClientCard card) {
        this(card, card.controller, 0);
    }

    public ChainInfo(ClientCard card, int player, long desc) {
        relatedCard = card;
        activatePlayer = player;
        activateId = card.id;
        activateAlias = card.alias;
        activateController = card.controller;
        activatePosition = card.position;
        activateSequence = card.sequence;
        activateLocation = card.location;
        activateLevel = card.level;
        activateRank = card.rank;
        activateType = card.type;
        activateRace = card.race;
        activateAttack = card.attack;
        activateDefense = card.defense;
        isSpecialSummoned = card.isSpecialSummoned;
        activateDescription = desc;
    }

    public boolean hasPosition(int position) {
        return (activatePosition & position) != 0;
    }

    public boolean hasLocation(int location) {
        return (activateLocation & location) != 0;
    }

    public boolean hasAnnounce(long desc) {
        return announces.contains(desc);
    }

    public boolean hasAnnounce(long... descs) {
        for (long desc : descs) {
            if (hasAnnounce(desc)) {
                return true;
            }
        }
        return false;
    }

    public boolean isActivateCode(int id) {
        return activateId == id || activateAlias == id && NamedCards.isAltartAlias(activateId, activateAlias);
    }

    public boolean isActivateCode(int... ids) {
        for (int id : ids) {
            if (isActivateCode(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isActivateCode(Collection<Integer> ids) {
        for (int id : ids) {
            if (isActivateCode(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCode(int id) {
        return relatedCard != null && relatedCard.isCode(id);
    }

    public boolean isCode(int... ids) {
        return relatedCard != null && relatedCard.isCode(ids);
    }

    public boolean isCode(Collection<Integer> ids) {
        return relatedCard != null && relatedCard.isCode(ids);
    }

    public boolean hasType(int type) {
        return relatedCard != null && (relatedCard.type & type) != 0;
    }

    public boolean isSpell() {
        return hasType(CardType.SPELL);
    }

    public boolean isTrap() {
        return hasType(CardType.TRAP);
    }
}
