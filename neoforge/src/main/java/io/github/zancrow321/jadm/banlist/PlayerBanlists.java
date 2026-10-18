package io.github.zancrow321.jadm.banlist;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The banlist players play with instead of the server's {@code banlist} setting, set with
 * {@code /jadm banlist use <list> <player>}: a setting like "genesys", "ocg" or a file name, per player.
 */
public final class PlayerBanlists extends SavedData {
    private static final String NAME = "jadm_player_banlists";

    private final Map<UUID, String> players = new HashMap<>();

    public static PlayerBanlists get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlayerBanlists::new, PlayerBanlists::load, null), NAME);
    }

    /** @return the player's own banlist setting, or {@code null} when they play with the server's */
    public String setting(UUID player) {
        return players.get(player);
    }

    /** Sets a player's own banlist setting; {@code null} gives them the server's again. */
    public void set(UUID player, String setting) {
        if (setting == null) {
            players.remove(player);
        } else {
            players.put(player, setting);
        }
        setDirty();
    }

    public Map<UUID, String> all() {
        return Map.copyOf(players);
    }

    private static PlayerBanlists load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerBanlists data = new PlayerBanlists();
        CompoundTag players = tag.getCompound("Players");
        for (String key : players.getAllKeys()) {
            data.players.put(UUID.fromString(key), players.getString(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        this.players.forEach((id, setting) -> players.putString(id.toString(), setting));
        tag.put("Players", players);
        return tag;
    }
}
