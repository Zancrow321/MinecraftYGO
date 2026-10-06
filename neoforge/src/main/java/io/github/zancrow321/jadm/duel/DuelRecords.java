package io.github.zancrow321.jadm.duel;

import io.github.zancrow321.jadm.network.DuelResultPayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every player's wins, losses and draws, against people and against NPCs and bots, saved with the world. Kept by
 * player id rather than on the player, so a duelist who logged off mid-duel still has their loss counted.
 */
public final class DuelRecords extends SavedData {
    private static final String NAME = "jadm_duel_records";

    /**
     * One player's record.
     *
     * @param streak     wins in a row up to now
     * @param bestStreak the most wins in a row so far
     */
    public record Record(String name, int playerWins, int playerLosses, int playerDraws, int npcWins, int npcLosses,
                         int npcDraws, int streak, int bestStreak) {
        static Record fresh(String name) {
            return new Record(name, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public int wins() {
            return playerWins + npcWins;
        }

        public int losses() {
            return playerLosses + npcLosses;
        }

        public int draws() {
            return playerDraws + npcDraws;
        }

        public int duels() {
            return wins() + losses() + draws();
        }

        /** "12 wins, 5 losses, 1 draw" */
        public String summary() {
            return count(wins(), "win") + ", " + count(losses(), "loss") + ", " + count(draws(), "draw");
        }

        /** Won share of all duels, in whole percent. */
        public int winRate() {
            return duels() == 0 ? 0 : Math.round(100f * wins() / duels());
        }

        private static String count(int n, String word) {
            return n + " " + (n == 1 ? word : word.endsWith("s") ? word + "es" : word + "s");
        }
    }

    private final Map<UUID, Record> records = new HashMap<>();

    public static DuelRecords get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DuelRecords::new, DuelRecords::load, null), NAME);
    }

    public Optional<Record> of(UUID player) {
        return Optional.ofNullable(records.get(player));
    }

    /** The record of the player last seen as {@code name}, ignoring case. */
    public Optional<Map.Entry<UUID, Record>> byName(String name) {
        return records.entrySet().stream().filter(e -> e.getValue().name().equalsIgnoreCase(name)).findFirst();
    }

    public List<String> names() {
        return records.values().stream().map(Record::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** The {@code count} players with the most wins (fewer losses first on a tie). */
    public List<Record> top(int count) {
        return records.values().stream()
                .filter(r -> r.duels() > 0)
                .sorted(Comparator.comparingInt(Record::wins).reversed()
                        .thenComparingInt(Record::losses)
                        .thenComparing(Record::name, String.CASE_INSENSITIVE_ORDER))
                .limit(count)
                .toList();
    }

    /**
     * Counts one finished duel for {@code player}.
     *
     * @param outcome   {@link DuelResultPayload#WON}, {@code LOST} or
     *                  {@code DRAW}
     * @param vsPlayers whether every opponent was a person (not an NPC or a bot)
     * @return the record after it
     */
    public Record count(UUID player, String name, int outcome, boolean vsPlayers) {
        Record r = records.getOrDefault(player, Record.fresh(name));
        boolean won = outcome == DuelResultPayload.WON;
        boolean lost = outcome == DuelResultPayload.LOST;
        boolean drew = !won && !lost;
        int streak = won ? r.streak() + 1 : 0;
        Record after = new Record(name,
                r.playerWins() + (vsPlayers && won ? 1 : 0),
                r.playerLosses() + (vsPlayers && lost ? 1 : 0),
                r.playerDraws() + (vsPlayers && drew ? 1 : 0),
                r.npcWins() + (!vsPlayers && won ? 1 : 0),
                r.npcLosses() + (!vsPlayers && lost ? 1 : 0),
                r.npcDraws() + (!vsPlayers && drew ? 1 : 0),
                streak, Math.max(streak, r.bestStreak()));
        records.put(player, after);
        setDirty();
        return after;
    }

    /** @return whether {@code player} had a record */
    public boolean reset(UUID player) {
        boolean had = records.remove(player) != null;
        if (had) {
            setDirty();
        }
        return had;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        records.forEach((id, r) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", r.name());
            entry.putInt("playerWins", r.playerWins());
            entry.putInt("playerLosses", r.playerLosses());
            entry.putInt("playerDraws", r.playerDraws());
            entry.putInt("npcWins", r.npcWins());
            entry.putInt("npcLosses", r.npcLosses());
            entry.putInt("npcDraws", r.npcDraws());
            entry.putInt("streak", r.streak());
            entry.putInt("bestStreak", r.bestStreak());
            players.put(id.toString(), entry);
        });
        tag.put("players", players);
        return tag;
    }

    private static DuelRecords load(CompoundTag tag, HolderLookup.Provider registries) {
        DuelRecords out = new DuelRecords();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            CompoundTag e = players.getCompound(key);
            out.records.put(UUID.fromString(key), new Record(e.getString("name"), e.getInt("playerWins"),
                    e.getInt("playerLosses"), e.getInt("playerDraws"), e.getInt("npcWins"), e.getInt("npcLosses"),
                    e.getInt("npcDraws"), e.getInt("streak"), e.getInt("bestStreak")));
        }
        return out;
    }
}
