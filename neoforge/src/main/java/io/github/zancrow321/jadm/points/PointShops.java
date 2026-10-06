package io.github.zancrow321.jadm.points;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Helpers for the shops that take points: paying, handing over items and taking them from the inventory. */
public final class PointShops {
    private PointShops() {
    }

    /** @return whether the player had {@code price} points, which are then taken */
    public static boolean pay(ServerPlayer player, long price) {
        return Points.get(player.server).take(player.server, player.getUUID(), price);
    }

    public static void earn(ServerPlayer player, long amount) {
        Points.get(player.server).add(player.server, player.getUUID(), amount);
    }

    /** Puts a bought item in the inventory, dropping what doesn't fit. */
    public static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack.copy());
    }

    /** @return how many items in the inventory match */
    public static int count(ServerPlayer player, Predicate<ItemStack> match) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && match.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Takes {@code amount} matching items from the inventory; @return whether there were that many */
    public static boolean take(ServerPlayer player, Predicate<ItemStack> match, int amount) {
        if (count(player, match) < amount) {
            return false;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (amount <= 0) {
                break;
            }
            if (!stack.isEmpty() && match.test(stack)) {
                int take = Math.min(amount, stack.getCount());
                stack.shrink(take);
                amount -= take;
            }
        }
        player.getInventory().setChanged();
        return true;
    }
}
