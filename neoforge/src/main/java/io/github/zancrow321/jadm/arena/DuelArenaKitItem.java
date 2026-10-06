package io.github.zancrow321.jadm.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Sets up a Duelist Kingdom arena on the clicked ground: 21 by 21 blocks, its middle over the clicked block and its
 * podiums at the ends of the way the player looks. Breaking any part of it packs it back into this kit.
 */
public final class DuelArenaKitItem extends Item {
    /** Clear space the arena needs above its platform, and above its podiums, which go up. */
    private static final int HEADROOM = 4;
    private static final int PODIUM_HEADROOM = DuelArena.LIFT + 4;

    public DuelArenaKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        BlockPos center = context.getClickedPos().above();
        Direction facing = context.getHorizontalDirection();
        if (!level.isClientSide()) {
            BlockPos blocked = obstruction(level, center, facing);
            if (blocked != null) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.jadm.duel_arena.blocked",
                            blocked.getX(), blocked.getY(), blocked.getZ()), true);
                }
                return InteractionResult.FAIL;
            }
            build(level, center, facing);
            level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1f);
            if (player != null && !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** The first block in the way, or {@code null} if there's room. */
    private static BlockPos obstruction(Level level, BlockPos center, Direction facing) {
        if (center.getY() + DuelArena.HEIGHT + PODIUM_HEADROOM >= level.getMaxBuildHeight()) {
            return center.atY(level.getMaxBuildHeight() - 1);
        }
        Direction side = facing.getClockWise();
        for (int along = -DuelArena.HALF_ALONG - 2; along <= DuelArena.HALF_ALONG + 2; along++) {
            for (int across = -DuelArena.HALF_ACROSS; across <= DuelArena.HALF_ACROSS; across++) {
                int height = DuelArena.height(along, across);
                if (height == 0) {
                    continue;
                }
                boolean podium = Math.abs(Math.abs(along) - DuelArena.PODIUM_ALONG) <= 1 && Math.abs(across) <= 1;
                BlockPos column = center.relative(facing, along).relative(side, across);
                for (int up = 0; up < height + (podium ? PODIUM_HEADROOM : HEADROOM); up++) {
                    BlockPos pos = column.above(up);
                    if (!level.getBlockState(pos).canBeReplaced()) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static void build(Level level, BlockPos center, Direction facing) {
        BlockState solid = DuelArena.SOLID.get().defaultBlockState();
        for (BlockPos pos : DuelArena.footprint(center, facing)) {
            if (!pos.equals(center)) {
                level.setBlock(pos, solid, Block.UPDATE_ALL);
            }
        }
        level.setBlock(center, DuelArena.ARENA.get().defaultBlockState().setValue(ArenaBlock.FACING, facing),
                Block.UPDATE_ALL);
        Direction side = facing.getClockWise();
        for (int along = -DuelArena.HALF_ALONG - 2; along <= DuelArena.HALF_ALONG + 2; along++) {
            for (int across = -DuelArena.HALF_ACROSS; across <= DuelArena.HALF_ACROSS; across++) {
                int height = DuelArena.height(along, across);
                if (height == 0) {
                    continue;
                }
                BlockPos column = center.relative(facing, along).relative(side, across);
                // Grass and flowers would poke through the platform.
                for (int up = height; up < height + HEADROOM; up++) {
                    if (!level.getBlockState(column.above(up)).isAir()) {
                        level.setBlock(column.above(up), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                // Whoever stood here is now on top rather than stuck inside.
                AABB inside = new AABB(column).expandTowards(0, height - 1, 0);
                for (Entity entity : level.getEntities((Entity) null, inside, e -> !e.isPassenger())) {
                    entity.teleportTo(entity.getX(), column.getY() + height, entity.getZ());
                }
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.jadm.duel_arena_kit.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
