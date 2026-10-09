package io.github.zancrow321.jadm.engine.tournament;

import io.github.zancrow321.jadm.engine.data.CardDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A booster draft: everyone opens a pack, takes one card and passes the rest on (to the left in the first round, to
 * the right in the second, and so on) until the packs are empty; then the next round's packs are opened. Everyone
 * picks at the same time, and the packs move on once all have picked. Plain fields, so a tournament saves it as JSON.
 */
public final class Draft {
    /** A card in a pack or a pool. */
    public static final class Card {
        public int code;
        /** {@code common}, {@code rare}, {@code super}, {@code ultra} or {@code secret} */
        public String rarity;

        public Card() {
        }

        public Card(int code, String rarity) {
            this.code = code;
            this.rarity = rarity;
        }
    }

    public int seats;
    public int rounds;
    /** The pack round being drafted, from 0; equal to {@link #rounds} once the draft is over. */
    public int round;
    /** Picks made so far in this round. */
    public int pick;
    /** The pack in front of each seat. */
    public List<List<Card>> packs = new ArrayList<>();
    /** What each seat has taken. */
    public List<List<Card>> picked = new ArrayList<>();
    /** The card each seat took from the pack in front of it this time, or -1 while it is still choosing. */
    public List<Integer> chosen = new ArrayList<>();

    /** For Gson. */
    public Draft() {
    }

    public Draft(int seats, int rounds) {
        this.seats = seats;
        this.rounds = rounds;
        for (int i = 0; i < seats; i++) {
            packs.add(new ArrayList<>());
            picked.add(new ArrayList<>());
            chosen.add(-1);
        }
    }

    /** Starts a round with a freshly opened pack in front of every seat. */
    public void open(List<List<Card>> opened) {
        if (opened.size() != seats) {
            throw new IllegalArgumentException("One pack per seat");
        }
        for (int i = 0; i < seats; i++) {
            packs.set(i, new ArrayList<>(opened.get(i)));
            chosen.set(i, -1);
        }
        pick = 0;
    }

    public boolean finished() {
        return round >= rounds;
    }

    /** Whether a seat still has to take a card from the pack in front of it. */
    public boolean waitingFor(int seat) {
        return !finished() && chosen.get(seat) < 0 && !packs.get(seat).isEmpty();
    }

    public boolean everyoneChose() {
        for (int i = 0; i < seats; i++) {
            if (waitingFor(i)) {
                return false;
            }
        }
        return true;
    }

    /** Takes card {@code index} of the pack in front of {@code seat}. @return whether that was a valid pick */
    public boolean choose(int seat, int index) {
        if (seat < 0 || seat >= seats || !waitingFor(seat) || index < 0 || index >= packs.get(seat).size()) {
            return false;
        }
        chosen.set(seat, index);
        return true;
    }

    /**
     * The seat a pack goes to after {@code seat} picked from it: the next seat in the 1st, 3rd, ... round, the one
     * before in the 2nd, 4th, ...
     */
    public int passesTo(int seat) {
        return Math.floorMod(round % 2 == 0 ? seat + 1 : seat - 1, seats);
    }

    /**
     * Once everyone has chosen: puts the chosen cards in their pools and passes the packs on.
     *
     * @return whether the round's packs are used up (then {@link #round} has moved on, and the next round's packs
     *         are to be {@linkplain #open opened} unless the draft is {@linkplain #finished over})
     */
    public boolean pass() {
        List<List<Card>> next = new ArrayList<>();
        for (int i = 0; i < seats; i++) {
            next.add(List.of());
        }
        for (int i = 0; i < seats; i++) {
            List<Card> pack = packs.get(i);
            int index = chosen.get(i);
            if (index >= 0 && index < pack.size()) {
                picked.get(i).add(pack.remove(index));
            }
            next.set(passesTo(i), pack);
        }
        for (int i = 0; i < seats; i++) {
            packs.set(i, new ArrayList<>(next.get(i)));
            chosen.set(i, -1);
        }
        pick++;
        if (packs.stream().allMatch(List::isEmpty)) {
            round++;
            pick = 0;
            return true;
        }
        return false;
    }

    /**
     * The card a bot takes from a pack: the best one for a deck (see {@link LimitedDecks#value}), with a little
     * chance so bots don't all draft alike.
     */
    public static int botPick(List<Card> pack, CardDatabase cards, Random random) {
        int best = 0;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < pack.size(); i++) {
            Card c = pack.get(i);
            double value = LimitedDecks.value(cards.card(c.code)) + rarityBonus(c.rarity) + random.nextDouble() * 3;
            if (value > bestValue) {
                bestValue = value;
                best = i;
            }
        }
        return best;
    }

    /** Foils are a little more tempting. */
    private static double rarityBonus(String rarity) {
        return switch (rarity == null ? "" : rarity) {
            case "rare" -> 1;
            case "super" -> 2;
            case "ultra", "secret" -> 3;
            default -> 0;
        };
    }
}
