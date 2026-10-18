package io.github.zancrow321.jadm.engine.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-kept decks of well-known tournament eras (Goat Control, Chaos Return, ...) that NPC duelists play once their
 * era has come. Cards are listed by name in {@value #RESOURCE}, so the file stays readable and survives passcode
 * changes; names the database doesn't know are left out.
 */
public record TournamentDecks(List<Entry> decks) {
    public static final String RESOURCE = "/jadm/tournament_decks.json";

    /** @param date when the deck was played; it's only handed out from then on */
    public record Entry(String id, String name, LocalDate date, List<Integer> main, List<Integer> extra) {
        public Entry {
            main = List.copyOf(main);
            extra = List.copyOf(extra);
        }

        /**
         * The deck under {@code banlist}: copies over a limit are dropped (and under a Genesys list the costliest
         * cards until it fits the point cap), and the main deck is filled back up to 40 with more copies of what it
         * already holds. {@code null} if that can't make it legal.
         */
        public Deck legal(Banlist banlist, CardDatabase cards) {
            List<Integer> legalMain = withinLimits(main, banlist);
            List<Integer> legalExtra = withinLimits(extra, banlist);
            withinPoints(legalMain, legalExtra, banlist);
            for (int i = 0; legalMain.size() < DeckRules.MAIN_MIN && i < legalMain.size(); i++) {
                int code = legalMain.get(i);
                if (legalMain.stream().filter(c -> c == code).count() < banlist.limit(code)
                        && banlist.points(code) == 0) {
                    legalMain.add(code);
                }
            }
            Deck deck = new Deck(name + " (" + date.getYear() + ")", legalMain, legalExtra, List.of());
            return DeckRules.problems(deck, cards, banlist).isEmpty() ? deck : null;
        }

        /** Under a Genesys list, drops the costliest cards until the deck is within the point cap. */
        private static void withinPoints(List<Integer> main, List<Integer> extra, Banlist banlist) {
            if (!banlist.genesys()) {
                return;
            }
            int points = DeckRules.points(new Deck("", main, extra, List.of()), banlist);
            while (points > banlist.pointCap()) {
                List<Integer> from = main;
                int costliest = -1;
                for (List<Integer> part : List.of(main, extra)) {
                    for (int code : part) {
                        if (costliest < 0 || banlist.points(code) > banlist.points(costliest)) {
                            costliest = code;
                            from = part;
                        }
                    }
                }
                from.remove(Integer.valueOf(costliest));
                points -= banlist.points(costliest);
            }
        }

        private static List<Integer> withinLimits(List<Integer> codes, Banlist banlist) {
            Map<Integer, Integer> copies = new HashMap<>();
            List<Integer> kept = new ArrayList<>();
            for (int code : codes) {
                if (copies.merge(code, 1, Integer::sum) <= banlist.limit(code)) {
                    kept.add(code);
                }
            }
            return kept;
        }
    }

    public TournamentDecks {
        decks = List.copyOf(decks);
    }

    /** The decks played by {@code date}. */
    public List<Entry> by(LocalDate date) {
        return decks.stream().filter(d -> !d.date().isAfter(date)).toList();
    }

    public static TournamentDecks loadBundled(CardDatabase cards) {
        try (InputStream in = TournamentDecks.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            Map<String, Integer> byName = new HashMap<>();
            for (CardInfo card : cards.all()) {
                if (card.data().alias() == 0) {
                    byName.merge(card.name(), card.code(), Math::min);
                }
            }
            List<Entry> decks = new ArrayList<>();
            for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                decks.add(new Entry(o.get("id").getAsString(), o.get("name").getAsString(),
                        LocalDate.parse(o.get("date").getAsString()), codes(o, "main", byName),
                        codes(o, "extra", byName)));
            }
            return new TournamentDecks(decks);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads entries like {@code "2 Scapegoat"}. */
    private static List<Integer> codes(JsonObject deck, String part, Map<String, Integer> byName) {
        List<Integer> codes = new ArrayList<>();
        for (JsonElement e : deck.getAsJsonArray(part)) {
            String line = e.getAsString();
            int space = line.indexOf(' ');
            Integer code = byName.get(line.substring(space + 1));
            if (code != null) {
                for (int i = Integer.parseInt(line.substring(0, space)); i > 0; i--) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }
}
