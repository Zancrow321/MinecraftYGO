package io.github.zancrow321.jadm.engine.ai.windbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/** WindBot's {@code AIUtil}: questions about the board that many executors ask. */
public class AIUtil {
    public final Duel duel;
    public final ClientField bot;
    public final ClientField enemy;
    private final Random random = new Random();

    public AIUtil(Duel duel) {
        this.duel = duel;
        bot = duel.fields[0];
        enemy = duel.fields[1];
    }

    private static List<ClientCard> monsters(ClientField field) {
        return CardContainer.getMonsters(field.monsterZone);
    }

    /** The total attack of the player's monsters that can attack. */
    public int getTotalAttackingMonsterAttack(int player) {
        int total = 0;
        for (ClientCard m : duel.fields[player].getMonsters()) {
            if (m.isAttack() || m.isMonsterAttackWhileInDefPos()) {
                total += m.getAttackPower();
            }
        }
        return total;
    }

    /** The best battle power (ATK in Attack Position, DEF in Defense Position) on the field, -1 for none. */
    public int getBestPower(ClientField field) {
        return getBestPower(field, false);
    }

    public int getBestPower(ClientField field, boolean onlyATK) {
        return monsters(field).stream().filter(card -> !onlyATK || card.isAttack())
                .mapToInt(ClientCard::getDefensePower).max().orElse(-1);
    }

    public int getBestAttack(ClientField field) {
        return getBestPower(field, true);
    }

    public boolean isOneEnemyBetterThanValue(int value, boolean onlyATK) {
        return monsters(enemy).stream()
                .anyMatch(card -> card.getDefensePower() > value && (!onlyATK || card.isAttack()));
    }

    public boolean isAllEnemyBetterThanValue(int value, boolean onlyATK) {
        List<ClientCard> monsters = monsters(enemy);
        return !monsters.isEmpty() && monsters.stream()
                .allMatch(card -> card.getDefensePower() > value && (!onlyATK || card.isAttack()));
    }

    public boolean isEnemyBetter(boolean onlyATK, boolean all) {
        return all ? isAllEnemyBetter(onlyATK) : isOneEnemyBetter(onlyATK);
    }

    public boolean isOneEnemyBetter() {
        return isOneEnemyBetter(false);
    }

    public boolean isOneEnemyBetter(boolean onlyATK) {
        return isOneEnemyBetterThanValue(getBestPower(bot, onlyATK), onlyATK);
    }

    public boolean isAllEnemyBetter() {
        return isAllEnemyBetter(false);
    }

    public boolean isAllEnemyBetter(boolean onlyATK) {
        return isAllEnemyBetterThanValue(getBestPower(bot, onlyATK), onlyATK);
    }

    public ClientCard getBestBotMonster() {
        return getBestBotMonster(false);
    }

    public ClientCard getBestBotMonster(boolean onlyATK) {
        return monsters(bot).stream().filter(card -> !onlyATK || card.isAttack())
                .sorted(Comparator.comparingInt(ClientCard::getDefensePower).reversed()).findFirst().orElse(null);
    }

    public ClientCard getWorstBotMonster() {
        return getWorstBotMonster(false);
    }

    public ClientCard getWorstBotMonster(boolean onlyATK) {
        return monsters(bot).stream().filter(card -> !onlyATK || card.isAttack())
                .sorted(Comparator.comparingInt(ClientCard::getDefensePower)).findFirst().orElse(null);
    }

    public ClientCard getOneEnemyBetterThanValue(int value) {
        return getOneEnemyBetterThanValue(value, false, false);
    }

    public ClientCard getOneEnemyBetterThanValue(int value, boolean onlyATK) {
        return getOneEnemyBetterThanValue(value, onlyATK, false);
    }

    public ClientCard getOneEnemyBetterThanValue(int value, boolean onlyATK, boolean canBeTarget) {
        return monsters(enemy).stream().filter(card -> card.getDefensePower() >= value
                && (!onlyATK || card.isAttack()) && (!canBeTarget || !card.isShouldNotBeTarget()))
                .findFirst().orElse(null);
    }

    public ClientCard getOneEnemyBetterThanMyBest() {
        return getOneEnemyBetterThanMyBest(false, false);
    }

    public ClientCard getOneEnemyBetterThanMyBest(boolean onlyATK) {
        return getOneEnemyBetterThanMyBest(onlyATK, false);
    }

    public ClientCard getOneEnemyBetterThanMyBest(boolean onlyATK, boolean canBeTarget) {
        return getOneEnemyBetterThanValue(getBestPower(bot, onlyATK), onlyATK, canBeTarget);
    }

    public ClientCard getProblematicEnemyCard() {
        return getProblematicEnemyCard(0, false);
    }

    public ClientCard getProblematicEnemyCard(int attack) {
        return getProblematicEnemyCard(attack, false);
    }

    public ClientCard getProblematicEnemyCard(int attack, boolean canBeTarget) {
        ClientCard card = CardContainer.getFloodgate(Arrays.asList(enemy.monsterZone), canBeTarget);
        if (card != null) {
            return card;
        }
        card = CardContainer.getFloodgate(Arrays.asList(enemy.spellZone), canBeTarget);
        if (card != null) {
            return card;
        }
        card = CardContainer.getDangerousMonster(Arrays.asList(enemy.monsterZone), canBeTarget);
        if (card != null) {
            return card;
        }
        card = CardContainer.getInvincibleMonster(Arrays.asList(enemy.monsterZone), canBeTarget);
        if (card != null) {
            return card;
        }
        if (attack == 0) {
            attack = getBestAttack(bot);
        }
        return getOneEnemyBetterThanValue(attack, true, canBeTarget);
    }

    public ClientCard getProblematicEnemyMonster() {
        return getProblematicEnemyMonster(0, false);
    }

    public ClientCard getProblematicEnemyMonster(int attack) {
        return getProblematicEnemyMonster(attack, false);
    }

    public ClientCard getProblematicEnemyMonster(int attack, boolean canBeTarget) {
        ClientCard card = CardContainer.getFloodgate(Arrays.asList(enemy.monsterZone), canBeTarget);
        if (card != null) {
            return card;
        }
        card = CardContainer.getDangerousMonster(Arrays.asList(enemy.monsterZone), canBeTarget);
        if (card != null) {
            return card;
        }
        card = CardContainer.getInvincibleMonster(Arrays.asList(enemy.monsterZone), canBeTarget);
        if (card != null) {
            return card;
        }
        if (attack == 0) {
            attack = getBestAttack(bot);
        }
        return getOneEnemyBetterThanValue(attack, true, canBeTarget);
    }

    public ClientCard getProblematicEnemySpell() {
        return CardContainer.getFloodgate(Arrays.asList(enemy.spellZone));
    }

    public ClientCard getBestEnemyCard() {
        return getBestEnemyCard(false, false);
    }

    public ClientCard getBestEnemyCard(boolean onlyFaceup) {
        return getBestEnemyCard(onlyFaceup, false);
    }

    public ClientCard getBestEnemyCard(boolean onlyFaceup, boolean canBeTarget) {
        ClientCard card = getBestEnemyMonster(onlyFaceup, canBeTarget);
        if (card != null) {
            return card;
        }
        return getBestEnemySpell(onlyFaceup);
    }

    public ClientCard getBestEnemyMonster() {
        return getBestEnemyMonster(false, false);
    }

    public ClientCard getBestEnemyMonster(boolean onlyFaceup) {
        return getBestEnemyMonster(onlyFaceup, false);
    }

    public ClientCard getBestEnemyMonster(boolean onlyFaceup, boolean canBeTarget) {
        ClientCard card = getProblematicEnemyMonster(0, canBeTarget);
        if (card != null) {
            return card;
        }
        card = CardContainer.getHighestAttackMonster(Arrays.asList(enemy.monsterZone), canBeTarget);
        if (card != null) {
            return card;
        }
        if (!onlyFaceup) {
            return enemy.getMonsters().stream().filter(monster -> !canBeTarget || !monster.isShouldNotBeTarget())
                    .findFirst().orElse(null);
        }
        return null;
    }

    public ClientCard getWorstEnemyMonster() {
        return getWorstEnemyMonster(false);
    }

    public ClientCard getWorstEnemyMonster(boolean onlyATK) {
        return monsters(enemy).stream().filter(card -> !onlyATK || card.isAttack())
                .sorted(Comparator.comparingInt(ClientCard::getDefensePower)).findFirst().orElse(null);
    }

    public ClientCard getBestEnemySpell() {
        return getBestEnemySpell(false);
    }

    public ClientCard getBestEnemySpell(boolean onlyFaceup) {
        ClientCard card = getProblematicEnemySpell();
        if (card != null) {
            return card;
        }
        List<ClientCard> spells = enemy.getSpells();
        card = spells.stream().filter(ecard -> ecard.isFaceup()
                && (ecard.hasType(CardType.CONTINUOUS) || ecard.hasType(CardType.FIELD))).findFirst().orElse(null);
        if (card != null) {
            return card;
        }
        if (!spells.isEmpty() && !onlyFaceup) {
            return spells.get(0);
        }
        return null;
    }

    /** @param id 0 for the left Pendulum Zone, 1 for the right */
    public ClientCard getPZone(int player, int id) {
        return duel.separatePendulumZones ? duel.fields[player].spellZone[6 + id]
                : duel.fields[player].spellZone[id * 4];
    }

    /** The description of a card's {@code option}-th string, as the core numbers them. */
    public long getStringId(int id, int option) {
        return ((long) id << 20) | (option & 0xfffff);
    }

    public boolean isTurn1OrMain2() {
        return duel.turn == 1 || duel.phase == DuelPhase.MAIN2;
    }

    /** The bot's Monster Zones a monster from the Extra Deck may go to, if {@code remove} left the field. */
    public int getBotAvailZonesFromExtraDeck(List<ClientCard> remove) {
        ClientCard[] botMZone = bot.monsterZone.clone();
        ClientCard[] enemyMZone = enemy.monsterZone.clone();
        for (int i = 0; i < 7; i++) {
            if (remove.contains(botMZone[i])) {
                botMZone[i] = null;
            }
            if (remove.contains(enemyMZone[i])) {
                enemyMZone[i] = null;
            }
        }
        if (!duel.isNewRule || duel.isNewRule2020) {
            return Zones.MAIN_MONSTER_ZONES;
        }
        int result = 0;
        if (botMZone[5] == null && botMZone[6] == null) {
            if (enemyMZone[5] == null) {
                result |= Zones.Z6;
            }
            if (enemyMZone[6] == null) {
                result |= Zones.Z5;
            }
        }
        if (botMZone[0] == null && (marker(botMZone[1], CardLinkMarker.LEFT)
                || marker(botMZone[5], CardLinkMarker.BOTTOM_LEFT)
                || marker(enemyMZone[6], CardLinkMarker.TOP_RIGHT))) {
            result |= Zones.Z0;
        }
        if (botMZone[1] == null && (marker(botMZone[0], CardLinkMarker.RIGHT)
                || marker(botMZone[2], CardLinkMarker.LEFT)
                || marker(botMZone[5], CardLinkMarker.BOTTOM)
                || marker(enemyMZone[6], CardLinkMarker.TOP))) {
            result |= Zones.Z1;
        }
        if (botMZone[2] == null && (marker(botMZone[1], CardLinkMarker.RIGHT)
                || marker(botMZone[3], CardLinkMarker.LEFT)
                || marker(botMZone[5], CardLinkMarker.BOTTOM_RIGHT)
                || marker(enemyMZone[6], CardLinkMarker.TOP_LEFT)
                || marker(botMZone[6], CardLinkMarker.BOTTOM_LEFT)
                || marker(enemyMZone[5], CardLinkMarker.TOP_RIGHT))) {
            result |= Zones.Z2;
        }
        if (botMZone[3] == null && (marker(botMZone[2], CardLinkMarker.RIGHT)
                || marker(botMZone[4], CardLinkMarker.LEFT)
                || marker(botMZone[6], CardLinkMarker.BOTTOM)
                || marker(enemyMZone[5], CardLinkMarker.TOP))) {
            result |= Zones.Z3;
        }
        if (botMZone[4] == null && (marker(botMZone[3], CardLinkMarker.RIGHT)
                || marker(botMZone[6], CardLinkMarker.BOTTOM_RIGHT)
                || marker(enemyMZone[5], CardLinkMarker.TOP_LEFT))) {
            result |= Zones.Z4;
        }
        return result;
    }

    private static boolean marker(ClientCard card, int marker) {
        return card != null && card.hasLinkMarker(marker);
    }

    public int getBotAvailZonesFromExtraDeck(ClientCard remove) {
        return getBotAvailZonesFromExtraDeck(Collections.singletonList(remove));
    }

    public int getBotAvailZonesFromExtraDeck() {
        return getBotAvailZonesFromExtraDeck(List.of());
    }

    public boolean isChainTarget(ClientCard card) {
        return duel.chainTargets.stream().anyMatch(c -> c == card);
    }

    public boolean isChainTargetOnly(ClientCard card) {
        return duel.chainTargetOnly.size() == 1 && card == duel.chainTargetOnly.get(0);
    }

    public boolean chainContainsCard(int id) {
        return duel.currentChain.stream().anyMatch(card -> card.isCode(id));
    }

    public boolean chainContainsCard(int... ids) {
        return duel.currentChain.stream().anyMatch(card -> card.isCode(ids));
    }

    public int chainCountPlayer(int player) {
        return (int) duel.currentChain.stream().filter(card -> card.controller == player).count();
    }

    public boolean chainContainPlayer(int player) {
        return duel.currentChain.stream().anyMatch(card -> card.controller == player);
    }

    public boolean hasChainedTrap(int player) {
        return duel.currentChain.stream().anyMatch(card -> card.controller == player && card.hasType(CardType.TRAP));
    }

    public ClientCard getLastChainCard() {
        return duel.currentChain.isEmpty() ? null : duel.currentChain.get(duel.currentChain.size() - 1);
    }

    public List<ClientCard> selectPreferredCards(ClientCard preferred, List<ClientCard> cards, int min, int max) {
        List<ClientCard> selected = new ArrayList<>();
        if (cards.contains(preferred) && selected.size() < max) {
            selected.add(preferred);
        }
        return selected;
    }

    public List<ClientCard> selectPreferredCards(int preferred, List<ClientCard> cards, int min, int max) {
        List<ClientCard> selected = new ArrayList<>();
        for (ClientCard card : cards) {
            if (card.isCode(preferred) && selected.size() < max) {
                selected.add(card);
            }
        }
        return selected;
    }

    public List<ClientCard> selectPreferredCards(List<ClientCard> preferred, List<ClientCard> cards, int min,
                                                 int max) {
        List<ClientCard> selected = new ArrayList<>();
        List<ClientCard> available = new ArrayList<>(cards);
        for (ClientCard card : preferred) {
            if (selected.size() >= max) {
                break;
            }
            if (available.remove(card)) {
                selected.add(card);
            }
        }
        return selected;
    }

    public List<ClientCard> selectPreferredCardIds(List<Integer> preferred, List<ClientCard> cards, int min,
                                                   int max) {
        List<ClientCard> selected = new ArrayList<>();
        for (int id : preferred) {
            for (ClientCard card : cards) {
                if (card.isCode(id) && selected.size() < max && !selected.contains(card)) {
                    selected.add(card);
                }
            }
            if (selected.size() >= max) {
                break;
            }
        }
        return selected;
    }

    /** {@code selected} cut or padded with other candidates to fit min..max, or {@code null} if it can't. */
    public List<ClientCard> checkSelectCount(List<ClientCard> selected, List<ClientCard> cards, int min, int max) {
        List<ClientCard> result = new ArrayList<>(new LinkedHashSet<>(selected.stream().filter(cards::contains)
                .toList()));
        if (result.size() < min) {
            for (ClientCard card : cards) {
                if (!result.contains(card)) {
                    result.add(card);
                }
                if (result.size() >= max) {
                    break;
                }
            }
            if (result.size() < min) {
                return null;
            }
        }
        while (result.size() > max) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    public List<List<ClientCard>> getSynchroMaterials(List<ClientCard> candidateMaterials, int synchroLevel,
                                                      int tunerCount, int nonTunerCount) {
        return getSynchroMaterials(candidateMaterials, synchroLevel, tunerCount, nonTunerCount, false, true, null,
                null);
    }

    /** Every set of materials whose Levels add up to {@code synchroLevel} with enough Tuners and non-Tuners. */
    public List<List<ClientCard>> getSynchroMaterials(List<ClientCard> candidateMaterials, int synchroLevel,
                                                      int tunerCount, int nonTunerCount, boolean allowMoreTuners,
                                                      boolean allowMoreNonTuners, Predicate<ClientCard> tunerFilter,
                                                      Predicate<ClientCard> nonTunerFilter) {
        List<List<ClientCard>> result = new ArrayList<>();
        if (candidateMaterials == null || synchroLevel <= 0 || tunerCount < 0 || nonTunerCount < 0) {
            return result;
        }
        List<ClientCard> eligible = new ArrayList<>(new LinkedHashSet<>(candidateMaterials.stream()
                .filter(card -> card != null && card.level > 0 && card.level <= synchroLevel
                        && !card.isMonsterNotBeSynchroMaterial()).toList()));
        eligible.removeIf(card -> card.hasType(CardType.TUNER)
                ? tunerFilter != null && !tunerFilter.test(card)
                : nonTunerFilter != null && !nonTunerFilter.test(card));
        eligible.sort(Comparator.comparingInt(card -> card.level));
        int availableTuners = (int) eligible.stream().filter(card -> card.hasType(CardType.TUNER)).count();
        if (availableTuners < tunerCount || eligible.size() - availableTuners < nonTunerCount) {
            return result;
        }
        List<Integer> selectedIndexes = new ArrayList<>();
        List<ClientCard> materials = new ArrayList<>();
        int nextIndex = 0;
        int sum = 0;
        int tuners = 0;
        int nonTuners = 0;
        while (true) {
            if (sum == synchroLevel) {
                if (tuners >= tunerCount && nonTuners >= nonTunerCount) {
                    result.add(new ArrayList<>(materials));
                }
            } else if (nextIndex < eligible.size()) {
                int index = nextIndex++;
                ClientCard material = eligible.get(index);
                boolean isTuner = material.hasType(CardType.TUNER);
                if (sum + material.level <= synchroLevel) {
                    if ((!isTuner || allowMoreTuners || tuners < tunerCount)
                            && (isTuner || allowMoreNonTuners || nonTuners < nonTunerCount)) {
                        selectedIndexes.add(index);
                        materials.add(material);
                        sum += material.level;
                        if (isTuner) {
                            tuners++;
                        } else {
                            nonTuners++;
                        }
                    }
                    continue;
                }
                // Later candidates can't fit either: the Levels are sorted ascending.
            }
            if (selectedIndexes.isEmpty()) {
                break;
            }
            int previousIndex = selectedIndexes.remove(selectedIndexes.size() - 1);
            ClientCard previous = eligible.get(previousIndex);
            materials.remove(materials.size() - 1);
            sum -= previous.level;
            if (previous.hasType(CardType.TUNER)) {
                tuners--;
            } else {
                nonTuners--;
            }
            nextIndex = previousIndex + 1;
        }
        return result;
    }

    public List<List<ClientCard>> getXyzMaterials(List<ClientCard> candidateMaterials, int materialLevel,
                                                  int materialCount) {
        return getXyzMaterials(candidateMaterials, materialLevel, materialCount, false, null);
    }

    /** Every set of {@code materialCount} (or more) monsters of the Level an Xyz Summon needs. */
    public List<List<ClientCard>> getXyzMaterials(List<ClientCard> candidateMaterials, int materialLevel,
                                                  int materialCount, boolean allowMoreMaterials,
                                                  Predicate<ClientCard> materialFilter) {
        List<List<ClientCard>> result = new ArrayList<>();
        if (candidateMaterials == null || materialLevel <= 0 || materialCount <= 0) {
            return result;
        }
        List<ClientCard> eligible = new ArrayList<>(new LinkedHashSet<>(candidateMaterials.stream()
                .filter(card -> card != null && card.level == materialLevel && !card.hasType(CardType.TOKEN)
                        && !card.isMonsterNotBeXyzMaterial()).toList()));
        if (materialFilter != null) {
            eligible.removeIf(card -> !materialFilter.test(card));
        }
        if (eligible.size() < materialCount) {
            return result;
        }
        int maxCount = allowMoreMaterials ? eligible.size() : materialCount;
        for (int count = materialCount; count <= maxCount; count++) {
            int[] indexes = new int[count];
            for (int i = 0; i < count; i++) {
                indexes[i] = i;
            }
            while (true) {
                List<ClientCard> materials = new ArrayList<>(count);
                for (int index : indexes) {
                    materials.add(eligible.get(index));
                }
                result.add(materials);
                // Advance the rightmost index that leaves room for all the following ones.
                int position = count - 1;
                while (position >= 0 && indexes[position] == eligible.size() - count + position) {
                    position--;
                }
                if (position < 0) {
                    break;
                }
                indexes[position]++;
                for (int i = position + 1; i < count; i++) {
                    indexes[i] = indexes[i - 1] + 1;
                }
            }
        }
        return result;
    }

    /** Whether the materials can make exactly that Link Rating (a Link Monster counts as 1 or its own rating). */
    public boolean canMakeLinkRating(List<ClientCard> materials, int linkRating) {
        if (materials == null || materials.isEmpty() || linkRating <= 0 || materials.contains(null)) {
            return false;
        }
        Set<Integer> totals = new HashSet<>(Set.of(0));
        for (ClientCard material : materials) {
            Set<Integer> next = new HashSet<>();
            for (int total : totals) {
                if (total + 1 <= linkRating) {
                    next.add(total + 1);
                }
                if (material.hasType(CardType.LINK) && material.linkCount > 1
                        && total + material.linkCount <= linkRating) {
                    next.add(total + material.linkCount);
                }
            }
            totals = next;
            if (totals.isEmpty()) {
                return false;
            }
        }
        return totals.contains(linkRating);
    }

    public List<List<ClientCard>> getLinkMaterials(List<ClientCard> candidateMaterials, int linkRating,
                                                   int minCount) {
        return getLinkMaterials(candidateMaterials, linkRating, minCount, 0, null);
    }

    public List<List<ClientCard>> getLinkMaterials(List<ClientCard> candidateMaterials, int linkRating, int minCount,
                                                   int maxCount, Predicate<ClientCard> materialFilter) {
        if (candidateMaterials == null || linkRating <= 0 || minCount <= 0) {
            return new ArrayList<>();
        }
        if (maxCount <= 0) {
            maxCount = linkRating;
        }
        Predicate<ClientCard> filter = materialFilter != null ? materialFilter : card -> true;
        List<ClientCard> eligible = new ArrayList<>(new LinkedHashSet<>(candidateMaterials.stream()
                .filter(card -> card != null && filter.test(card)).toList()));
        if (eligible.size() < minCount || eligible.size() > 20) {
            return new ArrayList<>();
        }
        List<List<ClientCard>> result = new ArrayList<>();
        int subsetCount = 1 << eligible.size();
        for (int mask = 1; mask < subsetCount; mask++) {
            List<ClientCard> materials = new ArrayList<>();
            for (int i = 0; i < eligible.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    materials.add(eligible.get(i));
                }
            }
            if (materials.size() >= minCount && materials.size() <= maxCount
                    && canMakeLinkRating(materials, linkRating)) {
                result.add(materials);
            }
        }
        return result;
    }

    public <T> List<T> shuffleList(List<T> list) {
        List<T> result = new ArrayList<>(list);
        shuffleListInPlace(result);
        return result;
    }

    public <T> void shuffleListInPlace(List<T> list) {
        Collections.shuffle(list, random);
    }
}
