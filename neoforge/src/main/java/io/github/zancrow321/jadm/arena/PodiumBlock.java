package io.github.zancrow321.jadm.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Duelist Podium: where a duelist stands in an arena players built. Placing or breaking one makes the Arena Cores
 * nearby measure their arena again. It can't be broken while its arena is in a duel.
 */
public final class PodiumBlock extends Block {
    public PodiumBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide() && !oldState.is(this)) {
            BuiltArena.changed(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide() && !newState.is(this)) {
            BuiltArena.changed(level, pos);
        }
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter getter, BlockPos pos) {
        if (getter instanceof Level level && !level.isClientSide()) {
            DuelArena.Podium podium = BuiltArena.podiumAt(level, pos);
            if (podium != null && level.getBlockEntity(podium.arena()) instanceof ArenaCoreBlockEntity core
                    && core.raised()) {
                return 0;
            }
        }
        return super.getDestroyProgress(state, player, getter, pos);
    }
}
