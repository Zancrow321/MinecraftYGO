package io.github.zancrow321.minecraftygo.engine.ai;

import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.duel.Board;
import io.github.zancrow321.minecraftygo.engine.data.CardInfo;
import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage.*;
import io.github.zancrow321.minecraftygo.engine.protocol.Responses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Answers any prompt with a random legal-looking choice. Used to test the engine end to end and as the
 * fallback for {@link DuelistAi}. It favours doing something over passing, so duels actually progress.
 */
public final class RandomResponder implements Responder {
    private final Random random;
    private final List<Integer> announceCandidates;

    public RandomResponder(long seed, CardDatabase cards) {
        this.random = new Random(seed);
        List<Integer> codes = new ArrayList<>();
        for (CardInfo card : cards.all()) {
            codes.add(card.code());
        }
        Collections.sort(codes);
        this.announceCandidates = List.copyOf(codes);
    }

    @Override
    public byte[] respond(Prompt prompt, Board board, int attempt) {
        return respond(prompt, attempt);
    }

    /**
     * @param attempt 0 for the first answer, then 1, 2... after each MSG_RETRY for the same prompt
     */
    public byte[] respond(Prompt prompt, int attempt) {
        return switch (prompt) {
            case SelectIdleCmd p -> idle(p);
            case SelectBattleCmd p -> battle(p);
            case SelectEffectYesNo p -> Responses.yesNo(random.nextInt(4) != 0);
            case SelectYesNo p -> Responses.yesNo(random.nextBoolean());
            case SelectOption p -> Responses.index(random.nextInt(p.options().size()));
            case SelectCard p -> Responses.cards(pick(p.cards().size(), p.min(), p.max()));
            case SelectChain p -> chain(p);
            case SelectPlace p -> Responses.zones(zones(p));
            case SelectPosition p -> Responses.position(randomBit(p.positions()));
            case SelectTribute p -> tribute(p);
            case SortCards p -> Responses.defaultOrder();
            case SelectCounter p -> counters(p);
            case SelectSum p -> sum(p);
            case SelectUnselectCard p -> unselect(p);
            case RockPaperScissors p -> Responses.hand(1 + random.nextInt(3));
            case AnnounceRace p -> Responses.race(randomBits(p.available(), p.count()));
            case AnnounceAttribute p -> Responses.attribute((int) randomBits(p.available(), p.count()));
            // The filter is an RPN program; rather than evaluate it, walk the pool until the core accepts one.
            case AnnounceCard p -> Responses.cardCode(announceCandidates.get(attempt % announceCandidates.size()));
            case AnnounceNumber p -> Responses.index(random.nextInt(p.values().size()));
        };
    }

    private byte[] idle(SelectIdleCmd p) {
        List<byte[]> options = new ArrayList<>();
        addAll(options, Responses.IDLE_SUMMON, p.summonable().size());
        addAll(options, Responses.IDLE_SPECIAL_SUMMON, p.specialSummonable().size());
        addAll(options, Responses.IDLE_SET_MONSTER, p.monsterSettable().size());
        addAll(options, Responses.IDLE_SET_SPELL, p.spellSettable().size());
        addAll(options, Responses.IDLE_ACTIVATE, p.activatable().size());
        // Only occasionally reposition, or bots flip back and forth forever.
        if (!p.repositionable().isEmpty() && random.nextInt(4) == 0) {
            options.add(Responses.command(Responses.IDLE_REPOSITION, random.nextInt(p.repositionable().size())));
        }
        if (!options.isEmpty() && random.nextInt(3) != 0) {
            return options.get(random.nextInt(options.size()));
        }
        if (p.canBattle()) {
            return Responses.command(Responses.IDLE_TO_BATTLE, 0);
        }
        return Responses.command(Responses.IDLE_TO_END, 0);
    }

    private byte[] battle(SelectBattleCmd p) {
        if (!p.attackers().isEmpty() && random.nextInt(5) != 0) {
            return Responses.command(Responses.BATTLE_ATTACK, random.nextInt(p.attackers().size()));
        }
        if (!p.activatable().isEmpty() && random.nextInt(3) == 0) {
            return Responses.command(Responses.BATTLE_ACTIVATE, random.nextInt(p.activatable().size()));
        }
        return Responses.command(p.canMain2() ? Responses.BATTLE_TO_MAIN2 : Responses.BATTLE_TO_END, 0);
    }

    private byte[] chain(SelectChain p) {
        if (p.chains().isEmpty()) {
            return Responses.index(-1);
        }
        if (p.forced() || random.nextInt(3) == 0) {
            return Responses.index(random.nextInt(p.chains().size()));
        }
        return Responses.index(-1);
    }

    private List<Responses.Zone> zones(SelectPlace p) {
        List<Responses.Zone> free = new ArrayList<>();
        for (int bit = 0; bit < 32; bit++) {
            if ((p.blockedZones() & (1 << bit)) != 0) {
                continue;
            }
            int player = bit < 16 ? p.player() : 1 - p.player();
            int local = bit % 16;
            if (local <= 6) {
                free.add(new Responses.Zone(player, 0x04, local));
            } else if (local >= 8) {
                free.add(new Responses.Zone(player, 0x08, local - 8));
            }
        }
        Collections.shuffle(free, random);
        // Prefer own zones, as most effects ask the chooser to place their own card.
        free.sort((a, b) -> Boolean.compare(a.player() != p.player(), b.player() != p.player()));
        return free.subList(0, Math.min(p.count(), free.size()));
    }

    private byte[] tribute(SelectTribute p) {
        List<Integer> order = shuffledIndices(p.cards().size());
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

    private byte[] counters(SelectCounter p) {
        List<Integer> remove = new ArrayList<>();
        int left = p.count();
        for (CounterCandidate c : p.cards()) {
            int take = Math.min(left, c.counters());
            remove.add(take);
            left -= take;
        }
        return Responses.counters(remove);
    }

    private byte[] sum(SelectSum p) {
        int mustTotal = 0;
        for (SumCandidate c : p.mustSelect()) {
            mustTotal += c.param() & 0xFFFF;
        }
        int n = Math.min(p.selectable().size(), 20);
        int bestMask = -1;
        for (int mask = 1; mask < (1 << n); mask++) {
            int count = Integer.bitCount(mask) + p.mustSelect().size();
            if (!p.atLeast() && (count < p.min() || count > p.max())) {
                continue;
            }
            if (p.atLeast() ? reachesWithoutSpare(p, mask) : sumMatches(p, mask, mustTotal)) {
                bestMask = mask;
                break;
            }
        }
        List<Integer> chosen = new ArrayList<>();
        for (int i = 0; bestMask > 0 && i < n; i++) {
            if ((bestMask & (1 << i)) != 0) {
                chosen.add(i);
            }
        }
        return Responses.cards(chosen);
    }

    /**
     * The core's rule for "at least" sums (Ritual tributes): the cards can reach the target, and dropping the
     * smallest one would not still reach it.
     */
    private static boolean reachesWithoutSpare(SelectSum p, int mask) {
        int least = 0;
        int most = 0;
        int smallest = Integer.MAX_VALUE;
        List<SumCandidate> chosen = new ArrayList<>(p.mustSelect());
        for (int i = 0; i < 20 && i < p.selectable().size(); i++) {
            if ((mask & (1 << i)) != 0) {
                chosen.add(p.selectable().get(i));
            }
        }
        for (SumCandidate c : chosen) {
            int primary = c.param() & 0xFFFF;
            int alt = c.param() >>> 16;
            int low = alt != 0 && alt < primary ? alt : primary;
            least += low;
            most += Math.max(primary, alt);
            smallest = Math.min(smallest, low);
        }
        return most >= p.target() && least - smallest < p.target();
    }

    private static boolean sumMatches(SelectSum p, int mask, int mustTotal) {
        // Try every mix of primary/alternate values (cards without an alternate only have one).
        List<int[]> values = new ArrayList<>();
        for (int i = 0; i < 20 && i < p.selectable().size(); i++) {
            if ((mask & (1 << i)) != 0) {
                int param = p.selectable().get(i).param();
                int alt = param >>> 16;
                values.add(alt == 0 ? new int[]{param & 0xFFFF} : new int[]{param & 0xFFFF, alt});
            }
        }
        return reaches(values, 0, mustTotal, p.target(), p.atLeast());
    }

    private static boolean reaches(List<int[]> values, int index, int total, int target, boolean atLeast) {
        if (index == values.size()) {
            return atLeast ? total >= target : total == target;
        }
        for (int v : values.get(index)) {
            if (reaches(values, index + 1, total + v, target, atLeast)) {
                return true;
            }
        }
        return false;
    }

    private byte[] unselect(SelectUnselectCard p) {
        if ((p.finishable() || p.cancelable()) && (p.selectable().isEmpty() || random.nextInt(3) == 0)) {
            return Responses.cancel();
        }
        if (p.selectable().isEmpty()) {
            return Responses.toggleCard(0);
        }
        return Responses.toggleCard(random.nextInt(p.selectable().size()));
    }

    private List<Integer> pick(int size, int min, int max) {
        int upper = Math.min(max, size);
        int count = upper <= min ? Math.min(min, size) : min + random.nextInt(upper - min + 1);
        return shuffledIndices(size).subList(0, count);
    }

    private List<Integer> shuffledIndices(int size) {
        List<Integer> indices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, random);
        return indices;
    }

    private int randomBit(int mask) {
        List<Integer> bits = new ArrayList<>();
        for (int bit = 0; bit < 32; bit++) {
            if ((mask & (1 << bit)) != 0) {
                bits.add(1 << bit);
            }
        }
        return bits.isEmpty() ? 0 : bits.get(random.nextInt(bits.size()));
    }

    private long randomBits(long mask, int count) {
        List<Long> bits = new ArrayList<>();
        for (int bit = 0; bit < 64; bit++) {
            if ((mask & (1L << bit)) != 0) {
                bits.add(1L << bit);
            }
        }
        Collections.shuffle(bits, random);
        long out = 0;
        for (int i = 0; i < count && i < bits.size(); i++) {
            out |= bits.get(i);
        }
        return out;
    }

    private static void addAll(List<byte[]> options, int type, int count) {
        for (int i = 0; i < count; i++) {
            options.add(Responses.command(type, i));
        }
    }
}
