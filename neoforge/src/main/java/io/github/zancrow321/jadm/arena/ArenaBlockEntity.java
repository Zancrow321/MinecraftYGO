package io.github.zancrow321.jadm.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The arena's middle block: draws the whole arena and keeps whether its podiums are up. While they are all the way
 * up, an invisible 3 by 3 floor stands under each so nobody up there is taken for flying.
 */
public final class ArenaBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation RAISE = RawAnimation.begin().thenPlayAndHold("raise");
    private static final RawAnimation LOWER = RawAnimation.begin().thenPlayAndHold("lower");
    private static final RawAnimation LOWERED = RawAnimation.begin().thenLoop("lowered");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean raised;
    /** The raised floors are still standing (they go once the podiums are back down). */
    private boolean floors;
    private long changedAt;
    /** Client: the podiums went up while this was loaded, so going down is worth animating. */
    private boolean seenRaised;
    /** Who waits for a duel here: bit 0 for someone on the podium at end +1, bit 1 for end -1. Not saved. */
    private int waiting;
    /** The game time the duel starts at once both podiums are taken, or 0. Not saved. */
    private long startsAt;

    public ArenaBlockEntity(BlockPos pos, BlockState state) {
        super(DuelArena.ARENA_ENTITY.get(), pos, state);
    }

    public boolean raised() {
        return raised;
    }

    /** Which podiums have someone waiting on them (see {@link #waiting}) and when their duel starts, or 0. */
    public int waiting() {
        return waiting;
    }

    public long startsAt() {
        return startsAt;
    }

    /** Server: shows who waits here, and the countdown, to everyone who can see the arena. */
    void lobby(int waiting, long startsAt) {
        if (this.waiting != waiting || this.startsAt != startsAt) {
            this.waiting = waiting;
            this.startsAt = startsAt;
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    void raise() {
        Level level = getLevel();
        if (level == null || raised) {
            return;
        }
        raised = true;
        changedAt = level.getGameTime();
        level.playSound(null, getBlockPos(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.5f, 0.6f);
        changed();
    }

    void lower() {
        Level level = getLevel();
        if (level == null || !raised) {
            return;
        }
        raised = false;
        changedAt = level.getGameTime();
        // Riders are carried down through where the floors were.
        removeFloors(level, getBlockPos(), getBlockState());
        level.playSound(null, getBlockPos(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.5f, 0.6f);
        changed();
    }

    /**
     * Server: once the podiums are all the way up, puts the floors under them (not sooner, or the riders would pass
     * through them on the way). Lowers podiums no duel is using, which happens after a restart.
     */
    static void serverTick(Level level, BlockPos pos, BlockState state, ArenaBlockEntity arena) {
        if (arena.raised && !arena.floors && level.getGameTime() - arena.changedAt >= DuelArena.LIFT_TICKS) {
            Direction facing = DuelArena.facing(state);
            for (int end : new int[]{1, -1}) {
                for (BlockPos floor : DuelArena.podiumFloor(pos, facing, end)) {
                    if (level.getBlockState(floor).canBeReplaced()) {
                        level.setBlock(floor, DuelArena.SOLID.get().defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
            arena.floors = true;
            arena.setChanged();
        }
        if (arena.raised && level.getGameTime() % 20 == 0 && !DuelArena.inUse(level, pos)) {
            arena.lower();
        }
    }

    private void removeFloors(Level level, BlockPos pos, BlockState state) {
        if (floors) {
            Direction facing = DuelArena.facing(state);
            for (int end : new int[]{1, -1}) {
                DuelArena.podiumFloor(pos, facing, end).forEach(p -> DuelArena.clearSolid(level, p));
            }
            floors = false;
        }
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Raised", raised);
        tag.putBoolean("Floors", floors);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        raised = tag.getBoolean("Raised");
        floors = tag.getBoolean("Floors");
        seenRaised |= raised;
        waiting = tag.getInt("Waiting");
        startsAt = tag.getLong("StartsAt");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Raised", raised);
        tag.putInt("Waiting", waiting);
        tag.putLong("StartsAt", startsAt);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "podiums", 0,
                state -> state.setAndContinue(raised ? RAISE : seenRaised ? LOWER : LOWERED)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
