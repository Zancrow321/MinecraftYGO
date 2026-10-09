package io.github.zancrow321.jadm.trade;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One player's trade window: their own offer on the left (slots they put items into), the other player's offer on
 * the right (to look at only), then their inventory. Points, confirmations and the lock after a change come as data
 * slots; the confirm button is {@link #clickMenuButton} 0.
 */
public final class TradeMenu extends AbstractContainerMenu {
    public static final int OWN = 0;
    public static final int THEIRS = Trade.SLOTS;
    public static final int INVENTORY = Trade.SLOTS * 2;
    public static final int COLUMNS = 4;
    public static final int SLOT_Y = 30;
    public static final int OWN_X = 8;
    public static final int THEIR_X = 98;
    public static final int INVENTORY_Y = 150;
    public static final int BUTTON_CONFIRM = 0;

    /** Data slots: confirmations, both offers' points (each as two 16-bit halves), points allowed, lock ticks. */
    private static final int MY_CONFIRMED = 0;
    private static final int THEIR_CONFIRMED = 1;
    private static final int MY_POINTS = 2;
    private static final int THEIR_POINTS = 4;
    private static final int POINTS_ALLOWED = 6;
    private static final int LOCK = 7;
    private static final int DATA = 8;

    /** The other player's name. */
    private final String partner;
    /** The trade and this player's side of it, on the server; {@code null} and -1 on the client. */
    private final Trade trade;
    private final int side;
    private final ContainerData data;

    /** The client's menu, filled by the server. */
    public TradeMenu(int containerId, Inventory inventory, String partner) {
        this(containerId, inventory, partner, null, -1, new SimpleContainer(Trade.SLOTS),
                new SimpleContainer(Trade.SLOTS), new SimpleContainerData(DATA));
    }

    TradeMenu(int containerId, Inventory inventory, Trade trade, int side) {
        this(containerId, inventory, trade.side(1 - side).name, trade, side, trade.side(side).offer,
                trade.side(1 - side).offer, new ContainerData() {
                    @Override
                    public int get(int index) {
                        Trade.Side me = trade.side(side);
                        Trade.Side them = trade.side(1 - side);
                        return switch (index) {
                            case MY_CONFIRMED -> me.confirmed ? 1 : 0;
                            case THEIR_CONFIRMED -> them.confirmed ? 1 : 0;
                            case MY_POINTS -> me.points & 0xFFFF;
                            case MY_POINTS + 1 -> me.points >>> 16;
                            case THEIR_POINTS -> them.points & 0xFFFF;
                            case THEIR_POINTS + 1 -> them.points >>> 16;
                            case POINTS_ALLOWED -> Trade.pointsAllowed() ? 1 : 0;
                            case LOCK -> trade.lockTicks();
                            default -> 0;
                        };
                    }

                    @Override
                    public void set(int index, int value) {
                    }

                    @Override
                    public int getCount() {
                        return DATA;
                    }
                });
    }

    private TradeMenu(int containerId, Inventory inventory, String partner, Trade trade, int side, Container own,
                      Container theirs, ContainerData data) {
        super(Trades.MENU.get(), containerId);
        this.partner = partner;
        this.trade = trade;
        this.side = side;
        this.data = data;
        addDataSlots(data);
        for (int i = 0; i < Trade.SLOTS; i++) {
            addSlot(new OfferSlot(own, i, OWN_X + i % COLUMNS * 18, SLOT_Y + i / COLUMNS * 18));
        }
        for (int i = 0; i < Trade.SLOTS; i++) {
            addSlot(new ViewSlot(theirs, i, THEIR_X + i % COLUMNS * 18, SLOT_Y + i / COLUMNS * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    Trade trade() {
        return trade;
    }

    public String partner() {
        return partner;
    }

    public boolean confirmed() {
        return data.get(MY_CONFIRMED) == 1;
    }

    public boolean partnerConfirmed() {
        return data.get(THEIR_CONFIRMED) == 1;
    }

    public int points() {
        return data.get(MY_POINTS) & 0xFFFF | (data.get(MY_POINTS + 1) & 0xFFFF) << 16;
    }

    public int partnerPoints() {
        return data.get(THEIR_POINTS) & 0xFFFF | (data.get(THEIR_POINTS + 1) & 0xFFFF) << 16;
    }

    public boolean pointsAllowed() {
        return data.get(POINTS_ALLOWED) == 1;
    }

    /** Ticks until the confirm button works again after a change. */
    public int lockTicks() {
        return data.get(LOCK) & 0xFFFF;
    }

    /** Whether there is anything in either offer. */
    public boolean hasOffers() {
        if (points() > 0 || partnerPoints() > 0) {
            return true;
        }
        for (int i = 0; i < INVENTORY; i++) {
            if (slots.get(i).hasItem()) {
                return true;
            }
        }
        return false;
    }

    /** @return whether a slot of this menu shows the other player's offer */
    public static boolean isTheirs(Slot slot) {
        return slot instanceof ViewSlot;
    }

    /** The player typed how many points they put in; ignored unless they have this trade open. */
    public static void setPoints(ServerPlayer player, int containerId, int amount) {
        if (player.containerMenu instanceof TradeMenu menu && menu.containerId == containerId
                && menu.trade != null) {
            menu.trade.setPoints(menu.side, amount);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_CONFIRM && trade != null) {
            trade.toggleConfirm(side);
            return true;
        }
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // The other offer is only to look at, and nothing moves once the trade is over.
        if (slotId >= THEIRS && slotId < INVENTORY || trade != null && trade.over()) {
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !isTheirs(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !isTheirs(slot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || isTheirs(slot)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        // The offer goes back to the inventory; the inventory goes into the offer.
        if (index < THEIRS ? !moveItemStackTo(stack, INVENTORY, slots.size(), true)
                : !moveItemStackTo(stack, OWN, THEIRS, false)) {
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
        // Walking away, a duel and logging out end the trade in Trades.tick, with their own message.
        return trade == null || !trade.over();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (trade != null && player instanceof ServerPlayer serverPlayer) {
            trade.cancel(serverPlayer, Component.translatable("message.jadm.trade.cancelled",
                    serverPlayer.getDisplayName()));
            // Normally done by cancel(), unless the trade had already ended.
            Trade.giveBack(serverPlayer, trade.side(side).offer);
        }
    }

    /** A slot of the player's own offer, for what {@code onlyModItems} allows. */
    private static final class OfferSlot extends Slot {
        OfferSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return Trade.tradeable(stack);
        }
    }

    /** A slot of the other player's offer: shown, never touched. */
    private static final class ViewSlot extends Slot {
        ViewSlot(Container container, int index, int x, int y) {
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
