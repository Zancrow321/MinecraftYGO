package io.github.zancrow321.jadm.clan;

import io.github.zancrow321.jadm.JadmServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every clan is mirrored into a vanilla scoreboard team, so its tag stands in front of its members' names in chat, in
 * the player list and above their heads, and chat mods that read teams show it too.
 */
public final class ClanTeams {
    /** Every clan team's name starts with this; others are left alone. */
    public static final String PREFIX = "jadm_clan_";

    private ClanTeams() {
    }

    static String teamName(Clans.Clan clan) {
        return PREFIX + clan.id.toString().substring(0, 8);
    }

    /** Brings every clan team in line with the clans: creates, updates, fills and removes them. */
    public static void syncAll(MinecraftServer server) {
        Scoreboard board = server.getScoreboard();
        Clans clans = Clans.get(server);
        Set<String> wanted = new HashSet<>();
        if (JadmServerConfig.CLANS.showTag.get()) {
            for (Clans.Clan clan : clans.all()) {
                wanted.add(teamName(clan));
                sync(server, clan);
            }
        }
        for (PlayerTeam team : List.copyOf(board.getPlayerTeams())) {
            if (team.getName().startsWith(PREFIX) && !wanted.contains(team.getName())) {
                board.removePlayerTeam(team);
            }
        }
    }

    /** Creates or updates {@code clan}'s team and puts exactly its members in it. */
    public static void sync(MinecraftServer server, Clans.Clan clan) {
        if (!JadmServerConfig.CLANS.showTag.get()) {
            return;
        }
        Scoreboard board = server.getScoreboard();
        String name = teamName(clan);
        PlayerTeam team = board.getPlayerTeam(name);
        if (team == null) {
            team = board.addPlayerTeam(name);
        }
        team.setDisplayName(Component.literal(clan.name));
        team.setPlayerPrefix(ClanText.tag(clan).append(" "));
        team.setColor(ClanText.color(clan.color));
        team.setNameTagVisibility(Team.Visibility.ALWAYS);
        Set<String> members = new HashSet<>();
        clan.members.values().forEach(m -> members.add(m.name));
        for (String entry : List.copyOf(team.getPlayers())) {
            if (!members.contains(entry)) {
                board.removePlayerFromTeam(entry, team);
            }
        }
        for (String member : members) {
            if (board.getPlayersTeam(member) != team) {
                board.addPlayerToTeam(member, team);
            }
        }
    }

    /** Takes {@code playerName} out of whichever clan team they are in. */
    public static void leave(MinecraftServer server, String playerName) {
        Scoreboard board = server.getScoreboard();
        PlayerTeam team = board.getPlayersTeam(playerName);
        if (team != null && team.getName().startsWith(PREFIX)) {
            board.removePlayerFromTeam(playerName, team);
        }
    }

    /** Removes {@code clan}'s team. */
    public static void remove(MinecraftServer server, Clans.Clan clan) {
        Scoreboard board = server.getScoreboard();
        PlayerTeam team = board.getPlayerTeam(teamName(clan));
        if (team != null) {
            board.removePlayerTeam(team);
        }
    }
}
