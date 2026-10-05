package io.github.zancrow321.minecraftygo.engine.ai;

import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.data.CardInfo;
import io.github.zancrow321.minecraftygo.engine.duel.Board;
import io.github.zancrow321.minecraftygo.engine.protocol.CardRef;
import io.github.zancrow321.minecraftygo.engine.protocol.CardState;
import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage.*;
import io.github.zancrow321.minecraftygo.engine.protocol.Responses;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * A rule-of-thumb duelist in the spirit of WindBot's executors: it plays its strongest monster (setting it when the
 * opponent has something bigger), sets traps, uses its spells and effects, attacks only when the attack wins or
 * goes direct, and springs its traps on the opponent's turn. It only knows what its seat may see. Whatever it has
 * no rule for, or a choice the engine rejects, falls back to {@link RandomResponder}.
 */
public final class DuelistAi implements Responder {
    /** Idle decisions in one turn before it stops trying things and moves on, so it can't loop. */
    private static final int MAX_IDLE_ACTIONS = 12;
    /** Each effect is tried at most this often per turn. */
    private static final int MAX_ACTIVATIONS = 2;

    private final CardDatabase cards;
    private final RandomResponder fallback;
    private int turn = -1;
    private int idleActions;
    private final Map<String, Integer> activations = new HashMap<>();
    /** The ATK of the monster just declared as an attacker, while its target is chosen. */
    private int attackingWith = -1;

    public DuelistAi(long seed, CardDatabase cards) {
        this.cards = cards;
        this.fallback = new RandomResponder(seed, cards);
    }

    @Override
    public byte[] respond(Prompt prompt, Board board, int attempt) {
        if (attempt > 0) {
            attackingWith = -1;
            return fallback.respond(prompt, attempt - 1);
        }
        if (board.turn() != turn) {
            turn = board.turn();
            idleActions = 0;
            activations.clear();
        }
        int me = prompt.player();
        return switch (prompt) {
            case SelectIdleCmd p -> idle(p, board, me);
            case SelectBattleCmd p -> battle(p, board, me);
            case SelectCard p -> selectCard(p, board, me);
            case SelectChain p -> chain(p, board, me);
            case SelectEffectYesNo p -> Responses.yesNo(true);
            case SelectYesNo p -> Responses.yesNo(true);
            case SelectPosition p -> position(p, board, me);
            case SelectTribute p -> tribute(p, board, me);
            default -> fallback.respond(prompt, 0);
        };
    }

    // --- Main phase ----------------------------------------------------------------------------------------------

    private byte[] idle(SelectIdleCmd p, Board board, int me) {
        if (++idleActions > MAX_IDLE_ACTIONS) {
            return endMain(p, board, me);
        }
        // Spells and effects first: they clear the way or power up what comes next.
        for (int i = 0; i < p.activatable().size(); i++) {
            Activatable a = p.activatable().get(i);
            if (worthActivating(a.card(), board, me) && tryActivation(a.card())) {
                return Responses.command(Responses.IDLE_ACTIVATE, i);
            }
        }
        for (int i = 0; i < p.specialSummonable().size(); i++) {
            if (tryActivation(p.specialSummonable().get(i))) {
                return Responses.command(Responses.IDLE_SPECIAL_SUMMON, i);
            }
        }
        int threat = strongestAttack(board.side(1 - me).monsters());
        int summon = best(p.summonable(), this::attackOf);
        if (summon >= 0 && (attackOf(p.summonable().get(summon)) >= threat || p.monsterSettable().isEmpty())) {
            return Responses.command(Responses.IDLE_SUMMON, summon);
        }
        int set = best(p.monsterSettable(), this::defenseOf);
        if (set >= 0) {
            return Responses.command(Responses.IDLE_SET_MONSTER, set);
        }
        // Traps work best face-down; keep a couple of zones free for spells.
        for (int i = 0; i < p.spellSettable().size(); i++) {
            CardInfo card = info(p.spellSettable().get(i));
            if (card != null && card.is(TYPE_TRAP) && countSpellsTraps(board, me) < 4) {
                return Responses.command(Responses.IDLE_SET_SPELL, i);
            }
        }
        return endMain(p, board, me);
    }

    private byte[] endMain(SelectIdleCmd p, Board board, int me) {
        if (p.canBattle() && canWinAnyAttack(board, me)) {
            return Responses.command(Responses.IDLE_TO_BATTLE, 0);
        }
        return Responses.command(p.canEnd() ? Responses.IDLE_TO_END : Responses.IDLE_TO_BATTLE, 0);
    }

    /** Equip and power-up spells only help with a monster out; everything else is played when it can be. */
    private boolean worthActivating(CardRef ref, Board board, int me) {
        CardInfo card = info(ref);
        if (card == null) {
            return true;
        }
        if (card.is(TYPE_SPELL) && (card.data().type() & TYPE_EQUIP) != 0) {
            return board.side(me).monsters().stream().anyMatch(c -> c != null && faceUp(c));
        }
        return true;
    }

    private boolean tryActivation(CardRef ref) {
        String key = ref.code() + "@" + ref.loc();
        int used = activations.merge(key, 1, Integer::sum);
        return used <= MAX_ACTIVATIONS;
    }

    // --- Battle --------------------------------------------------------------------------------------------------

    private byte[] battle(SelectBattleCmd p, Board board, int me) {
        List<CardState> mine = board.side(me).monsters();
        List<CardState> theirs = board.side(1 - me).monsters();
        boolean noBlockers = theirs.stream().allMatch(c -> c == null);
        int bestAttacker = -1;
        int bestAttack = -1;
        for (int i = 0; i < p.attackers().size(); i++) {
            Attacker attacker = p.attackers().get(i);
            int atk = monsterAt(mine, attacker.card()) == null ? 0 : monsterAt(mine, attacker.card()).attack();
            boolean wins = attacker.canAttackDirectly() || noBlockers || weakestDefender(theirs) < atk;
            if (wins && atk > bestAttack) {
                bestAttack = atk;
                bestAttacker = i;
            }
        }
        if (bestAttacker >= 0) {
            attackingWith = bestAttack;
            return Responses.command(Responses.BATTLE_ATTACK, bestAttacker);
        }
        return Responses.command(p.canMain2() ? Responses.BATTLE_TO_MAIN2 : Responses.BATTLE_TO_END, 0);
    }

    private boolean canWinAnyAttack(Board board, int me) {
        List<CardState> theirs = board.side(1 - me).monsters();
        int weakest = theirs.stream().allMatch(c -> c == null) ? -1 : weakestDefender(theirs);
        return board.side(me).monsters().stream()
                .anyMatch(c -> c != null && (c.position() & POS_FACEUP_ATTACK) != 0 && c.attack() > weakest);
    }

    /** The lowest stat an attack has to beat: ATK of attack-position monsters, DEF of face-up defenders. */
    private static int weakestDefender(List<CardState> monsters) {
        int weakest = Integer.MAX_VALUE;
        for (CardState c : monsters) {
            if (c != null) {
                weakest = Math.min(weakest, defendingStat(c));
            }
        }
        return weakest;
    }

    /** Face-down monsters are guessed at 1500, a typical DEF of the era. */
    private static int defendingStat(CardState c) {
        if ((c.position() & POS_FACEDOWN) != 0) {
            return 1500;
        }
        return (c.position() & POS_ATTACK) != 0 ? c.attack() : c.defense();
    }

    // --- Card choices --------------------------------------------------------------------------------------------

    private byte[] selectCard(SelectCard p, Board board, int me) {
        boolean opponents = p.cards().stream().allMatch(c -> c.loc().controller() != me);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < p.cards().size(); i++) {
            order.add(i);
        }
        if (attackingWith >= 0 && opponents) {
            // Choosing an attack target: the strongest monster the attacker still beats.
            int atk = attackingWith;
            attackingWith = -1;
            List<CardState> theirs = board.side(1 - me).monsters();
            order.sort(Comparator.comparingInt((Integer i) -> {
                CardState c = monsterAt(theirs, p.cards().get(i));
                int stat = c == null ? 0 : defendingStat(c);
                return stat < atk ? -stat : Integer.MAX_VALUE - stat;
            }));
        } else if (opponents) {
            // Destroying or stealing: the opponent's biggest threat first.
            order.sort(Comparator.comparingInt((Integer i) -> -value(p.cards().get(i), board)));
        } else {
            // Costs and discards from our own side: the least useful card first.
            order.sort(Comparator.comparingInt((Integer i) -> value(p.cards().get(i), board)));
        }
        int count = Math.max(p.min(), Math.min(1, p.max()));
        return Responses.cards(order.subList(0, Math.min(count, order.size())));
    }

    private byte[] chain(SelectChain p, Board board, int me) {
        if (p.chains().isEmpty()) {
            return Responses.index(-1);
        }
        if (p.forced()) {
            return Responses.index(0);
        }
        // Spring traps and quick effects on the opponent's turn; on our own turn, don't waste them.
        boolean theirTurn = board.turnPlayer() != me;
        for (int i = 0; i < p.chains().size(); i++) {
            if (theirTurn && tryActivation(p.chains().get(i).card())) {
                return Responses.index(i);
            }
        }
        return Responses.index(-1);
    }

    private byte[] position(SelectPosition p, Board board, int me) {
        CardInfo card = cards.card(p.code());
        int atk = card == null ? 0 : card.data().attack();
        int def = card == null ? 0 : card.data().defense();
        int threat = strongestAttack(board.side(1 - me).monsters());
        int wanted = atk >= threat || atk >= def ? POS_FACEUP_ATTACK : POS_FACEUP_DEFENSE;
        if ((p.positions() & wanted) == 0) {
            wanted = (p.positions() & POS_FACEDOWN_DEFENSE) != 0 && wanted == POS_FACEUP_DEFENSE
                    ? POS_FACEDOWN_DEFENSE : Integer.lowestOneBit(p.positions());
        }
        return Responses.position(wanted);
    }

    private byte[] tribute(SelectTribute p, Board board, int me) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < p.cards().size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt((Integer i) -> value(p.cards().get(i).card(), board)));
        List<Integer> chosen = new ArrayList<>();
        int total = 0;
        for (int i : order) {
            if (total >= p.min() || chosen.size() >= p.max()) {
                break;
            }
            chosen.add(i);
            total += p.cards().get(i).releaseParam();
        }
        return Responses.cards(chosen);
    }

    // --- Helpers -------------------------------------------------------------------------------------------------

    /** Roughly how much a card is worth keeping: a monster's ATK on the field, a card's ATK or 1000 elsewhere. */
    private int value(CardRef ref, Board board) {
        if (ref.loc().location() == LOCATION_MZONE) {
            CardState c = monsterAt(board.side(ref.loc().controller()).monsters(), ref);
            if (c != null && c.code() != 0) {
                return Math.max(c.attack(), c.defense());
            }
            return 1500;
        }
        CardInfo card = info(ref);
        if (card == null) {
            return 1000;
        }
        return card.is(TYPE_MONSTER) ? Math.max(card.data().attack(), card.data().defense()) : 1200;
    }

    private int attackOf(CardRef ref) {
        CardInfo card = info(ref);
        return card == null ? 0 : card.data().attack();
    }

    private int defenseOf(CardRef ref) {
        CardInfo card = info(ref);
        return card == null ? 0 : card.data().defense();
    }

    private CardInfo info(CardRef ref) {
        return ref.code() == 0 ? null : cards.card(ref.code());
    }

    private static int best(List<CardRef> refs, java.util.function.ToIntFunction<CardRef> score) {
        int best = -1;
        for (int i = 0; i < refs.size(); i++) {
            if (best < 0 || score.applyAsInt(refs.get(i)) > score.applyAsInt(refs.get(best))) {
                best = i;
            }
        }
        return best;
    }

    private static int strongestAttack(List<CardState> monsters) {
        int strongest = 0;
        for (CardState c : monsters) {
            if (c != null && faceUp(c) && (c.position() & POS_ATTACK) != 0) {
                strongest = Math.max(strongest, c.attack());
            }
        }
        return strongest;
    }

    private static int countSpellsTraps(Board board, int me) {
        int count = 0;
        List<CardState> spells = board.side(me).spells();
        for (int i = 0; i < Math.min(5, spells.size()); i++) {
            if (spells.get(i) != null) {
                count++;
            }
        }
        return count;
    }

    private static boolean faceUp(CardState c) {
        return (c.position() & POS_FACEUP) != 0;
    }

    private static CardState monsterAt(List<CardState> zones, CardRef ref) {
        int seq = ref.loc().sequence();
        return seq >= 0 && seq < zones.size() ? zones.get(seq) : null;
    }
}
