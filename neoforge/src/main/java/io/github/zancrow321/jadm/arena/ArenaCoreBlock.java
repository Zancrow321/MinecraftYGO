package io.github.zancrow321.jadm.arena;

import com.mojang.serialization.MapCodec;
import io.github.zancrow321.jadm.network.ArenaCoreOpenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Arena Core: placed anywhere in an arena players built, it measures the build and fits the duel field to it.
 * Using it shows the field's outline and opens its settings.
 */
public final class ArenaCoreBlock extends BaseEntityBlock {
    public static final MapCodec<ArenaCoreBlock> CODEC = simpleCodec(ArenaCoreBlock::new);

    public ArenaCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArenaCoreBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, DuelDome.CORE_ENTITY.get(), ArenaCoreBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof ArenaCoreBlockEntity core) {
            core.preview();
            PacketDistributor.sendToPlayer(server, new ArenaCoreOpenPayload(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter getter, BlockPos pos) {
        return getter.getBlockEntity(pos) instanceof ArenaCoreBlockEntity core && core.raised() ? 0
                : super.getDestroyProgress(state, player, getter, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ArenaCoreBlockEntity core) {
            core.removed();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
