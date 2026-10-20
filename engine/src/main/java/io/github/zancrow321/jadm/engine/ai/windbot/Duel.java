package io.github.zancrow321.jadm.engine.ai.windbot;

import java.util.ArrayList;
import java.util.List;

/**
 * The duel as the bot knows it (WindBot's {@code Duel}): both sides, the turn, and the chain and summons in progress.
 * Players are local: 0 is the bot, 1 its opponent.
 */
public final class Duel {
    /** Master Rule 4 or later (Extra Monster Zones). */
    public boolean isNewRule;
    /** Master Rule 5 (Fusion, Synchro and Xyz Monsters may go to any Main Monster Zone). */
    public boolean isNewRule2020;
    /** Master Rule 3: the Pendulum Zones are slots 6 and 7 beside the field, not Spell &amp; Trap Zones 0 and 4. */
    public boolean separatePendulumZones;

    public final ClientField[] fields = {new ClientField(), new ClientField()};

    public int turn;
    /** Whose turn it is. */
    public int player;
    public int phase;
    public MainPhase mainPhase = new MainPhase();
    public BattlePhase battlePhase = new BattlePhase();

    public int lastChainPlayer = -1;
    public int lastChainLocation;
    public final List<ClientCard> currentChain = new ArrayList<>();
    public final List<ChainInfo> currentChainInfo = new ArrayList<>();
    public final List<ClientCard> chainTargets = new ArrayList<>();
    public final List<ClientCard> lastChainTargets = new ArrayList<>();
    public final List<ClientCard> chainTargetOnly = new ArrayList<>();
    public int lastSummonPlayer = -1;
    public final List<ClientCard> summoningCards = new ArrayList<>();
    public final List<ClientCard> lastSummonedCards = new ArrayList<>();
    /** The chain link resolving now (1 for the first), 0 while the chain is being built. */
    public int solvingChainIndex;
    public final List<Integer> negatedChainIndexList = new ArrayList<>();

    public Duel() {
        fields[0].init(0, fields);
        fields[1].init(1, fields);
    }

    public ClientCard getCard(int player, int loc, int seq) {
        return getCard(player, loc, seq, 0);
    }

    /** @param loc a location, with {@link CardLocation#OVERLAY} for an Xyz material ({@code subSeq} its index) */
    public ClientCard getCard(int player, int loc, int seq, int subSeq) {
        if (player < 0 || player > 1) {
            return null;
        }
        boolean isXyz = (loc & CardLocation.OVERLAY) != 0;
        List<ClientCard> cards = list(fields[player], loc & 0x7f);
        if (cards == null || seq < 0 || seq >= cards.size()) {
            return null;
        }
        if (isXyz) {
            ClientCard card = cards.get(seq);
            if (card == null || subSeq < 0 || subSeq >= card.overlays.size()) {
                return null;
            }
            return new ClientCard(card.overlays.get(subSeq), CardLocation.OVERLAY, 0, 0);
        }
        return cards.get(seq);
    }

    /** The list behind a location; the zone arrays are wrapped, so writes to them go through. */
    static List<ClientCard> list(ClientField field, int location) {
        return switch (location) {
            case CardLocation.DECK -> field.deck;
            case CardLocation.HAND -> field.hand;
            case CardLocation.MONSTER_ZONE -> java.util.Arrays.asList(field.monsterZone);
            case CardLocation.SPELL_ZONE -> java.util.Arrays.asList(field.spellZone);
            case CardLocation.GRAVE -> field.graveyard;
            case CardLocation.REMOVED -> field.banished;
            case CardLocation.EXTRA -> field.extraDeck;
            default -> null;
        };
    }

    public void addCard(int loc, int cardId, int player, int seq, int pos) {
        addCard(loc, new ClientCard(cardId, loc, seq, pos), player, seq, pos, cardId);
    }

    public void addCard(int loc, ClientCard card, int player, int seq, int pos, int id) {
        card.location = loc;
        card.sequence = seq;
        card.position = pos;
        card.controller = player;
        card.setId(id);
        ClientField field = fields[player];
        switch (loc) {
            case CardLocation.HAND -> {
                field.hand.add(card);
                resetSequence(field.hand);
            }
            case CardLocation.GRAVE -> {
                field.graveyard.add(card);
                resetSequence(field.graveyard);
            }
            case CardLocation.REMOVED -> {
                field.banished.add(card);
                resetSequence(field.banished);
            }
            case CardLocation.MONSTER_ZONE -> {
                if (seq >= 0 && seq < field.monsterZone.length) {
                    field.monsterZone[seq] = card;
                }
            }
            case CardLocation.SPELL_ZONE -> {
                if (seq >= 0 && seq < field.spellZone.length) {
                    field.spellZone[seq] = card;
                }
            }
            case CardLocation.DECK -> {
                if (seq == 0 && !field.deck.isEmpty()) {
                    field.deck.add(0, card);
                } else {
                    field.deck.add(card);
                }
                resetSequence(field.deck);
            }
            case CardLocation.EXTRA -> {
                if (seq >= 0 && seq < field.extraDeck.size()) {
                    field.extraDeck.add(seq, card);
                } else {
                    field.extraDeck.add(card);
                }
                resetSequence(field.extraDeck);
            }
            default -> {
            }
        }
    }

    public void removeCard(int loc, ClientCard card, int player, int seq) {
        ClientField field = fields[player];
        switch (loc) {
            case CardLocation.HAND -> {
                field.hand.remove(card);
                resetSequence(field.hand);
            }
            case CardLocation.GRAVE -> {
                field.graveyard.remove(card);
                resetSequence(field.graveyard);
            }
            case CardLocation.REMOVED -> {
                field.banished.remove(card);
                resetSequence(field.banished);
            }
            case CardLocation.MONSTER_ZONE -> {
                if (seq >= 0 && seq < field.monsterZone.length) {
                    field.monsterZone[seq] = null;
                }
            }
            case CardLocation.SPELL_ZONE -> {
                if (seq >= 0 && seq < field.spellZone.length) {
                    field.spellZone[seq] = null;
                }
            }
            case CardLocation.DECK -> {
                field.deck.remove(card);
                resetSequence(field.deck);
            }
            case CardLocation.EXTRA -> {
                field.extraDeck.remove(card);
                resetSequence(field.extraDeck);
            }
            default -> {
            }
        }
    }

    static void resetSequence(List<ClientCard> cards) {
        for (int i = 0; i < cards.size(); ++i) {
            cards.get(i).sequence = i;
        }
    }

    /**
     * The newest chain link while the chain is being built, including while it picks its cost or targets; {@code null}
     * once the chain resolves.
     */
    public ClientCard getCurrentChainCard() {
        if (solvingChainIndex != 0 || currentChain.isEmpty()) {
            return null;
        }
        return currentChain.get(currentChain.size() - 1);
    }

    /** The chain link resolving now, {@code null} before the chain resolves. */
    public ClientCard getCurrentSolvingChainCard() {
        if (solvingChainIndex == 0 || solvingChainIndex > currentChain.size()) {
            return null;
        }
        return currentChain.get(solvingChainIndex - 1);
    }

    public ChainInfo getCurrentSolvingChainInfo() {
        if (solvingChainIndex == 0 || solvingChainIndex > currentChainInfo.size()) {
            return null;
        }
        return currentChainInfo.get(solvingChainIndex - 1);
    }

    public boolean isCurrentSolvingChainNegated() {
        return solvingChainIndex > 0 && negatedChainIndexList.contains(solvingChainIndex);
    }
}
