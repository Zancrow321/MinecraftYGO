package io.github.zancrow321.jadm.village;

import com.mojang.serialization.MapCodec;
import io.github.zancrow321.jadm.JadmServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Card Vending Machine: right-click it to buy packs and supplies at the {@code [shop]} prices, without a
 * villager. What it sells is set in {@code [shop.machine]}.
 */
public final class CardMachine extends HorizontalDirectionalBlock {
    public static final MapCodec<CardMachine> CODEC = simpleCodec(CardMachine::new);

    public CardMachine(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!allowed(context.getPlayer())) {
            if (context.getPlayer() != null && !context.getLevel().isClientSide()) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.jadm.card_machine.operators"), true);
            }
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return allowed(player) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
                                       net.minecraft.world.level.material.FluidState fluid) {
        // Also stops players in creative mode, who break blocks without destroy progress.
        return allowed(player) && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MachineShop.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Whether a player may place or break machines: anyone, unless {@code operatorsOnly}. */
    private static boolean allowed(Player player) {
        return !JadmServerConfig.MACHINE.operatorsOnly.get() || player != null && player.hasPermissions(2);
    }
}
