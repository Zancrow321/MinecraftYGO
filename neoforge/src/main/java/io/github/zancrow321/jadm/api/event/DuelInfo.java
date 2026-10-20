package io.github.zancrow321.jadm.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Who duels whom, and how.
 *
 * @param duelists   every seat, people and bots, in seat order
 * @param npc        the NPC duelist playing the bot seat, or {@code null}
 * @param ranked     a ranked 1v1 duel that moves the two people's ratings
 * @param ante       each person put a card from their deck up as a stake
 * @param tournament a tournament game
 * @param battleCity a tag duel where each partner plays on their own half of the field
 * @param lifePoints each team's starting life points
 */
public record DuelInfo(MinecraftServer server, List<Duelist> duelists, @Nullable Entity npc, boolean ranked,
                       boolean ante, boolean tournament, boolean battleCity, int lifePoints) {
    /**
     * One seat.
     *
     * @param team   0 or 1
     * @param player the person in it, or {@code null} for a bot or NPC
     */
    public record Duelist(int team, @Nullable UUID player, String name) {
        public boolean bot() {
            return player == null;
        }
    }

    public DuelInfo {
        duelists = List.copyOf(duelists);
    }

    /** A tag duel: two against two. */
    public boolean tag() {
        return duelists.size() > 2;
    }

    /** Whether a bot or NPC sits in any seat. */
    public boolean againstBot() {
        return duelists.stream().anyMatch(Duelist::bot);
    }

    /** The people in the duel who are online. */
    public List<ServerPlayer> players() {
        return duelists.stream().map(d -> d.player() == null ? null : server.getPlayerList().getPlayer(d.player()))
                .filter(Objects::nonNull).toList();
    }

    /** The online people on {@code team}. */
    public List<ServerPlayer> players(int team) {
        return duelists.stream().filter(d -> d.team() == team && d.player() != null)
                .map(d -> server.getPlayerList().getPlayer(d.player())).filter(Objects::nonNull).toList();
    }

    /** {@code player}'s team, or -1 if they aren't in this duel. */
    public int teamOf(UUID player) {
        return duelists.stream().filter(d -> player.equals(d.player())).mapToInt(Duelist::team).findFirst()
                .orElse(-1);
    }

    /** The names of the duelists on {@code team}. */
    public List<String> names(int team) {
        return duelists.stream().filter(d -> d.team() == team).map(Duelist::name).toList();
    }
}
