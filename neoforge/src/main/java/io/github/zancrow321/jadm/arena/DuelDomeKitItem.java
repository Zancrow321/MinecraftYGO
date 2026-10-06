package io.github.zancrow321.jadm.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Builds a whole Duel Dome where it is used: a 13 by 21 floor with the core in the middle of the clicked block,
 * lined up with the way the player faces, and two platforms at each end for 1v1 and tag duels.
 */
public final class DuelDomeKitItem extends Item {
    private static final int HALF_WIDTH = 6;
    private static final int HALF_LENGTH = 10;
    /** Platforms sit this far from the core, where a duelist stands at the end of the projected field. */
    private static final int PLATFORM_REACH = 9;
    private static final int HEADROOM = 4;

    public DuelDomeKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        BlockPos center = context.getClickedPos();
        Direction length = context.getHorizontalDirection();
        Direction width = length.getClockWise();
        if (!level.isClientSide()) {
            BlockPos blocked = obstruction(level, center, length, width);
            if (blocked != null) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.jadm.duel_dome.blocked",
                            blocked.getX(), blocked.getY(), blocked.getZ()), true);
                }
                return InteractionResult.FAIL;
            }
            build(level, center, length, width);
            level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1f);
            if (player != null && !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** The first block in the way above the floor, or {@code null} if the area is open. */
    private static BlockPos obstruction(Level level, BlockPos center, Direction length, Direction width) {
        for (int a = -HALF_LENGTH; a <= HALF_LENGTH; a++) {
            for (int w = -HALF_WIDTH; w <= HALF_WIDTH; w++) {
                BlockPos floor = center.relative(length, a).relative(width, w);
                if (level.getBlockState(floor).getDestroySpeed(level, floor) < 0
                        || level.getBlockEntity(floor) != null) {
                    return floor;
                }
                for (int up = 1; up <= HEADROOM; up++) {
                    BlockPos pos = floor.above(up);
                    if (!level.getBlockState(pos).canBeReplaced()) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static void build(Level level, BlockPos center, Direction length, Direction width) {
        for (int a = -HALF_LENGTH; a <= HALF_LENGTH; a++) {
            for (int w = -HALF_WIDTH; w <= HALF_WIDTH; w++) {
                BlockPos floor = center.relative(length, a).relative(width, w);
                level.setBlock(floor, floorBlock(a, w), Block.UPDATE_ALL);
                for (int up = 1; up <= HEADROOM; up++) {
                    level.setBlock(floor.above(up), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static BlockState floorBlock(int a, int w) {
        boolean edgeA = Math.abs(a) == HALF_LENGTH;
        boolean edgeW = Math.abs(w) == HALF_WIDTH;
        if (a == 0 && w == 0) {
            return DuelDome.CORE.get().defaultBlockState();
        }
        if (Math.abs(a) == PLATFORM_REACH && Math.abs(w) == 1) {
            return DuelDome.PLATFORM.get().defaultBlockState();
        }
        if (edgeA && edgeW) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        if (edgeA || edgeW) {
            return Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
        }
        // A cyan line across the middle, between the two halves of the field.
        if (a == 0) {
            return Blocks.CYAN_CONCRETE.defaultBlockState();
        }
        return Blocks.SMOOTH_QUARTZ.defaultBlockState();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.jadm.duel_dome_kit.tooltip")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
