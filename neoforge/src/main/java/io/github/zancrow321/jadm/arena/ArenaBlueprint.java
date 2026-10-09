package io.github.zancrow321.jadm.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Duelist Kingdom arena in ordinary blocks, as the Duel Arena kit builds it: a platform 3 blocks tall with a
 * glass-tiled top, white long sides with black markings, a ribbed red end and a ribbed blue end with steps up, sign
 * posts and lamps, an Duelist Podium near each end and the Arena Core in the middle of the floor. Players can build on
 * it or change it however they like; the core fits the field to whatever stands.
 */
final class ArenaBlueprint {
    /** Blocks from the middle to the ends of the platform, and to its long sides. */
    static final int HALF_ALONG = 13;
    static final int HALF_ACROSS = 10;
    /** The steps reach this far past the ends. */
    static final int STEPS = 2;
    /** The platform's height: duelists stand on top. */
    static final int HEIGHT = 3;
    /** Where the podiums are, in blocks from the middle. */
    static final int PODIUM = 12;
    /** How far the podiums go up in a duel, as on the anime's arena. */
    static final int LIFT = 3;
    /** Room the arena needs over its top (for the posts and lamps, and the podiums going up). */
    static final int HEADROOM = 7;

    private ArenaBlueprint() {
    }

    /** The first block in the way of an arena around {@code center} (its floor level), or {@code null}. */
    static BlockPos obstruction(Level level, BlockPos center, Direction facing) {
        if (center.getY() + HEIGHT + HEADROOM >= level.getMaxBuildHeight()) {
            return center.atY(level.getMaxBuildHeight() - 1);
        }
        Direction side = facing.getClockWise();
        for (int along = -HALF_ALONG - STEPS; along <= HALF_ALONG + STEPS; along++) {
            for (int across = -HALF_ACROSS; across <= HALF_ACROSS; across++) {
                if (height(along, across) == 0) {
                    continue;
                }
                BlockPos column = center.relative(facing, along).relative(side, across);
                for (int up = 0; up < HEIGHT + HEADROOM; up++) {
                    BlockPos pos = column.above(up);
                    if (!level.getBlockState(pos).canBeReplaced()) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** How tall the arena stands at this spot: the platform, and two steps up at each end. */
    private static int height(int along, int across) {
        int a = Math.abs(along);
        if (a <= HALF_ALONG && Math.abs(across) <= HALF_ACROSS) {
            return HEIGHT;
        }
        if (Math.abs(across) <= 1 && a <= HALF_ALONG + STEPS) {
            return HALF_ALONG + STEPS + 1 - a;
        }
        return 0;
    }

    /**
     * Builds the arena around {@code center} (the block over the clicked ground), its long axis along
     * {@code facing}; the red end lies ahead.
     *
     * @return where its Arena Core went
     */
    static BlockPos build(Level level, BlockPos center, Direction facing) {
        Direction side = facing.getClockWise();
        BlockPos core = center.above(HEIGHT - 1);
        for (int along = -HALF_ALONG - STEPS; along <= HALF_ALONG + STEPS; along++) {
            for (int across = -HALF_ACROSS; across <= HALF_ACROSS; across++) {
                int height = height(along, across);
                if (height == 0) {
                    continue;
                }
                BlockPos column = center.relative(facing, along).relative(side, across);
                for (int up = 0; up < height; up++) {
                    level.setBlock(column.above(up), block(along, across, up), Block.UPDATE_ALL);
                }
                // Grass and flowers would poke through the top.
                for (int up = height; up < height + HEADROOM; up++) {
                    if (!level.getBlockState(column.above(up)).isAir()) {
                        level.setBlock(column.above(up), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                // Whoever stood here is now on top rather than stuck inside.
                AABB inside = new AABB(column).expandTowards(0, height - 1, 0);
                for (Entity entity : level.getEntities((Entity) null, inside, e -> !e.isPassenger())) {
                    entity.teleportTo(entity.getX(), column.getY() + height, entity.getZ());
                }
            }
        }
        posts(level, center, facing, side);
        level.setBlock(core, DuelDome.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        for (int end : new int[]{1, -1}) {
            level.setBlock(center.relative(facing, end * PODIUM).above(HEIGHT - 1),
                    DuelDome.PLATFORM.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(core) instanceof ArenaCoreBlockEntity entity) {
            entity.settings(0, LIFT, false);
        }
        return core;
    }

    private static BlockState block(int along, int across, int up) {
        int a = Math.abs(along);
        int c = Math.abs(across);
        boolean red = along > 0;
        if (a > HALF_ALONG) {
            // The steps, in the end's colour.
            return (red ? Blocks.RED_CONCRETE : Blocks.BLUE_CONCRETE).defaultBlockState();
        }
        if (a == HALF_ALONG) {
            // The ends: upright ribs, darker every third block.
            boolean rib = Math.floorMod(across, 3) == 0;
            return (red ? rib ? Blocks.RED_TERRACOTTA : Blocks.RED_CONCRETE
                    : rib ? Blocks.BLUE_TERRACOTTA : Blocks.BLUE_CONCRETE).defaultBlockState();
        }
        if (c == HALF_ACROSS) {
            // The long sides: white, with "-II" markings along the middle.
            int m = Math.floorMod(along, 6);
            boolean mark = up == 1 && m == 0 || up <= 1 && (m == 2 || m == 3);
            return (up < HEIGHT - 1 && mark ? Blocks.BLACK_CONCRETE : Blocks.WHITE_CONCRETE).defaultBlockState();
        }
        if (up == HEIGHT - 1) {
            // The top: glass tiles two blocks wide with dark seams, lit from below.
            boolean seam = Math.floorMod(along, 3) == 0 || Math.floorMod(across, 3) == 0;
            return (seam ? Blocks.CYAN_TERRACOTTA : Blocks.LIGHT_GRAY_STAINED_GLASS).defaultBlockState();
        }
        if (up == HEIGHT - 2) {
            boolean lamp = Math.floorMod(along, 3) == 1 && Math.floorMod(across, 3) == 1;
            return (lamp ? Blocks.SEA_LANTERN : Blocks.PRISMARINE_BRICKS).defaultBlockState();
        }
        return Blocks.SMOOTH_STONE.defaultBlockState();
    }

    /** Red sign posts at the red end and lamp posts at the blue end, two each, either side of the steps. */
    private static void posts(Level level, BlockPos center, Direction facing, Direction side) {
        for (int end : new int[]{1, -1}) {
            for (int s : new int[]{-5, 5}) {
                BlockPos base = center.relative(facing, end * HALF_ALONG).relative(side, s).above(HEIGHT);
                for (int up = 0; up < 3; up++) {
                    level.setBlock(base.above(up), Blocks.CHAIN.defaultBlockState(), Block.UPDATE_ALL);
                }
                BlockState[] top = end > 0
                        ? new BlockState[]{Blocks.RED_CONCRETE.defaultBlockState(),
                        Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.RED_CONCRETE.defaultBlockState()}
                        : new BlockState[]{Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
                        Blocks.SEA_LANTERN.defaultBlockState(), Blocks.BLUE_STAINED_GLASS.defaultBlockState()};
                for (int i = 0; i < top.length; i++) {
                    level.setBlock(base.above(3 + i), top[i], Block.UPDATE_ALL);
                }
            }
        }
    }
}
