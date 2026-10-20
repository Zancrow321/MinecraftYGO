package io.github.zancrow321.jadm.engine.ai.windbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * WindBot's {@code CardContainer} extension methods as static helpers over card lists. Entries may be {@code null}
 * (empty zones); cards without data (hidden ones) are left out wherever WindBot leaves them out.
 */
public final class CardContainer {
    private CardContainer() {
    }

    public static int compareCardAttack(ClientCard cardA, ClientCard cardB) {
        return Integer.compare(cardA.attack, cardB.attack);
    }

    public static int compareCardAttackPower(ClientCard cardA, ClientCard cardB) {
        return Integer.compare(cardA.getAttackPower(), cardB.getAttackPower());
    }

    public static int compareCardLevel(ClientCard cardA, ClientCard cardB) {
        return Integer.compare(cardA.level, cardB.level);
    }

    public static int compareCardLink(ClientCard cardA, ClientCard cardB) {
        return Integer.compare(cardA.linkCount, cardB.linkCount);
    }

    public static int compareDefensePower(ClientCard cardA, ClientCard cardB) {
        if (cardA == null && cardB == null) {
            return 0;
        }
        if (cardA == null) {
            return -1;
        }
        if (cardB == null) {
            return 1;
        }
        return Integer.compare(cardA.getDefensePower(), cardB.getDefensePower());
    }

    private static boolean known(ClientCard card) {
        return card != null && card.data != null;
    }

    private static boolean faceupMonster(ClientCard card, boolean canBeTarget) {
        return known(card) && card.hasType(CardType.MONSTER) && card.isFaceup()
                && !(canBeTarget && card.isShouldNotBeTarget());
    }

    public static ClientCard getHighestAttackMonster(Collection<ClientCard> cards) {
        return getHighestAttackMonster(cards, false);
    }

    public static ClientCard getHighestAttackMonster(Collection<ClientCard> cards, boolean canBeTarget) {
        return cards.stream().filter(card -> faceupMonster(card, canBeTarget))
                .max(Comparator.comparingInt(card -> card.attack)).orElse(null);
    }

    public static ClientCard getHighestDefenseMonster(Collection<ClientCard> cards) {
        return getHighestDefenseMonster(cards, false);
    }

    public static ClientCard getHighestDefenseMonster(Collection<ClientCard> cards, boolean canBeTarget) {
        return cards.stream().filter(card -> faceupMonster(card, canBeTarget))
                .max(Comparator.comparingInt(card -> card.defense)).orElse(null);
    }

    public static ClientCard getLowestAttackMonster(Collection<ClientCard> cards) {
        return getLowestAttackMonster(cards, false);
    }

    public static ClientCard getLowestAttackMonster(Collection<ClientCard> cards, boolean canBeTarget) {
        return cards.stream().filter(card -> faceupMonster(card, canBeTarget))
                .min(Comparator.comparingInt(card -> card.attack)).orElse(null);
    }

    public static ClientCard getLowestDefenseMonster(Collection<ClientCard> cards) {
        return getLowestDefenseMonster(cards, false);
    }

    public static ClientCard getLowestDefenseMonster(Collection<ClientCard> cards, boolean canBeTarget) {
        return cards.stream().filter(card -> faceupMonster(card, canBeTarget))
                .min(Comparator.comparingInt(card -> card.defense)).orElse(null);
    }

    public static boolean containsCardWithId(Collection<ClientCard> cards, int id) {
        return cards.stream().anyMatch(card -> known(card) && card.isCode(id));
    }

    public static int getCardCount(Collection<ClientCard> cards, int id) {
        return (int) cards.stream().filter(card -> known(card) && card.isCode(id)).count();
    }

    public static int getCardCount(Collection<ClientCard> cards, Collection<Integer> ids) {
        return (int) cards.stream().filter(card -> known(card) && card.isCode(ids)).count();
    }

    public static List<ClientCard> getMonsters(Collection<ClientCard> cards) {
        return new ArrayList<>(cards.stream().filter(card -> known(card) && card.hasType(CardType.MONSTER)).toList());
    }

    public static List<ClientCard> getMonsters(ClientCard[] cards) {
        return getMonsters(Arrays.asList(cards));
    }

    public static List<ClientCard> getFaceupPendulumMonsters(Collection<ClientCard> cards) {
        return new ArrayList<>(cards.stream().filter(card -> known(card) && card.hasType(CardType.MONSTER)
                && card.isFaceup() && card.hasType(CardType.PENDULUM)).toList());
    }

    public static ClientCard getInvincibleMonster(Collection<ClientCard> cards) {
        return getInvincibleMonster(cards, false);
    }

    public static ClientCard getInvincibleMonster(Collection<ClientCard> cards, boolean canBeTarget) {
        return cards.stream().filter(card -> known(card) && card.isMonsterInvincible() && card.isFaceup()
                && (!canBeTarget || !card.isShouldNotBeTarget())).findFirst().orElse(null);
    }

    public static ClientCard getDangerousMonster(Collection<ClientCard> cards) {
        return getDangerousMonster(cards, false);
    }

    public static ClientCard getDangerousMonster(Collection<ClientCard> cards, boolean canBeTarget) {
        return cards.stream().filter(card -> known(card) && card.isMonsterDangerous() && card.isFaceup()
                && (!canBeTarget || !card.isShouldNotBeTarget())).findFirst().orElse(null);
    }

    public static ClientCard getFloodgate(Collection<ClientCard> cards) {
        return getFloodgate(cards, false);
    }

    public static ClientCard getFloodgate(Collection<ClientCard> cards, boolean canBeTarget) {
        return cards.stream().filter(card -> known(card) && card.isFloodgate() && card.isFaceup()
                && (!canBeTarget || !card.isShouldNotBeTarget())).findFirst().orElse(null);
    }

    public static ClientCard getFirstMatchingCard(Collection<ClientCard> cards, Predicate<ClientCard> filter) {
        return cards.stream().filter(card -> known(card) && filter.test(card)).findFirst().orElse(null);
    }

    public static ClientCard getFirstMatchingFaceupCard(Collection<ClientCard> cards, Predicate<ClientCard> filter) {
        return cards.stream().filter(card -> known(card) && card.isFaceup() && filter.test(card)).findFirst()
                .orElse(null);
    }

    public static List<ClientCard> getMatchingCards(Collection<ClientCard> cards, Predicate<ClientCard> filter) {
        return new ArrayList<>(cards.stream().filter(card -> known(card) && filter.test(card)).toList());
    }

    public static int getMatchingCardsCount(Collection<ClientCard> cards, Predicate<ClientCard> filter) {
        return (int) cards.stream().filter(card -> known(card) && filter.test(card)).count();
    }

    public static boolean isExistingMatchingCard(Collection<ClientCard> cards, Predicate<ClientCard> filter) {
        return isExistingMatchingCard(cards, filter, 1);
    }

    public static boolean isExistingMatchingCard(Collection<ClientCard> cards, Predicate<ClientCard> filter,
                                                 int count) {
        return getMatchingCardsCount(cards, filter) >= count;
    }

    public static ClientCard getShouldBeDisabledBeforeItUseEffectMonster(Collection<ClientCard> cards) {
        return getShouldBeDisabledBeforeItUseEffectMonster(cards, true);
    }

    public static ClientCard getShouldBeDisabledBeforeItUseEffectMonster(Collection<ClientCard> cards,
                                                                        boolean canBeTarget) {
        return cards.stream().filter(card -> known(card) && card.isMonsterShouldBeDisabledBeforeItUseEffect()
                && card.isFaceup() && (!canBeTarget || !card.isShouldNotBeTarget())).findFirst().orElse(null);
    }

    /** Every way to pick {@code k} of the elements, in order. */
    public static <T> List<List<T>> getCombinations(List<T> elements, int k) {
        List<List<T>> out = new ArrayList<>();
        combine(elements, k, 0, new ArrayList<>(), out);
        return out;
    }

    private static <T> void combine(List<T> elements, int k, int from, List<T> current, List<List<T>> out) {
        if (current.size() == k) {
            out.add(List.copyOf(current));
            return;
        }
        for (int i = from; i < elements.size(); i++) {
            current.add(elements.get(i));
            combine(elements, k, i + 1, current, out);
            current.remove(current.size() - 1);
        }
    }
}
