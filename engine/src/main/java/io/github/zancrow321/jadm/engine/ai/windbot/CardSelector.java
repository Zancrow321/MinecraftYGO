package io.github.zancrow321.jadm.engine.ai.windbot;

import java.util.ArrayList;
import java.util.List;

/** A choice made in advance for the next card selection: a card, cards, passcodes or a location. */
public final class CardSelector {
    private enum SelectType { CARD, CARDS, ID, IDS, LOCATION }

    private final SelectType type;
    private ClientCard card;
    private List<ClientCard> cards;
    private int id;
    private List<Integer> ids;
    private int location;

    public CardSelector(ClientCard card) {
        type = SelectType.CARD;
        this.card = card;
    }

    public CardSelector(List<ClientCard> cards) {
        type = SelectType.CARDS;
        this.cards = cards;
    }

    public CardSelector(int cardId) {
        type = SelectType.ID;
        id = cardId;
    }

    public static CardSelector ofIds(List<Integer> ids) {
        CardSelector selector = new CardSelector(SelectType.IDS);
        selector.ids = ids;
        return selector;
    }

    public static CardSelector ofLocation(int location) {
        CardSelector selector = new CardSelector(SelectType.LOCATION);
        selector.location = location;
        return selector;
    }

    private CardSelector(SelectType type) {
        this.type = type;
    }

    public List<ClientCard> select(List<ClientCard> cards, int min, int max) {
        List<ClientCard> result = new ArrayList<>();
        switch (type) {
            case CARD -> {
                if (cards.contains(card)) {
                    result.add(card);
                }
            }
            case CARDS -> {
                for (ClientCard c : this.cards) {
                    if (cards.contains(c) && !result.contains(c)) {
                        result.add(c);
                    }
                }
            }
            case ID -> {
                for (ClientCard c : cards) {
                    if (c.isCode(id)) {
                        result.add(c);
                    }
                }
            }
            case IDS -> {
                for (int i : ids) {
                    for (ClientCard c : cards) {
                        if (c.isCode(i) && !result.contains(c)) {
                            result.add(c);
                        }
                    }
                }
            }
            case LOCATION -> {
                for (ClientCard c : cards) {
                    if (c.location == location) {
                        result.add(c);
                    }
                }
            }
        }
        if (result.size() < min) {
            for (ClientCard c : cards) {
                if (!result.contains(c)) {
                    result.add(c);
                }
                if (result.size() >= min) {
                    break;
                }
            }
        }
        while (result.size() > max) {
            result.remove(result.size() - 1);
        }
        return result;
    }
}
