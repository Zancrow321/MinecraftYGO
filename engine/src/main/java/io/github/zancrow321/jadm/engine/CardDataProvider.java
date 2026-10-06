package io.github.zancrow321.jadm.engine;

/**
 * Supplies card stats to the engine. Called on the thread driving the duel.
 */
@FunctionalInterface
public interface CardDataProvider {
    /**
     * @return the card's data, or {@code null} if the code is unknown (the core then treats it as a blank card)
     */
    CardData get(int code);
}
