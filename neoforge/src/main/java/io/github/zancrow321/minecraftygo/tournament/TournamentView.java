package io.github.zancrow321.minecraftygo.tournament;

import io.github.zancrow321.minecraftygo.engine.tournament.Bracket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What the tournament window shows: sent to clients as JSON whenever the tournament changes. */
public final class TournamentView {
    public String name;
    public String host;
    /** single, double, swiss, roundrobin */
    public String format;
    public String formatName;
    /** open, running, done, cancelled */
    public String state;
    /** One line under the title, e.g. "Registration open: 5 of 16, starts in 3 minutes" */
    public String status;
    public int bestOf;
    public long closesAt;
    public List<String> entrants = new ArrayList<>();
    public List<Boolean> npc = new ArrayList<>();
    public List<String> decks = new ArrayList<>();
    public List<Integer> places = new ArrayList<>();
    public List<Bracket.Match> matches = new ArrayList<>();
    public List<String> titles = new ArrayList<>();
    /** match id -> "Playing on arena 1", ... */
    public Map<Integer, String> live = new HashMap<>();
    public List<Bracket.Standing> standings = new ArrayList<>();
    public List<String> rules = new ArrayList<>();
    public List<String> prizes = new ArrayList<>();
    public List<String> news = new ArrayList<>();
    public int arenas;

    static TournamentView of(Tournament t, TournamentManager manager) {
        TournamentView v = new TournamentView();
        v.name = t.name;
        v.host = t.hostName;
        v.format = t.format().id;
        v.formatName = t.format().displayName;
        v.state = t.state;
        v.bestOf = t.integer("bestOf");
        v.closesAt = t.closesAt;
        v.arenas = manager.arenaCount();
        for (Tournament.Entrant e : t.entrants) {
            v.entrants.add(e.name);
            v.npc.add(e.npc());
            v.decks.add(e.deckName == null ? "" : e.deckName);
            v.places.add(e.place);
        }
        long people = t.entrants.stream().filter(e -> !e.npc()).count();
        v.status = switch (t.state) {
            case Tournament.OPEN -> "Registration open: " + people + " of " + t.integer("maxPlayers") + " players"
                    + (t.entrants.size() > people ? " + " + (t.entrants.size() - people) + " NPC" : "")
                    + (t.closesAt > 0 ? ", starts in " + minutes(t.closesAt) : ", starts when " + t.hostName
                    + " starts it");
            case Tournament.RUNNING -> running(t);
            case Tournament.DONE -> "Finished";
            default -> "Called off";
        };
        if (t.bracket != null) {
            v.matches = t.bracket.matches;
            for (Bracket.Match m : t.bracket.matches) {
                v.titles.add(t.bracket.title(m));
                String live = manager.liveText(t, m.id);
                if (live != null) {
                    v.live.put(m.id, live);
                }
            }
            v.standings = t.bracket.standings();
        }
        v.rules = rules(t, manager);
        v.prizes = prizes(t);
        v.news = List.copyOf(t.news);
        return v;
    }

    private static String minutes(long at) {
        long m = Math.max(1, (at - System.currentTimeMillis() + 59_999) / 60_000);
        return m + (m == 1 ? " minute" : " minutes");
    }

    private static String running(Tournament t) {
        Bracket b = t.bracket;
        return switch (t.format()) {
            case SWISS -> b.cutStarted ? "Top cut" : "Swiss round " + b.round + " of " + b.swissRoundCount();
            case ROUND_ROBIN -> "Round " + b.round + " of " + b.roundRobinCount();
            default -> {
                // "Quarterfinal", or "Winners round 2, losers round 1" while both brackets play
                List<String> rounds = b.matches.stream().filter(Bracket.Match::playable).map(b::title).distinct()
                        .toList();
                yield rounds.isEmpty() ? "Finishing" : String.join(", ", rounds);
            }
        };
    }

    private static List<String> rules(Tournament t, TournamentManager manager) {
        List<String> out = new ArrayList<>();
        Bracket.Format f = t.format();
        String games = t.integer("bestOf") == 1 ? "one game per match" : "best of " + t.integer("bestOf");
        out.add(f.displayName + ", " + games);
        if (f == Bracket.Format.SWISS) {
            int rounds = t.integer("swissRounds");
            out.add((rounds == 0 ? "Rounds: enough for one unbeaten duelist" : rounds + " Swiss rounds")
                    + (t.integer("topCut") >= 2 ? ", then a top " + t.integer("topCut") + " cut" : ""));
        }
        if (f == Bracket.Format.DOUBLE) {
            out.add(t.bool("grandFinalReset") ? "Grand final played again if the losers' champion wins it"
                    : "One grand final");
        }
        if ((f == Bracket.Format.SINGLE || t.integer("topCut") >= 2) && t.bool("thirdPlaceMatch")) {
            out.add("A match for third place");
        }
        out.add(t.integer("minPlayers") + " to " + t.integer("maxPlayers") + " duelists, NPCs fill up: "
                + switch (t.setting("npcFill").toLowerCase(Locale.ROOT)) {
                    case "none" -> "no";
                    case "bracket" -> "to a full bracket";
                    default -> "to " + t.integer("minPlayers");
                } + (t.integer("npcCount") > 0 ? ", plus " + t.integer("npcCount") + " NPCs" : ""));
        out.add("Rules: " + manager.ruleset(t).displayName() + ", banlist: "
                + TournamentManager.banlist(t, manager.effectiveStep(t)).name());
        int lp = t.integer("startingLifePoints");
        int time = t.integer("turnTimeLimit");
        int turns = t.integer("maxTurns");
        out.add((lp > 0 ? lp + " LP" : "Server's LP") + ", turn time: " + (time < 0 ? "server's" : time == 0
                ? "none" : time + " s") + (turns > 0 ? ", games end after turn " + turns + " (on LP)" : ""));
        out.add(t.bool("lockDeck") ? "Decks are locked in at joining" : "Any legal deck box, match by match");
        int fee = t.integer("entryFee");
        if (fee > 0) {
            out.add("Entry: " + Fees.amount(t, fee)
                    + ", pot " + t.pot + " shared " + t.setting("potShare").replace(",", " / ") + " %");
        }
        out.add("Matches on " + manager.arenaCount() + (manager.arenaCount() == 1 ? " arena" : " arenas")
                + ", no-show after " + t.integer("noShowMinutes") + " min");
        return out;
    }

    private static List<String> prizes(Tournament t) {
        List<String> out = new ArrayList<>();
        String[][] places = {{"1st", "prizesFirst"}, {"2nd", "prizesSecond"}, {"3rd", "prizesThird"},
                {"4th", "prizesFourth"}, {"5th-8th", "prizesTop8"}, {"Everyone", "prizesEveryone"}};
        for (String[] p : places) {
            List<String> entries = t.list(p[1]);
            if (!entries.isEmpty()) {
                out.add(p[0] + ": " + String.join(", ", entries.stream().map(TournamentView::describe).toList()));
            }
        }
        return out;
    }

    /** "pack 3" -> "3 booster packs", "minecraft:diamond 2" -> "2 diamonds", ... */
    static String describe(String entry) {
        String[] p = entry.strip().split("\\s+");
        String kind = p[0].toLowerCase(Locale.ROOT);
        int n = 1;
        try {
            n = p.length > 1 && !kind.equals("command") && !kind.equals("card") ? Integer.parseInt(p[1])
                    : kind.equals("card") && p.length > 2 ? Integer.parseInt(p[2]) : 1;
        } catch (NumberFormatException ignored) {
            // shown as written
        }
        if (kind.equals("pack")) {
            return n + (n == 1 ? " booster pack" : " booster packs");
        }
        if (kind.startsWith("pack:")) {
            return n + "x " + p[0].substring(5) + " pack";
        }
        if (kind.equals("xp")) {
            return n + " XP";
        }
        if (kind.equals("points")) {
            return io.github.zancrow321.minecraftygo.points.Points.format(n);
        }
        if (kind.equals("command")) {
            return "a surprise";
        }
        if (kind.equals("card")) {
            try {
                return (n > 1 ? n + "x " : "") + io.github.zancrow321.minecraftygo.YgoData.text()
                        .cardName(Integer.parseInt(p[1]));
            } catch (RuntimeException e) {
                return entry;
            }
        }
        return n + " " + TournamentManager.itemName(p[0], n);
    }
}
