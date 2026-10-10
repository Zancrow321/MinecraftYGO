package io.github.zancrow321.jadm.api.event;

import io.github.zancrow321.jadm.ranking.Tiers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** A ranked duel moved a player's rating. Ranks go 0 Bronze, 1 Silver, 2 Gold, 3 Platinum, 4 Diamond, 5 Duel King. */
public final class RankChangeEvent extends Event {
    private final MinecraftServer server;
    private final UUID playerId;
    private final String name;
    private final int ratingBefore;
    private final int ratingAfter;
    private final int rankBefore;
    private final int rankAfter;
    private final boolean promoted;

    /** @param promoted whether they reached a rank they never had before (what the promotion bonus pays for) */
    public RankChangeEvent(MinecraftServer server, UUID playerId, String name, int ratingBefore, int ratingAfter,
                           int rankBefore, int rankAfter, boolean promoted) {
        this.server = server;
        this.playerId = playerId;
        this.name = name;
        this.ratingBefore = ratingBefore;
        this.ratingAfter = ratingAfter;
        this.rankBefore = rankBefore;
        this.rankAfter = rankAfter;
        this.promoted = promoted;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    /** The player, or {@code null} if they went offline. */
    public @Nullable ServerPlayer getPlayer() {
        return server.getPlayerList().getPlayer(playerId);
    }

    public String getName() {
        return name;
    }

    public int getRatingBefore() {
        return ratingBefore;
    }

    public int getRatingAfter() {
        return ratingAfter;
    }

    public int getChange() {
        return ratingAfter - ratingBefore;
    }

    public int getRankBefore() {
        return rankBefore;
    }

    public int getRankAfter() {
        return rankAfter;
    }

    /** e.g. "Gold". */
    public String getRankName() {
        return Tiers.name(rankAfter);
    }

    public boolean isPromoted() {
        return promoted;
    }
}
