package io.github.zancrow321.jadm.arena;

import net.minecraft.ChatFormatting;
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

import java.util.List;

/**
 * Builds a Duelist Kingdom arena out of ordinary blocks on the clicked ground (see {@link ArenaBlueprint}), its middle
 * over the clicked block and its podiums at the ends of the way the player looks. It is an arena like any players
 * build: they can change it however they like, and its Arena Core fits the field to it.
 */
public final class DuelArenaKitItem extends Item {
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
            BlockPos blocked = ArenaBlueprint.obstruction(level, center, facing);
            if (blocked != null) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.jadm.duel_arena.blocked",
                            blocked.getX(), blocked.getY(), blocked.getZ()), true);
                }
                return InteractionResult.FAIL;
            }
            ArenaBlueprint.build(level, center, facing);
            level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1f);
            if (player != null && !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.jadm.duel_arena_kit.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
