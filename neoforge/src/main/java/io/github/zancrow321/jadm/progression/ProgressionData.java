package io.github.zancrow321.jadm.progression;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How far a world has progressed: the id of its newest unlocked product, and with {@code scope = "player"} each
 * player's. Also who has picked their starter deck and who has been given the handbook. Saved by product id so that a data update which adds products keeps everyone where they were.
 */
public final class ProgressionData extends SavedData {
    private static final String NAME = "jadm_progression";

    /** The world's product, or {@code null} until the first step is taken (the start product applies). */
    private String world;
    private final Map<UUID, String> players = new HashMap<>();
    /** Players who have picked their starter deck. */
    private final java.util.Set<UUID> starters = new java.util.HashSet<>();
    /** Players who have been given the handbook on their first join. */
    private final java.util.Set<UUID> guides = new java.util.HashSet<>();

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

    boolean hasStarter(UUID player) {
        return starters.contains(player);
    }

    void starter(UUID player) {
        starters.add(player);
        setDirty();
    }

    /** Marks the player as given the handbook. @return whether they hadn't been given it before */
    public boolean giveGuide(UUID player) {
        if (!guides.add(player)) {
            return false;
        }
        setDirty();
        return true;
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
        for (net.minecraft.nbt.Tag t : tag.getList("Starters", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            data.starters.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
        }
        for (net.minecraft.nbt.Tag t : tag.getList("Guides", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            data.guides.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
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
        net.minecraft.nbt.ListTag starters = new net.minecraft.nbt.ListTag();
        this.starters.forEach(id -> starters.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
        tag.put("Starters", starters);
        net.minecraft.nbt.ListTag guides = new net.minecraft.nbt.ListTag();
        this.guides.forEach(id -> guides.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
        tag.put("Guides", guides);
        return tag;
    }
}
