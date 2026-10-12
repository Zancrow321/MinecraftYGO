package io.github.zancrow321.jadm.clan;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The FTB Teams link: when FTB Teams is installed and {@code [clans] ftbTeams} is on, every clan is also an FTB Teams
 * party with the same members, leader (the party owner), officers, name, color, motto and "free to join", so FTB mods
 * (chunk claims, quests, team chat, {@code /ftbteams}) treat the whole clan as one team.
 * <p>
 * Changes on either side carry over. Clan commands do the same thing to the party; joining, leaving, a new owner or
 * new settings in FTB Teams arrive as its events. Officers changed in FTB Teams fire no event, so ranks are compared
 * every few seconds, with FTB Teams' ranks winning.
 * <p>
 * FTB Teams is optional: its classes are only touched inside {@link Ftb}, which loads only once {@link #LOADED} says
 * the mod is there.
 */
public final class ClanParties {
    public static final boolean LOADED = ModList.get().isLoaded("ftbteams");
    /** How often the clans are compared with their parties, in ticks. */
    private static final int CHECK_TICKS = 100;

    private ClanParties() {
    }

    public static boolean active() {
        return LOADED && JadmServerConfig.SPEC.isLoaded() && JadmServerConfig.CLANS.enabled.get()
                && JadmServerConfig.CLANS.ftbTeams.get();
    }

    /** Registers the FTB Teams listeners; called once while the mod loads. */
    public static void init() {
        if (LOADED) {
            Ftb.init();
        }
    }

    /** Why FTB Teams keeps the player from founding a clan, or {@code null}. */
    public static String whyNotFound(ServerPlayer player) {
        return active() ? Ftb.whyNotFound(player) : null;
    }

    /** Why FTB Teams keeps the player from joining the clan, or {@code null}. */
    public static String whyNotJoin(ServerPlayer player, Clans.Clan clan) {
        return active() ? Ftb.whyNotJoin(player, clan) : null;
    }

    /** A clan was founded: the founder's party becomes the clan's, or the clan gets a new party. */
    public static void founded(Clans.Clan clan, ServerPlayer founder) {
        if (active()) {
            Ftb.link(founder.server, clan, founder);
        }
    }

    /** The player joined the clan: they join its party too. */
    public static void joined(MinecraftServer server, Clans.Clan clan, UUID player, String name) {
        if (active()) {
            Ftb.join(server, clan, player, name);
        }
    }

    /** The player left the clan or was removed from it ({@code kicked}): they leave its party too. */
    public static void left(MinecraftServer server, Clans.Clan clan, UUID player, String name, boolean kicked) {
        if (active()) {
            Ftb.leave(server, clan, player, name, kicked);
        }
    }

    /** The leader made a member an officer or a member again. */
    public static void roleChanged(Clans.Clan clan, ServerPlayer by, UUID player, String name, Clans.Role role) {
        if (active()) {
            Ftb.role(clan, by, player, name, role);
        }
    }

    /** The leader handed the lead over: the new leader owns the party. */
    public static void leaderChanged(Clans.Clan clan, ServerPlayer by, UUID player, String name) {
        if (active()) {
            Ftb.owner(clan, by, player, name);
        }
    }

    /** The clan's name, color, motto or "open" changed. */
    public static void looksChanged(MinecraftServer server, Clans.Clan clan) {
        if (active()) {
            Ftb.push(server, clan);
        }
    }

    /** The party's name as {@code /ftbteams} commands take it, or {@code null} without one. */
    public static String partyName(Clans.Clan clan) {
        return active() ? Ftb.shortName(clan) : null;
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_TICKS == 0 && active()) {
            Ftb.checkAll(server);
        }
    }

    /** On login: a clan that has no party yet gets one once its leader is around. */
    public static void onLogin(ServerPlayer player) {
        if (active()) {
            Ftb.check(player.server, Clans.get(player.server).of(player.getUUID()), true);
        }
    }

    /** The clan color closest to an RGB color. */
    static String nearestColor(int rgb) {
        String best = ClanText.COLORS.get(0);
        long bestDistance = Long.MAX_VALUE;
        for (String name : ClanText.COLORS) {
            int c = ClanText.rgb(name);
            long dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            long dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            long db = (c & 0xFF) - (rgb & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = name;
            }
        }
        return best;
    }

    private static final class Ftb {
        /** Clans that could not get a party; tried again when their leader logs in. */
        static final Set<UUID> UNLINKABLE = new HashSet<>();

        static void init() {
            TeamEvent.PLAYER_JOINED_PARTY.register(e -> {
                if (active()) {
                    joinedParty(e.getTeam(), e.getPlayer().getUUID(), e.getPlayer().getScoreboardName());
                }
            });
            TeamEvent.PLAYER_LEFT_PARTY.register(e -> {
                if (active()) {
                    leftParty(e.getTeam(), e.getPlayerId(), e.getTeamDeleted());
                }
            });
            TeamEvent.OWNERSHIP_TRANSFERRED.register(e -> {
                if (active()) {
                    Clans.Clan clan = clanOf(e.getTeam());
                    if (clan != null && e.getTeam() instanceof PartyTeam party) {
                        pullRanks(manager().getServer(), clan, party);
                    }
                }
            });
            TeamEvent.PROPERTIES_CHANGED.register(e -> {
                if (active()) {
                    Clans.Clan clan = clanOf(e.getTeam());
                    if (clan != null && e.getTeam() instanceof PartyTeam party) {
                        pull(manager().getServer(), clan, party);
                    }
                }
            });
        }

        static Clans.Role role(TeamRank rank) {
            return switch (rank) {
                case OWNER -> Clans.Role.LEADER;
                case OFFICER -> Clans.Role.OFFICER;
                default -> Clans.Role.MEMBER;
            };
        }

        static TeamManager manager() {
            return FTBTeamsAPI.api().isManagerLoaded() ? FTBTeamsAPI.api().getManager() : null;
        }

        static PartyTeam party(Clans.Clan clan) {
            TeamManager m = manager();
            if (m == null || clan == null || clan.party == null) {
                return null;
            }
            return m.getTeamByID(clan.party).filter(t -> t instanceof PartyTeam && t.isValid())
                    .map(t -> (PartyTeam) t).orElse(null);
        }

        static Clans.Clan clanOf(Team team) {
            TeamManager m = manager();
            if (m == null || team == null) {
                return null;
            }
            for (Clans.Clan c : Clans.get(m.getServer()).all()) {
                if (team.getId().equals(c.party)) {
                    return c;
                }
            }
            return null;
        }

        static String shortName(Clans.Clan clan) {
            PartyTeam party = party(clan);
            return party == null ? null : party.getShortName();
        }

        static String whyNotFound(ServerPlayer player) {
            TeamManager m = manager();
            Team team = m == null ? null : m.getTeamForPlayer(player).orElse(null);
            if (!(team instanceof PartyTeam party)) {
                return null;
            }
            String name = party.getProperty(TeamProperties.DISPLAY_NAME);
            if (!party.getOwner().equals(player.getUUID())) {
                return "You are in the FTB Teams party " + name + ". Its owner can turn it into a clan; or leave it "
                        + "first with /ftbteams party leave.";
            }
            if (clanOf(party) != null) {
                return "Your FTB Teams party already is a clan.";
            }
            Clans clans = Clans.get(player.server);
            for (UUID id : party.getMembers()) {
                if (!id.equals(player.getUUID()) && clans.of(id) != null) {
                    return nameOf(m, id) + " from your FTB Teams party is in another clan. They need to leave one of "
                            + "the two first.";
                }
            }
            return null;
        }

        static String whyNotJoin(ServerPlayer player, Clans.Clan clan) {
            TeamManager m = manager();
            Team team = m == null ? null : m.getTeamForPlayer(player).orElse(null);
            PartyTeam party = party(clan);
            if (team instanceof PartyTeam own && (party == null || !own.getId().equals(party.getId()))) {
                return "You are in the FTB Teams party " + own.getProperty(TeamProperties.DISPLAY_NAME) + ". Leave it "
                        + "first with /ftbteams party leave, then join the clan.";
            }
            return null;
        }

        /**
         * Gives the clan a party: the leader's own party when they own one that is no clan yet, else a new one. The
         * party's members join the clan and the clan's members join the party, as far as each can.
         *
         * @return whether the clan has a party now
         */
        static boolean link(MinecraftServer server, Clans.Clan clan, ServerPlayer leader) {
            TeamManager m = manager();
            Team team = m == null ? null : m.getTeamForPlayer(leader).orElse(null);
            if (team == null) {
                return false;
            }
            Clans clans = Clans.get(server);
            PartyTeam party;
            if (team instanceof PartyTeam own) {
                if (!own.getOwner().equals(leader.getUUID()) || clanOf(own) != null) {
                    return false;
                }
                party = own;
                if (clan.motto.isEmpty()) {
                    clan.motto = cut(own.getProperty(TeamProperties.DESCRIPTION));
                }
            } else {
                try {
                    party = ((TeamManagerImpl) m).createParty(leader.getUUID(), leader, clan.name, clan.motto,
                            Color4I.rgb(ClanText.rgb(clan.color)));
                } catch (CommandSyntaxException e) {
                    Jadm.LOGGER.info("No FTB Teams party for clan [{}]: {}", clan.tag, e.getMessage());
                    return false;
                }
            }
            UNLINKABLE.remove(clan.id);
            clan.party = party.getId();
            clans.changed();
            for (UUID id : List.copyOf(party.getMembers())) {
                if (!clan.members.containsKey(id) && clans.of(id) == null) {
                    clans.join(clan, id, nameOf(m, id));
                    clan.members.get(id).role = Clans.Role.MEMBER;
                }
            }
            for (Map.Entry<UUID, Clans.Member> e : List.copyOf(clan.members.entrySet())) {
                UUID id = e.getKey();
                if (!party.getMembers().contains(id)) {
                    join(server, clan, id, e.getValue().name);
                }
                if (party.getMembers().contains(id) && e.getValue().role == Clans.Role.OFFICER
                        && party.getRankForPlayer(id) == TeamRank.MEMBER) {
                    try {
                        party.promote(leader, List.of(new GameProfile(id, e.getValue().name)));
                    } catch (CommandSyntaxException ignored) {
                        // The next check takes the party's rank.
                    }
                }
            }
            pullRanks(server, clan, party);
            push(server, clan);
            ClanCommands.refresh(server, clan);
            ClanText.tell(server, clan, Component.literal("The clan is the FTB Teams party " + party.getShortName()
                    + " too: claims, quests and team chat of FTB mods count for the whole clan.")
                    .withStyle(ChatFormatting.GRAY));
            return true;
        }

        static void join(MinecraftServer server, Clans.Clan clan, UUID player, String name) {
            PartyTeam party = party(clan);
            if (party == null || party.getMembers().contains(player)) {
                return;
            }
            Team team = manager().getTeamForPlayerID(player).orElse(null);
            if (team == null || !team.isPlayerTeam()) {
                return;
            }
            try {
                party.join(server.getPlayerList().getPlayer(player), new GameProfile(player, name));
            } catch (CommandSyntaxException e) {
                // A full party, say; the clan check tries again every few seconds.
                Jadm.LOGGER.debug("{} could not join the FTB Teams party of clan [{}]: {}", name, clan.tag,
                        e.getMessage());
            }
        }

        static void leave(MinecraftServer server, Clans.Clan clan, UUID player, String name, boolean kicked) {
            PartyTeam party = party(clan);
            if (party == null || !party.getMembers().contains(player) || party.getOwner().equals(player)) {
                return;
            }
            try {
                if (kicked) {
                    party.kick(server.createCommandSourceStack(), List.of(new GameProfile(player, name)));
                } else {
                    party.leave(player);
                }
            } catch (CommandSyntaxException e) {
                Jadm.LOGGER.info("{} could not leave the FTB Teams party of clan [{}]: {}", name, clan.tag,
                        e.getMessage());
            }
        }

        static void role(Clans.Clan clan, ServerPlayer by, UUID player, String name, Clans.Role role) {
            PartyTeam party = party(clan);
            if (party == null || !party.getMembers().contains(player)) {
                return;
            }
            TeamRank rank = party.getRankForPlayer(player);
            try {
                if (role == Clans.Role.OFFICER && rank == TeamRank.MEMBER) {
                    party.promote(by, List.of(new GameProfile(player, name)));
                } else if (role == Clans.Role.MEMBER && rank == TeamRank.OFFICER) {
                    party.demote(by, List.of(new GameProfile(player, name)));
                }
            } catch (CommandSyntaxException e) {
                Jadm.LOGGER.info("FTB Teams rank of {} in clan [{}] unchanged: {}", name, clan.tag, e.getMessage());
            }
        }

        static void owner(Clans.Clan clan, ServerPlayer by, UUID player, String name) {
            PartyTeam party = party(clan);
            if (party == null || party.getOwner().equals(player) || !party.getMembers().contains(player)) {
                return;
            }
            try {
                party.transferOwnership(by.createCommandSourceStack(), new GameProfile(player, name));
            } catch (CommandSyntaxException e) {
                Jadm.LOGGER.info("FTB Teams owner of clan [{}] unchanged: {}", clan.tag, e.getMessage());
            }
        }

        /** Clan to party: name, motto, color and "free to join". */
        static void push(MinecraftServer server, Clans.Clan clan) {
            PartyTeam party = party(clan);
            if (party == null) {
                return;
            }
            party.setProperty(TeamProperties.DISPLAY_NAME, clan.name);
            party.setProperty(TeamProperties.DESCRIPTION, clan.motto);
            party.setProperty(TeamProperties.COLOR, Color4I.rgb(ClanText.rgb(clan.color)));
            party.setProperty(TeamProperties.FREE_TO_JOIN, clan.open);
            party.markDirty();
            ((TeamManagerImpl) manager()).syncToAll(party);
        }

        /** Party to clan, after the party's settings were changed in FTB Teams. */
        static void pull(MinecraftServer server, Clans.Clan clan, PartyTeam party) {
            boolean changed = false;
            String name = party.getProperty(TeamProperties.DISPLAY_NAME).trim();
            if (!name.equals(clan.name)) {
                if (ClanCommands.checkName(Clans.get(server), name, clan) == null) {
                    clan.name = name;
                    changed = true;
                } else {
                    // A name the clans can't take: the party gets the clan's back.
                    party.setProperty(TeamProperties.DISPLAY_NAME, clan.name);
                    party.markDirty();
                    ((TeamManagerImpl) manager()).syncToAll(party);
                }
            }
            String motto = cut(party.getProperty(TeamProperties.DESCRIPTION));
            if (!motto.equals(clan.motto)) {
                clan.motto = motto;
                changed = true;
            }
            int rgb = party.getProperty(TeamProperties.COLOR).rgb() & 0xFFFFFF;
            if (rgb != ClanText.rgb(clan.color)) {
                String color = nearestColor(rgb);
                if (!color.equals(clan.color)) {
                    clan.color = color;
                    changed = true;
                }
            }
            boolean open = party.getProperty(TeamProperties.FREE_TO_JOIN);
            if (open != clan.open) {
                clan.open = open;
                changed = true;
            }
            if (changed) {
                Clans.get(server).changed();
                ClanCommands.refresh(server, clan);
            }
        }

        /** Party to clan: the owner leads, officers are officers. */
        static void pullRanks(MinecraftServer server, Clans.Clan clan, PartyTeam party) {
            boolean changed = false;
            boolean ownerInClan = clan.members.containsKey(party.getOwner());
            for (Map.Entry<UUID, Clans.Member> e : clan.members.entrySet()) {
                if (!party.getMembers().contains(e.getKey())
                        || !ownerInClan && e.getValue().role == Clans.Role.LEADER) {
                    continue;
                }
                Clans.Role role = role(party.getRankForPlayer(e.getKey()));
                if (e.getValue().role != role) {
                    e.getValue().role = role;
                    changed = true;
                }
            }
            if (clan.leader() == null) {
                // The owner isn't in the clan (yet): someone has to lead it.
                clan.members.values().stream().filter(m -> m.role == Clans.Role.OFFICER).findFirst()
                        .or(() -> clan.members.values().stream().findFirst())
                        .ifPresent(m -> m.role = Clans.Role.LEADER);
                changed = true;
            }
            if (changed) {
                Clans.get(server).changed();
                ClanCommands.refresh(server, clan);
            }
        }

        static void joinedParty(Team team, UUID player, String name) {
            Clans.Clan clan = clanOf(team);
            if (clan == null || clan.members.containsKey(player)) {
                return;
            }
            MinecraftServer server = manager().getServer();
            Clans clans = Clans.get(server);
            Clans.Clan old = clans.of(player);
            if (old != null) {
                if (old.role(player) == Clans.Role.LEADER) {
                    // Leading a clan without a party: that clan comes first.
                    return;
                }
                ClanCommands.dropMember(server, old, player);
            }
            clans.join(clan, player, name);
            if (team instanceof PartyTeam party) {
                pullRanks(server, clan, party);
            }
            ClanText.tell(server, clan, Component.literal(name + " joined the clan ").withStyle(ChatFormatting.GREEN)
                    .append(ClanText.tag(clan)).append(Component.literal(" through FTB Teams!")
                            .withStyle(ChatFormatting.GREEN)));
            ClanCommands.refresh(server, clan);
        }

        static void leftParty(Team team, UUID player, boolean deleted) {
            Clans.Clan clan = clanOf(team);
            if (clan == null || !clan.members.containsKey(player)) {
                return;
            }
            MinecraftServer server = manager().getServer();
            if (deleted || clan.members.size() == 1) {
                // The last member left the party: the clan ends with it.
                long treasury = ClanCommands.disband(server, clan);
                if (treasury > 0) {
                    Points.get(server).add(server, player, treasury);
                    ServerPlayer online = server.getPlayerList().getPlayer(player);
                    if (online != null) {
                        online.sendSystemMessage(Component.literal("The clan treasury, " + Points.format(treasury)
                                + ", goes to you.").withStyle(ChatFormatting.GRAY));
                    }
                }
                return;
            }
            String name = clan.members.get(player).name;
            boolean led = clan.role(player) == Clans.Role.LEADER;
            ClanCommands.dropMember(server, clan, player);
            if (led && team instanceof PartyTeam party) {
                pullRanks(server, clan, party);
            }
            ClanText.tell(server, clan, Component.literal(name + " left the clan (and its FTB Teams party).")
                    .withStyle(ChatFormatting.GRAY));
            ClanCommands.refresh(server, clan);
            ClanCommands.refresh(server, player);
        }

        static void checkAll(MinecraftServer server) {
            for (Clans.Clan clan : List.copyOf(Clans.get(server).all())) {
                check(server, clan, false);
            }
        }

        /**
         * Compares a clan with its party, and gives it one if its leader is around to own it (a clan that could not
         * get one is only tried again on {@code retry}).
         */
        static void check(MinecraftServer server, Clans.Clan clan, boolean retry) {
            if (clan == null || manager() == null) {
                return;
            }
            PartyTeam party = party(clan);
            if (party == null) {
                if (clan.party != null) {
                    clan.party = null;
                    Clans.get(server).changed();
                }
                UUID leader = clan.leader();
                ServerPlayer online = leader == null ? null : server.getPlayerList().getPlayer(leader);
                if (online != null && (retry || !UNLINKABLE.contains(clan.id)) && !link(server, clan, online)) {
                    UNLINKABLE.add(clan.id);
                }
                return;
            }
            for (Map.Entry<UUID, Clans.Member> e : List.copyOf(clan.members.entrySet())) {
                join(server, clan, e.getKey(), e.getValue().name);
            }
            for (UUID id : List.copyOf(party.getMembers())) {
                if (!clan.members.containsKey(id)) {
                    joinedParty(party, id, nameOf(manager(), id));
                }
            }
            pullRanks(server, clan, party);
        }

        static String nameOf(TeamManager m, UUID id) {
            return ((TeamManagerImpl) m).getPlayerName(id).getString();
        }

        static String cut(String text) {
            String t = text == null ? "" : text.trim();
            return t.length() > ClanCommands.MOTTO ? t.substring(0, ClanCommands.MOTTO) : t;
        }
    }
}
