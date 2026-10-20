package io.github.zancrow321.jadm.display;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * A collector display. Right-clicking opens its slots, which take only what it shows; its owner (whoever placed it)
 * can lock it, so only they can take things out or break it, and switch its light on. Card frames and showcases
 * hang on a wall or lie on a table, the others stand on the floor.
 */
public final class DisplayBlock extends BaseEntityBlock {
    /** The way the display's front looks (on the floor: where the bottom of the cards points). */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** On a wall or lying on the floor; ceilings aren't used. */
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final DisplayKind kind;
    private final MapCodec<DisplayBlock> codec;
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public DisplayBlock(DisplayKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new DisplayBlock(kind, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH)
                .setValue(FACE, kind.wall() ? AttachFace.WALL : AttachFace.FLOOR).setValue(LIT, false));
    }

    public DisplayKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction toPlayer = context.getHorizontalDirection().getOpposite();
        if (kind.wall() && clicked.getAxis().isHorizontal()) {
            return defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, clicked);
        }
        if (kind.wall() && clicked == Direction.DOWN) {
            // Under a ceiling: hang it facing whoever placed it.
            return defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, toPlayer);
        }
        return defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(FACING, toPlayer);
    }

    /** The display's outline in its model, facing south with its back at the north side, in sixteenths. */
    private float[] modelBox() {
        return switch (kind) {
            case CARD_FRAME -> new float[]{2, 1, 0, 14, 15, 2};
            case SHOWCASE -> new float[]{0, 0, 0, 16, 16, 3};
            case TOPLOADER_STAND -> new float[]{3, 0, 5, 13, 14, 11};
            case PACK_DISPLAY -> new float[]{1, 0, 3, 15, 10, 13};
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.computeIfAbsent(state, this::shape);
    }

    private VoxelShape shape(BlockState state) {
        float[] box = modelBox();
        float minX = 16, minY = 16, minZ = 16, maxX = 0, maxY = 0, maxZ = 0;
        for (int corner = 0; corner < 8; corner++) {
            Vector3f p = new Vector3f(box[(corner & 1) == 0 ? 0 : 3], box[(corner & 2) == 0 ? 1 : 4],
                    box[(corner & 4) == 0 ? 2 : 5]);
            DisplayGeometry.toBlock(state, p.sub(8, 8, 8).div(16)).mul(16).add(8, 8, 8);
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }
        return Shapes.box(Math.round(minX) / 16d, Math.round(minY) / 16d, Math.round(minZ) / 16d,
                Math.round(maxX) / 16d, Math.round(maxY) / 16d, Math.round(maxZ) / 16d);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof DisplayBlockEntity display) {
            display.owner(player.getUUID(), player.getGameProfile().getName());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof DisplayBlockEntity display)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!display.canOpen(player)) {
            player.displayClientMessage(Component.translatable("message.jadm.display.locked", display.ownerName()),
                    true);
            return InteractionResult.CONSUME;
        }
        serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new DisplayMenu(id, inventory, display),
                display.title()), buf -> buf.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return canBreak(level, pos, player) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
                                       FluidState fluid) {
        if (!canBreak(level, pos, player)) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DisplayBlockEntity display) {
                player.displayClientMessage(Component.translatable("message.jadm.display.locked",
                        display.ownerName()), true);
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    private static boolean canBreak(BlockGetter level, BlockPos pos, Player player) {
        return !(level.getBlockEntity(pos) instanceof DisplayBlockEntity display) || display.canOpen(player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DisplayBlockEntity display) {
            Containers.dropContents(level, pos, display.items());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
