package io.github.zancrow321.jadm.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The invisible blocks the arena's model stands on and the raised podiums rest on. Breaking any of them takes the
 * whole arena down and gives back its kit, unless a duel is using it.
 */
public final class ArenaSolidBlock extends Block {
    public ArenaSolidBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Whether the arena around this position is in a duel (its podiums are up). */
    static boolean inDuel(BlockGetter getter, BlockPos pos) {
        if (!(getter instanceof Level level)) {
            return false;
        }
        BlockPos arena = DuelArena.arenaAt(level, pos);
        return arena != null && level.getBlockEntity(arena) instanceof ArenaBlockEntity entity && entity.raised();
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter getter, BlockPos pos) {
        return inDuel(getter, pos) ? 0 : super.getDestroyProgress(state, player, getter, pos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos arena = DuelArena.arenaAt(level, pos);
            if (arena != null) {
                DuelArena.dismantle(level, arena, !player.getAbilities().instabuild);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
