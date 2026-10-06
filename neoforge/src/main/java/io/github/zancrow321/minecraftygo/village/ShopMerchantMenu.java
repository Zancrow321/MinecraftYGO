package io.github.zancrow321.minecraftygo.village;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;

import java.util.function.Predicate;

/** The villager trade window for a shop that is not a villager: a Card Vending Machine or a Shop Stand. */
final class ShopMerchantMenu extends MerchantMenu {
    private final Predicate<Player> valid;

    ShopMerchantMenu(int containerId, Inventory inventory, Merchant shop, Predicate<Player> valid) {
        super(containerId, inventory, shop);
        this.valid = valid;
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && valid.test(player);
    }

    /** As the villager's, but without its trade sound, which only works for a villager. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index != RESULT_SLOT) {
            return super.quickMoveStack(player, index);
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (!moveItemStackTo(stack, 3, 39, true)) {
            return ItemStack.EMPTY;
        }
        slot.onQuickCraft(stack, before);
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return before;
    }
}
