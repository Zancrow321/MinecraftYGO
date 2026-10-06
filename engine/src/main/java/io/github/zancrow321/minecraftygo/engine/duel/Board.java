package io.github.zancrow321.minecraftygo.engine.duel;

import io.github.zancrow321.minecraftygo.engine.protocol.CardState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A snapshot of both players' cards. The server holds the full board; {@link #viewedBy(int)} removes what a
 * player isn't allowed to see before it is sent anywhere.
 */
public record Board(int turn, int turnPlayer, int phase, List<Side> sides, int options) {
    public Board(int turn, int turnPlayer, int phase, List<Side> sides) {
        this(turn, turnPlayer, phase, sides, 0);
    }

    /** Whether the rules have Extra Monster Zones (Master Rule 4 and later). */
    public boolean extraMonsterZones() {
        return (options & io.github.zancrow321.minecraftygo.engine.OcgConstants.DUEL_EMZONE) != 0;
    }

    /** Whether the Pendulum Zones are their own zones beside the field (Master Rule 3). */
    public boolean separatePendulumZones() {
        return (options & io.github.zancrow321.minecraftygo.engine.OcgConstants.DUEL_SEPARATE_PZONE) != 0;
    }

    /** Whether Pendulum Zones exist at all (Master Rule 3 and later). */
    public boolean pendulumZones() {
        return (options & io.github.zancrow321.minecraftygo.engine.OcgConstants.DUEL_PZONE) != 0;
    }

    /**
     * One player's cards. Zone lists are fixed-size (7 monster, 8 spell/trap) with {@code null} for empty zones;
     * hidden cards keep their slot but have code 0.
     */
    public record Side(int lifePoints, int deckCount, List<CardState> hand, List<CardState> monsters,
                       List<CardState> spells, List<CardState> graveyard, List<CardState> banished,
                       List<CardState> extra) {
    }

    public Side side(int player) {
        return sides.get(player);
    }

    /** @return a copy with every card {@code viewer} may not see replaced by a blank (code 0) card */
    public Board viewedBy(int viewer) {
        return viewedBy(viewer, true);
    }

    /**
     * @param handVisible whether the viewer may see their own side's hand; in a tag duel only the partner who is
     *                    playing holds it
     */
    public Board viewedBy(int viewer, boolean handVisible) {
        List<Side> censored = new ArrayList<>(2);
        for (int player = 0; player < 2; player++) {
            Side s = sides.get(player);
            boolean own = player == viewer;
            boolean ownHand = own && handVisible;
            censored.add(new Side(s.lifePoints(), s.deckCount(),
                    map(s.hand(), c -> ownHand || c.isPublic() ? c : hidden(c)),
                    map(s.monsters(), c -> own || isFaceUp(c) ? c : hidden(c)),
                    map(s.spells(), c -> own || isFaceUp(c) ? c : hidden(c)),
                    s.graveyard(),
                    map(s.banished(), c -> own || isFaceUp(c) ? c : hidden(c)),
                    map(s.extra(), c -> own || isFaceUp(c) ? c : hidden(c))));
        }
        return new Board(turn, turnPlayer, phase, List.copyOf(censored), options);
    }

    private static boolean isFaceUp(CardState card) {
        return (card.position() & 0x5) != 0;
    }

    /** Keeps only the position, so the viewer sees a face-down card in that slot. */
    private static CardState hidden(CardState card) {
        return new CardState(0, card.position(), 0, 0, 0, 0, 0, 0, 0, 0, card.owner(), false, true, List.of());
    }

    private static List<CardState> map(List<CardState> cards, UnaryOperator<CardState> f) {
        List<CardState> out = new ArrayList<>(cards.size());
        for (CardState card : cards) {
            out.add(card == null ? null : f.apply(card));
        }
        return Collections.unmodifiableList(out);
    }
}
