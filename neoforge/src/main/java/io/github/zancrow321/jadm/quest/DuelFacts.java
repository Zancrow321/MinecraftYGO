package io.github.zancrow321.jadm.quest;

import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.duel.DuelTable;

/**
 * What a finished duel was for one person, as the quests count it.
 *
 * @param opponent   "players", "npcs" (NPC duelists in the world) or "bots"
 * @param tournament a tournament game
 * @param deck       the deck they played, or {@code null} if unknown
 * @param lifePoints their team's life points at the end
 * @param turns      the turn the duel ended on
 * @param tally      what their team did in the duel
 */
public record DuelFacts(boolean won, String opponent, boolean ranked, boolean tournament, Deck deck, int lifePoints,
                        int turns, DuelTable.Tally tally) {
}
