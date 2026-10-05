package io.github.zancrow321.minecraftygo.arena;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.network.DuelFieldPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The Duel Dome: a core block in the middle of an arena and duelist platforms at either end. Duelists standing on
 * the platforms of one dome get the field projected over the core, lined up with the arena, instead of between
 * wherever they happen to stand.
 */
public final class DuelDome {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MinecraftYgo.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MinecraftYgo.MOD_ID);

    public static final DeferredBlock<Block> CORE = BLOCKS.registerSimpleBlock("duel_dome_core",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3f, 6f)
                    .sound(SoundType.METAL).lightLevel(state -> 12).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> PLATFORM = BLOCKS.registerSimpleBlock("duelist_platform",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(3f, 6f)
                    .sound(SoundType.METAL).lightLevel(state -> 6).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.registerSimpleBlockItem(CORE);
    public static final DeferredItem<BlockItem> PLATFORM_ITEM = ITEMS.registerSimpleBlockItem(PLATFORM);
    public static final DeferredItem<DuelDomeKitItem> KIT = ITEMS.registerItem("duel_dome_kit",
            DuelDomeKitItem::new, new Item.Properties().stacksTo(1));

    /** How far from the core a platform may be, along the arena's long axis. */
    static final int MIN_REACH = 4;
    static final int MAX_REACH = 14;
    /** How far a platform may be off the arena's center line (tag partners stand side by side). */
    private static final int LATERAL = 2;

    private DuelDome() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    /**
     * The field for a duel between these teams, if every person in it stands on a platform of the same dome, team 0
     * at one end and team 1 at the other (a team of bots needs no platform).
     *
     * @return the field over the core, facing from team 0's end to team 1's, or {@code null}
     */
    public static DuelFieldPayload field(List<ServerPlayer> team0, List<ServerPlayer> team1) {
        if (team0.isEmpty()) {
            return null;
        }
        ServerPlayer first = team0.get(0);
        Level level = first.level();
        BlockPos platform = platformUnder(first);
        if (platform == null) {
            return null;
        }
        for (Direction toward : Direction.Plane.HORIZONTAL) {
            BlockPos core = findCore(level, platform, toward);
            if (core == null) {
                continue;
            }
            if (onSide(team0, level, core, toward.getOpposite()) && onSide(team1, level, core, toward)) {
                float yaw = toward.toYRot();
                return new DuelFieldPayload(true, core.getX() + 0.5, core.getY() + 1, core.getZ() + 0.5, yaw);
            }
        }
        return null;
    }

    /** The platform block the player stands on, or {@code null}. */
    private static BlockPos platformUnder(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        for (BlockPos pos : List.of(feet.below(), feet)) {
            if (player.level().getBlockState(pos).is(PLATFORM.get())) {
                return pos;
            }
        }
        return null;
    }

    private static BlockPos findCore(Level level, BlockPos platform, Direction toward) {
        Direction side = toward.getClockWise();
        for (int reach = MIN_REACH; reach <= MAX_REACH; reach++) {
            for (int lateral = -LATERAL; lateral <= LATERAL; lateral++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = platform.relative(toward, reach).relative(side, lateral).above(dy);
                    if (level.getBlockState(pos).is(CORE.get())) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** Every player stands on a platform at the {@code end} of the dome around {@code core}. */
    private static boolean onSide(List<ServerPlayer> players, Level level, BlockPos core, Direction end) {
        for (ServerPlayer player : players) {
            BlockPos platform = platformUnder(player);
            if (platform == null || player.level() != level) {
                return false;
            }
            int along = (platform.getX() - core.getX()) * end.getStepX() + (platform.getZ() - core.getZ()) * end.getStepZ();
            int across = Math.abs((platform.getX() - core.getX()) * end.getStepZ()
                    - (platform.getZ() - core.getZ()) * end.getStepX());
            if (along < MIN_REACH || along > MAX_REACH || across > LATERAL || Math.abs(platform.getY() - core.getY()) > 1) {
                return false;
            }
        }
        return true;
    }
}
