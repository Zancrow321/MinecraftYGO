package io.github.zancrow321.minecraftygo.duel;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.YgoServerConfig;
import io.github.zancrow321.minecraftygo.item.DeckBoxItem;
import io.github.zancrow321.minecraftygo.item.YgoItems;
import net.minecraft.world.item.ItemStack;
import io.github.zancrow321.minecraftygo.engine.DuelSettings;
import io.github.zancrow321.minecraftygo.engine.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.ai.RandomResponder;
import io.github.zancrow321.minecraftygo.engine.data.BundledScripts;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.duel.DuelTable;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.engine.duel.ViewCodec;
import io.github.zancrow321.minecraftygo.network.DuelFieldPayload;
import io.github.zancrow321.minecraftygo.network.DuelistStatePayload;
import io.github.zancrow321.minecraftygo.network.DuelViewPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks challenges and running duels on one server. All methods run on the server thread.
 */
public final class DuelManager {
    private static final long CHALLENGE_TIMEOUT_TICKS = 20 * 60;
    /** Lent to players without a deck box, if the server allows it. */
    private static final String STARTER_DECK = "starter_yugi";
    private static final String BOT_DECK = "starter_kaiba";

    private static DuelManager instance;

    private final MinecraftServer server;
    /** target -> challenge */
    private final Map<UUID, Challenge> challenges = new HashMap<>();
    private final Map<UUID, ServerDuel> duelsByPlayer = new HashMap<>();
    private final List<ServerDuel> duels = new ArrayList<>();

    private record Challenge(UUID challenger, long expiresAt) {
    }

    /** A running duel, who sits where ({@code null} seats are bots), and where its field is projected. */
    private record ServerDuel(DuelTable table, UUID[] seats, DuelFieldPayload field) {
    }

    /** How far in front of a player a duel against a bot is projected (the field is about 16 blocks long). */
    private static final double BOT_FIELD_DISTANCE = 8;

    private DuelManager(MinecraftServer server) {
        this.server = server;
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

    public void challenge(ServerPlayer challenger, ServerPlayer target) {
        if (challenger == target) {
            challenger.sendSystemMessage(Component.literal("You can't duel yourself. Try /ygo duel bot"));
            return;
        }
        if (inDuel(challenger) || inDuel(target)) {
            challenger.sendSystemMessage(Component.literal("One of you is already in a duel."));
            return;
        }
        challenges.put(target.getUUID(), new Challenge(challenger.getUUID(),
                server.getTickCount() + CHALLENGE_TIMEOUT_TICKS));
        challenger.sendSystemMessage(Component.literal("Challenge sent to " + target.getScoreboardName() + "."));
        target.sendSystemMessage(Component.literal(challenger.getScoreboardName() + " challenges you to a duel! "
                        + (DuelDisks.has(target) ? "Right-click them with your Duel Disk or click " : ""))
                .append(Component.literal("[Accept]").withStyle(s -> s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ygo accept")))));
    }

    /**
     * {@code player} right-clicked {@code other} with a duel disk: accepts their challenge if they sent one,
     * otherwise challenges them.
     */
    public void diskInteract(ServerPlayer player, ServerPlayer other) {
        if (!DuelDisks.has(other)) {
            player.sendSystemMessage(Component.literal(other.getScoreboardName() + " has no Duel Disk."));
            return;
        }
        long now = server.getTickCount();
        Challenge received = challenges.get(player.getUUID());
        if (received != null && received.challenger().equals(other.getUUID()) && received.expiresAt() >= now) {
            accept(player);
            return;
        }
        Challenge sent = challenges.get(other.getUUID());
        if (sent != null && sent.challenger().equals(player.getUUID()) && sent.expiresAt() >= now) {
            player.sendSystemMessage(Component.literal("Waiting for " + other.getScoreboardName() + " to accept."));
            return;
        }
        challenge(player, other);
    }

    public void accept(ServerPlayer target) {
        Challenge challenge = challenges.remove(target.getUUID());
        ServerPlayer challenger = challenge == null ? null
                : server.getPlayerList().getPlayer(challenge.challenger());
        if (challenger == null || challenge.expiresAt() < server.getTickCount()) {
            target.sendSystemMessage(Component.literal("You have no pending challenge."));
            return;
        }
        if (inDuel(challenger) || inDuel(target)) {
            target.sendSystemMessage(Component.literal("One of you is already in a duel."));
            return;
        }
        Deck first = deckFor(challenger);
        Deck second = deckFor(target);
        if (first == null || second == null) {
            message(first == null ? target.getUUID() : challenger.getUUID(), Component.literal(
                    (first == null ? challenger : target).getScoreboardName() + " has no legal deck."));
            return;
        }
        start(new UUID[]{challenger.getUUID(), target.getUUID()},
                List.of(challenger.getScoreboardName(), target.getScoreboardName()), new Deck[]{first, second},
                fieldBetween(challenger, target));
    }

    public void duelBot(ServerPlayer player) {
        if (inDuel(player)) {
            player.sendSystemMessage(Component.literal("You are already in a duel."));
            return;
        }
        Deck deck = deckFor(player);
        if (deck == null) {
            return;
        }
        start(new UUID[]{player.getUUID(), null}, List.of(player.getScoreboardName(), "Duel Bot"),
                new Deck[]{deck, Deck.bundled(BOT_DECK)}, fieldInFrontOf(player));
    }

    /** Centered between the two duelists, on the lower one's feet level, player 0's side toward player 0. */
    private static DuelFieldPayload fieldBetween(ServerPlayer first, ServerPlayer second) {
        Vec3 a = first.position();
        Vec3 b = second.position();
        if (a.distanceToSqr(b) < 1 || first.level() != second.level()) {
            return fieldInFrontOf(first);
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));
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
     * The deck a player duels with: the first legal deck box they carry (hands first, then the inventory), or a
     * starter deck if the server allows it. Tells the player why when there is none.
     *
     * @return the deck, or {@code null}
     */
    private static Deck deckFor(ServerPlayer player) {
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
                return DeckBoxItem.toDeck(box);
            }
        }
        if (!boxes.isEmpty()) {
            player.sendSystemMessage(Component.literal("Your deck box \"" + boxes.get(0).getHoverName().getString()
                    + "\" isn't legal: " + DeckBoxItem.problems(boxes.get(0)).get(0)));
            return null;
        }
        if (YgoServerConfig.STARTER_DECKS.get()) {
            return Deck.bundled(STARTER_DECK);
        }
        player.sendSystemMessage(Component.literal("You need a deck box with a legal deck to duel."));
        return null;
    }

    private void start(UUID[] seats, List<String> names, Deck[] decks, DuelFieldPayload field) {
        var random = server.overworld().getRandom();
        long[] seed = {random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong() | 1};
        RandomResponder[] bots = new RandomResponder[2];
        for (int i = 0; i < 2; i++) {
            if (seats[i] == null) {
                bots[i] = new RandomResponder(random.nextLong(), YgoData.cards());
            }
        }
        DuelTable table;
        try {
            table = new DuelTable(YgoData.text(), new BundledScripts(),
                    DuelSettings.standard(seed, OcgConstants.DUEL_MODE_MR1), decks[0],
                    decks[1], names, bots,
                    (type, message) -> MinecraftYgo.LOGGER.debug("[ocgcore {}] {}", type, message));
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            MinecraftYgo.LOGGER.error("Could not start a duel", e);
            for (UUID seat : seats) {
                message(seat, Component.literal("The duel engine isn't available on this server."));
            }
            return;
        }
        ServerDuel duel = new ServerDuel(table, seats, field);
        duels.add(duel);
        for (UUID seat : seats) {
            if (seat != null) {
                duelsByPlayer.put(seat, duel);
                ServerPlayer player = player(seat);
                if (player != null) {
                    // The disk unfolds first; the client grows the field once it has.
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                            new DuelistStatePayload(player.getId(), true));
                    PacketDistributor.sendToPlayer(player, field);
                }
                message(seat, Component.literal("Duel! " + names.get(0) + " vs " + names.get(1)
                        + ". Right-click glowing zones on the field, or press Y for every choice."));
            }
        }
        run(duel, table::start);
    }

    public void respond(ServerPlayer player, byte[] response) {
        ServerDuel duel = duelsByPlayer.get(player.getUUID());
        if (duel == null) {
            return;
        }
        int seat = seatOf(duel, player.getUUID());
        if (duel.table().waitingFor() != seat) {
            return; // stale or duplicate click
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

    /** A player came into view of {@code tracker}: show their disk unfolded if they're dueling. */
    public void onStartTracking(ServerPlayer tracker, ServerPlayer target) {
        if (inDuel(target)) {
            PacketDistributor.sendToPlayer(tracker, new DuelistStatePayload(target.getId(), true));
        }
    }

    public void onLogout(ServerPlayer player) {
        challenges.remove(player.getUUID());
        ServerDuel duel = duelsByPlayer.get(player.getUUID());
        if (duel != null) {
            run(duel, () -> duel.table().forfeit(seatOf(duel, player.getUUID())));
        }
    }

    public void tick() {
        long now = server.getTickCount();
        challenges.values().removeIf(c -> c.expiresAt() < now);
        for (ServerDuel duel : List.copyOf(duels)) {
            run(duel, duel.table()::pump);
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
                ServerPlayer player = player(seat);
                if (player != null) {
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                            new DuelistStatePayload(player.getId(), false));
                }
            }
        }
        duel.table().close();
    }

    private static int seatOf(ServerDuel duel, UUID player) {
        return player.equals(duel.seats()[0]) ? 0 : 1;
    }

    private ServerPlayer player(UUID id) {
        return id == null ? null : server.getPlayerList().getPlayer(id);
    }

    private void message(UUID id, Component text) {
        ServerPlayer player = player(id);
        if (player != null) {
            player.sendSystemMessage(text);
        }
    }
}
