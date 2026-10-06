package io.github.zancrow321.minecraftygo.progression;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How far a world has progressed: the id of its newest unlocked product, and with {@code scope = "player"} each
 * player's. Saved by product id so that a data update which adds products keeps everyone where they were.
 */
public final class ProgressionData extends SavedData {
    private static final String NAME = "minecraftygo_progression";

    /** The world's product, or {@code null} until the first step is taken (the start product applies). */
    private String world;
    private final Map<UUID, String> players = new HashMap<>();

    public static ProgressionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ProgressionData::new, ProgressionData::load, null), NAME);
    }

    /** @return the world's product id, or {@code null} for the start product */
    String world() {
        return world;
    }

    void world(String id) {
        world = id;
        setDirty();
    }

    /** @return the player's product id, or {@code null} for the start product */
    String player(UUID player) {
        return players.get(player);
    }

    void player(UUID player, String id) {
        players.put(player, id);
        setDirty();
    }

    private static ProgressionData load(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressionData data = new ProgressionData();
        if (tag.contains("World")) {
            data.world = tag.getString("World");
        }
        CompoundTag players = tag.getCompound("Players");
        for (String key : players.getAllKeys()) {
            data.players.put(UUID.fromString(key), players.getString(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (world != null) {
            tag.putString("World", world);
        }
        CompoundTag players = new CompoundTag();
        this.players.forEach((id, product) -> players.putString(id.toString(), product));
        tag.put("Players", players);
        return tag;
    }
}
