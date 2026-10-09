package io.github.zancrow321.jadm.duel;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.arena.DuelArena;
import io.github.zancrow321.jadm.cosmetics.Cosmetics;
import io.github.zancrow321.jadm.cosmetics.PlayerCosmetics;
import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.Ruleset;
import io.github.zancrow321.jadm.engine.ai.DuelistAi;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.duel.DuelTable;
import io.github.zancrow321.jadm.engine.duel.DuelView;
import io.github.zancrow321.jadm.engine.duel.ViewCodec;
import io.github.zancrow321.jadm.entity.DuelistNpc;
import io.github.zancrow321.jadm.item.DeckBoxItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.github.zancrow321.jadm.item.JadmItems;
import io.github.zancrow321.jadm.network.DuelClockPayload;
import io.github.zancrow321.jadm.network.DuelFieldPayload;
import io.github.zancrow321.jadm.network.DuelResultPayload;
import io.github.zancrow321.jadm.network.DuelistStatePayload;
import io.github.zancrow321.jadm.network.DuelViewPayload;
import io.github.zancrow321.jadm.ranking.RankedDuels;
import io.github.zancrow321.jadm.ranking.Ranking;
import io.github.zancrow321.jadm.ranking.Tiers;
import io.github.zancrow321.jadm.starchips.StarChips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks invitations and running duels on one server: 1v1 challenges (optionally with an ante), tag duels for two
 * teams of two, and duels against bots. All methods run on the server thread.
 */
public final class DuelManager {
    private static final long INVITE_TIMEOUT_TICKS = 20 * 60;
    /** Lent to players without a deck box, if the server allows it. */
    private static final String STARTER_DECK = "starter_yugi";
    private static final List<String> BOT_DECKS = List.of("starter_kaiba", "starter_yugi");
    private static final List<String> BOT_NAMES = List.of("Duel Bot", "Bandit Bot", "Rare Hunter Bot");
    /** How far in front of a player a duel against bots is projected (the field is about 16 blocks long). */
    private static final double BOT_FIELD_DISTANCE = 8;

    private static DuelManager instance;

    private final MinecraftServer server;
    /** invitee -> the invitation waiting for their answer */
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, ServerDuel> duelsByPlayer = new HashMap<>();
    private final Map<UUID, ServerDuel> duelsBySpectator = new HashMap<>();
    private final List<ServerDuel> duels = new ArrayList<>();
    /** Where each dueling person stands (once they're on the ground); they are held there until the duel ends. */
    private final Map<UUID, Vec3> anchors = new HashMap<>();

    /** One duelist: a person, or a bot when {@code player} is {@code null}. */
    private record Entrant(int team, UUID player, String name) {
    }

    /**
     * A duel waiting for everyone invited to accept.
     *
     * @param npc   the NPC duelist sitting in the bot seat, or {@code null}
     * @param split a Battle City tag duel, each partner on their own half of the team's zones
     * @param ranked a ranked 1v1 duel between two people, which moves their ratings
     */
    private record Invite(UUID host, List<Entrant> entrants, Set<UUID> pending, boolean ante, long expiresAt,
                          DuelistNpc npc, boolean split, boolean ranked) {
        Invite(UUID host, List<Entrant> entrants, Set<UUID> pending, boolean ante, long expiresAt, DuelistNpc npc) {
            this(host, entrants, pending, ante, expiresAt, npc, false, false);
        }
    }

    /**
     * A running duel and who sits where ({@code null} seats are bots).
     *
     * @param ante the escrow id of the ante, or {@code null} for a duel without one
     * @param npc the NPC duelist playing the bot seat, or {@code null}
     * @param match the organized duel (a tournament game) this is, or {@code null}
     * @param ranked whether it moves the two people's ratings
     */
    private record ServerDuel(DuelTable table, UUID[] seats, DuelFieldPayload field, UUID ante, DuelistNpc npc,
                              Set<UUID> spectators, Clock clock, MatchSetup match, boolean ranked) {
    }

    /**
     * The turn time limit: ticks each seat spent choosing this turn, and the bot that chooses for whoever ran out.
     */
    private static final class Clock {
        final DuelistAi standIn;
        final Map<Integer, Integer> used = new HashMap<>();
        final Set<Integer> outOfTime = new HashSet<>();
        int turn;
        /** Answers the stand-in gave for the current prompt that weren't allowed. */
        int refused;

        Clock(long seed) {
            standIn = new DuelistAi(seed, JadmData.cards());
        }
    }

    private DuelManager(MinecraftServer server) {
        this.server = server;
        AnteEscrow.get(server).refundUnsettled();
    }

    public static DuelManager get(MinecraftServer server) {
        if (instance == null || instance.server != server) {
            instance = new DuelManager(server);
        }
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.duels.forEach(d -> d.table().close());
            instance = null;
        }
    }

    public boolean inDuel(ServerPlayer player) {
        return duelsByPlayer.containsKey(player.getUUID());
    }

    /** Invites {@code target} to a 1v1 duel, with an ante if {@code ante} and the server allows it. */
    public void challenge(ServerPlayer challenger, ServerPlayer target, boolean ante) {
        challenge(challenger, target, ante, false);
    }

    /**
     * Invites {@code target} to a 1v1 duel, with an ante if {@code ante} and the server allows it, ranked if
     * {@code ranked}.
     */
    public void challenge(ServerPlayer challenger, ServerPlayer target, boolean ante, boolean ranked) {
        if (challenger == target) {
            challenger.sendSystemMessage(Component.literal("You can't duel yourself. Try /jadm duel bot"));
            return;
        }
        if (ante && !JadmServerConfig.ALLOW_ANTE.get()) {
            challenger.sendSystemMessage(Component.literal("Ante duels are turned off on this server."));
            return;
        }
        if (ranked) {
            String why = RankedDuels.whyNot(server, challenger.getUUID(), target.getUUID());
            if (why != null) {
                challenger.sendSystemMessage(Component.literal(why));
                return;
            }
        }
        invite(challenger, List.of(new Entrant(0, challenger.getUUID(), challenger.getScoreboardName()),
                new Entrant(1, target.getUUID(), target.getScoreboardName())), ante, false, ranked);
    }

    /**
     * Invites players to a tag duel: the host and {@code partner} against {@code opponents}. A {@code null} player
     * is a bot. With {@code split}, it is a Battle City duel: each partner plays on their own half of the field.
     */
    public void tag(ServerPlayer host, ServerPlayer partner, ServerPlayer opponent1, ServerPlayer opponent2,
                    boolean split) {
        List<Entrant> entrants = new ArrayList<>();
        entrants.add(new Entrant(0, host.getUUID(), host.getScoreboardName()));
        int bots = 0;
        ServerPlayer[] others = {partner, opponent1, opponent2};
        for (int i = 0; i < others.length; i++) {
            int team = i == 0 ? 0 : 1;
            entrants.add(others[i] == null
                    ? new Entrant(team, null, BOT_NAMES.get(bots++ % BOT_NAMES.size()))
                    : new Entrant(team, others[i].getUUID(), others[i].getScoreboardName()));
        }
        Set<UUID> people = new HashSet<>();
        for (Entrant e : entrants) {
            if (e.player() != null && !people.add(e.player())) {
                host.sendSystemMessage(Component.literal("Each duelist can only take one seat."));
                return;
            }
        }
        invite(host, entrants, false, split);
    }

    private void invite(ServerPlayer host, List<Entrant> entrants, boolean ante) {
        invite(host, entrants, ante, false, false);
    }

    private void invite(ServerPlayer host, List<Entrant> entrants, boolean ante, boolean split) {
        invite(host, entrants, ante, split, false);
    }

    private void invite(ServerPlayer host, List<Entrant> entrants, boolean ante, boolean split, boolean ranked) {
        for (Entrant e : entrants) {
            ServerPlayer player = player(e.player());
            if (player != null && inDuel(player)) {
                host.sendSystemMessage(Component.literal(e.name() + " is already in a duel."));
                return;
            }
        }
        Set<UUID> pending = new LinkedHashSet<>();
        entrants.stream().map(Entrant::player).filter(Objects::nonNull).filter(id -> !id.equals(host.getUUID()))
                .forEach(pending::add);
        Invite invite = new Invite(host.getUUID(), List.copyOf(entrants), pending, ante,
                server.getTickCount() + INVITE_TIMEOUT_TICKS, null, split, ranked);
        if (pending.isEmpty()) {
            launch(invite);
            return;
        }
        String matchup = matchup(entrants) + (ranked && ante ? " (ranked, ante)" : ranked ? " (ranked)"
                : ante ? " (ante)" : split ? " (Battle City)" : "");
        String chips = entrants.size() == 2
                ? StarChips.get(server).inviteNote(entrants.get(0).player(), entrants.get(1).player()) : "";
        host.sendSystemMessage(Component.literal("Invitation sent: " + matchup + "."));
        for (UUID id : pending) {
            invites.put(id, invite);
            ServerPlayer target = player(id);
            if (target == null) {
                continue;
            }
            Component accept = Component.literal("[Accept]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jadm accept")));
            String how = entrants.size() == 2 && DuelDisks.has(target)
                    ? " Right-click them with your Duel Disk or click " : " Click ";
            target.sendSystemMessage(Component.literal(host.getScoreboardName() + " invites you: " + matchup
                    + (ante ? ". The winner takes a random card from the loser's deck box." : ".")
                    + (ranked ? rankedNote(host, target) : "") + chips + how)
                    .append(accept));
        }
    }

    /** " Ranked: your rating 1180 (Silver) against their 1216 (Gold)." */
    private String rankedNote(ServerPlayer host, ServerPlayer target) {
        Ranking ranking = Ranking.get(server);
        int mine = ranking.rating(target.getUUID());
        int theirs = ranking.rating(host.getUUID());
        List<Integer> starts = JadmServerConfig.RANKING.tierStarts();
        return " Ranked: your rating " + mine + " (" + Tiers.name(Tiers.of(mine, starts)) + ") against their "
                + theirs + " (" + Tiers.name(Tiers.of(theirs, starts)) + ").";
    }

    private static String matchup(List<Entrant> entrants) {
        return teamName(entrants, 0) + " vs " + teamName(entrants, 1);
    }

    private static String teamName(List<Entrant> entrants, int team) {
        return String.join(" & ", entrants.stream().filter(e -> e.team() == team).map(Entrant::name).toList());
    }

    /**
     * {@code player} right-clicked {@code other} with a duel disk: accepts their invitation if they sent one,
     * otherwise challenges them (with an ante when sneaking).
     */
    public void diskInteract(ServerPlayer player, ServerPlayer other) {
        if (!DuelDisks.has(other)) {
            player.sendSystemMessage(Component.literal(other.getScoreboardName() + " has no Duel Disk."));
            return;
        }
        Invite received = invites.get(player.getUUID());
        if (received != null && received.host().equals(other.getUUID())) {
            accept(player);
            return;
        }
        Invite sent = invites.get(other.getUUID());
        if (sent != null && sent.host().equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Waiting for " + other.getScoreboardName() + " to accept."));
            return;
        }
        challenge(player, other, player.isShiftKeyDown() && JadmServerConfig.ALLOW_ANTE.get());
    }

    public void accept(ServerPlayer player) {
        Invite invite = invites.remove(player.getUUID());
        if (invite == null || invite.expiresAt() < server.getTickCount()) {
            player.sendSystemMessage(Component.literal("You have no pending invitation."));
            return;
        }
        invite.pending().remove(player.getUUID());
        if (!invite.pending().isEmpty()) {
            String waiting = String.join(", ", invite.pending().stream().map(this::nameOf).toList());
            for (Entrant e : invite.entrants()) {
                message(e.player(), Component.literal(player.getScoreboardName() + " is in. Waiting for "
                        + waiting + "."));
            }
            return;
        }
        launch(invite);
    }

    public void duelBot(ServerPlayer player) {
        if (inDuel(player)) {
            player.sendSystemMessage(Component.literal("You are already in a duel."));
            return;
        }
        invite(player, List.of(new Entrant(0, player.getUUID(), player.getScoreboardName()),
                new Entrant(1, null, BOT_NAMES.get(0))), false);
    }

    /** {@code player} right-clicked an NPC duelist: duel it, unless it is busy or wants a rematch break. */
    public void duelNpc(ServerPlayer player, DuelistNpc npc) {
        if (inDuel(player)) {
            player.sendSystemMessage(Component.literal("You are already in a duel."));
            return;
        }
        Component refusal = npc.refusal(player);
        if (refusal != null) {
            player.sendSystemMessage(refusal);
            return;
        }
        launch(new Invite(player.getUUID(), List.of(new Entrant(0, player.getUUID(), player.getScoreboardName()),
                new Entrant(1, null, npc.duelistName())), Set.of(), false, 0, npc));
    }

    /**
     * Starts a 1v1 duel between two people standing on the two podiums of a free duel arena, no invitation needed.
     * The coin toss decides who goes first, as for any duel.
     */
    public void arenaDuel(ServerPlayer first, ServerPlayer second) {
        if (first == second || inDuel(first) || inDuel(second)) {
            return;
        }
        boolean ranked = JadmServerConfig.RANKING.arenaDuels.get()
                && RankedDuels.whyNot(server, first.getUUID(), second.getUUID()) == null;
        launch(new Invite(first.getUUID(), List.of(new Entrant(0, first.getUUID(), first.getScoreboardName()),
                new Entrant(1, second.getUUID(), second.getScoreboardName())), Set.of(), false, 0, null, false,
                ranked));
    }

    /** Starts a tag duel between two pairs standing on the podiums of a free player-built arena. */
    public void arenaDuel(List<ServerPlayer> team0, List<ServerPlayer> team1) {
        List<Entrant> entrants = new ArrayList<>();
        for (int team = 0; team < 2; team++) {
            for (ServerPlayer player : team == 0 ? team0 : team1) {
                if (inDuel(player)) {
                    return;
                }
                entrants.add(new Entrant(team, player.getUUID(), player.getScoreboardName()));
            }
        }
        launch(new Invite(team0.get(0).getUUID(), List.copyOf(entrants), Set.of(), false, 0, null));
    }

    /**
     * Why {@code player} couldn't duel for want of a deck, or {@code null} if they can (with a legal deck box, or a
     * lent starter deck). Tells them nothing.
     */
    public static String deckProblem(ServerPlayer player) {
        io.github.zancrow321.jadm.engine.data.Banlist banlist = JadmData.banlist(JadmData.step(player));
        List<ItemStack> boxes = deckBoxes(player);
        for (ItemStack box : boxes) {
            if (DeckBoxItem.problems(box, player, banlist).isEmpty()) {
                return null;
            }
        }
        if (JadmServerConfig.STARTER_DECKS.get()) {
            return null;
        }
        return boxes.isEmpty() ? "You need a deck box with a legal deck to duel."
                : "Your deck box \"" + boxes.get(0).getHoverName().getString() + "\" isn't legal: "
                + DeckBoxItem.problems(boxes.get(0), player, banlist).get(0);
    }

    /** Everyone accepted: checks decks, takes the ante and starts the duel. */
    private void launch(Invite invite) {
        List<Entrant> entrants = invite.entrants();
        // The coin toss: OCG-Core always lets team 0 go first, so the teams trade places half the time.
        if (server.overworld().getRandom().nextBoolean()) {
            entrants = entrants.stream().map(e -> new Entrant(1 - e.team(), e.player(), e.name())).toList();
        }
        List<ServerPlayer> online = new ArrayList<>();
        for (Entrant e : entrants) {
            if (e.player() == null) {
                continue;
            }
            ServerPlayer player = player(e.player());
            if (player == null || inDuel(player)) {
                broadcast(entrants, Component.literal(e.name() + (player == null ? " is offline." : " is already in a duel.")));
                return;
            }
            online.add(player);
        }
        // In a progression world the duel is played under the rules and banlist of whoever is furthest along.
        int step = online.stream().mapToInt(JadmData::step).max().orElse(JadmData.step(null));
        io.github.zancrow321.jadm.engine.data.Banlist banlist = JadmData.banlist(step);
        List<Deck> decks = new ArrayList<>();
        List<ItemStack> boxes = new ArrayList<>();
        int botIndex = 0;
        for (Entrant e : entrants) {
            if (e.player() == null) {
                Deck npcDeck = invite.npc() != null ? invite.npc().deck(step)
                        : Deck.bundled(BOT_DECKS.get(botIndex++ % BOT_DECKS.size()));
                if (invite.npc() != null && !npcDeck.name().equals(invite.npc().duelistName())) {
                    // A tournament or structure deck: say which, so the player knows what they are up against.
                    broadcast(entrants, Component.literal(invite.npc().duelistName() + " plays " + npcDeck.name()
                            + "."));
                }
                decks.add(npcDeck);
                boxes.add(ItemStack.EMPTY);
                continue;
            }
            ServerPlayer player = player(e.player());
            ItemStack box = legalDeckBox(player, banlist);
            Deck deck = box != null ? DeckBoxItem.toDeck(box) : starterFor(player, invite.ante());
            if (deck == null) {
                broadcast(entrants, Component.literal(e.name() + " has no legal deck."));
                return;
            }
            decks.add(deck);
            boxes.add(box == null ? ItemStack.EMPTY : box);
        }
        List<Entrant> seated = entrants;
        List<ServerPlayer> team0 = online.stream().filter(p -> teamOf(seated, p) == 0).toList();
        List<ServerPlayer> team1 = online.stream().filter(p -> teamOf(seated, p) == 1).toList();
        DuelFieldPayload field = DuelArena.claim(team0, team1, invite.npc());
        if (field == null && invite.npc() != null) {
            // Against an NPC the one person may be on either team; the field is built from their end.
            ServerPlayer person = online.get(0);
            field = fieldAgainstNpc(person, invite.npc());
            field = teamOf(seated, person) == 0 ? field : turned(field);
        }
        if (field == null) {
            field = team1.isEmpty() ? fieldInFrontOf(team0.get(0))
                    : team0.isEmpty() ? turned(fieldInFrontOf(team1.get(0)))
                    : fieldBetween(team0.get(0), team1.get(0));
        }
        if (invite.ranked()) {
            broadcast(entrants, Component.literal("Ranked duel: " + String.join(" vs ", entrants.stream()
                    .map(e -> e.name() + " (" + Ranking.get(server).rating(e.player()) + ")").toList()) + ".")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (start(entrants, decks, field, invite.ante() ? boxes : null, invite.npc(), invite.split(), step, null,
                invite.ranked()) && entrants.size() == 2) {
            StarChips.get(server).begin(entrants.get(0).player(), entrants.get(1).player(), invite.npc() != null);
        }
    }

    /**
     * Starts an organized duel, such as a tournament game, between people standing on the two podiums of a free duel
     * arena (or one person and a bot).
     *
     * @return why it couldn't start, or {@code null} once it has
     */
    public String startMatch(MatchSetup setup) {
        var random = server.overworld().getRandom();
        // OCG-Core lets team 0 go first.
        boolean swapped = setup.firstTeam() == 1 || setup.firstTeam() < 0 && random.nextBoolean();
        List<Entrant> entrants = new ArrayList<>();
        List<ServerPlayer> online = new ArrayList<>();
        for (int i = 0; i < setup.seats().size(); i++) {
            MatchSetup.Seat seat = setup.seats().get(i);
            entrants.add(new Entrant(swapped ? 1 - i : i, seat.player(), seat.name()));
            if (seat.player() != null) {
                ServerPlayer player = player(seat.player());
                if (player == null) {
                    return seat.name() + " is offline";
                }
                if (inDuel(player)) {
                    return seat.name() + " is already in a duel";
                }
                online.add(player);
            }
        }
        int step = online.stream().mapToInt(JadmData::step).max().orElse(JadmData.step(null));
        io.github.zancrow321.jadm.engine.data.Banlist banlist = setup.rules().banlist() != null
                ? setup.rules().banlist() : JadmData.banlist(step);
        List<Deck> decks = new ArrayList<>();
        for (MatchSetup.Seat seat : setup.seats()) {
            Deck deck = seat.deck();
            if (deck == null && seat.player() != null) {
                ServerPlayer player = player(seat.player());
                ItemStack box = legalDeckBox(player, banlist);
                deck = box != null ? DeckBoxItem.toDeck(box) : starterFor(player, false);
                if (deck == null) {
                    return seat.name() + " has no legal deck";
                }
            }
            decks.add(deck != null ? deck : Deck.bundled(BOT_DECKS.get(0)));
        }
        List<ServerPlayer> team0 = online.stream().filter(p -> teamOf(entrants, p) == 0).toList();
        List<ServerPlayer> team1 = online.stream().filter(p -> teamOf(entrants, p) == 1).toList();
        DuelFieldPayload field = DuelArena.claim(team0, team1, setup.npc());
        if (field == null) {
            return "the duelists aren't on the podiums of a free duel arena";
        }
        java.util.function.IntConsumer told = setup.onEnd();
        MatchSetup unswapped = new MatchSetup(setup.seats(), setup.rules(), setup.firstTeam(), setup.npc(),
                setup.ranked(), winner -> told.accept(swapped && (winner == 0 || winner == 1) ? 1 - winner : winner));
        boolean ranked = setup.ranked() && online.size() == 2
                && RankedDuels.whyNot(server, online.get(0).getUUID(), online.get(1).getUUID()) == null;
        return start(entrants, decks, field, null, setup.npc(), false, step, unswapped, ranked) ? null
                : "the duel engine isn't available";
    }

    /** The same field seen from the other end: team 0 gets the far side. */
    private static DuelFieldPayload turned(DuelFieldPayload field) {
        return field == null ? null : field.turned();
    }

    private static int teamOf(List<Entrant> entrants, ServerPlayer player) {
        return entrants.stream().filter(e -> player.getUUID().equals(e.player())).findFirst().orElseThrow().team();
    }

    /** Centered between two duelists, on the lower one's feet level, the first one's side toward them. */
    private static DuelFieldPayload fieldBetween(ServerPlayer first, ServerPlayer second) {
        Vec3 a = first.position();
        Vec3 b = second.position();
        if (a.distanceToSqr(b) < 1 || first.level() != second.level()) {
            return fieldInFrontOf(first);
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));
        return new DuelFieldPayload(true, (a.x + b.x) / 2, Math.min(a.y, b.y), (a.z + b.z) / 2, yaw);
    }

    /**
     * Projected ahead of the player toward the NPC, which steps over to the far end so it doesn't stand on the
     * cards. If there's no room over there it stays put and the field goes between the two of them.
     */
    private static DuelFieldPayload fieldAgainstNpc(ServerPlayer player, DuelistNpc npc) {
        Vec3 a = player.position();
        Vec3 b = npc.position();
        if (a.distanceToSqr(b) < 1 || player.level() != npc.level()) {
            return fieldInFrontOf(player);
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 end = a.add(forward.scale(2 * BOT_FIELD_DISTANCE));
        Vec3 spot = new Vec3(end.x, b.y, end.z);
        if (npc.level().noCollision(npc, npc.getBoundingBox().move(spot.subtract(b)))) {
            npc.moveTo(spot.x, spot.y, spot.z, yaw + 180, 0);
            npc.setYHeadRot(yaw + 180);
            npc.setYBodyRot(yaw + 180);
            Vec3 center = a.add(forward.scale(BOT_FIELD_DISTANCE));
            return new DuelFieldPayload(true, center.x, Math.min(a.y, b.y), center.z, yaw);
        }
        return new DuelFieldPayload(true, (a.x + b.x) / 2, Math.min(a.y, b.y), (a.z + b.z) / 2, yaw);
    }

    /** Projected ahead of the player, who stands at their own end of the field. */
    private static DuelFieldPayload fieldInFrontOf(ServerPlayer player) {
        float yaw = player.getYRot();
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 center = player.position().add(forward.scale(BOT_FIELD_DISTANCE));
        return new DuelFieldPayload(true, center.x, player.getY(), center.z, yaw);
    }

    /**
     * The first legal deck box a player carries (hands first, then the inventory). Tells the player when they carry
     * deck boxes but none is legal.
     *
     * @return the deck box, or {@code null}
     */
    private static ItemStack legalDeckBox(ServerPlayer player,
                                          io.github.zancrow321.jadm.engine.data.Banlist banlist) {
        List<ItemStack> boxes = deckBoxes(player);
        for (ItemStack box : boxes) {
            if (DeckBoxItem.problems(box, player, banlist).isEmpty()) {
                return box;
            }
        }
        if (!boxes.isEmpty()) {
            player.sendSystemMessage(Component.literal("Your deck box \"" + boxes.get(0).getHoverName().getString()
                    + "\" isn't legal: " + DeckBoxItem.problems(boxes.get(0), player, banlist).get(0)));
        }
        return null;
    }

    /** The player's deck boxes, the ones in hand first. */
    private static List<ItemStack> deckBoxes(ServerPlayer player) {
        List<ItemStack> boxes = new ArrayList<>();
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.is(JadmItems.DECK_BOX.get())) {
                boxes.add(stack);
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(JadmItems.DECK_BOX.get()) && !boxes.contains(stack)) {
                boxes.add(stack);
            }
        }
        return boxes;
    }

    /** The starter deck lent to a player without a legal deck box, or {@code null} with the reason told to them. */
    private static Deck starterFor(ServerPlayer player, boolean ante) {
        if (ante) {
            player.sendSystemMessage(Component.literal("An ante duel needs a legal deck box: the ante comes from it."));
            return null;
        }
        if (JadmServerConfig.STARTER_DECKS.get()) {
            return Deck.bundled(STARTER_DECK);
        }
        player.sendSystemMessage(Component.literal("You need a deck box with a legal deck to duel."));
        return null;
    }

    /**
     * @param anteBoxes each person's deck box to take the ante from, or {@code null} for a duel without an ante
     * @param step      the progression step whose rules the duel is played under
     * @param match     the organized duel this is, with its own rules, or {@code null}
     * @return whether the duel started
     */
    private boolean start(List<Entrant> entrants, List<Deck> decks, DuelFieldPayload field, List<ItemStack> anteBoxes,
                          DuelistNpc npc, boolean split, int step, MatchSetup match, boolean ranked) {
        var random = server.overworld().getRandom();
        long[] seed = {random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong() | 1};
        List<DuelTable.Seat> seats = new ArrayList<>();
        UUID[] people = new UUID[entrants.size()];
        for (int i = 0; i < entrants.size(); i++) {
            Entrant e = entrants.get(i);
            people[i] = e.player();
            seats.add(new DuelTable.Seat(e.team(), e.name(),
                    e.player() == null ? new DuelistAi(random.nextLong(), JadmData.cards()) : null));
        }
        MatchSetup.Rules rules = match != null ? match.rules() : MatchSetup.Rules.SERVER;
        Ruleset ruleset = rules.ruleset() != null ? rules.ruleset() : JadmData.ruleset(step);
        int serverLifePoints = entrants.size() > 2 ? JadmServerConfig.TAG_STARTING_LIFE_POINTS.get()
                : JadmServerConfig.STARTING_LIFE_POINTS.get();
        DuelSettings.Team team = new DuelSettings.Team(rules.lifePoints() > 0 ? rules.lifePoints()
                : serverLifePoints, 5, 1);
        DuelTable table;
        try {
            table = new DuelTable(JadmData.text(), new BundledScripts(),
                    new DuelSettings(seed, ruleset.flags(), team, team), seats, decks,
                    (type, message) -> Jadm.LOGGER.debug("[ocgcore {}] {}", type, message));
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Jadm.LOGGER.error("Could not start a duel", e);
            broadcast(entrants, Component.literal("The duel engine isn't available on this server."));
            List<UUID> riders = new ArrayList<>(entrants.stream().map(Entrant::player).filter(Objects::nonNull).toList());
            if (npc != null) {
                riders.add(npc.getUUID());
            }
            DuelArena.release(riders);
            return false;
        }
        UUID ante = anteBoxes == null ? null : takeAnte(entrants, anteBoxes, random);
        table.splitField(split);
        if (split) {
            // One sleeve per duelist, in seat order within each team, for their own half of the field.
            List<String> sleeves = new ArrayList<>();
            for (int side = 0; side < 2; side++) {
                for (Entrant e : entrants) {
                    if (e.team() == side) {
                        ServerPlayer player = player(e.player());
                        sleeves.add(player != null ? PlayerCosmetics.sleeve(player) : Cosmetics.DEFAULT_SLEEVE);
                    }
                }
            }
            field = field.withLayout(true, sleeves);
        } else {
            field = field.withLayout(false, List.of(sleeveOf(entrants, 0, npc), sleeveOf(entrants, 1, npc)));
        }
        ServerDuel duel = new ServerDuel(table, people, field, ante, npc, new HashSet<>(),
                new Clock(random.nextLong()), match, ranked);
        duels.add(duel);
        if (npc != null) {
            npc.setDueling(true);
            PacketDistributor.sendToPlayersTrackingEntity(npc, new DuelistStatePayload(npc.getId(), true));
        }
        boolean tag = entrants.size() > 2;
        for (UUID seat : people) {
            if (seat == null) {
                continue;
            }
            duelsByPlayer.put(seat, duel);
            ServerPlayer player = player(seat);
            if (player != null) {
                if (player.onGround()) {
                    anchors.put(seat, player.position());
                }
                calmMobs(player);
                // The disk unfolds first; the client grows the field once it has.
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                        new DuelistStatePayload(player.getId(), true));
                PacketDistributor.sendToPlayer(player, field);
            }
            message(seat, Component.literal((split ? "Battle City duel! " : tag ? "Tag duel! " : "Duel! ") + matchup(entrants) + ", "
                    + ruleset.displayName() + " rules. L opens the duel log, Esc the duel menu." + (tag ? " Partners take turns; you answer when it's yours." : "")
                    + (split ? " Each partner plays on their own half of the field." : "")));
        }
        run(duel, table::start);
        return true;
    }

    /** A team's card sleeve: its first person's pick, the NPC's own, or the classic back for bots. */
    private String sleeveOf(List<Entrant> entrants, int team, DuelistNpc npc) {
        for (Entrant e : entrants) {
            ServerPlayer player = player(e.player());
            if (e.team() == team && player != null) {
                return PlayerCosmetics.sleeve(player);
            }
        }
        return npc != null ? npc.sleeve() : Cosmetics.DEFAULT_SLEEVE;
    }

    /** Takes a random main deck card out of each person's deck box into escrow. */
    private UUID takeAnte(List<Entrant> entrants, List<ItemStack> boxes, net.minecraft.util.RandomSource random) {
        UUID id = UUID.randomUUID();
        AnteEscrow escrow = AnteEscrow.get(server);
        List<String> bets = new ArrayList<>();
        for (int i = 0; i < entrants.size(); i++) {
            ItemStack box = boxes.get(i);
            if (box.isEmpty()) {
                continue;
            }
            JadmComponents.DeckList list = DeckBoxItem.deck(box);
            List<Integer> main = new ArrayList<>(list.main());
            int code = main.remove(random.nextInt(main.size()));
            box.set(JadmComponents.DECK.get(), new JadmComponents.DeckList(List.copyOf(main), list.extra()));
            escrow.put(id, entrants.get(i).player(), code);
            bets.add(entrants.get(i).name() + " puts up " + JadmData.text().cardName(code));
        }
        broadcast(entrants, Component.literal("Ante: " + String.join(", ", bets) + ".")
                .withStyle(ChatFormatting.GOLD));
        return id;
    }

    public void respond(ServerPlayer player, byte[] response) {
        ServerDuel duel = duelsByPlayer.get(player.getUUID());
        if (duel == null) {
            return;
        }
        int seat = seatOf(duel, player.getUUID());
        if (duel.table().waitingFor() != seat) {
            return; // stale or duplicate click, or the partner's turn
        }
        run(duel, () -> duel.table().respond(seat, response));
    }

    /** {@code watcher} right-clicked {@code duelist}, who is dueling: watch their duel. */
    public void watch(ServerPlayer watcher, ServerPlayer duelist) {
        ServerDuel duel = duelsByPlayer.get(duelist.getUUID());
        if (duel == null) {
            watcher.sendSystemMessage(Component.literal(duelist.getScoreboardName() + " is not in a duel."));
            return;
        }
        watch(watcher, duel);
    }

    /** {@code watcher} right-clicked an NPC duelist that is dueling: watch that duel. */
    public void watchNpc(ServerPlayer watcher, DuelistNpc npc) {
        duels.stream().filter(d -> d.npc() == npc).findFirst().ifPresent(duel -> watch(watcher, duel));
    }

    private void watch(ServerPlayer watcher, ServerDuel duel) {
        if (inDuel(watcher)) {
            watcher.sendSystemMessage(Component.literal("You are in a duel yourself."));
            return;
        }
        if (duel.spectators().contains(watcher.getUUID())) {
            return;
        }
        unwatch(watcher, false);
        duel.spectators().add(watcher.getUUID());
        duelsBySpectator.put(watcher.getUUID(), duel);
        calmMobs(watcher);
        PacketDistributor.sendToPlayer(watcher, duel.field().watching());
        PacketDistributor.sendToPlayer(watcher, new DuelViewPayload(ViewCodec.encode(duel.table().watchingNow())));
        watcher.sendSystemMessage(Component.literal("Watching " + String.join(" vs ", duel.table().names())
                + ". Esc and Stop watching to leave."));
    }

    public void unwatch(ServerPlayer watcher) {
        if (!unwatch(watcher, true)) {
            watcher.sendSystemMessage(Component.literal("You are not watching a duel."));
        }
    }

    /** @return whether {@code watcher} was watching a duel */
    private boolean unwatch(ServerPlayer watcher, boolean removeField) {
        ServerDuel duel = duelsBySpectator.remove(watcher.getUUID());
        if (duel == null) {
            return false;
        }
        duel.spectators().remove(watcher.getUUID());
        if (removeField) {
            PacketDistributor.sendToPlayer(watcher, DuelFieldPayload.none());
        }
        return true;
    }

    public void forfeit(ServerPlayer player) {
        ServerDuel duel = duelsByPlayer.get(player.getUUID());
        if (duel == null) {
            player.sendSystemMessage(Component.literal("You are not in a duel."));
            return;
        }
        run(duel, () -> duel.table().forfeit(seatOf(duel, player.getUUID())));
    }

    /** A duelist came into view of {@code tracker}: show their disk unfolded if they're dueling. */
    public void onStartTracking(ServerPlayer tracker, Entity target) {
        if (target instanceof ServerPlayer player ? inDuel(player)
                : target instanceof DuelistNpc npc && npc.isDueling()) {
            PacketDistributor.sendToPlayer(tracker, new DuelistStatePayload(target.getId(), true));
        }
    }

    /** Hands over ante cards won (or returned) while the player was away. */
    public void onLogin(ServerPlayer player) {
        AnteEscrow.get(server).deliver(player);
    }

    public void onLogout(ServerPlayer player) {
        unwatch(player, false);
        invites.remove(player.getUUID());
        invites.values().removeIf(invite -> invite.host().equals(player.getUUID()));
        ServerDuel duel = duelsByPlayer.get(player.getUUID());
        if (duel != null) {
            run(duel, () -> duel.table().forfeit(seatOf(duel, player.getUUID())));
        }
    }

    public void tick() {
        long now = server.getTickCount();
        invites.values().removeIf(invite -> invite.expiresAt() < now);
        for (ServerDuel duel : List.copyOf(duels)) {
            run(duel, duel.table()::pump);
            MatchSetup.Rules rules = duel.match() != null ? duel.match().rules() : MatchSetup.Rules.SERVER;
            int limit = (rules.turnTimeLimit() >= 0 ? rules.turnTimeLimit() : JadmServerConfig.TURN_TIME_LIMIT.get())
                    * 20;
            if (rules.maxTurns() > 0 && duels.contains(duel) && duel.table().turn() > rules.maxTurns()) {
                run(duel, () -> duel.table().endOnLifePoints("Turn limit reached"));
            }
            if (limit > 0 && duels.contains(duel) && !duel.table().finished()) {
                clock(duel, limit, now);
            }
        }
        for (UUID id : duelsByPlayer.keySet()) {
            ServerPlayer player = player(id);
            if (player == null) {
                continue;
            }
            Vec3 spot = anchors.get(id);
            if (spot == null) {
                // Someone who started the duel mid-jump is held where they land.
                if (player.onGround()) {
                    anchors.put(id, player.position());
                }
            } else {
                // On an arena podium the spot goes up with it.
                Vec3 at = spot.add(0, DuelArena.lift(id), 0);
                if (player.position().distanceToSqr(at) > 0.01) {
                    player.connection.teleport(at.x, at.y, at.z, player.getYRot(), player.getXRot());
                    player.setDeltaMovement(Vec3.ZERO);
                }
            }
        }
        DuelArena.tick(server, id -> duelsByPlayer.containsKey(id) && anchors.containsKey(id));
    }

    /** Counts down the waiting person's time for this turn; once it's gone, the stand-in bot chooses for them. */
    private void clock(ServerDuel duel, int limit, long now) {
        Clock clock = duel.clock();
        DuelTable table = duel.table();
        if (table.turn() != clock.turn) {
            clock.turn = table.turn();
            clock.used.clear();
            clock.outOfTime.clear();
        }
        int seat = table.waitingFor();
        if (seat < 0 || table.isBot(seat)) {
            return;
        }
        int used = clock.used.merge(seat, 1, Integer::sum);
        ServerPlayer player = player(duel.seats()[seat]);
        if (used < limit) {
            if (player != null && (limit - used) % 20 == 0) {
                PacketDistributor.sendToPlayer(player, new DuelClockPayload(limit - used));
            }
            return;
        }
        if (clock.outOfTime.add(seat)) {
            message(duel.seats()[seat], Component.literal("Out of time: your choices are made for you until this "
                    + "turn ends.").withStyle(ChatFormatting.RED));
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new DuelClockPayload(0));
            }
        }
        run(duel, () -> {
            Map<Integer, DuelView> views = table.timeOut(seat, clock.standIn, clock.refused);
            DuelView own = views.get(seat);
            boolean refused = own != null && own.prompt() != null && own.log().contains(DuelTable.RETRY_LINE);
            clock.refused = refused ? clock.refused + 1 : 0;
            return views;
        });
    }

    /** Whether {@code entity} is a person at a duel or watching one, who can't be hurt or targeted by mobs. */
    public boolean protects(Entity entity) {
        return entity instanceof ServerPlayer player && (duelsByPlayer.containsKey(player.getUUID())
                || duelsBySpectator.containsKey(player.getUUID()));
    }

    /** Mobs that are already after the player give up when the duel starts. */
    private static void calmMobs(ServerPlayer player) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48),
                mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
        }
    }

    private void run(ServerDuel duel, java.util.function.Supplier<Map<Integer, DuelView>> action) {
        Map<Integer, DuelView> views;
        try {
            views = action.get();
        } catch (RuntimeException e) {
            Jadm.LOGGER.error("Duel crashed", e);
            for (UUID seat : duel.seats()) {
                message(seat, Component.literal("The duel ended because of an error."));
            }
            end(duel);
            return;
        }
        views.forEach((seat, view) -> {
            ServerPlayer player = player(duel.seats()[seat]);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new DuelViewPayload(ViewCodec.encode(view)));
            }
        });
        if (!views.isEmpty()) {
            // Something happened: spectators get their (public) share of it.
            DuelViewPayload watched = new DuelViewPayload(ViewCodec.encode(duel.table().spectatorView()));
            for (UUID id : duel.spectators()) {
                ServerPlayer spectator = player(id);
                if (spectator != null) {
                    PacketDistributor.sendToPlayer(spectator, watched);
                }
            }
        }
        if (duel.table().finished()) {
            end(duel);
        }
    }

    private void end(ServerDuel duel) {
        duels.remove(duel);
        // Spectators keep the field until they close the result screen.
        duel.spectators().forEach(duelsBySpectator::remove);
        for (UUID seat : duel.seats()) {
            if (seat != null) {
                duelsByPlayer.remove(seat);
                anchors.remove(seat);
                ServerPlayer player = player(seat);
                if (player != null) {
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                            new DuelistStatePayload(player.getId(), false));
                }
            }
        }
        List<UUID> riders = new ArrayList<>();
        for (UUID seat : duel.seats()) {
            if (seat != null) {
                riders.add(seat);
            }
        }
        if (duel.npc() != null) {
            riders.add(duel.npc().getUUID());
        }
        DuelArena.release(riders);
        int winner = duel.table().winner();
        boolean decided = winner == 0 || winner == 1;
        Map<UUID, List<String>> rewards = new HashMap<>();
        Map<UUID, String> records = new HashMap<>();
        Set<UUID> gifted = new HashSet<>();
        boolean againstNpc = duel.npc() != null;
        for (int seat = 0; seat < duel.seats().length; seat++) {
            UUID id = duel.seats()[seat];
            if (id == null) {
                continue;
            }
            int team = duel.table().seats().get(seat).team();
            boolean won = decided && team == winner;
            int outcome = !decided ? DuelResultPayload.DRAW : won ? DuelResultPayload.WON : DuelResultPayload.LOST;
            boolean vsPlayers = !againstNpc && opponentsArePeople(duel, team);
            // A duel cut short by an error doesn't count.
            if (JadmServerConfig.TRACK_RECORD.get() && duel.table().finished()) {
                DuelRecords.Record record = DuelRecords.get(server).count(id,
                        duel.table().seats().get(seat).name(), outcome, vsPlayers);
                records.put(id, record.summary() + (record.streak() > 1 ? ", " + record.streak() + " wins in a row"
                        : ""));
            }
            // Settled for people who left mid-duel too: leaving forfeits.
            List<String> chips = StarChips.get(server).settle(id, decided, won);
            ServerPlayer player = player(id);
            if (player == null) {
                continue;
            }
            List<String> lines = new ArrayList<>(chips);
            rewards.put(id, lines);
            if (won) {
                PlayerCosmetics.wonDuel(player, againstNpc).forEach(u -> lines.add("Unlocked: " + u));
            }
            // Tournament games bring the tournament's prizes instead.
            if (decided && duel.match() == null
                    && (vsPlayers || againstNpc || JadmServerConfig.REWARD_BOT_DUELS.get())) {
                List<String> given = DuelRewards.give(player, vsPlayers ? JadmServerConfig.PLAYER_REWARDS
                        : JadmServerConfig.NPC_REWARDS, won);
                if (!given.isEmpty()) {
                    gifted.add(id);
                    lines.addAll(given);
                }
            }
        }
        Map<UUID, RankedDuels.Outcome> ranks = new HashMap<>();
        if (duel.ranked() && duel.table().finished() && duel.seats().length == 2 && duel.seats()[0] != null
                && duel.seats()[1] != null) {
            int team0 = duel.table().seats().get(0).team();
            double score = !decided ? 0.5 : team0 == winner ? 1 : 0;
            ranks = RankedDuels.finish(server, duel.seats()[0], duel.table().seats().get(0).name(),
                    duel.seats()[1], duel.table().seats().get(1).name(), score);
            ranks.forEach((id, outcome) -> {
                if (rewards.containsKey(id)) {
                    rewards.get(id).addAll(outcome.notes());
                }
            });
        }
        if (againstNpc && duel.match() != null) {
            // An NPC standing in for a tournament duelist only came to show; it hands out nothing.
            duel.npc().setDueling(false);
            PacketDistributor.sendToPlayersTrackingEntity(duel.npc(),
                    new DuelistStatePayload(duel.npc().getId(), false));
        } else if (againstNpc) {
            DuelistNpc npc = duel.npc();
            PacketDistributor.sendToPlayersTrackingEntity(npc, new DuelistStatePayload(npc.getId(), false));
            // An NPC duel has one person; the coin toss may have put them on either team.
            int seat = duel.seats()[0] != null ? 0 : 1;
            ServerPlayer person = player(duel.seats()[seat]);
            boolean won = decided && duel.table().seats().get(seat).team() == winner;
            npc.duelEnded(person, won, person != null && gifted.contains(person.getUUID()));
        }
        // A tournament announces its own results.
        if (JadmServerConfig.ANNOUNCE_RESULTS.get() && duel.match() == null) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(announcement(duel, winner))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (duel.ante() != null) {
            // Only 1v1 duels between two people have an ante, so the winning team has exactly one person.
            UUID payTo = null;
            for (int seat = 0; seat < duel.seats().length; seat++) {
                if (decided && duel.table().seats().get(seat).team() == winner) {
                    payTo = duel.seats()[seat];
                }
            }
            AnteEscrow escrow = AnteEscrow.get(server);
            for (AnteEscrow.Stake stake : escrow.stakes(duel.ante())) {
                String card = JadmData.text().cardName(stake.code());
                for (UUID person : rewards.keySet()) {
                    if (payTo == null) {
                        if (person.equals(stake.owner())) {
                            rewards.get(person).add("Ante returned: " + card);
                        }
                    } else if (person.equals(payTo)) {
                        rewards.get(person).add((person.equals(stake.owner()) ? "Ante kept: " : "Ante won: ") + card);
                    } else if (person.equals(stake.owner())) {
                        rewards.get(person).add("Ante lost: " + card);
                    }
                }
            }
            escrow.settle(server, duel.ante(), payTo);
        }
        for (int seat = 0; seat < duel.seats().length; seat++) {
            ServerPlayer player = player(duel.seats()[seat]);
            if (player != null) {
                int outcome = !decided ? DuelResultPayload.DRAW
                        : duel.table().seats().get(seat).team() == winner ? DuelResultPayload.WON : DuelResultPayload.LOST;
                RankedDuels.Outcome rank = ranks.get(player.getUUID());
                PacketDistributor.sendToPlayer(player, new DuelResultPayload(outcome,
                        List.copyOf(rewards.get(player.getUUID())), records.getOrDefault(player.getUUID(), ""),
                        rank == null ? "" : rank.line(), rank == null ? 0 : rank.color()));
            }
        }
        duel.table().close();
        if (duel.match() != null) {
            duel.match().onEnd().accept(duel.table().finished() ? winner : -1);
        }
    }

    /** Whether every seat on the other team than {@code team} is a person rather than a bot. */
    private static boolean opponentsArePeople(ServerDuel duel, int team) {
        for (int seat = 0; seat < duel.seats().length; seat++) {
            if (duel.table().seats().get(seat).team() != team && duel.seats()[seat] == null) {
                return false;
            }
        }
        return true;
    }

    /** "Yugi beat Kaiba.", "Yugi & Joey beat Mai & bot." or "Yugi and Kaiba drew." */
    private static String announcement(ServerDuel duel, int winner) {
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        for (DuelTable.Seat seat : duel.table().seats()) {
            (seat.team() == (winner == 1 ? 1 : 0) ? first : second).add(seat.name());
        }
        return winner == 0 || winner == 1
                ? String.join(" & ", first) + " beat " + String.join(" & ", second) + "."
                : String.join(" & ", first) + " and " + String.join(" & ", second) + " drew.";
    }

    private static int seatOf(ServerDuel duel, UUID player) {
        for (int seat = 0; seat < duel.seats().length; seat++) {
            if (player.equals(duel.seats()[seat])) {
                return seat;
            }
        }
        throw new IllegalArgumentException("Not seated: " + player);
    }

    private String nameOf(UUID id) {
        ServerPlayer player = player(id);
        return player == null ? "?" : player.getScoreboardName();
    }

    private ServerPlayer player(UUID id) {
        return id == null ? null : server.getPlayerList().getPlayer(id);
    }

    private void broadcast(List<Entrant> entrants, Component text) {
        entrants.forEach(e -> message(e.player(), text));
    }

    private void message(UUID id, Component text) {
        ServerPlayer player = player(id);
        if (player != null) {
            player.sendSystemMessage(text);
        }
    }
}
