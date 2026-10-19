package io.github.zancrow321.jadm.display;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A collector display's slots, laid out like a dispenser's 3x3 grid (a frame or stand uses the middle one, a pack
 * display the middle row), above the player's inventory. Its owner gets two buttons: lock and light.
 */
public final class DisplayMenu extends AbstractContainerMenu {
    public static final int BUTTON_LOCK = 0;
    public static final int BUTTON_LIGHT = 1;
    private static final int DATA_LOCKED = 0;
    private static final int DATA_LIT = 1;
    private static final int DATA_MANAGE = 2;

    private final DisplayKind kind;
    private final Container container;
    /** The display, on the server; {@code null} on the client. */
    private final DisplayBlockEntity display;
    private final ContainerData data;

    /** The client's menu, for the display at the position the server sent. */
    static DisplayMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        DisplayKind kind = inventory.player.level().getBlockState(pos).getBlock() instanceof DisplayBlock block
                ? block.kind() : DisplayKind.SHOWCASE;
        return new DisplayMenu(containerId, inventory, kind, new SimpleContainer(kind.slots()), null,
                new SimpleContainerData(3));
    }

    DisplayMenu(int containerId, Inventory inventory, DisplayBlockEntity display) {
        this(containerId, inventory, display.kind(), display.items(), display, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_LOCKED -> display.locked() ? 1 : 0;
                    case DATA_LIT -> display.getBlockState().getValue(DisplayBlock.LIT) ? 1 : 0;
                    default -> display.canManage(inventory.player) ? 1 : 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 3;
            }
        });
    }

    private DisplayMenu(int containerId, Inventory inventory, DisplayKind kind, Container container,
                        DisplayBlockEntity display, ContainerData data) {
        super(Displays.MENU.get(), containerId);
        this.kind = kind;
        this.container = container;
        this.display = display;
        this.data = data;
        addDataSlots(data);
        int[] cells = cells(kind);
        for (int i = 0; i < cells.length; i++) {
            int x = 62 + cells[i] % 3 * 18;
            int y = 17 + cells[i] / 3 * 18;
            addSlot(new Slot(container, i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return kind.accepts(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    /** Which cells of the 3x3 grid a display's slots use, in slot order. */
    public static int[] cells(DisplayKind kind) {
        return switch (kind) {
            case SHOWCASE -> new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
            case PACK_DISPLAY -> new int[]{3, 4, 5};
            default -> new int[]{4};
        };
    }

    public DisplayKind kind() {
        return kind;
    }

    public boolean locked() {
        return data.get(DATA_LOCKED) == 1;
    }

    public boolean lit() {
        return data.get(DATA_LIT) == 1;
    }

    /** Whether this player may use the lock and light buttons. */
    public boolean canManage() {
        return data.get(DATA_MANAGE) == 1;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (display == null || !display.canManage(player)) {
            return false;
        }
        if (id == BUTTON_LOCK) {
            display.locked(!display.locked());
            return true;
        }
        if (id == BUTTON_LIGHT && display.getLevel() != null) {
            BlockState state = display.getBlockState();
            display.getLevel().setBlock(display.getBlockPos(), state.cycle(DisplayBlock.LIT), 3);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int size = container.getContainerSize();
        if (index < size) {
            if (!moveItemStackTo(stack, size, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!kind.accepts(stack) || !moveItemStackTo(stack, 0, size, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (display == null) {
            return true;
        }
        return !display.isRemoved() && display.canOpen(player)
                && player.canInteractWithBlock(display.getBlockPos(), 4);
    }
}
