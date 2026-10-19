package io.github.zancrow321.jadm.display;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * What a collector display holds, who owns it and whether it is locked. Everyone nearby gets the items, so they see
 * them in the display.
 */
public final class DisplayBlockEntity extends BlockEntity {
    private final DisplayKind kind;
    private final SimpleContainer items;
    private UUID owner;
    private String ownerName = "";
    /** Locked: only the owner (and operators) can take things out or break it. New displays are locked. */
    private boolean locked = true;
    private Component customName;
    /** The game tick the client last let the display sparkle; client only. */
    public long sparkled;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(Displays.ENTITY.get(), pos, state);
        this.kind = state.getBlock() instanceof DisplayBlock block ? block.kind() : DisplayKind.CARD_FRAME;
        this.items = new SimpleContainer(kind.slots()) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return kind.accepts(stack);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                changed();
            }
        };
    }

    public DisplayKind kind() {
        return kind;
    }

    public SimpleContainer items() {
        return items;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    void owner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
        changed();
    }

    public boolean locked() {
        return locked;
    }

    void locked(boolean locked) {
        this.locked = locked;
        changed();
    }

    /** The name it was given in an anvil, or {@code null}. */
    public Component customName() {
        return customName;
    }

    Component title() {
        return customName != null ? customName : getBlockState().getBlock().getName();
    }

    /** Whether a player may change the lock and the light: the owner, and operators. */
    public boolean canManage(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    /** Whether a player may open it and break it: anyone while it is open, else whoever can manage it. */
    public boolean canOpen(Player player) {
        return !locked || canManage(player);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items.getItems(), true, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        tag.putBoolean("Locked", locked);
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.getItems().replaceAll(stack -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items.getItems(), registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        locked = !tag.contains("Locked") || tag.getBoolean("Locked");
        customName = tag.contains("CustomName") ? Component.Serializer.fromJson(tag.getString("CustomName"),
                registries) : null;
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        customName = input.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, customName);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("CustomName");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
