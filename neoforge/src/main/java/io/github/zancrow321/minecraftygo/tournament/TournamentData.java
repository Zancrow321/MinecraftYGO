package io.github.zancrow321.minecraftygo.tournament;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.zancrow321.minecraftygo.MinecraftYgo;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The tournament (running, or the last one), the arenas tournaments are played on and prizes waiting for players
 * who were offline, saved with the world.
 */
final class TournamentData extends SavedData {
    private static final String NAME = "minecraftygo_tournament";
    static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    static final class Store {
        Tournament current;
        List<ArenaRef> arenas = new ArrayList<>();
        /** player -> prize entries (refunds are item entries too) to hand over when they are next online */
        Map<UUID, List<String>> owed = new HashMap<>();
        /** The minute (epoch ms / 60000) a scheduled tournament last opened, so it opens only once. */
        long lastScheduled;
        /** People to send back from a tournament arena once they are online and out of their duel. */
        Map<UUID, Tournament.Back> returns = new HashMap<>();

        Map<UUID, Tournament.Back> owedReturns() {
            if (returns == null) {
                returns = new HashMap<>();
            }
            return returns;
        }
    }

    /** A duel arena that tournament matches are played on: its middle block. */
    static final class ArenaRef {
        String dim;
        int x;
        int y;
        int z;
    }

    Store store = new Store();

    static TournamentData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TournamentData::new, TournamentData::load, null), NAME);
    }

    private static TournamentData load(CompoundTag tag, HolderLookup.Provider registries) {
        TournamentData data = new TournamentData();
        try {
            Store store = GSON.fromJson(tag.getString("Json"), Store.class);
            if (store != null) {
                data.store = store;
            }
        } catch (JsonParseException e) {
            MinecraftYgo.LOGGER.error("Could not read the saved tournament", e);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Json", GSON.toJson(store));
        return tag;
    }
}
