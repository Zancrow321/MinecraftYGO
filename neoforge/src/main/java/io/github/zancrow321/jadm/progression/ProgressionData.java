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
 * player's, and the day from which the wait for the next set counts. Also who has picked their starter deck and who has been given the handbook. Saved by product id so that a data update which adds products keeps everyone where they were.
 */
public final class ProgressionData extends SavedData {
    private static final String NAME = "jadm_progression";

    /** The world's product, or {@code null} until the first step is taken (the start product applies). */
    private String world;
    private final Map<UUID, String> players = new HashMap<>();
    /** The Minecraft day the world's (or each player's) wait for the next set counts from; missing until first seen. */
    private Long worldDay;
    private final Map<UUID, Long> playerDays = new HashMap<>();
    /** Players who have picked their starter deck. */
    private final java.util.Set<UUID> starters = new java.util.HashSet<>();
    /** Players who have been given the handbook on their first join. */
    private final java.util.Set<UUID> guides = new java.util.HashSet<>();
    /**
     * The release dates the saved products were reached under ({@code id=YYYY-MM-DD}), so that progress can be carried
     * over when they change, also while the server was down.
     */
    private java.util.List<String> releaseDates = java.util.List.of();

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

    /** @return the day the world's wait for the next set counts from, or {@code null} before it is first seen */
    Long worldDay() {
        return worldDay;
    }

    void worldDay(long day) {
        worldDay = day;
        setDirty();
    }

    Long playerDay(UUID player) {
        return playerDays.get(player);
    }

    void playerDay(UUID player, long day) {
        playerDays.put(player, day);
        setDirty();
    }

    /** Every player with progress of their own. */
    java.util.Set<UUID> players() {
        return java.util.Set.copyOf(players.keySet());
    }

    java.util.List<String> releaseDates() {
        return releaseDates;
    }

    void releaseDates(java.util.List<String> dates) {
        releaseDates = java.util.List.copyOf(dates);
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
        if (tag.contains("WorldDay")) {
            data.worldDay = tag.getLong("WorldDay");
        }
        CompoundTag days = tag.getCompound("PlayerDays");
        for (String key : days.getAllKeys()) {
            data.playerDays.put(UUID.fromString(key), days.getLong(key));
        }
        for (net.minecraft.nbt.Tag t : tag.getList("Starters", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            data.starters.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
        }
        for (net.minecraft.nbt.Tag t : tag.getList("Guides", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            data.guides.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
        }
        for (net.minecraft.nbt.Tag t : tag.getList("ReleaseDates", net.minecraft.nbt.Tag.TAG_STRING)) {
            data.releaseDates = new java.util.ArrayList<>(data.releaseDates);
            data.releaseDates.add(t.getAsString());
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
        if (worldDay != null) {
            tag.putLong("WorldDay", worldDay);
        }
        CompoundTag days = new CompoundTag();
        playerDays.forEach((id, day) -> days.putLong(id.toString(), day));
        tag.put("PlayerDays", days);
        net.minecraft.nbt.ListTag starters = new net.minecraft.nbt.ListTag();
        this.starters.forEach(id -> starters.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
        tag.put("Starters", starters);
        net.minecraft.nbt.ListTag guides = new net.minecraft.nbt.ListTag();
        this.guides.forEach(id -> guides.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
        tag.put("Guides", guides);
        net.minecraft.nbt.ListTag dates = new net.minecraft.nbt.ListTag();
        releaseDates.forEach(d -> dates.add(net.minecraft.nbt.StringTag.valueOf(d)));
        tag.put("ReleaseDates", dates);
        return tag;
    }
}
