package io.github.zancrow321.minecraftygo.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * A Shop Stand's owner and its 54 slots, laid out as in {@link ShopStandMenu}: what it sells, the price of each,
 * the stock and the till. Not a {@link net.minecraft.world.Container} itself, so hoppers can't reach in.
 */
public final class ShopStandBlockEntity extends BlockEntity {
    private final SimpleContainer slots = new SimpleContainer(ShopStandMenu.SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            ShopStandBlockEntity.this.setChanged();
        }
    };
    /** The price of each ware in points, for {@code currency = "points"}; 0 for not for sale. */
    private final int[] points = new int[ShopStandMenu.OFFERS];
    private UUID owner;
    private String ownerName = "";
    private Component customName;
    /** Who has the stand open, as owner or buyer; one at a time, so stock and till can't change under a trade. */
    private Player user;

    public ShopStandBlockEntity(BlockPos pos, BlockState state) {
        super(PlayerShops.STAND_ENTITY.get(), pos, state);
    }

    SimpleContainer slots() {
        return slots;
    }

    int points(int column) {
        return points[column];
    }

    void points(int column, int price) {
        points[column] = Math.max(0, price);
        setChanged();
    }

    UUID owner() {
        return owner;
    }

    String ownerName() {
        return ownerName;
    }

    void owner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
        setChanged();
    }

    Component customName() {
        return customName;
    }

    void customName(Component name) {
        customName = name;
        setChanged();
    }

    /** The stand's name: its own if it was renamed, else "{owner}'s Shop". */
    Component title() {
        return customName != null ? customName
                : Component.translatable("container.minecraftygo.shop_stand", ownerName);
    }

    /** @return whether {@code player} may use the stand now; if so it is theirs until {@link #release} */
    boolean claim(Player player) {
        if (user != null && user != player && user.isAlive() && !user.isRemoved()
                && user.containerMenu != user.inventoryMenu) {
            return false;
        }
        user = player;
        return true;
    }

    void release(Player player) {
        if (user == player) {
            user = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, slots.getItems(), registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putString("ownerName", ownerName);
        tag.putIntArray("points", points);
        if (customName != null) {
            tag.putString("customName", Component.Serializer.toJson(customName, registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        slots.getItems().replaceAll(stack -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, slots.getItems(), registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        int[] saved = tag.getIntArray("points");
        java.util.Arrays.fill(points, 0);
        System.arraycopy(saved, 0, points, 0, Math.min(saved.length, points.length));
        customName = tag.contains("customName") ? Component.Serializer.fromJson(tag.getString("customName"),
                registries) : null;
    }
}
