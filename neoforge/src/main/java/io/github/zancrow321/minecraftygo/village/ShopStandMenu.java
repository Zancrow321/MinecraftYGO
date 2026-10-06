package io.github.zancrow321.minecraftygo.village;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The owner's view of a Shop Stand, laid out like a large chest: the top row is what the stand sells (a sample,
 * as many as one sale gives), the second row the price of the ware above it, the next three rows the stock the wares
 * come out of, and the bottom row the till the payments go into. The top two rows hold samples, not items: clicking
 * with an item sets a copy of it, right-click adds or takes one, clicking with an empty hand clears it.
 */
public final class ShopStandMenu extends AbstractContainerMenu {
    public static final int OFFERS = 9;
    public static final int PRICES = OFFERS;
    public static final int STOCK = OFFERS * 2;
    public static final int TILL = STOCK + 27;
    public static final int SIZE = TILL + 9;

    private final Container container;
    /** The stand, on the server; {@code null} on the client. */
    private final ShopStandBlockEntity stand;

    /** The client's menu, filled by the server. */
    public ShopStandMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SIZE), null);
    }

    ShopStandMenu(int containerId, Inventory inventory, ShopStandBlockEntity stand) {
        this(containerId, inventory, stand.slots(), stand);
    }

    private ShopStandMenu(int containerId, Inventory inventory, Container container, ShopStandBlockEntity stand) {
        super(PlayerShops.STAND_MENU.get(), containerId);
        this.container = container;
        this.stand = stand;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                addSlot(index < STOCK ? new SampleSlot(container, index, x, y) : new Slot(container, index, x, y));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 198));
        }
    }

    /** @return whether a slot of this menu is one of the sample rows */
    public static boolean isSample(Slot slot) {
        return slot instanceof SampleSlot;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId < 0 || slotId >= STOCK) {
            super.clicked(slotId, button, type, player);
            return;
        }
        Slot slot = slots.get(slotId);
        ItemStack carried = getCarried();
        ItemStack sample = slot.getItem();
        if (type == ClickType.QUICK_MOVE) {
            slot.set(ItemStack.EMPTY);
        } else if (type == ClickType.PICKUP) {
            if (carried.isEmpty()) {
                slot.set(button == 0 || sample.getCount() <= 1 ? ItemStack.EMPTY
                        : sample.copyWithCount(sample.getCount() - 1));
            } else if (button == 0) {
                slot.set(carried.copy());
            } else if (ItemStack.isSameItemSameComponents(sample, carried)) {
                slot.set(sample.copyWithCount(Math.min(sample.getCount() + 1, sample.getMaxStackSize())));
            } else {
                slot.set(carried.copyWithCount(1));
            }
        }
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !isSample(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !isSample(slot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || isSample(slot)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        // Stock and till go to the inventory; the inventory goes into the stock.
        if (index < SIZE ? !moveItemStackTo(stack, SIZE, slots.size(), true)
                : !moveItemStackTo(stack, STOCK, TILL, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return stand == null || !stand.isRemoved() && Container.stillValidBlockEntity(stand, player)
                && ShopStand.canManage(stand, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (stand != null) {
            stand.release(player);
            ShopStand.warnPrices(stand, player);
        }
    }

    /** A slot of the top two rows, which holds a sample set by clicking rather than an item put in. */
    private static final class SampleSlot extends Slot {
        SampleSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
