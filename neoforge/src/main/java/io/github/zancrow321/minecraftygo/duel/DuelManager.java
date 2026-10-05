package io.github.zancrow321.minecraftygo.duel;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.DuelSettings;
import io.github.zancrow321.minecraftygo.engine.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.ai.RandomResponder;
import io.github.zancrow321.minecraftygo.engine.data.BundledScripts;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.duel.DuelTable;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.engine.duel.ViewCodec;
import io.github.zancrow321.minecraftygo.network.DuelViewPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
    private static final String CHALLENGER_DECK = "starter_yugi";
    private static final String OPPONENT_DECK = "starter_kaiba";

    private static DuelManager instance;

    private final MinecraftServer server;
    /** target -> challenge */
    private final Map<UUID, Challenge> challenges = new HashMap<>();
    private final Map<UUID, ServerDuel> duelsByPlayer = new HashMap<>();
    private final List<ServerDuel> duels = new ArrayList<>();

    private record Challenge(UUID challenger, long expiresAt) {
    }

    /** A running duel and who sits where; {@code null} seats are bots. */
    private record ServerDuel(DuelTable table, UUID[] seats) {
    }

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
        target.sendSystemMessage(Component.literal(challenger.getScoreboardName() + " challenges you to a duel! ")
                .append(Component.literal("[Accept]").withStyle(s -> s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ygo accept")))));
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
        start(new UUID[]{challenger.getUUID(), target.getUUID()},
                List.of(challenger.getScoreboardName(), target.getScoreboardName()));
    }

    public void duelBot(ServerPlayer player) {
        if (inDuel(player)) {
            player.sendSystemMessage(Component.literal("You are already in a duel."));
            return;
        }
        start(new UUID[]{player.getUUID(), null}, List.of(player.getScoreboardName(), "Duel Bot"));
    }

    private void start(UUID[] seats, List<String> names) {
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
                    DuelSettings.standard(seed, OcgConstants.DUEL_MODE_MR1), Deck.bundled(CHALLENGER_DECK),
                    Deck.bundled(OPPONENT_DECK), names, bots,
                    (type, message) -> MinecraftYgo.LOGGER.debug("[ocgcore {}] {}", type, message));
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            MinecraftYgo.LOGGER.error("Could not start a duel", e);
            for (UUID seat : seats) {
                message(seat, Component.literal("The duel engine isn't available on this server."));
            }
            return;
        }
        ServerDuel duel = new ServerDuel(table, seats);
        duels.add(duel);
        for (UUID seat : seats) {
            if (seat != null) {
                duelsByPlayer.put(seat, duel);
                message(seat, Component.literal("Duel! " + names.get(0) + " vs " + names.get(1)
                        + ". Press Y to open the duel screen."));
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
