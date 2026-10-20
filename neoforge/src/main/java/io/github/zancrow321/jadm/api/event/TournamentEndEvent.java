package io.github.zancrow321.jadm.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** A tournament finished and handed out its prizes. */
public final class TournamentEndEvent extends Event {
    /**
     * Where a duelist finished.
     *
     * @param player the person, or {@code null} for an NPC
     * @param place  1 for the winner; duelists who share a place have the same number
     */
    public record Placement(@Nullable UUID player, String name, int place) {
    }

    private final MinecraftServer server;
    private final String name;
    private final String format;
    private final List<Placement> placements;

    public TournamentEndEvent(MinecraftServer server, String name, String format, List<Placement> placements) {
        this.server = server;
        this.name = name;
        this.format = format;
        this.placements = List.copyOf(placements);
    }

    public MinecraftServer getServer() {
        return server;
    }

    public String getName() {
        return name;
    }

    /** e.g. {@code single}, {@code double}, {@code swiss}, {@code draft} or {@code sealed}. */
    public String getFormat() {
        return format;
    }

    /** Everyone, best first. */
    public List<Placement> getPlacements() {
        return placements;
    }

    /** The online people who finished in {@code place}. */
    public List<ServerPlayer> getPlayers(int place) {
        return placements.stream().filter(p -> p.place() == place && p.player() != null)
                .map(p -> server.getPlayerList().getPlayer(p.player())).filter(java.util.Objects::nonNull).toList();
    }

    /** The winner's name, or {@code ""} if no one placed first. */
    public String getWinnerName() {
        return placements.stream().filter(p -> p.place() == 1).map(Placement::name).findFirst().orElse("");
    }
}
