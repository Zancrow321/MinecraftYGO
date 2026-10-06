package io.github.zancrow321.jadm.village;

import com.mojang.serialization.MapCodec;
import io.github.zancrow321.jadm.JadmServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Shop Stand: a market stall a player sets up to sell their own things. Its owner (and operators, with {@code
 * operatorsManage}) right-click it to set wares and prices, fill the stock and empty the till; everyone else, and the
 * owner when sneaking, gets its trade window. Only they can break it, which drops the stock and the till.
 */
public final class ShopStand extends BaseEntityBlock {
    public static final MapCodec<ShopStand> CODEC = simpleCodec(ShopStand::new);

    public ShopStand(Properties properties) {
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
        return new ShopStandBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        int max = JadmServerConfig.PLAYER_SHOPS.maxPerPlayer.get();
        if (player instanceof ServerPlayer serverPlayer && max > 0 && !serverPlayer.isCreative()
                && PlayerShops.Owners.get(serverPlayer.server).count(player.getUUID()) >= max) {
            player.displayClientMessage(Component.translatable("message.jadm.shop_stand.limit", max), true);
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof ShopStandBlockEntity stand) {
            stand.owner(player.getUUID(), player.getGameProfile().getName());
            if (stack.has(DataComponents.CUSTOM_NAME)) {
                stand.customName(stack.getHoverName());
            }
            PlayerShops.Owners.get(player.server).add(player.getUUID(), level, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof ShopStandBlockEntity stand)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        boolean manage = canManage(stand, player) && !player.isShiftKeyDown();
        if (!manage && !JadmServerConfig.PLAYER_SHOPS.enabled.get()) {
            player.displayClientMessage(Component.translatable("message.jadm.shop_stand.closed"), true);
        } else if (!stand.claim(player)) {
            player.displayClientMessage(Component.translatable("message.jadm.shop_stand.busy"), true);
        } else if (manage) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShopStandMenu(id, inventory,
                    stand), stand.title()));
        } else {
            StandShop.open(stand, serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    /** Whether a player may stock the stand and break it: its owner, and operators with {@code operatorsManage}. */
    static boolean canManage(ShopStandBlockEntity stand, Player player) {
        return player.getUUID().equals(stand.owner()) || stand.owner() == null
                || JadmServerConfig.PLAYER_SHOPS.operatorsManage.get() && player.hasPermissions(2);
    }

    /** Tells the owner, when they close the stand, about prices {@code currencyOnly} or {@code onlyModItems} hide. */
    static void warnPrices(ShopStandBlockEntity stand, Player player) {
        if (io.github.zancrow321.jadm.points.Points.active()) {
            return;
        }
        for (int column = 0; column < ShopStandMenu.OFFERS; column++) {
            ItemStack ware = stand.slots().getItem(column);
            ItemStack price = stand.slots().getItem(ShopStandMenu.PRICES + column);
            if (!ware.isEmpty() && !price.isEmpty() && !StandShop.sellable(ware, price)) {
                player.displayClientMessage(Component.translatable("message.jadm.shop_stand.not_sellable",
                        ware.getHoverName(), CardShop.currency().getDescription()).withStyle(ChatFormatting.RED),
                        false);
            }
        }
    }

    private static boolean canManage(BlockGetter level, BlockPos pos, Player player) {
        return !(level.getBlockEntity(pos) instanceof ShopStandBlockEntity stand) || canManage(stand, player);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return canManage(level, pos, player) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
                                       FluidState fluid) {
        if (!canManage(level, pos, player)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.jadm.shop_stand.not_owner"),
                        true);
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ShopStandBlockEntity stand) {
            // The samples aren't items; the stock and the till are.
            for (int i = ShopStandMenu.STOCK; i < ShopStandMenu.SIZE; i++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stand.slots().getItem(i));
            }
            if (stand.owner() != null && level.getServer() != null) {
                PlayerShops.Owners.get(level.getServer()).remove(stand.owner(), level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
