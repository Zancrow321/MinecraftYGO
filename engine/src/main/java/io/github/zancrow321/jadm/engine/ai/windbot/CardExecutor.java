package io.github.zancrow321.jadm.engine.ai.windbot;

import io.github.zancrow321.jadm.engine.data.CardInfo;

import java.util.function.BooleanSupplier;

/** One rule of an {@link Executor}: for this kind of decision and card (-1 any card), whether to do it. */
public final class CardExecutor {
    public final int cardId;
    public final ExecutorType type;
    /** {@code null} means always. */
    public final BooleanSupplier func;

    public CardExecutor(ExecutorType type, int cardId, BooleanSupplier func) {
        CardInfo card = NamedCards.get(cardId);
        this.cardId = card != null && NamedCards.isAltartAlias(card.code(), card.data().alias())
                ? card.data().alias() : cardId;
        this.type = type;
        this.func = func;
    }
}
