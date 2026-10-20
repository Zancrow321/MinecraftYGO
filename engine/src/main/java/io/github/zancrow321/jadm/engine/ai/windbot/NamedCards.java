package io.github.zancrow321.jadm.engine.ai.windbot;

import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.CardInfo;

/**
 * The card database WindBot looks names and printed stats up in (WindBot's {@code NamedCardsManager}). Every duel
 * uses the same one, so it is set once.
 */
public final class NamedCards {
    private static volatile CardDatabase database;

    private NamedCards() {
    }

    public static void use(CardDatabase cards) {
        database = cards;
    }

    public static CardDatabase database() {
        return database;
    }

    /** @return the card, or {@code null} for 0, tokens the database doesn't have and unknown codes */
    public static CardInfo get(int id) {
        CardDatabase cards = database;
        return id == 0 || cards == null ? null : cards.card(id);
    }

    /** Whether {@code alias} only marks {@code cardId} as an alternate artwork of the same card. */
    public static boolean isAltartAlias(int cardId, int alias) {
        // YGOPro treats this normal-monster printing as a rule-name alias of the ritual monster.
        if (cardId == 5405695) {
            return false;
        }
        return cardId > 0 && alias > 0 && Math.abs(cardId - alias) < 20;
    }
}
