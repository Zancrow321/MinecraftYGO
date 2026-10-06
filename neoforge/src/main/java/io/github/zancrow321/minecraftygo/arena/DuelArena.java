package io.github.zancrow321.minecraftygo.arena;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.network.DuelFieldPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Duelist Kingdom arena: one block entity in the middle drawn as one big model, with invisible blocks under the
 * rest of it to walk on. Duelists standing on its podiums duel over its middle, and the podiums carry them up while
 * the duel lasts.
 *
 * <p>The arena's facing is its long axis; the podiums sit {@link #PODIUM_ALONG} blocks from the middle either way.
 */
public final class DuelArena {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MinecraftYgo.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MinecraftYgo.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MinecraftYgo.MOD_ID);

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(3f, 1200f).sound(SoundType.METAL)
                .noOcclusion().pushReaction(PushReaction.BLOCK).isViewBlocking((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false);
    }

    public static final DeferredBlock<ArenaBlock> ARENA = BLOCKS.registerBlock("duel_arena", ArenaBlock::new,
            properties());
    public static final DeferredBlock<ArenaSolidBlock> SOLID = BLOCKS.registerBlock("duel_arena_floor",
            ArenaSolidBlock::new, properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArenaBlockEntity>> ARENA_ENTITY =
            BLOCK_ENTITIES.register("duel_arena",
                    () -> BlockEntityType.Builder.of(ArenaBlockEntity::new, ARENA.get()).build(null));
    public static final DeferredItem<DuelArenaKitItem> KIT = ITEMS.registerItem("duel_arena_kit",
            DuelArenaKitItem::new, new Item.Properties().stacksTo(1));

    /** Blocks from the middle to the end of the platform, along its long axis. */
    static final int HALF_ALONG = 13;
    /** Blocks from the middle to the long sides. */
    static final int HALF_ACROSS = 10;
    /** How tall the platform is, in blocks; duelists stand on top. */
    public static final int HEIGHT = 3;
    /** Where a duelist stands, in blocks from the middle. */
    static final int PODIUM_ALONG = 12;
    /** How far {@link #arenaAt} looks for the middle block: the steps reach two blocks past the ends. */
    private static final int REACH = HALF_ALONG + 2;
    /** How far the podiums go up, in blocks. */
    static final int LIFT = 3;
    /** How long they take to get there, as in the model's animation. */
    static final int LIFT_TICKS = 40;

    /** Everyone riding a podium (or coming back down from one), by entity. */
    private static final Map<UUID, Rider> RIDERS = new HashMap<>();

    private record Rider(ServerLevel level, BlockPos arena, Vec3 base, long since, boolean up) {
    }

    private DuelArena() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    /** The arena's long axis, toward its "a" end. */
    static Direction facing(BlockState arena) {
        return arena.getValue(ArenaBlock.FACING);
    }

    /** The arena block whose platform covers this position, or {@code null}. */
    static BlockPos arenaAt(Level level, BlockPos pos) {
        for (int dy = 0; dy >= -(HEIGHT + LIFT); dy--) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    if (level.getBlockState(at).is(ARENA.get())) {
                        return at;
                    }
                }
            }
        }
        return null;
    }

    /** The 3 by 3 blocks a raised podium stands on, at the {@code end} (+1 or -1) of the arena. */
    static List<BlockPos> podiumFloor(BlockPos arena, Direction facing, int end) {
        List<BlockPos> out = new ArrayList<>();
        Direction side = facing.getClockWise();
        for (int along = PODIUM_ALONG - 1; along <= PODIUM_ALONG + 1; along++) {
            for (int across = -1; across <= 1; across++) {
                out.add(arena.relative(facing, end * along).relative(side, across).above(HEIGHT + LIFT - 1));
            }
        }
        return out;
    }

    /**
     * How tall the arena stands at this spot, in blocks, or 0 off it: the platform, and two steps up at each end
     * behind the podium.
     */
    static int height(int along, int across) {
        int a = Math.abs(along);
        if (a <= HALF_ALONG && Math.abs(across) <= HALF_ACROSS) {
            return HEIGHT;
        }
        if (Math.abs(across) <= 1 && a <= HALF_ALONG + 2) {
            return HALF_ALONG + 3 - a;
        }
        return 0;
    }

    /** Every block the arena fills (its invisible floor and the middle block), for a middle block at {@code arena}. */
    static List<BlockPos> footprint(BlockPos arena, Direction facing) {
        List<BlockPos> out = new ArrayList<>();
        Direction side = facing.getClockWise();
        for (int along = -REACH; along <= REACH; along++) {
            for (int across = -HALF_ACROSS; across <= HALF_ACROSS; across++) {
                BlockPos column = arena.relative(facing, along).relative(side, across);
                for (int up = 0; up < height(along, across); up++) {
                    out.add(column.above(up));
                }
            }
        }
        return out;
    }

    /**
     * The field for a duel on an arena, if every person in it stands on one podium of the same arena, team 0 at one
     * end and team 1 at the other. A team without people (bots, or the NPC, who is walked over to the free podium)
     * needs no podium. The podiums start going up.
     *
     * @return the field over the arena's middle, facing from team 0's end to team 1's, or {@code null}
     */
    public static DuelFieldPayload claim(List<ServerPlayer> team0, List<ServerPlayer> team1, Entity npc) {
        List<ServerPlayer> all = new ArrayList<>(team0);
        all.addAll(team1);
        if (all.isEmpty() || !(all.get(0).level() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos arena = arenaAt(level, all.get(0).blockPosition().below());
        if (arena == null || level.getBlockEntity(arena) instanceof ArenaBlockEntity entity && entity.raised()) {
            return null;
        }
        Direction facing = facing(level.getBlockState(arena));
        int end0 = team0.isEmpty() ? -end(team1, level, arena, facing) : end(team0, level, arena, facing);
        if (end0 == 0 || !team1.isEmpty() && end(team1, level, arena, facing) != -end0) {
            return null;
        }
        long now = level.getGameTime();
        for (ServerPlayer player : all) {
            RIDERS.put(player.getUUID(), new Rider(level, arena, player.position(), now, true));
        }
        if (npc != null && npc.level() == level) {
            int npcEnd = team0.isEmpty() ? end0 : -end0;
            Vec3 spot = Vec3.atBottomCenterOf(arena.relative(facing, npcEnd * PODIUM_ALONG).above(HEIGHT));
            float yaw = (npcEnd > 0 ? facing.getOpposite() : facing).toYRot();
            npc.moveTo(spot.x, spot.y, spot.z, yaw, 0);
            npc.setYHeadRot(yaw);
            RIDERS.put(npc.getUUID(), new Rider(level, arena, spot, now, true));
        }
        if (level.getBlockEntity(arena) instanceof ArenaBlockEntity entity) {
            entity.raise();
        }
        // Team 0 looks from its end toward the middle.
        float yaw = (end0 > 0 ? facing.getOpposite() : facing).toYRot();
        return new DuelFieldPayload(true, arena.getX() + 0.5, arena.getY() + HEIGHT, arena.getZ() + 0.5, yaw);
    }

    /** The end (+1 or -1) of the arena all these players stand on a podium at, or 0. */
    private static int end(List<ServerPlayer> players, Level level, BlockPos arena, Direction facing) {
        int end = 0;
        Direction side = facing.getClockWise();
        for (ServerPlayer player : players) {
            Vec3 d = player.position().subtract(Vec3.atBottomCenterOf(arena));
            double along = d.x * facing.getStepX() + d.z * facing.getStepZ();
            double across = d.x * side.getStepX() + d.z * side.getStepZ();
            int at = (int) Math.signum(along);
            if (player.level() != level || Math.abs(Math.abs(along) - PODIUM_ALONG) > 1.5 || Math.abs(across) > 1.5
                    || d.y < HEIGHT - 0.5 || d.y > HEIGHT + 1 || end != 0 && at != end) {
                return 0;
            }
            end = at;
        }
        return end;
    }

    /** How far up a rider is right now, in blocks; 0 for anyone not on a podium. */
    public static double lift(UUID id) {
        Rider rider = RIDERS.get(id);
        if (rider == null) {
            return 0;
        }
        double t = Math.clamp((rider.level.getGameTime() - rider.since) / (double) LIFT_TICKS, 0, 1);
        return LIFT * (rider.up ? t : 1 - t);
    }

    /** The duel is over: the podiums come down with everyone on them. */
    public static void release(List<UUID> riders) {
        for (UUID id : riders) {
            Rider rider = RIDERS.get(id);
            if (rider != null && rider.up) {
                long now = rider.level.getGameTime();
                RIDERS.put(id, new Rider(rider.level, rider.arena, rider.base, now, false));
                if (rider.level.getBlockEntity(rider.arena) instanceof ArenaBlockEntity entity) {
                    entity.lower();
                }
            }
        }
    }

    /** The middle block of the arena someone standing at {@code pos} is on (or under), or {@code null}. */
    public static BlockPos arenaUnder(Level level, BlockPos pos) {
        return arenaAt(level, pos);
    }

    /** Whether an arena stands at {@code arena} and no duel is using it. */
    public static boolean available(Level level, BlockPos arena) {
        return level.isLoaded(arena) && level.getBlockState(arena).is(ARENA.get()) && !inUse(level, arena)
                && !(level.getBlockEntity(arena) instanceof ArenaBlockEntity entity && entity.raised());
    }

    /** Where a duelist stands on the podium at {@code end} (+1 or -1) of an arena, looking at the middle. */
    public record Spot(Vec3 pos, float yaw) {
    }

    public static Spot podium(Level level, BlockPos arena, int end) {
        Direction facing = facing(level.getBlockState(arena));
        Vec3 pos = Vec3.atBottomCenterOf(arena.relative(facing, end * PODIUM_ALONG).above(HEIGHT));
        return new Spot(pos, (end > 0 ? facing.getOpposite() : facing).toYRot());
    }

    /** Whether a duel is using this arena. */
    static boolean inUse(Level level, BlockPos arena) {
        return RIDERS.values().stream().anyMatch(r -> r.level == level && r.arena.equals(arena));
    }

    /**
     * Carries riders whose position the duel doesn't hold: the NPC, and everyone on the way back down. People
     * sitting at a duel are held by the duel at their spot plus {@link #lift}.
     *
     * @param held whether the duel holds this person in place
     */
    public static void tick(MinecraftServer server, java.util.function.Predicate<UUID> held) {
        RIDERS.entrySet().removeIf(e -> {
            Rider rider = e.getValue();
            long now = rider.level.getGameTime();
            boolean moving = now - rider.since <= LIFT_TICKS;
            Entity entity = rider.level.getEntity(e.getKey());
            if (entity != null && moving && !held.test(e.getKey())) {
                Vec3 to = rider.base.add(0, lift(e.getKey()), 0);
                if (entity instanceof ServerPlayer player) {
                    player.connection.teleport(to.x, to.y, to.z, player.getYRot(), player.getXRot());
                } else {
                    entity.teleportTo(to.x, to.y, to.z);
                }
                entity.setDeltaMovement(Vec3.ZERO);
                entity.resetFallDistance();
            }
            return !rider.up && !moving;
        });
    }

    /** Takes the whole arena away and gives back its kit. */
    static void dismantle(Level level, BlockPos arena, boolean dropKit) {
        BlockState state = level.getBlockState(arena);
        if (!state.is(ARENA.get())) {
            return;
        }
        Direction facing = facing(state);
        footprint(arena, facing).forEach(pos -> clearSolid(level, pos));
        for (int end : new int[]{1, -1}) {
            podiumFloor(arena, facing, end).forEach(pos -> clearSolid(level, pos));
        }
        level.removeBlock(arena, false);
        if (dropKit) {
            Block.popResource(level, arena.above(HEIGHT), new ItemStack(KIT.get()));
        }
    }

    static void clearSolid(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(SOLID.get())) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
