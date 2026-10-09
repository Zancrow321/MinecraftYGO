package io.github.zancrow321.jadm.ranking;

import io.github.zancrow321.jadm.JadmServerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The ranking as the window shows it, sent to the client as JSON: the best players, the ranks and where the viewer
 * stands.
 */
public final class RankingView {
    /** How many players the window lists. */
    public static final int ROWS = 100;

    public static final class Row {
        public String name;
        public int rating;
        public int tier;
        public int wins;
        public int losses;
        public int draws;
        public int peak;
    }

    public int season;
    public boolean enabled;
    /** The rank names, their colors and the rating each starts at (0 for Bronze). */
    public List<String> tierNames = new ArrayList<>();
    public List<Integer> tierColors = new ArrayList<>();
    public List<Integer> tierStarts = new ArrayList<>();
    public List<Row> rows = new ArrayList<>();
    /** Everyone ranked this season. */
    public int total;
    /** The viewer's place from 1, or 0 if they aren't ranked yet. */
    public int myPlace;
    /** The viewer's standing (the start rating if they aren't ranked yet). */
    public Row me;

    public static RankingView of(Ranking ranking, UUID viewer, String viewerName) {
        RankingView v = new RankingView();
        v.season = ranking.season();
        v.enabled = JadmServerConfig.RANKING.enabled.get();
        List<Integer> starts = JadmServerConfig.RANKING.tierStarts();
        for (int i = 0; i < Tiers.NAMES.size(); i++) {
            v.tierNames.add(Tiers.name(i));
            v.tierColors.add(Tiers.color(i));
            v.tierStarts.add(i == 0 ? 0 : i - 1 < starts.size() ? starts.get(i - 1) : Integer.MAX_VALUE);
        }
        List<Map.Entry<UUID, Ranking.Entry>> standings = ranking.standings();
        v.total = standings.size();
        for (int i = 0; i < standings.size(); i++) {
            Map.Entry<UUID, Ranking.Entry> e = standings.get(i);
            if (i < ROWS) {
                v.rows.add(row(e.getValue()));
            }
            if (e.getKey().equals(viewer)) {
                v.myPlace = i + 1;
                v.me = row(e.getValue());
            }
        }
        if (v.me == null) {
            int start = JadmServerConfig.RANKING.startRating.get();
            v.me = row(new Ranking.Entry(viewerName, start, start, 0, 0, 0, 0));
        }
        return v;
    }

    private static Row row(Ranking.Entry e) {
        Row r = new Row();
        r.name = e.name();
        r.rating = e.rating();
        r.tier = e.tier();
        r.wins = e.wins();
        r.losses = e.losses();
        r.draws = e.draws();
        r.peak = e.peak();
        return r;
    }
}
