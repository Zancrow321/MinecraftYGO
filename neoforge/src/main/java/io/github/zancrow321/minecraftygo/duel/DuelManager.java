package io.github.zancrow321.minecraftygo.duel;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.YgoServerConfig;
import io.github.zancrow321.minecraftygo.arena.DuelDome;
import io.github.zancrow321.minecraftygo.cosmetics.Cosmetics;
import io.github.zancrow321.minecraftygo.cosmetics.PlayerCosmetics;
import io.github.zancrow321.minecraftygo.engine.DuelSettings;
import io.github.zancrow321.minecraftygo.engine.Ruleset;
import io.github.zancrow321.minecraftygo.engine.ai.DuelistAi;
import io.github.zancrow321.minecraftygo.engine.data.BundledScripts;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.duel.DuelTable;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.engine.duel.ViewCodec;
import io.github.zancrow321.minecraftygo.entity.DuelistNpc;
import io.github.zancrow321.minecraftygo.item.DeckBoxItem;
import io.github.zancrow321.minecraftygo.item.YgoComponents;
import io.github.zancrow321.minecraftygo.item.YgoItems;
import io.github.zancrow321.minecraftygo.network.DuelFieldPayload;
import io.github.zancrow321.minecraftygo.network.DuelResultPayload;
import io.github.zancrow321.minecraftygo.network.DuelistStatePayload;
import io.github.zancrow321.minecraftygo.network.DuelViewPayload;
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
     */
    private record Invite(UUID host, List<Entrant> entrants, Set<UUID> pending, boolean ante, long expiresAt,
                          DuelistNpc npc, boolean split) {
        Invite(UUID host, List<Entrant> entrants, Set<UUID> pending, boolean ante, long expiresAt, DuelistNpc npc) {
            this(host, entrants, pending, ante, expiresAt, npc, false);
        }
    }

    /**
     * A running duel and who sits where ({@code null} seats are bots).
     *
     * @param ante the escrow id of the ante, or {@code null} for a duel without one
     * @param npc the NPC duelist playing the bot seat, or {@code null}
     */
    private record ServerDuel(DuelTable table, UUID[] seats, DuelFieldPayload field, UUID ante, DuelistNpc npc) {
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
        if (challenger == target) {
            challenger.sendSystemMessage(Component.literal("You can't duel yourself. Try /ygo duel bot"));
            return;
        }
        if (ante && !YgoServerConfig.ALLOW_ANTE.get()) {
            challenger.sendSystemMessage(Component.literal("Ante duels are turned off on this server."));
            return;
        }
        invite(challenger, List.of(new Entrant(0, challenger.getUUID(), challenger.getScoreboardName()),
                new Entrant(1, target.getUUID(), target.getScoreboardName())), ante);
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
        invite(host, entrants, ante, false);
    }

    private void invite(ServerPlayer host, List<Entrant> entrants, boolean ante, boolean split) {
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
                server.getTickCount() + INVITE_TIMEOUT_TICKS, null, split);
        if (pending.isEmpty()) {
            launch(invite);
            return;
        }
        String matchup = matchup(entrants) + (ante ? " (ante)" : split ? " (Battle City)" : "");
        host.sendSystemMessage(Component.literal("Invitation sent: " + matchup + "."));
        for (UUID id : pending) {
            invites.put(id, invite);
            ServerPlayer target = player(id);
            if (target == null) {
                continue;
            }
            Component accept = Component.literal("[Accept]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ygo accept")));
            String how = entrants.size() == 2 && DuelDisks.has(target)
                    ? " Right-click them with your Duel Disk or click " : " Click ";
            target.sendSystemMessage(Component.literal(host.getScoreboardName() + " invites you: " + matchup
                    + (ante ? ". The winner takes a random card from the loser's deck box." : ".") + how)
                    .append(accept));
        }
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
        challenge(player, other, player.isShiftKeyDown() && YgoServerConfig.ALLOW_ANTE.get());
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
        List<Deck> decks = new ArrayList<>();
        List<ItemStack> boxes = new ArrayList<>();
        int botIndex = 0;
        for (Entrant e : entrants) {
            if (e.player() == null) {
                decks.add(invite.npc() != null ? invite.npc().deck()
                        : Deck.bundled(BOT_DECKS.get(botIndex++ % BOT_DECKS.size())));
                boxes.add(ItemStack.EMPTY);
                continue;
            }
            ServerPlayer player = player(e.player());
            ItemStack box = legalDeckBox(player);
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
        DuelFieldPayload field = team0.isEmpty() ? turned(DuelDome.field(team1, team0)) : DuelDome.field(team0, team1);
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
        start(entrants, decks, field, invite.ante() ? boxes : null, invite.npc(), invite.split());
    }

    /** The same field seen from the other end: team 0 gets the far side. */
    private static DuelFieldPayload turned(DuelFieldPayload field) {
        return field == null ? null : new DuelFieldPayload(true, field.x(), field.y(), field.z(), field.yaw() + 180);
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
    private static ItemStack legalDeckBox(ServerPlayer player) {
        List<ItemStack> boxes = new ArrayList<>();
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.is(YgoItems.DECK_BOX.get())) {
                boxes.add(stack);
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(YgoItems.DECK_BOX.get()) && !boxes.contains(stack)) {
                boxes.add(stack);
            }
        }
        for (ItemStack box : boxes) {
            if (DeckBoxItem.problems(box).isEmpty()) {
                return box;
            }
        }
        if (!boxes.isEmpty()) {
            player.sendSystemMessage(Component.literal("Your deck box \"" + boxes.get(0).getHoverName().getString()
                    + "\" isn't legal: " + DeckBoxItem.problems(boxes.get(0)).get(0)));
        }
        return null;
    }

    /** The starter deck lent to a player without a legal deck box, or {@code null} with the reason told to them. */
    private static Deck starterFor(ServerPlayer player, boolean ante) {
        if (ante) {
            player.sendSystemMessage(Component.literal("An ante duel needs a legal deck box: the ante comes from it."));
            return null;
        }
        if (YgoServerConfig.STARTER_DECKS.get()) {
            return Deck.bundled(STARTER_DECK);
        }
        player.sendSystemMessage(Component.literal("You need a deck box with a legal deck to duel."));
        return null;
    }

    /**
     * @param anteBoxes each person's deck box to take the ante from, or {@code null} for a duel without an ante
     */
    private void start(List<Entrant> entrants, List<Deck> decks, DuelFieldPayload field, List<ItemStack> anteBoxes,
                       DuelistNpc npc, boolean split) {
        var random = server.overworld().getRandom();
        long[] seed = {random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong() | 1};
        List<DuelTable.Seat> seats = new ArrayList<>();
        UUID[] people = new UUID[entrants.size()];
        for (int i = 0; i < entrants.size(); i++) {
            Entrant e = entrants.get(i);
            people[i] = e.player();
            seats.add(new DuelTable.Seat(e.team(), e.name(),
                    e.player() == null ? new DuelistAi(random.nextLong(), YgoData.cards()) : null));
        }
        Ruleset ruleset = Ruleset.parse(YgoServerConfig.RULESET.get());
        DuelSettings.Team team = new DuelSettings.Team(YgoServerConfig.STARTING_LIFE_POINTS.get(), 5, 1);
        DuelTable table;
        try {
            table = new DuelTable(YgoData.text(), new BundledScripts(),
                    new DuelSettings(seed, ruleset.flags(), team, team), seats, decks,
                    (type, message) -> MinecraftYgo.LOGGER.debug("[ocgcore {}] {}", type, message));
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            MinecraftYgo.LOGGER.error("Could not start a duel", e);
            broadcast(entrants, Component.literal("The duel engine isn't available on this server."));
            return;
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
        ServerDuel duel = new ServerDuel(table, people, field, ante, npc);
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
            YgoComponents.DeckList list = DeckBoxItem.deck(box);
            List<Integer> main = new ArrayList<>(list.main());
            int code = main.remove(random.nextInt(main.size()));
            box.set(YgoComponents.DECK.get(), new YgoComponents.DeckList(List.copyOf(main), list.extra()));
            escrow.put(id, entrants.get(i).player(), code);
            bets.add(entrants.get(i).name() + " puts up " + YgoData.text().cardName(code));
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
            } else if (player.position().distanceToSqr(spot) > 0.01) {
                player.connection.teleport(spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
                player.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /** Whether {@code entity} is a person at a duel, who can't be hurt or targeted by mobs. */
    public boolean protects(Entity entity) {
        return entity instanceof ServerPlayer player && duelsByPlayer.containsKey(player.getUUID());
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
            MinecraftYgo.LOGGER.error("Duel crashed", e);
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
        if (duel.table().finished()) {
            end(duel);
        }
    }

    private void end(ServerDuel duel) {
        duels.remove(duel);
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
        int winner = duel.table().winner();
        boolean decided = winner == 0 || winner == 1;
        Map<UUID, List<String>> rewards = new HashMap<>();
        for (int seat = 0; seat < duel.seats().length; seat++) {
            ServerPlayer player = player(duel.seats()[seat]);
            if (player != null) {
                rewards.put(player.getUUID(), new ArrayList<>());
                if (decided && duel.table().seats().get(seat).team() == winner) {
                    PlayerCosmetics.wonDuel(player, duel.npc() != null)
                            .forEach(u -> rewards.get(player.getUUID()).add("Unlocked: " + u));
                }
            }
        }
        if (duel.npc() != null) {
            DuelistNpc npc = duel.npc();
            PacketDistributor.sendToPlayersTrackingEntity(npc, new DuelistStatePayload(npc.getId(), false));
            // An NPC duel has one person; the coin toss may have put them on either team.
            int seat = duel.seats()[0] != null ? 0 : 1;
            ServerPlayer person = player(duel.seats()[seat]);
            ItemStack pack = npc.duelEnded(person, decided && duel.table().seats().get(seat).team() == winner);
            if (person != null && !pack.isEmpty()) {
                rewards.get(person.getUUID()).add("Won " + pack.getHoverName().getString());
            }
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
                String card = YgoData.text().cardName(stake.code());
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
                PacketDistributor.sendToPlayer(player,
                        new DuelResultPayload(outcome, List.copyOf(rewards.get(player.getUUID()))));
            }
        }
        duel.table().close();
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
