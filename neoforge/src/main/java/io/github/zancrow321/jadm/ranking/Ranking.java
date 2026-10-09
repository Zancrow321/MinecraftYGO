package io.github.zancrow321.jadm.ranking;

import io.github.zancrow321.jadm.JadmServerConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every ranked player's Elo rating, their best rank this season and their ranked wins and losses, saved with the
 * world. Only ranked duels change it; a new season puts everyone back to the start rating.
 */
public final class Ranking extends SavedData {
    private static final String NAME = "jadm_ranking";

    /**
     * One player's standing.
     *
     * @param peak     the highest rating this season
     * @param bestTier the highest rank reached this season (promotion bonuses are paid once per rank)
     */
    public record Entry(String name, int rating, int peak, int wins, int losses, int draws, int bestTier) {
        public int games() {
            return wins + losses + draws;
        }

        public int tier() {
            return Tiers.of(rating, JadmServerConfig.RANKING.tierStarts());
        }
    }

    /**
     * What one ranked duel did to one player.
     *
     * @param promotedTo the rank they reached for the first time this season, or -1
     */
    public record Change(UUID player, Entry before, Entry after, int promotedTo) {
        public int delta() {
            return after.rating() - before.rating();
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    /** Ranked duels per pair of players today: "smaller uuid|larger uuid" to the count. */
    private final Map<String, Integer> pairsToday = new HashMap<>();
    private long pairsDay;
    private int season = 1;
    /** Goes up with every change, so Ranking Boards know when to redraw. Not saved. */
    private int version;

    public static Ranking get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Ranking::new, Ranking::load, null), NAME);
    }

    public int season() {
        return season;
    }

    public int version() {
        return version;
    }

    public Optional<Entry> of(UUID player) {
        return Optional.ofNullable(entries.get(player));
    }

    /** The rating a player has, or would start with. */
    public int rating(UUID player) {
        Entry e = entries.get(player);
        return e != null ? e.rating() : JadmServerConfig.RANKING.startRating.get();
    }

    /** The entry of the player last seen as {@code name}, ignoring case. */
    public Optional<Map.Entry<UUID, Entry>> byName(String name) {
        return entries.entrySet().stream().filter(e -> e.getValue().name().equalsIgnoreCase(name)).findFirst();
    }

    public List<String> names() {
        return entries.values().stream().map(Entry::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** Everyone who played a ranked duel this season, best first (more wins first on a tie). */
    public List<Map.Entry<UUID, Entry>> standings() {
        List<Map.Entry<UUID, Entry>> out = new ArrayList<>(entries.entrySet().stream()
                .filter(e -> e.getValue().games() > 0).toList());
        out.sort(Comparator.<Map.Entry<UUID, Entry>>comparingInt(e -> -e.getValue().rating())
                .thenComparingInt(e -> -e.getValue().wins())
                .thenComparing(e -> e.getValue().name(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** A player's place in the standings, from 1, or 0 if they haven't played a ranked duel. */
    public int place(UUID player) {
        List<Map.Entry<UUID, Entry>> standings = standings();
        for (int i = 0; i < standings.size(); i++) {
            if (standings.get(i).getKey().equals(player)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Whether a ranked duel between these two still counts today ({@code [ranking] maxPerPairPerDay}).
     */
    public boolean pairMayCount(UUID a, UUID b) {
        int max = JadmServerConfig.RANKING.maxPerPairPerDay.get();
        rollDay();
        return max <= 0 || pairsToday.getOrDefault(pairKey(a, b), 0) < max;
    }

    /**
     * Counts a finished ranked duel between {@code a} and {@code b}.
     *
     * @param scoreA 1 if {@code a} won, 0 if {@code b} won, 0.5 for a draw
     * @return what it did to {@code a}, then to {@code b}
     */
    public List<Change> record(UUID a, String nameA, UUID b, String nameB, double scoreA) {
        Entry ea = entryOf(a, nameA);
        Entry eb = entryOf(b, nameB);
        double expectedA = 1 / (1 + Math.pow(10, (eb.rating() - ea.rating()) / 400.0));
        int deltaA = (int) Math.round(k(ea) * (scoreA - expectedA));
        int deltaB = (int) Math.round(k(eb) * ((1 - scoreA) - (1 - expectedA)));
        Change ca = apply(a, ea, deltaA, scoreA);
        Change cb = apply(b, eb, deltaB, 1 - scoreA);
        rollDay();
        pairsToday.merge(pairKey(a, b), 1, Integer::sum);
        changed();
        return List.of(ca, cb);
    }

    private static int k(Entry e) {
        int k = JadmServerConfig.RANKING.kFactor.get();
        return e.games() < JadmServerConfig.RANKING.placementGames.get() ? 2 * k : k;
    }

    private Change apply(UUID player, Entry before, int delta, double score) {
        int rating = Math.max(0, before.rating() + delta);
        Entry moved = new Entry(before.name(), rating, Math.max(before.peak(), rating),
                before.wins() + (score > 0.75 ? 1 : 0), before.losses() + (score < 0.25 ? 1 : 0),
                before.draws() + (score >= 0.25 && score <= 0.75 ? 1 : 0), before.bestTier());
        int tier = moved.tier();
        int promotedTo = tier > before.bestTier() ? tier : -1;
        Entry after = new Entry(moved.name(), moved.rating(), moved.peak(), moved.wins(), moved.losses(),
                moved.draws(), Math.max(before.bestTier(), tier));
        entries.put(player, after);
        return new Change(player, before, after, promotedTo);
    }

    private Entry entryOf(UUID player, String name) {
        Entry e = entries.get(player);
        int start = JadmServerConfig.RANKING.startRating.get();
        if (e == null) {
            return new Entry(name, start, start, 0, 0, 0, Tiers.of(start, JadmServerConfig.RANKING.tierStarts()));
        }
        return e.name().equals(name) ? e
                : new Entry(name, e.rating(), e.peak(), e.wins(), e.losses(), e.draws(), e.bestTier());
    }

    /** Sets a player's rating (an operator's correction); their record stays. */
    public Entry set(UUID player, String name, int rating) {
        Entry e = entryOf(player, name);
        Entry after = new Entry(e.name(), Math.max(0, rating), Math.max(e.peak(), rating), e.wins(), e.losses(),
                e.draws(), Math.max(e.bestTier(), Tiers.of(rating, JadmServerConfig.RANKING.tierStarts())));
        entries.put(player, after);
        changed();
        return after;
    }

    /** @return whether {@code player} was ranked */
    public boolean reset(UUID player) {
        boolean had = entries.remove(player) != null;
        if (had) {
            changed();
        }
        return had;
    }

    /** Starts a new season: everyone goes back to the start rating. */
    public int newSeason() {
        entries.clear();
        pairsToday.clear();
        season++;
        changed();
        return season;
    }

    private void changed() {
        version++;
        setDirty();
    }

    private void rollDay() {
        long today = LocalDate.now().toEpochDay();
        if (pairsDay != today) {
            pairsDay = today;
            pairsToday.clear();
        }
    }

    private static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        entries.forEach((id, e) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", e.name());
            entry.putInt("rating", e.rating());
            entry.putInt("peak", e.peak());
            entry.putInt("wins", e.wins());
            entry.putInt("losses", e.losses());
            entry.putInt("draws", e.draws());
            entry.putInt("bestTier", e.bestTier());
            players.put(id.toString(), entry);
        });
        tag.put("players", players);
        CompoundTag pairs = new CompoundTag();
        pairsToday.forEach(pairs::putInt);
        tag.put("pairsToday", pairs);
        tag.putLong("pairsDay", pairsDay);
        tag.putInt("season", season);
        return tag;
    }

    private static Ranking load(CompoundTag tag, HolderLookup.Provider registries) {
        Ranking out = new Ranking();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            CompoundTag e = players.getCompound(key);
            out.entries.put(UUID.fromString(key), new Entry(e.getString("name"), e.getInt("rating"),
                    e.getInt("peak"), e.getInt("wins"), e.getInt("losses"), e.getInt("draws"), e.getInt("bestTier")));
        }
        CompoundTag pairs = tag.getCompound("pairsToday");
        for (String key : pairs.getAllKeys()) {
            out.pairsToday.put(key, pairs.getInt(key));
        }
        out.pairsDay = tag.getLong("pairsDay");
        out.season = Math.max(1, tag.getInt("season"));
        return out;
    }
}
