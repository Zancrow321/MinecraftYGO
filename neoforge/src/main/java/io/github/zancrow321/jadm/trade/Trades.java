package io.github.zancrow321.jadm.trade;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.duel.DuelManager;
import io.github.zancrow321.jadm.item.BinderItem;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.DeckBoxItem;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trading between players: {@code /jadm trade <player>} (or sneak-right-clicking them with a card in hand) asks, the
 * other accepts by clicking in chat or with {@code /jadm trade accept}, and both get a {@link TradeMenu}. A trade ends
 * when both confirm, when either closes the window or logs out, or when one walks too far away or starts a duel.
 */
public final class Trades {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Jadm.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<TradeMenu>> MENU = MENUS.register("trade",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new TradeMenu(id, inventory, data.readUtf())));

    /** {@code from} asked {@code to} to trade; the request lapses at server tick {@code expires}. */
    private record Request(UUID from, UUID to, int expires) {
    }

    private static final List<Request> REQUESTS = new ArrayList<>();
    /** The trade each trading player is in. */
    private static final Map<UUID, Trade> TRADES = new HashMap<>();

    private Trades() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }

    /** The server stopped: forget everything. */
    public static void reset() {
        REQUESTS.clear();
        TRADES.clear();
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("trade")
                .then(Commands.literal("accept")
                        .executes(ctx -> accept(ctx.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> accept(ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("deny")
                        .executes(ctx -> deny(ctx.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> deny(ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> request(ctx.getSource().getPlayerOrException(),
                                EntityArgument.getPlayer(ctx, "player"))));
    }

    /**
     * Right-clicking another player while sneaking with a card, binder or deck box in hand asks them to trade.
     *
     * @return whether the click was taken for that (on both sides, so the item isn't used too)
     */
    public static boolean interact(Player player, Player other) {
        Item held = player.getMainHandItem().getItem();
        if (!player.isShiftKeyDown() || !JadmServerConfig.TRADE.rightClick.get()
                || !(held instanceof CardItem || held instanceof BinderItem || held instanceof DeckBoxItem)) {
            return false;
        }
        if (player instanceof ServerPlayer from && other instanceof ServerPlayer to) {
            request(from, to);
        }
        return true;
    }

    /** {@code from} asks {@code to} to trade, or accepts if {@code to} already asked them. */
    static int request(ServerPlayer from, ServerPlayer to) {
        if (!check(from, to, from)) {
            return 0;
        }
        int now = from.server.getTickCount();
        REQUESTS.removeIf(r -> r.expires() < now);
        if (REQUESTS.stream().anyMatch(r -> r.from().equals(to.getUUID()) && r.to().equals(from.getUUID()))) {
            start(to, from);
            return 1;
        }
        if (REQUESTS.removeIf(r -> r.from().equals(from.getUUID()) && r.to().equals(to.getUUID()))) {
            from.sendSystemMessage(Component.translatable("message.jadm.trade.again", to.getDisplayName()));
        }
        REQUESTS.add(new Request(from.getUUID(), to.getUUID(),
                now + JadmServerConfig.TRADE.requestSeconds.get() * 20));
        from.sendSystemMessage(Component.translatable("message.jadm.trade.sent", to.getDisplayName(),
                JadmServerConfig.TRADE.requestSeconds.get()));
        String name = from.getScoreboardName();
        MutableComponent message = Component.translatable("message.jadm.trade.request", from.getDisplayName())
                .withStyle(ChatFormatting.GOLD)
                .append(" ")
                .append(button("message.jadm.trade.accept_button", ChatFormatting.GREEN,
                        "/" + Jadm.COMMAND + " trade accept " + name))
                .append(" ")
                .append(button("message.jadm.trade.deny_button", ChatFormatting.RED,
                        "/" + Jadm.COMMAND + " trade deny " + name));
        to.sendSystemMessage(message);
        to.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.7f, 1.2f);
        return 1;
    }

    private static Component button(String key, ChatFormatting color, String command) {
        return Component.translatable(key).withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    /** {@code player} accepts the request of {@code from}, or the newest one when {@code from} is null. */
    static int accept(ServerPlayer player, ServerPlayer from) {
        Request request = find(player, from);
        if (request == null) {
            player.sendSystemMessage(Component.translatable("message.jadm.trade.no_request")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer requester = player.server.getPlayerList().getPlayer(request.from());
        REQUESTS.remove(request);
        if (requester == null) {
            player.sendSystemMessage(Component.translatable("message.jadm.trade.no_request")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!check(requester, player, player)) {
            return 0;
        }
        start(requester, player);
        return 1;
    }

    static int deny(ServerPlayer player, ServerPlayer from) {
        Request request = find(player, from);
        if (request == null) {
            player.sendSystemMessage(Component.translatable("message.jadm.trade.no_request")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        REQUESTS.remove(request);
        ServerPlayer requester = player.server.getPlayerList().getPlayer(request.from());
        if (requester != null) {
            requester.sendSystemMessage(Component.translatable("message.jadm.trade.denied", player.getDisplayName())
                    .withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.translatable("message.jadm.trade.you_denied",
                    requester.getDisplayName()));
        }
        return 1;
    }

    /** The newest request to {@code player} that is still open, from {@code from} if given. */
    private static Request find(ServerPlayer player, ServerPlayer from) {
        int now = player.server.getTickCount();
        REQUESTS.removeIf(r -> r.expires() < now);
        for (int i = REQUESTS.size() - 1; i >= 0; i--) {
            Request request = REQUESTS.get(i);
            if (request.to().equals(player.getUUID()) && (from == null || request.from().equals(from.getUUID()))) {
                return request;
            }
        }
        return null;
    }

    /**
     * Whether {@code a} and {@code b} can trade now; if not, {@code told} hears why.
     */
    private static boolean check(ServerPlayer a, ServerPlayer b, ServerPlayer told) {
        ServerPlayer other = told == a ? b : a;
        Component problem = null;
        if (!JadmServerConfig.TRADE.enabled.get()) {
            problem = Component.translatable("message.jadm.trade.disabled");
        } else if (a == b) {
            problem = Component.translatable("message.jadm.trade.self");
        } else if (TRADES.containsKey(told.getUUID())) {
            problem = Component.translatable("message.jadm.trade.busy_self");
        } else if (TRADES.containsKey(other.getUUID())) {
            problem = Component.translatable("message.jadm.trade.busy", other.getDisplayName());
        } else if (DuelManager.get(a.server).inDuel(a) || DuelManager.get(a.server).inDuel(b)) {
            problem = Component.translatable("message.jadm.trade.in_duel");
        } else if (!near(a, b)) {
            problem = Component.translatable("message.jadm.trade.too_far", other.getDisplayName(),
                    JadmServerConfig.TRADE.maxDistance.get());
        }
        if (problem != null) {
            told.sendSystemMessage(problem.copy().withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    /** Whether the two are within {@code maxDistance} (always, when it is 0). */
    private static boolean near(ServerPlayer a, ServerPlayer b) {
        int max = JadmServerConfig.TRADE.maxDistance.get();
        return max <= 0 || a.level() == b.level() && a.distanceToSqr(b) <= (double) max * max;
    }

    private static void start(ServerPlayer first, ServerPlayer second) {
        REQUESTS.removeIf(r -> r.from().equals(first.getUUID()) && r.to().equals(second.getUUID())
                || r.from().equals(second.getUUID()) && r.to().equals(first.getUUID()));
        Trade trade = new Trade(first, second);
        TRADES.put(first.getUUID(), trade);
        TRADES.put(second.getUUID(), trade);
        open(first, second, trade, 0);
        open(second, first, trade, 1);
        Jadm.LOGGER.info("Trade started between {} and {}", first.getScoreboardName(), second.getScoreboardName());
    }

    private static void open(ServerPlayer player, ServerPlayer partner, Trade trade, int side) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TradeMenu(id, inventory, trade, side),
                Component.translatable("container.jadm.trade", partner.getDisplayName())),
                data -> data.writeUtf(partner.getScoreboardName()));
    }

    /** The trade ended (done or called off). */
    static void finished(Trade trade) {
        TRADES.values().removeIf(t -> t == trade);
    }

    /** Ends trades whose players walked apart, started a duel or died, and drops lapsed requests. */
    public static void tick(MinecraftServer server) {
        if (TRADES.isEmpty()) {
            return;
        }
        for (Trade trade : new HashSet<>(TRADES.values())) {
            ServerPlayer first = trade.side(0).online();
            ServerPlayer second = trade.side(1).online();
            Component reason = null;
            if (first == null || second == null) {
                reason = Component.translatable("message.jadm.trade.left",
                        trade.side(first == null ? 0 : 1).name);
            } else if (!first.isAlive() || !second.isAlive()) {
                reason = Component.translatable("message.jadm.trade.cancelled_plain");
            } else if (DuelManager.get(server).inDuel(first) || DuelManager.get(server).inDuel(second)) {
                reason = Component.translatable("message.jadm.trade.duel_started");
            } else if (!near(first, second)) {
                reason = Component.translatable("message.jadm.trade.walked_away");
            } else if (!(first.containerMenu instanceof TradeMenu a && a.trade() == trade)
                    || !(second.containerMenu instanceof TradeMenu b && b.trade() == trade)) {
                // A window got replaced without being closed (another mod opened something).
                reason = Component.translatable("message.jadm.trade.cancelled_plain");
            }
            if (reason != null) {
                trade.cancel(null, reason);
            }
        }
    }

    /** A player logs out: their trade ends (their offer goes back into their inventory before it is saved). */
    public static void logout(ServerPlayer player) {
        REQUESTS.removeIf(r -> r.from().equals(player.getUUID()) || r.to().equals(player.getUUID()));
        Trade trade = TRADES.get(player.getUUID());
        if (trade != null) {
            trade.cancel(null, Component.translatable("message.jadm.trade.left", player.getScoreboardName()));
        }
    }
}
