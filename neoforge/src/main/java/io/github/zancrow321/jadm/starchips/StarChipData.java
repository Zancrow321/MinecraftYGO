package io.github.zancrow321.jadm.starchips;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.zancrow321.jadm.Jadm;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The Star Chip event (running, or the last one), saved with the world as JSON. */
final class StarChipData extends SavedData {
    private static final String NAME = "jadm_star_chips";
    private static final Gson GSON = new GsonBuilder().create();

    /** One Duelist Kingdom event. */
    static final class Event {
        static final String RUNNING = "running";
        /** Enough have qualified (or time ran out): the finals are being held as a tournament. */
        static final String FINALS = "finals";
        static final String DONE = "done";
        static final String CANCELLED = "cancelled";

        String name;
        String state = RUNNING;
        /** {@code null} when the server console started it */
        UUID host;
        /** Epoch milliseconds; {@code endsAt} 0 runs until enough have qualified. */
        long startedAt;
        long endsAt;
        /** Fixed at the start, so a config change doesn't move the goal mid-event. */
        int goal;
        int startChips;
        int finalistsWanted;
        Map<UUID, Duelist> duelists = new LinkedHashMap<>();
        /** Who goes to the finals, in the order they qualified. */
        List<UUID> finalists = new ArrayList<>();
        /** The name of the finals tournament once it is held */
        String finalsName;
        boolean finalsStarted;
        String champion;

        boolean active() {
            return state.equals(RUNNING) || state.equals(FINALS);
        }

        long qualified() {
            return duelists.values().stream().filter(d -> d.status.equals(Duelist.QUALIFIED)).count();
        }
    }

    static final class Duelist {
        static final String IN = "in";
        static final String QUALIFIED = "qualified";
        static final String OUT = "out";
        static final String LEFT = "left";

        String name;
        int chips;
        String status = IN;
        int wins;
        int losses;
    }

    Event event;

    static StarChipData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StarChipData::new, StarChipData::load, null), NAME);
    }

    private static StarChipData load(CompoundTag tag, HolderLookup.Provider registries) {
        StarChipData data = new StarChipData();
        try {
            data.event = GSON.fromJson(tag.getString("Json"), Event.class);
        } catch (JsonParseException e) {
            Jadm.LOGGER.error("Could not read the saved Star Chip event", e);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Json", event == null ? "" : GSON.toJson(event));
        return tag;
    }
}
