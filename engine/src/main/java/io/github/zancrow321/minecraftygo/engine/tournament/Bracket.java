package io.github.zancrow321.minecraftygo.engine.tournament;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The pairings and results of one tournament, without anything about who the entrants are: they are numbered
 * {@code 0..n-1} in seed order. Elimination brackets are built up front as matches that take their duelists from
 * earlier matches (the winner of one, the loser of another); Swiss and round robin rounds are paired one at a time
 * once the round before is over. Plain fields, so the whole state can be saved as JSON.
 */
public final class Bracket {
    /** A match side or result that no one fills: a bye, or the winner of a match between two byes. */
    public static final int NONE = -1;
    /** A side or result that isn't known yet. */
    public static final int OPEN = -2;
    /** The result of a drawn match (Swiss and round robin only). */
    public static final int DRAW = -3;
    /** Swiss points for a match win and a draw. */
    public static final int WIN_POINTS = 3;
    public static final int DRAW_POINTS = 1;
    /** Backtracking steps the Swiss pairing may take before it settles for a rematch. */
    private static final int PAIRING_BUDGET = 200_000;

    public enum Format {
        SINGLE("single", "Single elimination"),
        DOUBLE("double", "Double elimination"),
        SWISS("swiss", "Swiss"),
        ROUND_ROBIN("roundrobin", "Round robin");

        public final String id;
        public final String displayName;

        Format(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        /** @return the format for an id such as {@code "swiss"} (a few other spellings work too), or {@code null} */
        public static Format parse(String text) {
            String s = text.strip().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
            return switch (s) {
                case "single", "singleelimination", "ko", "knockout", "elimination" -> SINGLE;
                case "double", "doubleelimination", "doubleko" -> DOUBLE;
                case "swiss" -> SWISS;
                case "roundrobin", "rr", "league", "all" -> ROUND_ROBIN;
                default -> null;
            };
        }
    }

    /** Where a match side comes from. */
    public static final class Source {
        /** 'E' an entrant, 'W' the winner of a match, 'L' its loser, 'N' no one */
        public char kind;
        public int ref;

        Source() {
        }

        Source(char kind, int ref) {
            this.kind = kind;
            this.ref = ref;
        }

        static Source entrant(int e) {
            return new Source(e < 0 ? 'N' : 'E', e);
        }
    }

    public static final class Match {
        public int id;
        /**
         * "W" winners' bracket (and single elimination), "L" losers' bracket, "F" grand final, "F2" its reset,
         * "3" third place, "S" Swiss, "R" round robin, "T" Swiss top cut
         */
        public String stage;
        public int round;
        /** How many matches this round of the stage has. */
        public int roundSize;
        public Source[] from = new Source[2];
        public int a = OPEN;
        public int b = OPEN;
        public int winsA;
        public int winsB;
        public int draws;
        /** The winning entrant, {@link #DRAW}, {@link #NONE} or {@link #OPEN} while it's undecided. */
        public int winner = OPEN;
        public int loser = OPEN;
        /** Decided without being played: a bye, a no-show or someone who left. */
        public boolean walkover;
        /** The place the winner (and loser) finishes in, if this match settles it; 0 otherwise. */
        public int winnerPlace;
        public int loserPlace;
        /** A grand final reset: only played if the losers' bracket champion won the first final. */
        public boolean reset;

        public boolean decided() {
            return winner != OPEN;
        }

        /** Both duelists are known, there are two of them and the match isn't over. */
        public boolean playable() {
            return a >= 0 && b >= 0 && !decided();
        }

        public int games() {
            return winsA + winsB + draws;
        }
    }

    public static final class Standing {
        public int entrant;
        public int points;
        public int wins;
        public int losses;
        public int draws;
        public int byes;
        public int gameWins;
        public int games;
        /** Opponents' match win percentage and own game win percentage, each at least a third (0..1). */
        public double omw;
        public double gw;
        public boolean dropped;
    }

    public record Placement(int entrant, int place) {
    }

    public Format format;
    public int entrants;
    public int bestOf = 1;
    /** Swiss rounds before the cut; 0 picks enough rounds for one undefeated duelist. */
    public int swissRounds;
    /** How many duelists go from Swiss to a single elimination top cut; 0 or 1 for none. */
    public int topCut;
    public boolean thirdPlace;
    public boolean grandFinalReset = true;
    public List<Match> matches = new ArrayList<>();
    public boolean[] dropped;
    public int round;
    public boolean cutStarted;

    public Bracket() {
    }

    /**
     * @param entrants how many duelists, numbered in seed order (seed 1 is entrant 0)
     */
    public static Bracket create(Format format, int entrants, int bestOf, int swissRounds, int topCut,
                                 boolean thirdPlace, boolean grandFinalReset) {
        if (entrants < 2) {
            throw new IllegalArgumentException("A tournament needs at least two duelists");
        }
        Bracket bracket = new Bracket();
        bracket.format = format;
        bracket.entrants = entrants;
        bracket.bestOf = Math.max(1, bestOf | 1);
        bracket.swissRounds = swissRounds;
        bracket.topCut = topCut;
        bracket.thirdPlace = thirdPlace;
        bracket.grandFinalReset = grandFinalReset;
        bracket.dropped = new boolean[entrants];
        List<Source> seeds = new ArrayList<>();
        for (int e = 0; e < entrants; e++) {
            seeds.add(Source.entrant(e));
        }
        switch (format) {
            case SINGLE -> bracket.single(seeds, "W", thirdPlace);
            case DOUBLE -> bracket.doubleElimination(seeds);
            case SWISS, ROUND_ROBIN -> bracket.nextRound();
        }
        bracket.advance();
        return bracket;
    }

    /** Swiss rounds this bracket plays (the configured count, or enough for one undefeated duelist). */
    public int swissRoundCount() {
        if (swissRounds > 0) {
            return swissRounds;
        }
        int rounds = 0;
        while ((1 << rounds) < entrants) {
            rounds++;
        }
        return Math.max(1, rounds);
    }

    /** Round robin rounds: everyone meets everyone once. */
    public int roundRobinCount() {
        return entrants % 2 == 0 ? entrants - 1 : entrants;
    }

    public Match match(int id) {
        return matches.get(id);
    }

    /** The matches that can be played now, in order. */
    public List<Match> playable() {
        return matches.stream().filter(Match::playable).toList();
    }

    public boolean finished() {
        return !matches.isEmpty() && matches.stream().allMatch(Match::decided) && !moreRounds();
    }

    /** Records one game: {@code side} 0 for a win of {@code a}, 1 for {@code b}, 2 for a draw. */
    public void reportGame(int id, int side) {
        Match m = match(id);
        if (!m.playable()) {
            throw new IllegalStateException("Match " + id + " isn't being played");
        }
        switch (side) {
            case 0 -> m.winsA++;
            case 1 -> m.winsB++;
            default -> m.draws++;
        }
        int needed = bestOf / 2 + 1;
        if (m.winsA >= needed) {
            decide(m, m.a, m.b);
        } else if (m.winsB >= needed) {
            decide(m, m.b, m.a);
        } else if (m.games() >= bestOf) {
            if (m.winsA != m.winsB) {
                decide(m, m.winsA > m.winsB ? m.a : m.b, m.winsA > m.winsB ? m.b : m.a);
            } else if (canDraw(m)) {
                decide(m, DRAW, DRAW);
            } else if (m.draws > bestOf + 2) {
                // Elimination needs a winner; after this many drawn games the higher seed goes on.
                decide(m, m.a, m.b);
            }
        }
        advance();
    }

    /** Ends a match without playing (on): {@code side} 0 or 1 wins it, -1 means both lose. */
    public void award(int id, int side) {
        Match m = match(id);
        if (m.decided()) {
            return;
        }
        m.walkover = true;
        if (side < 0) {
            decide(m, NONE, NONE);
        } else {
            decide(m, side == 0 ? m.a : m.b, side == 0 ? m.b : m.a);
        }
        advance();
    }

    /** An entrant leaves: they lose the match they are in and every match they would have played. */
    public void drop(int entrant) {
        dropped[entrant] = true;
        advance();
    }

    /** Whether {@code m} can end in a draw: only in Swiss and round robin rounds. */
    private static boolean canDraw(Match m) {
        return m.stage.equals("S") || m.stage.equals("R");
    }

    private void decide(Match m, int winner, int loser) {
        m.winner = winner;
        m.loser = loser;
    }

    /** Fills in sides whose source is decided, settles byes and pairs the next round when one is due. */
    public void advance() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Match m : matches) {
                if (m.decided()) {
                    continue;
                }
                if (m.a == OPEN) {
                    m.a = resolve(m.from[0]);
                    changed |= m.a != OPEN;
                }
                if (m.b == OPEN) {
                    m.b = resolve(m.from[1]);
                    changed |= m.b != OPEN;
                }
                if (m.a == OPEN || m.b == OPEN) {
                    continue;
                }
                if (m.reset) {
                    Match first = match(m.from[0].ref);
                    if (first.winner == first.a) {
                        // The winners' bracket champion won the final: no second final.
                        m.walkover = true;
                        decide(m, first.winner, first.loser);
                        changed = true;
                        continue;
                    }
                }
                boolean outA = m.a < 0 || dropped[m.a];
                boolean outB = m.b < 0 || dropped[m.b];
                if (outA || outB) {
                    m.walkover = true;
                    if (outA && outB) {
                        decide(m, NONE, NONE);
                    } else {
                        decide(m, outA ? m.b : m.a, outA ? m.a : m.b);
                    }
                    changed = true;
                }
            }
            if (!changed && moreRounds() && matches.stream().allMatch(Match::decided)) {
                nextRound();
                changed = true;
            }
        }
    }

    private int resolve(Source source) {
        return switch (source.kind) {
            case 'E' -> source.ref;
            case 'N' -> NONE;
            default -> {
                Match from = match(source.ref);
                if (!from.decided()) {
                    yield OPEN;
                }
                int e = source.kind == 'W' ? from.winner : from.loser;
                yield e == DRAW ? NONE : e;
            }
        };
    }

    /** Whether a Swiss, round robin or top cut round is still to be paired. */
    private boolean moreRounds() {
        return switch (format) {
            case SWISS -> round < swissRoundCount() || cutDue();
            case ROUND_ROBIN -> round < roundRobinCount();
            default -> false;
        };
    }

    private boolean cutDue() {
        return !cutStarted && topCut >= 2 && active().size() >= 2;
    }

    private List<Integer> active() {
        List<Integer> out = new ArrayList<>();
        for (int e = 0; e < entrants; e++) {
            if (!dropped[e]) {
                out.add(e);
            }
        }
        return out;
    }

    private void nextRound() {
        if (format == Format.ROUND_ROBIN) {
            roundRobinRound();
        } else if (round < swissRoundCount()) {
            swissRound();
        } else {
            cutStarted = true;
            List<Source> seeds = new ArrayList<>();
            for (Standing s : standings()) {
                if (!s.dropped && seeds.size() < topCut) {
                    seeds.add(Source.entrant(s.entrant));
                }
            }
            single(seeds, "T", thirdPlace);
        }
    }

    private Match add(String stage, int round, int roundSize, Source a, Source b) {
        Match m = new Match();
        m.id = matches.size();
        m.stage = stage;
        m.round = round;
        m.roundSize = roundSize;
        m.from[0] = a;
        m.from[1] = b;
        matches.add(m);
        return m;
    }

    /** The bracket position of each seed (0-based) so that the best seeds meet last, e.g. 0,7,3,4,1,6,2,5. */
    static int[] seedOrder(int size) {
        int[] order = {0};
        while (order.length < size) {
            int n = order.length * 2;
            int[] next = new int[n];
            for (int i = 0; i < order.length; i++) {
                next[2 * i] = order[i];
                next[2 * i + 1] = n - 1 - order[i];
            }
            order = next;
        }
        return order;
    }

    private static int bracketSize(int n) {
        int size = 1;
        while (size < n) {
            size *= 2;
        }
        return Math.max(2, size);
    }

    /**
     * A single elimination bracket over {@code seeds} (best first), byes for the top seeds.
     *
     * @return the rounds, first round first
     */
    private List<List<Match>> single(List<Source> seeds, String stage, boolean third) {
        int size = bracketSize(seeds.size());
        int[] order = seedOrder(size);
        List<List<Match>> rounds = new ArrayList<>();
        List<Match> current = new ArrayList<>();
        for (int i = 0; i < size; i += 2) {
            current.add(add(stage, 1, size / 2, seed(seeds, order[i]), seed(seeds, order[i + 1])));
        }
        rounds.add(current);
        while (current.size() > 1) {
            List<Match> next = new ArrayList<>();
            for (int i = 0; i < current.size(); i += 2) {
                next.add(add(stage, rounds.size() + 1, current.size() / 2,
                        new Source('W', current.get(i).id), new Source('W', current.get(i + 1).id)));
            }
            rounds.add(next);
            current = next;
        }
        Match fin = current.get(0);
        if (!stage.equals("W") || format == Format.SINGLE) {
            fin.winnerPlace = 1;
            fin.loserPlace = 2;
            int place = 3;
            int last = rounds.size() - 2;
            if (third && rounds.size() >= 2) {
                List<Match> semis = rounds.get(last);
                Match bronze = add("3", rounds.size(), 1, new Source('L', semis.get(0).id),
                        new Source('L', semis.get(1).id));
                bronze.winnerPlace = 3;
                bronze.loserPlace = 4;
                place = 5;
                last--;
            }
            for (int r = last; r >= 0; r--) {
                for (Match m : rounds.get(r)) {
                    m.loserPlace = place;
                }
                place += rounds.get(r).size();
            }
        }
        return rounds;
    }

    private static Source seed(List<Source> seeds, int index) {
        return index < seeds.size() ? seeds.get(index) : Source.entrant(NONE);
    }

    /**
     * A winners' bracket, a losers' bracket that each first loss drops into, and a grand final between their
     * champions (played twice if the losers' champion wins the first one and {@link #grandFinalReset} is on).
     */
    private void doubleElimination(List<Source> seeds) {
        List<List<Match>> winners = single(seeds, "W", false);
        Source champion;
        List<List<Match>> losers = new ArrayList<>();
        if (winners.size() == 1) {
            champion = new Source('L', winners.get(0).get(0).id);
        } else {
            List<Match> first = winners.get(0);
            List<Match> lr = new ArrayList<>();
            for (int i = 0; i < first.size(); i += 2) {
                lr.add(add("L", 1, first.size() / 2, new Source('L', first.get(i).id),
                        new Source('L', first.get(i + 1).id)));
            }
            losers.add(lr);
            List<Match> previous = lr;
            for (int r = 1; r < winners.size(); r++) {
                List<Match> dropping = winners.get(r);
                List<Match> drop = new ArrayList<>();
                for (int i = 0; i < previous.size(); i++) {
                    // Alternate the order the losers come in, so duelists don't meet again straight away.
                    int from = r % 2 == 1 ? dropping.size() - 1 - i : i;
                    drop.add(add("L", losers.size() + 1, previous.size(), new Source('W', previous.get(i).id),
                            new Source('L', dropping.get(from).id)));
                }
                losers.add(drop);
                previous = drop;
                if (r < winners.size() - 1) {
                    List<Match> merge = new ArrayList<>();
                    for (int i = 0; i < previous.size(); i += 2) {
                        merge.add(add("L", losers.size() + 1, previous.size() / 2,
                                new Source('W', previous.get(i).id), new Source('W', previous.get(i + 1).id)));
                    }
                    losers.add(merge);
                    previous = merge;
                }
            }
            champion = new Source('W', previous.get(0).id);
        }
        Match wbFinal = winners.get(winners.size() - 1).get(0);
        Match fin = add("F", 1, 1, new Source('W', wbFinal.id), champion);
        if (grandFinalReset) {
            Match again = add("F2", 2, 1, new Source('W', fin.id), new Source('L', fin.id));
            again.reset = true;
            again.winnerPlace = 1;
            again.loserPlace = 2;
            // The first final settles both places when the winners' champion wins it; see placements().
        } else {
            fin.winnerPlace = 1;
            fin.loserPlace = 2;
        }
        int place = 3;
        for (int r = losers.size() - 1; r >= 0; r--) {
            for (Match m : losers.get(r)) {
                m.loserPlace = place;
            }
            place += losers.get(r).size();
        }
    }

    private void swissRound() {
        round++;
        List<Standing> table = standings();
        List<Integer> order = new ArrayList<>();
        for (Standing s : table) {
            if (!s.dropped) {
                order.add(s.entrant);
            }
        }
        Set<Long> met = new HashSet<>();
        Map<Integer, Integer> byes = new HashMap<>();
        for (Match m : matches) {
            if (m.stage.equals("S")) {
                if (m.a >= 0 && m.b >= 0) {
                    met.add(key(m.a, m.b));
                } else {
                    byes.merge(m.a >= 0 ? m.a : m.b, 1, Integer::sum);
                }
            }
        }
        int bye = NONE;
        if (order.size() % 2 == 1) {
            // The lowest ranked duelist who hasn't had a bye yet sits this round out (and wins it).
            for (int i = order.size() - 1; i >= 0; i--) {
                if (!byes.containsKey(order.get(i))) {
                    bye = order.remove(i);
                    break;
                }
            }
            if (bye == NONE) {
                bye = order.remove(order.size() - 1);
            }
        }
        int[] budget = {PAIRING_BUDGET};
        List<int[]> pairs = pair(order, met, budget);
        if (pairs == null) {
            pairs = new ArrayList<>();
            for (int i = 0; i + 1 < order.size(); i += 2) {
                pairs.add(new int[]{order.get(i), order.get(i + 1)});
            }
        }
        int size = pairs.size() + (bye == NONE ? 0 : 1);
        for (int[] p : pairs) {
            add("S", round, size, Source.entrant(p[0]), Source.entrant(p[1]));
        }
        if (bye != NONE) {
            add("S", round, size, Source.entrant(bye), Source.entrant(NONE));
        }
    }

    private static long key(int a, int b) {
        return ((long) Math.min(a, b) << 32) | Math.max(a, b);
    }

    /** Pairs the duelists top-down, each with the best ranked one they haven't met, backtracking where needed. */
    private static List<int[]> pair(List<Integer> order, Set<Long> met, int[] budget) {
        if (order.isEmpty()) {
            return new ArrayList<>();
        }
        if (--budget[0] < 0) {
            return null;
        }
        int first = order.get(0);
        for (int i = 1; i < order.size(); i++) {
            int other = order.get(i);
            if (met.contains(key(first, other))) {
                continue;
            }
            List<Integer> rest = new ArrayList<>(order);
            rest.remove(i);
            rest.remove(0);
            List<int[]> tail = pair(rest, met, budget);
            if (tail != null) {
                tail.add(0, new int[]{first, other});
                return tail;
            }
            if (budget[0] < 0) {
                return null;
            }
        }
        return null;
    }

    /** The next round of the circle method: entrant 0 stays put, everyone else turns one place each round. */
    private void roundRobinRound() {
        round++;
        int n = entrants % 2 == 0 ? entrants : entrants + 1;
        int[] ring = new int[n];
        for (int i = 0; i < n; i++) {
            ring[i] = i < entrants ? i : NONE;
        }
        // Turn everyone but the first round - 1 places.
        int[] turned = Arrays.copyOf(ring, n);
        for (int i = 1; i < n; i++) {
            turned[i] = ring[1 + Math.floorMod(i - 1 - (round - 1), n - 1)];
        }
        for (int i = 0; i < n / 2; i++) {
            int a = turned[i];
            int b = turned[n - 1 - i];
            if (a == NONE) {
                int t = a;
                a = b;
                b = t;
            }
            add("R", round, n / 2, Source.entrant(a), Source.entrant(b));
        }
    }

    /**
     * The Swiss or round robin table, best first: by points, then opponents' match win percentage, game win
     * percentage and seed. Elimination formats list wins and losses in seed order.
     */
    public List<Standing> standings() {
        Standing[] table = new Standing[entrants];
        Map<Integer, List<Integer>> opponents = new HashMap<>();
        for (int e = 0; e < entrants; e++) {
            table[e] = new Standing();
            table[e].entrant = e;
            table[e].dropped = dropped[e];
            opponents.put(e, new ArrayList<>());
        }
        for (Match m : matches) {
            if (!m.decided() || m.stage.equals("T") && format == Format.SWISS) {
                continue;
            }
            for (int side = 0; side < 2; side++) {
                int me = side == 0 ? m.a : m.b;
                int them = side == 0 ? m.b : m.a;
                if (me < 0) {
                    continue;
                }
                Standing s = table[me];
                if (them < 0) {
                    if (m.winner == me) {
                        s.byes++;
                        s.wins++;
                        s.points += WIN_POINTS;
                    }
                    continue;
                }
                opponents.get(me).add(them);
                s.gameWins += side == 0 ? m.winsA : m.winsB;
                s.games += m.games();
                if (m.winner == DRAW) {
                    s.draws++;
                    s.points += DRAW_POINTS;
                } else if (m.winner == me) {
                    s.wins++;
                    s.points += WIN_POINTS;
                } else {
                    s.losses++;
                }
            }
        }
        for (Standing s : table) {
            s.gw = s.games == 0 ? 0 : Math.max(1 / 3.0, s.gameWins / (double) s.games);
        }
        for (Standing s : table) {
            List<Integer> opp = opponents.get(s.entrant);
            double sum = 0;
            for (int o : opp) {
                Standing t = table[o];
                int played = t.wins + t.losses + t.draws - t.byes;
                double mw = played == 0 ? 0 : (t.points - WIN_POINTS * t.byes) / (double) (WIN_POINTS * played);
                sum += Math.max(1 / 3.0, mw);
            }
            s.omw = opp.isEmpty() ? 0 : sum / opp.size();
        }
        List<Standing> out = new ArrayList<>(Arrays.asList(table));
        if (format == Format.SWISS || format == Format.ROUND_ROBIN) {
            out.sort(Comparator.comparingInt((Standing s) -> -s.points)
                    .thenComparingDouble(s -> -s.omw)
                    .thenComparingDouble(s -> -s.gw)
                    .thenComparingInt(s -> s.entrant));
        }
        return out;
    }

    /**
     * Final places, best first. Elimination places come from the matches each duelist went out in (duelists who
     * went out in the same round share a place); Swiss and round robin from the table, with the top cut ahead.
     */
    public List<Placement> placements() {
        int[] place = new int[entrants];
        Arrays.fill(place, 0);
        if (format == Format.SINGLE || format == Format.DOUBLE || cutStarted) {
            for (Match m : matches) {
                if (!m.decided()) {
                    continue;
                }
                if (m.stage.equals("F") && grandFinalReset && m.winner >= 0) {
                    Match again = matches.get(m.id + 1);
                    if (again.walkover && again.winner == m.winner) {
                        place[m.winner] = 1;
                        if (m.loser >= 0) {
                            place[m.loser] = 2;
                        }
                    }
                    continue;
                }
                if (m.winnerPlace > 0 && m.winner >= 0) {
                    place[m.winner] = m.winnerPlace;
                }
                if (m.loserPlace > 0 && m.loser >= 0) {
                    place[m.loser] = m.loserPlace;
                }
            }
        }
        List<Placement> out = new ArrayList<>();
        if (format == Format.SWISS || format == Format.ROUND_ROBIN) {
            int next = 1;
            for (int e = 0; e < entrants; e++) {
                if (place[e] > 0) {
                    next = Math.max(next, place[e] + 1);
                }
            }
            if (cutStarted) {
                next = Math.max(next, topCut + 1);
            }
            for (Standing s : standings()) {
                if (place[s.entrant] == 0) {
                    place[s.entrant] = next++;
                }
            }
        } else {
            int last = 0;
            for (int p : place) {
                last = Math.max(last, p);
            }
            for (int e = 0; e < entrants; e++) {
                if (place[e] == 0) {
                    // Someone who left before they could be placed.
                    place[e] = last + 1;
                }
            }
        }
        for (int e = 0; e < entrants; e++) {
            out.add(new Placement(e, place[e]));
        }
        out.sort(Comparator.comparingInt(Placement::place).thenComparingInt(Placement::entrant));
        return out;
    }

    /** "Final", "Semifinal", "Losers round 3", "Swiss round 2", ... */
    public String title(Match m) {
        return switch (m.stage) {
            case "F" -> "Grand final";
            case "F2" -> "Grand final, second match";
            case "3" -> "Third place match";
            case "L" -> "Losers round " + m.round;
            case "S" -> "Swiss round " + m.round;
            case "R" -> "Round " + m.round;
            default -> {
                String name = switch (m.roundSize) {
                    case 1 -> "Final";
                    case 2 -> "Semifinal";
                    case 4 -> "Quarterfinal";
                    default -> "Round " + m.round;
                };
                yield (m.stage.equals("T") ? "Top cut " : format == Format.DOUBLE ? "Winners " : "")
                        + (m.stage.equals("T") || format == Format.DOUBLE ? name.toLowerCase(Locale.ROOT) : name);
            }
        };
    }
}
