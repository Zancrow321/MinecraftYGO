package io.github.zancrow321.minecraftygo.tournament;

import io.github.zancrow321.minecraftygo.engine.Ruleset;
import io.github.zancrow321.minecraftygo.engine.tournament.Bracket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Every tournament setting: the defaults in the {@code [tournament]} section of the server config, which each
 * tournament copies when it is created and its host can then change with {@code /ygo tournament set}. One table
 * drives the config file, the command, its suggestions and the tournament window.
 */
public final class TournamentOptions {
    public enum Kind { INT, BOOL, TEXT, LIST }

    /**
     * @param perTournament whether a host can change it for one tournament (the rest are server-wide)
     * @param valid         for text and list entries
     */
    public record Option(String key, Kind kind, String comment, Object fallback, int min, int max,
                         Predicate<String> valid, boolean perTournament) {
    }

    private static final Predicate<String> ANY = s -> true;
    private static final List<Option> ALL = new ArrayList<>();
    private static final Map<String, ModConfigSpec.ConfigValue<?>> VALUES = new LinkedHashMap<>();

    static {
        text("format", "single", "How the tournament is played: \"single\" (single elimination), \"double\" (double "
                + "elimination with a losers' bracket), \"swiss\" (Swiss rounds, optionally with a top cut) or "
                + "\"roundrobin\" (everyone plays everyone).", s -> Bracket.Format.parse(s) != null);
        integer("bestOf", 1, 1, 9, "Games per match: 1, 3, 5, ... (the first to win more than half takes the match).");
        integer("minPlayers", 4, 2, 256, "The tournament needs this many duelists to start (NPCs can fill up, see "
                + "npcFill).");
        integer("maxPlayers", 32, 2, 256, "No more than this many duelists can join.");
        integer("registrationMinutes", 5, 0, 10080, "Joining closes and the tournament starts this many minutes after "
                + "it was opened; 0 waits for the host to start it.");
        bool("startWhenFull", true, "Start as soon as maxPlayers have joined.");
        text("npcFill", "min", "NPC duelists who fill empty seats at the start: \"none\", \"min\" (up to minPlayers) "
                + "or \"bracket\" (up to a full bracket, so nobody gets a bye).",
                s -> List.of("none", "min", "bracket").contains(s.toLowerCase(Locale.ROOT)));
        integer("npcCount", 0, 0, 256, "This many NPC duelists join every tournament on top of the players.");
        integer("swissRounds", 0, 0, 20, "Swiss rounds; 0 plays enough rounds for one duelist to stay unbeaten.");
        integer("topCut", 0, 0, 64, "After the Swiss rounds the best this many duelists play a single elimination "
                + "top cut (4, 8, ...); 0 for none.");
        bool("thirdPlaceMatch", false, "Single elimination (and top cut): the semifinal losers play for third place.");
        bool("grandFinalReset", true, "Double elimination: if the losers' bracket champion wins the grand final, it "
                + "is played again, since both have lost once.");
        text("ruleset", "server", "The rules: \"server\" (the server's ruleset setting) or \"auto\", \"mr1\", "
                + "\"goat\", \"mr2\", \"mr3\", \"mr4\", \"modern\".", TournamentOptions::validRuleset);
        text("banlist", "server", "The Forbidden & Limited List: \"server\", \"auto\", \"none\" or the name of a file "
                + "in config/minecraftygo/banlists/.", ANY);
        integer("startingLifePoints", 0, 0, 1_000_000, "Life points per duelist; 0 keeps the server's.");
        integer("turnTimeLimit", -1, -1, 3600, "Seconds per duelist and turn; -1 keeps the server's, 0 for none.");
        integer("maxTurns", 0, 0, 1000, "A game ends after this many turns and whoever has more life points wins it "
                + "(level life points are a draw); 0 for no limit.");
        bool("lockDeck", true, "The deck a duelist joins with is the one they play the whole tournament with.");
        bool("starterDecks", false, "Players without a legal deck box may join with Yugi's starter deck (only if "
                + "the server lends starter decks).");
        integer("callSeconds", 15, 3, 600, "Seconds between being called to the arena and the duel; Ready starts it "
                + "sooner once both are ready.");
        integer("noShowMinutes", 3, 1, 1440, "A duelist who is offline or busy for this long while their match is "
                + "due loses it.");
        integer("gamePauseSeconds", 10, 0, 600, "Seconds between the games of a match.");
        text("entryFeeItem", "minecraft:emerald", "The item the entry fee is paid in.", TournamentOptions::validItem);
        integer("entryFee", 0, 0, 100_000, "How many of entryFeeItem it costs to join; refunded if the tournament "
                + "is called off.");
        text("potShare", "60,30,10", "How the entry fees are shared out: percent for 1st, 2nd, 3rd, ... place, "
                + "comma separated. What isn't shared out (or goes to an NPC) is kept.", TournamentOptions::validShare);
        list("prizesFirst", List.of("pack 5"), "Prizes for 1st place. Each entry is \"pack [count]\" (a random "
                + "booster pack that is out), \"pack:<set id> [count]\", \"card <passcode> [count]\", \"xp <points>\", "
                + "an item such as \"minecraft:diamond 3\", or \"command <command>\" run by the server with {player} "
                + "for the winner's name.");
        list("prizesSecond", List.of("pack 3"), "Prizes for 2nd place.");
        list("prizesThird", List.of("pack 2"), "Prizes for 3rd place (both semifinal losers without a third place "
                + "match).");
        list("prizesFourth", List.of("pack 1"), "Prizes for 4th place.");
        list("prizesTop8", List.of(), "Prizes for 5th to 8th place.");
        list("prizesEveryone", List.of(), "Prizes for every player who played the tournament to the end.");
        text("npcMatches", "play", "Matches between two NPC duelists: \"play\" (bots duel it out unseen) or "
                + "\"coinflip\".", s -> List.of("play", "coinflip").contains(s.toLowerCase(Locale.ROOT)));
        bool("announce", true, "Tell the whole server about the tournament (opening, rounds, results); off tells "
                + "only the duelists.");
        serverWide(Kind.BOOL, "playersCanHost", false, "Players without operator rights may host tournaments.");
        serverWide(Kind.LIST, "schedule", List.of(), "Tournaments that open by themselves, with these settings, at "
                + "times such as \"20:00\" (every day) or \"SAT 18:30\" (every Saturday), in the server's time zone.");
        serverWide(Kind.TEXT, "scheduledName", "Server Tournament", "The name of scheduled tournaments.");
    }

    private TournamentOptions() {
    }

    private static void integer(String key, int fallback, int min, int max, String comment) {
        ALL.add(new Option(key, Kind.INT, comment, fallback, min, max, ANY, true));
    }

    private static void bool(String key, boolean fallback, String comment) {
        ALL.add(new Option(key, Kind.BOOL, comment, fallback, 0, 0, ANY, true));
    }

    private static void text(String key, String fallback, String comment, Predicate<String> valid) {
        ALL.add(new Option(key, Kind.TEXT, comment, fallback, 0, 0, valid, true));
    }

    private static void list(String key, List<String> fallback, String comment) {
        ALL.add(new Option(key, Kind.LIST, comment, fallback, 0, 0, Prizes::valid, true));
    }

    private static void serverWide(Kind kind, String key, Object fallback, String comment) {
        ALL.add(new Option(key, kind, comment, fallback, 0, 0,
                key.equals("schedule") ? Schedule::valid : ANY, false));
    }

    private static boolean validRuleset(String s) {
        String t = s.strip().toLowerCase(Locale.ROOT);
        return t.equals("server") || t.equals("auto") || t.equals("mr5")
                || Arrays.stream(Ruleset.values()).anyMatch(r -> r.name().equalsIgnoreCase(t));
    }

    private static boolean validItem(String s) {
        ResourceLocation id = ResourceLocation.tryParse(s.strip());
        return id != null && BuiltInRegistries.ITEM.containsKey(id);
    }

    private static boolean validShare(String s) {
        try {
            int total = 0;
            for (String part : s.split(",")) {
                if (!part.isBlank()) {
                    int p = Integer.parseInt(part.strip());
                    if (p < 0) {
                        return false;
                    }
                    total += p;
                }
            }
            return total <= 100;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Adds the {@code [tournament]} section to the server config. */
    public static void define(ModConfigSpec.Builder builder) {
        for (Option o : ALL) {
            builder.comment(o.comment());
            VALUES.put(o.key(), switch (o.kind()) {
                case INT -> builder.defineInRange(o.key(), (int) o.fallback(), o.min(), o.max());
                case BOOL -> builder.define(o.key(), (boolean) o.fallback());
                case TEXT -> builder.define(o.key(), (String) o.fallback(),
                        v -> v instanceof String s && o.valid().test(s));
                case LIST -> {
                    @SuppressWarnings("unchecked")
                    List<String> fallback = (List<String>) o.fallback();
                    yield builder.defineListAllowEmpty(o.key(), fallback, () -> "",
                            v -> v instanceof String s && o.valid().test(s));
                }
            });
        }
    }

    public static List<Option> all() {
        return ALL;
    }

    public static Option option(String key) {
        for (Option o : ALL) {
            if (o.key().equalsIgnoreCase(key)) {
                return o;
            }
        }
        return null;
    }

    /** The config file's value, as a tournament stores it (list entries joined by "; "). */
    public static String configured(Option o) {
        ModConfigSpec.ConfigValue<?> value = VALUES.get(o.key());
        Object v = value != null ? safeGet(value, o) : o.fallback();
        return v instanceof List<?> l ? String.join("; ", l.stream().map(String::valueOf).toList()) : String.valueOf(v);
    }

    private static Object safeGet(ModConfigSpec.ConfigValue<?> value, Option o) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            // The config isn't loaded (no world yet).
            return o.fallback();
        }
    }

    /** The current settings of the config file, as a new tournament starts with. */
    public static Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Option o : ALL) {
            if (o.perTournament()) {
                out.put(o.key(), configured(o));
            }
        }
        return out;
    }

    /**
     * @return the value as it is stored (lists as "a; b"), or {@code null} if it isn't allowed for {@code o}
     */
    public static String parse(Option o, String text) {
        String t = text.strip();
        switch (o.kind()) {
            case INT -> {
                try {
                    int v = Integer.parseInt(t);
                    return v < o.min() || v > o.max() ? null : String.valueOf(v);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case BOOL -> {
                return switch (t.toLowerCase(Locale.ROOT)) {
                    case "true", "on", "yes", "an", "ja" -> "true";
                    case "false", "off", "no", "aus", "nein" -> "false";
                    default -> null;
                };
            }
            case TEXT -> {
                return o.valid().test(t) ? t : null;
            }
            default -> {
                List<String> entries = splitList(t);
                return entries.stream().allMatch(o.valid()) ? String.join("; ", entries) : null;
            }
        }
    }

    /** List entries from a stored value (or a command): separated by ";". "none" or "-" is an empty list. */
    public static List<String> splitList(String stored) {
        List<String> out = new ArrayList<>();
        if (stored.isBlank() || stored.strip().equals("-") || stored.strip().equalsIgnoreCase("none")) {
            return out;
        }
        for (String part : stored.split(";")) {
            if (!part.isBlank()) {
                out.add(part.strip());
            }
        }
        return out;
    }

    /** Server-wide settings straight from the config. */
    static boolean playersCanHost() {
        return Boolean.parseBoolean(configured(option("playersCanHost")));
    }

    static List<String> schedule() {
        return splitList(configured(option("schedule")));
    }

    static String scheduledName() {
        return configured(option("scheduledName"));
    }
}
