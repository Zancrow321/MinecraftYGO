package io.github.zancrow321.jadm.quest;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.github.zancrow321.jadm.Jadm;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Each player's current quests and progress, saved with the world as JSON. */
final class QuestData extends SavedData {
    private static final String NAME = "jadm_quests";
    private static final Gson GSON = new Gson();

    /** One quest a player has. */
    static final class Active {
        String id;
        int progress;
        boolean claimed;

        Active() {
        }

        Active(String id) {
            this.id = id;
        }
    }

    static final class PlayerQuests {
        /** The day and week (see {@link Quests#dayKey}) the lists were drawn for; -1 before the first draw. */
        long dailyKey = -1;
        long weeklyKey = -1;
        List<Active> daily = new ArrayList<>();
        List<Active> weekly = new ArrayList<>();
        /** Rerolls used on the day {@code rerollDay}. */
        int rerolls;
        long rerollDay = -1;

        List<Active> list(boolean weekly) {
            return weekly ? this.weekly : daily;
        }
    }

    /** The quests everyone has with {@code sameForEveryone}. */
    static final class Shared {
        long dailyKey = -1;
        long weeklyKey = -1;
        List<String> daily = new ArrayList<>();
        List<String> weekly = new ArrayList<>();
    }

    private static final class Saved {
        Map<UUID, PlayerQuests> players = new HashMap<>();
        Shared shared = new Shared();
    }

    private Saved saved = new Saved();

    static QuestData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(QuestData::new, QuestData::load, null), NAME);
    }

    PlayerQuests player(UUID id) {
        return saved.players.computeIfAbsent(id, u -> new PlayerQuests());
    }

    void reset(UUID id) {
        saved.players.remove(id);
        setDirty();
    }

    Shared shared() {
        return saved.shared;
    }

    private static QuestData load(CompoundTag tag, HolderLookup.Provider registries) {
        QuestData data = new QuestData();
        try {
            Saved read = GSON.fromJson(tag.getString("Json"), Saved.class);
            if (read != null) {
                if (read.players == null) {
                    read.players = new HashMap<>();
                }
                if (read.shared == null) {
                    read.shared = new Shared();
                }
                data.saved = read;
            }
        } catch (JsonParseException e) {
            Jadm.LOGGER.error("Could not read the saved quests", e);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Json", GSON.toJson(saved));
        return tag;
    }
}
