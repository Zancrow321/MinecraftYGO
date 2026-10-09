package io.github.zancrow321.jadm.trade;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One trade between two players: each side's offer (items in {@link #SLOTS} slots and Duel Points) and whether they
 * confirmed it. Any change to either offer takes both confirmations back, and nobody can confirm for {@link #LOCK_TICKS}
 * after a change, so what both confirmed is always what they saw. Once both have, the offers swap in one go.
 */
public final class Trade {
    /** Item slots on each side. */
    public static final int SLOTS = 12;
    /** Ticks after a change before either side can confirm. */
    public static final int LOCK_TICKS = 30;

    /** One player's half of the trade. */
    final class Side {
        final UUID player;
        final String name;
        final SimpleContainer offer = new SimpleContainer(SLOTS);
        int points;
        boolean confirmed;

        Side(ServerPlayer player) {
            this.player = player.getUUID();
            this.name = player.getScoreboardName();
            offer.addListener(container -> changed());
        }

        ServerPlayer online() {
            return server.getPlayerList().getPlayer(player);
        }
    }

    private final MinecraftServer server;
    private final Side[] sides = new Side[2];
    private int lastChange;
    private boolean over;

    Trade(ServerPlayer first, ServerPlayer second) {
        this.server = first.server;
        sides[0] = new Side(first);
        sides[1] = new Side(second);
        lastChange = server.getTickCount();
    }

    Side side(int index) {
        return sides[index];
    }

    /** Which side {@code player} is on, or -1. */
    int sideOf(UUID player) {
        return sides[0].player.equals(player) ? 0 : sides[1].player.equals(player) ? 1 : -1;
    }

    boolean over() {
        return over;
    }

    /** Ticks until anyone can confirm again. */
    int lockTicks() {
        return Math.max(0, lastChange + LOCK_TICKS - server.getTickCount());
    }

    /** Whether Duel Points can go into trades on this server. */
    static boolean pointsAllowed() {
        return Points.active() && JadmServerConfig.TRADE.points.get() && JadmServerConfig.POINTS.transfers.get();
    }

    /** Whether {@code onlyModItems} lets an item be traded. */
    static boolean tradeable(ItemStack stack) {
        return !JadmServerConfig.TRADE.onlyModItems.get()
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(Jadm.MOD_ID);
    }

    /** Something in an offer changed: both confirmations are taken back. */
    private void changed() {
        if (over) {
            return;
        }
        lastChange = server.getTickCount();
        sides[0].confirmed = false;
        sides[1].confirmed = false;
    }

    /** A side typed how many points it puts in, as much as it has. */
    void setPoints(int side, int amount) {
        if (over || !pointsAllowed()) {
            return;
        }
        long balance = Points.get(server).balance(sides[side].player);
        int points = (int) Math.max(0, Math.min(amount, Math.min(balance, Integer.MAX_VALUE)));
        if (points != sides[side].points) {
            sides[side].points = points;
            changed();
        }
    }

    /** A side pressed the confirm button: confirm, or take it back. Both confirmed, the trade happens. */
    void toggleConfirm(int side) {
        if (over) {
            return;
        }
        Side me = sides[side];
        if (me.confirmed) {
            me.confirmed = false;
            return;
        }
        if (lockTicks() > 0 || empty()) {
            return;
        }
        me.confirmed = true;
        ServerPlayer other = sides[1 - side].online();
        if (other != null) {
            other.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
        }
        if (sides[0].confirmed && sides[1].confirmed) {
            complete();
        }
    }

    private boolean empty() {
        for (Side side : sides) {
            if (side.points > 0 || !side.offer.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Swaps the offers, if both are still there and both still have the points they put in. */
    private void complete() {
        ServerPlayer first = sides[0].online();
        ServerPlayer second = sides[1].online();
        if (first == null || second == null) {
            return;
        }
        Points points = Points.get(server);
        for (Side side : sides) {
            if (side.points > 0 && (!pointsAllowed() || points.balance(side.player) < side.points)) {
                sides[0].confirmed = false;
                sides[1].confirmed = false;
                for (ServerPlayer player : List.of(first, second)) {
                    player.sendSystemMessage(Component.translatable("message.jadm.trade.points_gone", side.name)
                            .withStyle(ChatFormatting.RED));
                }
                return;
            }
        }
        over = true;
        List<ItemStack> toSecond = drain(sides[0].offer);
        List<ItemStack> toFirst = drain(sides[1].offer);
        if (sides[0].points > 0 || sides[1].points > 0) {
            points.take(server, sides[0].player, sides[0].points);
            points.take(server, sides[1].player, sides[1].points);
            points.add(server, sides[1].player, sides[0].points);
            points.add(server, sides[0].player, sides[1].points);
        }
        Jadm.LOGGER.info("Trade: {} gave {} and {} points, {} gave {} and {} points", sides[0].name,
                describe(toSecond), sides[0].points, sides[1].name, describe(toFirst), sides[1].points);
        toSecond.forEach(stack -> second.getInventory().placeItemBackInInventory(stack));
        toFirst.forEach(stack -> first.getInventory().placeItemBackInInventory(stack));
        Trades.finished(this);
        for (ServerPlayer player : List.of(first, second)) {
            player.closeContainer();
            player.sendSystemMessage(Component.translatable("message.jadm.trade.done",
                    (player == first ? second : first).getDisplayName()).withStyle(ChatFormatting.GREEN));
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f);
        }
    }

    private static List<ItemStack> drain(SimpleContainer offer) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < offer.getContainerSize(); i++) {
            ItemStack stack = offer.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static String describe(List<ItemStack> stacks) {
        List<String> names = new ArrayList<>();
        for (ItemStack stack : stacks) {
            names.add(stack.getCount() + "x " + stack.getHoverName().getString());
        }
        return names.isEmpty() ? "nothing" : String.join(", ", names);
    }

    /**
     * The trade is called off: each side gets their own offer back and their window closes, and is told {@code
     * reason}. {@code closing} is the player who closed their window, if that is how it ended.
     */
    void cancel(ServerPlayer closing, Component reason) {
        if (over) {
            return;
        }
        over = true;
        Trades.finished(this);
        for (Side side : sides) {
            ServerPlayer player = side.online();
            if (player == null) {
                continue;
            }
            giveBack(player, side.offer);
            if (player == closing) {
                continue;
            }
            player.sendSystemMessage(reason.copy().withStyle(ChatFormatting.YELLOW));
            if (player.containerMenu instanceof TradeMenu menu && menu.trade() == this) {
                player.closeContainer();
            }
        }
    }

    /** Puts an offer back into its owner's inventory (whatever doesn't fit drops at their feet). */
    static void giveBack(ServerPlayer player, SimpleContainer offer) {
        for (int i = 0; i < offer.getContainerSize(); i++) {
            ItemStack stack = offer.removeItemNoUpdate(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (player.isAlive()) {
                // Also while logging out: the inventory is saved right after.
                player.getInventory().placeItemBackInInventory(stack, !player.hasDisconnected());
            } else {
                player.drop(stack, false);
            }
        }
    }
}
