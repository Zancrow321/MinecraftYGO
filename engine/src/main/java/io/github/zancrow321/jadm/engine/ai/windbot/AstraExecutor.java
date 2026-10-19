package io.github.zancrow321.jadm.engine.ai.windbot;

import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Astra's executor for any deck: WindBot's own executors only play the cards of their own deck lists, so this one
 * knows the classic staples by name and plays everything else with WindBot's generic rules. The four styles differ
 * in how they summon, attack and spend removal.
 */
public final class AstraExecutor extends DefaultExecutor {
    public enum Style { BALANCED, AGGRESSIVE, DEFENSIVE, CONTROL }

    static final class CardId {
        static final int POT_OF_GREED = 55144522;
        static final int GRACEFUL_CHARITY = 79571449;
        static final int UPSTART_GOBLIN = 70368879;
        static final int DELINQUENT_DUO = 44763025;
        static final int CONFISCATION = 17375316;
        static final int FORCEFUL_SENTRY = 42829885;
        static final int CARD_DESTRUCTION = 72892473;
        static final int HEAVY_STORM = 19613556;
        static final int MYSTICAL_SPACE_TYPHOON = 5318639;
        static final int DUST_TORNADO = 60082869;
        static final int DARK_HOLE = 53129443;
        static final int RAIGEKI = 12580477;
        static final int LIGHTNING_VORTEX = 69162969;
        static final int SMASHING_GROUND = 97169186;
        static final int FISSURE = 66788016;
        static final int HAMMER_SHOT = 26412047;
        static final int NOBLEMAN_OF_CROSSOUT = 71044499;
        static final int BOOK_OF_MOON = 14087893;
        static final int SNATCH_STEAL = 45986603;
        static final int CHANGE_OF_HEART = 4031928;
        static final int BRAIN_CONTROL = 87910978;
        static final int ENEMY_CONTROLLER = 98045062;
        static final int MONSTER_REBORN = 83764718;
        static final int PREMATURE_BURIAL = 70828912;
        static final int CALL_OF_THE_HAUNTED = 97077563;
        static final int SCAPEGOAT = 73915051;
        static final int SWORDS_OF_REVEALING_LIGHT = 72302403;
        static final int MESSENGER_OF_PEACE = 44656491;
        static final int MIRROR_FORCE = 44095762;
        static final int TORRENTIAL_TRIBUTE = 53582587;
        static final int SOLEMN_JUDGMENT = 41420027;
        static final int BOTTOMLESS_TRAP_HOLE = 29401950;
        static final int TRAP_HOLE = 4206964;
        static final int SAKURETSU_ARMOR = 56120475;
        static final int MAGIC_CYLINDER = 62279055;
        static final int NEGATE_ATTACK = 14315573;
        static final int RING_OF_DESTRUCTION = 83555666;
        static final int COMPULSORY_EVACUATION_DEVICE = 94192409;
        static final int MAGIC_JAMMER = 77414722;
        static final int SEVEN_TOOLS_OF_THE_BANDIT = 3819470;
        static final int WABOKU = 12607053;
        static final int THREATENING_ROAR = 36361633;
        static final int TSUKUYOMI = 34853266;

        private CardId() {
        }
    }

    private static final int[] DRAW_SPELLS = {
            CardId.POT_OF_GREED, CardId.GRACEFUL_CHARITY, CardId.UPSTART_GOBLIN, CardId.CARD_DESTRUCTION,
    };

    private final Style style;

    public AstraExecutor(GameAI ai, Duel duel, Style style) {
        super(ai, duel);
        this.style = style;

        // Card advantage first: draw, then the hand attacks.
        for (int id : DRAW_SPELLS) {
            addExecutor(ExecutorType.ACTIVATE, id, this::drawSpell);
        }
        addExecutor(ExecutorType.ACTIVATE, CardId.DELINQUENT_DUO, this::handAttack);
        addExecutor(ExecutorType.ACTIVATE, CardId.CONFISCATION, this::handAttack);
        addExecutor(ExecutorType.ACTIVATE, CardId.FORCEFUL_SENTRY, this::handAttack);

        // Removal.
        addExecutor(ExecutorType.ACTIVATE, CardId.HEAVY_STORM, this::defaultHeavyStorm);
        addExecutor(ExecutorType.ACTIVATE, CardId.MYSTICAL_SPACE_TYPHOON, this::defaultMysticalSpaceTyphoon);
        addExecutor(ExecutorType.ACTIVATE, CardId.DUST_TORNADO, this::defaultMysticalSpaceTyphoon);
        addExecutor(ExecutorType.ACTIVATE, CardId.RAIGEKI, this::defaultRaigeki);
        addExecutor(ExecutorType.ACTIVATE, CardId.DARK_HOLE, this::defaultDarkHole);
        addExecutor(ExecutorType.ACTIVATE, CardId.LIGHTNING_VORTEX, this::lightningVortex);
        addExecutor(ExecutorType.ACTIVATE, CardId.SMASHING_GROUND, this::defaultSmashingGround);
        addExecutor(ExecutorType.ACTIVATE, CardId.FISSURE, this::enemyHasFaceUpMonster);
        addExecutor(ExecutorType.ACTIVATE, CardId.HAMMER_SHOT, this::defaultHammerShot);
        addExecutor(ExecutorType.ACTIVATE, CardId.NOBLEMAN_OF_CROSSOUT, this::nobleman);
        addExecutor(ExecutorType.ACTIVATE, CardId.BOOK_OF_MOON, this::defaultBookOfMoon);

        // Taking monsters.
        addExecutor(ExecutorType.ACTIVATE, CardId.SNATCH_STEAL, this::takeBestEnemyMonster);
        addExecutor(ExecutorType.ACTIVATE, CardId.CHANGE_OF_HEART, this::takeBestEnemyMonster);
        addExecutor(ExecutorType.ACTIVATE, CardId.BRAIN_CONTROL, this::takeBestEnemyMonster);
        addExecutor(ExecutorType.ACTIVATE, CardId.ENEMY_CONTROLLER, this::enemyController);

        // Revival.
        addExecutor(ExecutorType.ACTIVATE, CardId.MONSTER_REBORN, this::revive);
        addExecutor(ExecutorType.ACTIVATE, CardId.PREMATURE_BURIAL, this::revive);
        addExecutor(ExecutorType.ACTIVATE, CardId.CALL_OF_THE_HAUNTED, this::defaultCallOfTheHaunted);

        // Defence.
        addExecutor(ExecutorType.ACTIVATE, CardId.SCAPEGOAT, this::defaultScapegoat);
        addExecutor(ExecutorType.ACTIVATE, CardId.SWORDS_OF_REVEALING_LIGHT, this::swordsOfRevealingLight);
        addExecutor(ExecutorType.ACTIVATE, CardId.MESSENGER_OF_PEACE, this::swordsOfRevealingLight);

        // Traps.
        addExecutor(ExecutorType.ACTIVATE, CardId.SOLEMN_JUDGMENT, this::defaultSolemnJudgment);
        addExecutor(ExecutorType.ACTIVATE, CardId.MAGIC_JAMMER, this::defaultSolemnJudgment);
        addExecutor(ExecutorType.ACTIVATE, CardId.SEVEN_TOOLS_OF_THE_BANDIT, this::defaultSolemnJudgment);
        addExecutor(ExecutorType.ACTIVATE, CardId.TORRENTIAL_TRIBUTE, this::defaultTorrentialTribute);
        addExecutor(ExecutorType.ACTIVATE, CardId.BOTTOMLESS_TRAP_HOLE, this::defaultBottomlessTrapHole);
        addExecutor(ExecutorType.ACTIVATE, CardId.TRAP_HOLE, this::defaultUniqueTrap);
        addExecutor(ExecutorType.ACTIVATE, CardId.COMPULSORY_EVACUATION_DEVICE,
                this::defaultCompulsoryEvacuationDevice);
        addExecutor(ExecutorType.ACTIVATE, CardId.MIRROR_FORCE, this::underAttack);
        addExecutor(ExecutorType.ACTIVATE, CardId.SAKURETSU_ARMOR, this::underAttack);
        addExecutor(ExecutorType.ACTIVATE, CardId.MAGIC_CYLINDER, this::underAttack);
        addExecutor(ExecutorType.ACTIVATE, CardId.NEGATE_ATTACK, this::underAttack);
        addExecutor(ExecutorType.ACTIVATE, CardId.WABOKU, this::underAttack);
        addExecutor(ExecutorType.ACTIVATE, CardId.THREATENING_ROAR, this::underAttack);
        addExecutor(ExecutorType.ACTIVATE, CardId.RING_OF_DESTRUCTION, this::ringOfDestruction);

        // Monsters.
        addExecutor(ExecutorType.ACTIVATE, CardId.TSUKUYOMI, this::tsukuyomiTarget);
        addExecutor(ExecutorType.SP_SUMMON, this::specialSummon);
        if (style == Style.DEFENSIVE || style == Style.CONTROL) {
            addExecutor(ExecutorType.SUMMON, this::strongEnoughToShow);
            addExecutor(ExecutorType.MONSTER_SET, this::setMonster);
        } else {
            addExecutor(ExecutorType.SUMMON_OR_SET, this::summonOrSet);
        }
        addExecutor(ExecutorType.REPOS, this::defaultMonsterRepos);

        // Everything else: effects when they don't chain to our own, traps on the opponent's moves.
        addExecutor(ExecutorType.ACTIVATE, this::otherCard);
        addExecutor(ExecutorType.SPELL_SET, this::setSpellOrTrap);
    }

    public Style style() {
        return style;
    }

    @Override
    public boolean onSelectHand() {
        return true; // go first
    }

    @Override
    public boolean onPreBattleBetween(ClientCard attacker, ClientCard defender) {
        if (!super.onPreBattleBetween(attacker, defender)) {
            return false;
        }
        // Aggressive duelists also trade monsters of equal strength.
        if (style == Style.AGGRESSIVE && defender.isAttack() && attacker.attack == defender.attack) {
            attacker.realPower += 1;
        }
        return true;
    }

    @Override
    public boolean onSelectMonsterSummonOrSet(ClientCard card) {
        if (style == Style.AGGRESSIVE) {
            return card.attack < 1000 && !hasBetterMonsterThan(card.attack);
        }
        return super.onSelectMonsterSummonOrSet(card);
    }

    private boolean drawSpell() {
        if (card.isCode(CardId.CARD_DESTRUCTION)) {
            return bot.hand.size() <= enemy.hand.size() && bot.hand.size() <= 3;
        }
        return defaultCheckWhetherBotCanDraw();
    }

    private boolean handAttack() {
        return !enemy.hand.isEmpty();
    }

    private boolean enemyHasFaceUpMonster() {
        return enemy.getMonsters().stream().anyMatch(ClientCard::isFaceup);
    }

    private boolean lightningVortex() {
        if (!enemyHasFaceUpMonster()) {
            return false;
        }
        ClientCard discard = bot.hand.stream().filter(c -> c != card)
                .min(Comparator.comparingInt(c -> c.attack)).orElse(null);
        if (discard != null) {
            ai.selectCard(discard);
        }
        return enemy.getMonsters().stream().filter(ClientCard::isFaceup).count()
                >= (style == Style.CONTROL ? 1 : 2);
    }

    private boolean nobleman() {
        ClientCard target = enemy.getMonsters().stream().filter(ClientCard::isFacedown).findFirst().orElse(null);
        if (target == null) {
            return false;
        }
        ai.selectCard(target);
        return true;
    }

    private boolean takeBestEnemyMonster() {
        ClientCard best = util.getBestEnemyMonster(true);
        if (best == null) {
            return false;
        }
        if (style != Style.CONTROL && best.attack < util.getBestAttack(bot)) {
            return false;
        }
        ai.selectCard(best);
        return true;
    }

    private boolean enemyController() {
        // Change the battle position of the strongest opponent monster in Attack Position.
        ClientCard best = util.getBestEnemyMonster(true);
        if (best == null || !best.isAttack()) {
            return false;
        }
        ai.selectCard(best);
        return duel.player == 0 && duel.phase == DuelPhase.MAIN1;
    }

    private boolean revive() {
        ClientCard best = Stream.concat(bot.graveyard.stream(), enemy.graveyard.stream())
                .filter(c -> c.isMonster() && !c.hasType(CardType.FUSION))
                .max(Comparator.comparingInt(c -> c.attack)).orElse(null);
        if (best == null || best.attack < 1500) {
            return false;
        }
        ai.selectCard(best);
        return true;
    }

    private boolean swordsOfRevealingLight() {
        return util.getBestAttack(enemy) > util.getBestAttack(bot)
                || enemy.getMonsterCount() > bot.getMonsterCount() + 1;
    }

    private boolean underAttack() {
        if (defaultCheckWhetherCardIsNegated(card)) {
            return false;
        }
        if (card.isCode(CardId.MIRROR_FORCE)) {
            return duel.player == 1 && enemy.getMonsters().stream().filter(ClientCard::isAttack).count()
                    >= (style == Style.AGGRESSIVE ? 1 : 2)
                    || duel.player == 1 && enemy.battlingMonster != null
                    && bot.lifePoints <= enemy.battlingMonster.attack;
        }
        return duel.player == 1 && enemy.battlingMonster != null
                && (card.isCode(CardId.SAKURETSU_ARMOR, CardId.MAGIC_CYLINDER)
                || bot.lifePoints <= enemy.battlingMonster.attack * 2
                || bot.battlingMonster != null
                && bot.battlingMonster.getDefensePower() < enemy.battlingMonster.attack);
    }

    private boolean ringOfDestruction() {
        ClientCard best = util.getBestEnemyMonster(true);
        if (best == null || best.attack >= bot.lifePoints) {
            return false;
        }
        if (best.attack < enemy.lifePoints && duel.player == 0) {
            return false;
        }
        ai.selectCard(best);
        return true;
    }

    /**
     * From the hand or the Pendulum Zones whenever it can; from the Extra Deck only the strongest choice, and only
     * when it beats every monster it may cost.
     */
    private boolean specialSummon() {
        if (card.location != CardLocation.EXTRA) {
            return true;
        }
        int strongestOwn = bot.getMonsters().stream().filter(ClientCard::isFaceup).mapToInt(c -> c.attack).max()
                .orElse(0);
        int strongestChoice = duel.mainPhase.specialSummonableCards.stream()
                .filter(c -> c.location == CardLocation.EXTRA).mapToInt(c -> c.attack).max().orElse(0);
        return card.attack > strongestOwn && card.attack >= strongestChoice;
    }

    private boolean summonOrSet() {
        if (card.isCode(CardId.TSUKUYOMI) && !enemyHasFaceUpMonster()) {
            return false;
        }
        return defaultMonsterSummon();
    }

    private boolean tsukuyomiTarget() {
        // Its effect is mandatory: with no face-up opponent monster it would flip one of ours face-down.
        ClientCard best = util.getBestEnemyMonster(true);
        if (best != null) {
            ai.selectCard(best);
        }
        return true;
    }

    private boolean strongEnoughToShow() {
        // Defensive styles show a monster only when nothing on the other side beats it.
        if (card.isCode(CardId.TSUKUYOMI)) {
            return enemyHasFaceUpMonster();
        }
        if (!defaultMonsterSummon()) {
            return false;
        }
        return card.attack > util.getBestPower(enemy) && card.attack >= 1600;
    }

    private boolean setMonster() {
        return card.level <= 4 && bot.getMonsterCount() < 5;
    }

    private boolean hasBetterMonsterThan(int attack) {
        return bot.hand.stream().anyMatch(c -> c.isMonster() && c.level <= 4 && c.attack > attack);
    }

    private boolean otherCard() {
        if (card.isTrap()) {
            return defaultTrap() && defaultUniqueTrap();
        }
        if (card.hasType(CardType.CONTINUOUS) && card.location == CardLocation.SPELL_ZONE && card.isFaceup()) {
            return defaultDontChainMyself();
        }
        if (card.hasType(CardType.FIELD)) {
            return defaultField();
        }
        if (card.isSpell() && card.hasType(CardType.EQUIP)) {
            return bot.getMonsters().stream().anyMatch(ClientCard::isFaceup);
        }
        return defaultDontChainMyself();
    }

    private boolean setSpellOrTrap() {
        if (style == Style.AGGRESSIVE && !card.isTrap()) {
            return false;
        }
        return defaultSpellSet();
    }
}
