package io.github.zancrow321.jadm.engine.ai.windbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One player's side as the bot knows it (WindBot's {@code ClientField}). Zone arrays are fixed-size (7 Monster
 * Zones, 8 Spell &amp; Trap slots: 0-4, 5 the Field Zone, 6-7 the separate Pendulum Zones) with {@code null} for
 * empty ones.
 */
public final class ClientField {
    public final List<ClientCard> hand = new ArrayList<>();
    public final ClientCard[] monsterZone = new ClientCard[7];
    public final ClientCard[] spellZone = new ClientCard[8];
    public final List<ClientCard> graveyard = new ArrayList<>();
    public final List<ClientCard> banished = new ArrayList<>();
    public final List<ClientCard> deck = new ArrayList<>();
    public final List<ClientCard> extraDeck = new ArrayList<>();

    public int lifePoints;
    public ClientCard battlingMonster;
    /** Descriptions a player hint put on this side (PHINT_DESC_ADD), e.g. "can't Special Summon this turn". */
    public final Set<Long> hintDescriptions = new HashSet<>();
    public boolean underAttack;

    /**
     * The Main Deck the duel started with, by passcode, when the bot knows it (only its own). What is left in the
     * Deck is that, less the copies seen anywhere else.
     */
    private Map<Integer, Integer> initialDeck;
    /** The side this is (0 the bot, 1 the opponent), to count the bot's own cards outside the Deck. */
    private int player;
    /** Both sides, so cards the opponent took over still count as out of the Deck. */
    private ClientField[] sides;

    void init(int player, ClientField[] sides) {
        this.player = player;
        this.sides = sides;
    }

    /** @param codes the Main Deck the duel started with */
    public void setInitialDeck(Collection<Integer> codes) {
        initialDeck = new HashMap<>();
        for (int code : codes) {
            initialDeck.merge(code, 1, Integer::sum);
        }
    }

    private boolean canQueryDeck() {
        return initialDeck != null;
    }

    private int getTrackedDeckCount(int cardId) {
        int started = 0;
        for (Map.Entry<Integer, Integer> entry : initialDeck.entrySet()) {
            int code = entry.getKey();
            if (code == cardId || isAltartOf(code, cardId)) {
                started += entry.getValue();
            }
        }
        if (started == 0) {
            return 0;
        }
        int elsewhere = 0;
        for (ClientField side : sides) {
            elsewhere += countOwn(side.hand, cardId) + countOwn(Arrays.asList(side.monsterZone), cardId)
                    + countOwn(Arrays.asList(side.spellZone), cardId) + countOwn(side.graveyard, cardId)
                    + countOwn(side.banished, cardId) + countOwn(side.extraDeck, cardId);
            for (ClientCard card : side.monsterZone) {
                if (card != null && card.owner == player) {
                    for (int code : card.overlays) {
                        if (code == cardId || isAltartOf(code, cardId)) {
                            elsewhere++;
                        }
                    }
                }
            }
        }
        return Math.max(0, started - elsewhere);
    }

    private static boolean isAltartOf(int code, int cardId) {
        var data = NamedCards.get(code);
        return data != null && data.data().alias() == cardId && NamedCards.isAltartAlias(code, cardId);
    }

    private int countOwn(List<ClientCard> cards, int cardId) {
        int count = 0;
        for (ClientCard card : cards) {
            if (card != null && card.owner == player && (card.id == cardId || isAltartOf(card.id, cardId))) {
                count++;
            }
        }
        return count;
    }

    public int getMonstersExtraZoneCount() {
        int count = 0;
        if (monsterZone[5] != null) {
            count++;
        }
        if (monsterZone[6] != null) {
            count++;
        }
        return count;
    }

    public int getMonsterCount() {
        return getCount(Arrays.asList(monsterZone));
    }

    public int getSpellCount() {
        return getCount(Arrays.asList(spellZone));
    }

    public int getHandCount() {
        return getCount(hand);
    }

    public int getSpellCountWithoutField() {
        int count = 0;
        for (int i = 0; i < 5; ++i) {
            if (spellZone[i] != null) {
                ++count;
            }
        }
        return count;
    }

    public int getColumnCount(int zone) {
        return getColumnCount(zone, true);
    }

    public int getColumnCount(int zone, boolean includeExtraMonsterZone) {
        int count = 0;
        if (spellZone[zone] != null) {
            count++;
        }
        if (monsterZone[zone] != null) {
            count++;
        }
        if (zone == 1 && includeExtraMonsterZone && monsterZone[5] != null) {
            count++;
        }
        if (zone == 3 && includeExtraMonsterZone && monsterZone[6] != null) {
            count++;
        }
        return count;
    }

    public int getFieldCount() {
        return getSpellCount() + getMonsterCount();
    }

    public int getFieldHandCount() {
        return getSpellCount() + getMonsterCount() + getHandCount();
    }

    public boolean isFieldEmpty() {
        return getMonsters().isEmpty() && getSpells().isEmpty();
    }

    public int getLinkedZones() {
        int zones = 0;
        for (int i = 0; i < 7; i++) {
            if (monsterZone[i] != null) {
                zones |= monsterZone[i].getLinkedZones();
            }
        }
        return zones;
    }

    public List<ClientCard> getMonsters() {
        return getCards(Arrays.asList(monsterZone), card -> true);
    }

    public List<ClientCard> getFaceupMonsters() {
        return getCards(Arrays.asList(monsterZone), ClientCard::isFaceup);
    }

    public List<ClientCard> getGraveyardMonsters() {
        return getCards(graveyard, card -> card.hasType(CardType.MONSTER));
    }

    public List<ClientCard> getGraveyardSpells() {
        return getCards(graveyard, card -> card.hasType(CardType.SPELL));
    }

    public List<ClientCard> getGraveyardTraps() {
        return getCards(graveyard, card -> card.hasType(CardType.TRAP));
    }

    public List<ClientCard> getSpells() {
        return getCards(Arrays.asList(spellZone), card -> true);
    }

    public List<ClientCard> getMonstersInExtraZone() {
        return getCards(Arrays.asList(monsterZone), card -> card.sequence >= 5);
    }

    public List<ClientCard> getMonstersInMainZone() {
        return getCards(Arrays.asList(monsterZone), card -> card.sequence < 5);
    }

    public ClientCard getFieldSpellCard() {
        return spellZone[5];
    }

    public boolean hasInDeck(int cardId) {
        return canQueryDeck() && getTrackedDeckCount(cardId) > 0;
    }

    public boolean hasInDeck(int... cardIds) {
        if (!canQueryDeck()) {
            return false;
        }
        for (int id : cardIds) {
            if (getTrackedDeckCount(id) > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean hasInHand(int cardId) {
        return hasInCards(hand, card -> card.isCode(cardId));
    }

    public boolean hasInHand(Collection<Integer> cardId) {
        return hasInCards(hand, card -> card.isCode(cardId));
    }

    public boolean hasInGraveyard(int cardId) {
        return hasInCards(graveyard, card -> card.isCode(cardId));
    }

    public boolean hasInGraveyard(Collection<Integer> cardId) {
        return hasInCards(graveyard, card -> card.isCode(cardId));
    }

    public boolean hasInBanished(int cardId) {
        return hasInCards(banished, card -> card.isCode(cardId));
    }

    public boolean hasInBanished(Collection<Integer> cardId) {
        return hasInCards(banished, card -> card.isCode(cardId));
    }

    public boolean hasInExtra(int cardId) {
        return hasInCards(extraDeck, card -> card.isCode(cardId));
    }

    public boolean hasInExtra(Collection<Integer> cardId) {
        return hasInCards(extraDeck, card -> card.isCode(cardId));
    }

    public boolean hasAttackingMonster() {
        return hasAttackingMonster(false);
    }

    public boolean hasAttackingMonster(boolean strict) {
        return getMonsters().stream().anyMatch(card -> card.isAttack()
                || (!strict && card.isMonsterAttackWhileInDefPos()));
    }

    public boolean hasDefendingMonster() {
        return getMonsters().stream().anyMatch(ClientCard::isDefense);
    }

    public boolean hasInMonstersZone(int cardId) {
        return hasInMonstersZone(cardId, false, false, false);
    }

    public boolean hasInMonstersZone(int cardId, boolean notDisabled) {
        return hasInMonstersZone(cardId, notDisabled, false, false);
    }

    public boolean hasInMonstersZone(int cardId, boolean notDisabled, boolean hasXyzMaterial) {
        return hasInMonstersZone(cardId, notDisabled, hasXyzMaterial, false);
    }

    public boolean hasInMonstersZone(int cardId, boolean notDisabled, boolean hasXyzMaterial, boolean faceUp) {
        return hasInCards(Arrays.asList(monsterZone), card -> card.isCode(cardId)
                && matches(card, notDisabled, hasXyzMaterial, faceUp));
    }

    public boolean hasInMonstersZone(Collection<Integer> cardId) {
        return hasInMonstersZone(cardId, false, false, false);
    }

    public boolean hasInMonstersZone(Collection<Integer> cardId, boolean notDisabled, boolean hasXyzMaterial,
                                     boolean faceUp) {
        return hasInCards(Arrays.asList(monsterZone), card -> card.isCode(cardId)
                && matches(card, notDisabled, hasXyzMaterial, faceUp));
    }

    public boolean hasInSpellZone(int cardId) {
        return hasInSpellZone(cardId, false, false);
    }

    public boolean hasInSpellZone(int cardId, boolean notDisabled) {
        return hasInSpellZone(cardId, notDisabled, false);
    }

    public boolean hasInSpellZone(int cardId, boolean notDisabled, boolean faceUp) {
        return hasInCards(Arrays.asList(spellZone), card -> card.isCode(cardId)
                && matches(card, notDisabled, false, faceUp));
    }

    public boolean hasInSpellZone(Collection<Integer> cardId) {
        return hasInSpellZone(cardId, false, false);
    }

    public boolean hasInSpellZone(Collection<Integer> cardId, boolean notDisabled, boolean faceUp) {
        return hasInCards(Arrays.asList(spellZone), card -> card.isCode(cardId)
                && matches(card, notDisabled, false, faceUp));
    }

    public boolean hasInPendulumZone(int cardId) {
        return hasInPendulumZone(cardId, false);
    }

    public boolean hasInPendulumZone(int cardId, boolean notDisabled) {
        // Slots 0 and 4 are the Pendulum Zones from Master Rule 4 on, 6 and 7 the separate ones before.
        for (ClientCard card : new ClientCard[]{spellZone[0], spellZone[4], spellZone[6], spellZone[7]}) {
            if (card == null || !card.isCode(cardId) || card.isFacedown()) {
                continue;
            }
            if (notDisabled && card.isDisabled()) {
                continue;
            }
            if (!card.hasType(CardType.PENDULUM)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public boolean hasInHandOrInSpellZone(int cardId) {
        return hasInHand(cardId) || hasInSpellZone(cardId);
    }

    public boolean hasInHandOrHasInMonstersZone(int cardId) {
        return hasInHand(cardId) || hasInMonstersZone(cardId);
    }

    public boolean hasInHandOrInGraveyard(int cardId) {
        return hasInHand(cardId) || hasInGraveyard(cardId);
    }

    public boolean hasInGraveyardOrInBanished(int cardId) {
        return hasInBanished(cardId) || hasInGraveyard(cardId);
    }

    public boolean hasInMonstersZoneOrInGraveyard(int cardId) {
        return hasInMonstersZone(cardId) || hasInGraveyard(cardId);
    }

    public boolean hasInSpellZoneOrInGraveyard(int cardId) {
        return hasInSpellZone(cardId) || hasInGraveyard(cardId);
    }

    public boolean hasInHandOrInMonstersZoneOrInGraveyard(int cardId) {
        return hasInHand(cardId) || hasInMonstersZone(cardId) || hasInGraveyard(cardId);
    }

    public boolean hasInHandOrInSpellZoneOrInGraveyard(int cardId) {
        return hasInHand(cardId) || hasInSpellZone(cardId) || hasInGraveyard(cardId);
    }

    public boolean hasInHandOrInSpellZone(Collection<Integer> cardId) {
        return hasInHand(cardId) || hasInSpellZone(cardId);
    }

    public boolean hasInHandOrHasInMonstersZone(Collection<Integer> cardId) {
        return hasInHand(cardId) || hasInMonstersZone(cardId);
    }

    public boolean hasInHandOrInGraveyard(Collection<Integer> cardId) {
        return hasInHand(cardId) || hasInGraveyard(cardId);
    }

    public boolean hasInMonstersZoneOrInGraveyard(Collection<Integer> cardId) {
        return hasInMonstersZone(cardId) || hasInGraveyard(cardId);
    }

    public boolean hasInSpellZoneOrInGraveyard(Collection<Integer> cardId) {
        return hasInSpellZone(cardId) || hasInGraveyard(cardId);
    }

    public boolean hasInHandOrInMonstersZoneOrInGraveyard(Collection<Integer> cardId) {
        return hasInHand(cardId) || hasInMonstersZone(cardId) || hasInGraveyard(cardId);
    }

    public boolean hasInHandOrInSpellZoneOrInGraveyard(Collection<Integer> cardId) {
        return hasInHand(cardId) || hasInSpellZone(cardId) || hasInGraveyard(cardId);
    }

    /** @deprecated use {@link #getCardCountInDeck(int)} */
    @Deprecated
    public int getRemainingCount(int cardId, int initialCount) {
        return getCardCountInDeck(cardId);
    }

    public int getCardCountInDeck(int cardId) {
        return canQueryDeck() ? getTrackedDeckCount(cardId) : 0;
    }

    public int getCardCountInDeck(Collection<Integer> cardIds) {
        if (!canQueryDeck()) {
            return 0;
        }
        int count = 0;
        for (int id : cardIds) {
            count += getTrackedDeckCount(id);
        }
        return count;
    }

    private static boolean matches(ClientCard card, boolean notDisabled, boolean hasXyzMaterial, boolean faceUp) {
        return !(notDisabled && card.isDisabled()) && !(hasXyzMaterial && !card.hasXyzMaterial())
                && !(faceUp && card.isFacedown());
    }

    private static int getCount(List<ClientCard> cards) {
        int count = 0;
        for (ClientCard card : cards) {
            if (card != null) {
                count++;
            }
        }
        return count;
    }

    private static List<ClientCard> getCards(List<ClientCard> cards, Predicate<ClientCard> filter) {
        List<ClientCard> out = new ArrayList<>();
        for (ClientCard card : cards) {
            if (card != null && filter.test(card)) {
                out.add(card);
            }
        }
        return out;
    }

    private static boolean hasInCards(List<ClientCard> cards, Predicate<ClientCard> filter) {
        for (ClientCard card : cards) {
            if (card != null && filter.test(card)) {
                return true;
            }
        }
        return false;
    }
}
