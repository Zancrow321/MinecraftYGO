package io.github.zancrow321.jadm.arena;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.network.DuelFieldPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arenas players build themselves: any blocks, plus an Arena Core somewhere inside and a Duelist Podium for each
 * duelist (one per side, or two side by side for tag duels). The core measures the build: the field goes between
 * the two sides and turns with them, and grows or shrinks to the free floor between the podiums; the room over it
 * keeps monsters under the roof.
 */
public final class BuiltArena {
    /** How far from the core podiums are looked for, sideways and up or down. */
    static final int SEARCH = 24;
    /** How far from the core its outline and podiums may be drawn. */
    public static final int SEARCH_RENDER = SEARCH + 12;
    private static final int SEARCH_DOWN = 4;
    private static final int SEARCH_UP = 8;
    /** Half the mat's width and length at size 1, with its rim, in blocks. */
    static final double MAT_HALF_WIDTH = 10.2;
    static final double MAT_HALF_LENGTH = 7.15;
    /** How much floor a duelist keeps between their feet and the mat's edge. */
    private static final double MARGIN = 0.8;
    /** How far out the sides are looked at for walls, and up for a roof. */
    private static final int MAX_SIDE = 24;
    private static final int MAX_ROOF = 14;
    /** Podiums further apart than this within one side don't count as one side. */
    private static final double SIDE_SPREAD = 4.5;
    /** Less room than this over the mat, and the field doesn't fit. */
    static final double MIN_CEILING = 2.5;
    /** How long a measurement holds for the waiting duelists before it is taken again, in ticks. */
    private static final int FRESH_TICKS = 100;
    /** The sizes a core can be set to, in percent; 0 is automatic. */
    public static final int[] SIZES = {0, 50, 75, 100, 125, 150, 200};
    public static final int MAX_LIFT = 5;

    /** Every loaded core, by level (server only). */
    private static final Map<Level, Set<BlockPos>> CORES = new HashMap<>();

    private BuiltArena() {
    }

    /**
     * What the core found when it last measured the build.
     *
     * @param plus     the podiums on one side (end +1), from the core's corner
     * @param minus    the podiums on the other side (end -1)
     * @param center   the middle of the mat, on the floor
     * @param yaw      from the -1 side toward the +1 side (degrees, Minecraft convention)
     * @param size     how big the mat is drawn, 1 being the usual size
     * @param fits     the largest size the room allows
     * @param ceiling  blocks of room over the mat, 0 for open sky
     * @param lift     how far the podiums go up in a duel
     * @param problems translation keys of what keeps the arena from being used; empty when it can be
     * @param notes    translation keys of things worth knowing that don't stop it
     */
    public record Survey(List<BlockPos> plus, List<BlockPos> minus, Vec3 center, float yaw, double size, double fits,
                         double ceiling, int lift, List<String> problems, List<String> notes) {
        public static final Survey NONE = new Survey(List.of(), List.of(), Vec3.ZERO, 0, 0, 0, 0, 0,
                List.of("no_podiums"), List.of());

        public boolean ok() {
            return problems.isEmpty();
        }

        /** The podiums at {@code end} (+1 or -1). */
        public List<BlockPos> side(int end) {
            return end > 0 ? plus : minus;
        }

        /** Which end (+1 or -1) this podium is at, or 0 if it isn't one of the arena's. */
        public int endOf(BlockPos podium) {
            return plus.contains(podium) ? 1 : minus.contains(podium) ? -1 : 0;
        }
    }

    // ------------------------------------------------------------------ the loaded cores

    static void loaded(Level level, BlockPos core) {
        if (!level.isClientSide()) {
            CORES.computeIfAbsent(level, l -> new HashSet<>()).add(core.immutable());
        }
    }

    static void unloaded(Level level, BlockPos core) {
        Set<BlockPos> cores = CORES.get(level);
        if (cores != null) {
            cores.remove(core);
            if (cores.isEmpty()) {
                CORES.remove(level);
            }
        }
    }

    public static void reset() {
        CORES.clear();
    }

    private static ArenaCoreBlockEntity core(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ArenaCoreBlockEntity core ? core : null;
    }

    /** Something changed around {@code pos}: cores near it measure again the next time they are asked. */
    static void changed(Level level, BlockPos pos) {
        Set<BlockPos> cores = CORES.get(level);
        if (cores == null) {
            return;
        }
        for (BlockPos at : cores) {
            if (at.distManhattan(pos) <= 3 * SEARCH) {
                ArenaCoreBlockEntity core = core(level, at);
                if (core != null) {
                    core.stale();
                }
            }
        }
    }

    /** The core's measurement, taken again if it is older than a few seconds or something changed. */
    static Survey fresh(ArenaCoreBlockEntity core) {
        Level level = core.getLevel();
        if (level != null && (core.survey() == null || level.getGameTime() - core.surveyedAt() > FRESH_TICKS)) {
            core.measure();
        }
        return core.survey() == null ? Survey.NONE : core.survey();
    }

    // ------------------------------------------------------------------ measuring

    /** Measures the build around the core at {@code pos} with its settings. */
    static Survey survey(Level level, BlockPos pos, int sizeSetting, int liftSetting) {
        List<BlockPos> podiums = new ArrayList<>();
        for (int dy = -SEARCH_DOWN; dy <= SEARCH_UP; dy++) {
            for (int dx = -SEARCH; dx <= SEARCH; dx++) {
                for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    if (level.getBlockState(at).is(DuelDome.PLATFORM.get())) {
                        podiums.add(at);
                    }
                }
            }
        }
        List<String> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (podiums.isEmpty()) {
            return Survey.NONE;
        }
        if (podiums.size() == 1) {
            return failed(podiums, List.of(), "one_side");
        }
        // The two sides: the two podiums furthest apart, and everything else with whichever of them is nearer.
        BlockPos a = null;
        BlockPos b = null;
        double far = -1;
        for (BlockPos p : podiums) {
            for (BlockPos q : podiums) {
                double d = horizontal(p, q);
                if (d > far) {
                    far = d;
                    a = p;
                    b = q;
                }
            }
        }
        List<BlockPos> sideA = new ArrayList<>();
        List<BlockPos> sideB = new ArrayList<>();
        for (BlockPos p : podiums) {
            (horizontal(p, a) <= horizontal(p, b) ? sideA : sideB).add(p);
        }
        Vec3 ca = centroid(sideA);
        Vec3 cb = centroid(sideB);
        // The +1 side is the one further east (or south): the same arena always comes out the same way round.
        boolean swap = ca.x < cb.x - 0.01 || Math.abs(ca.x - cb.x) <= 0.01 && ca.z < cb.z;
        List<BlockPos> plus = sort(swap ? sideB : sideA);
        List<BlockPos> minus = sort(swap ? sideA : sideB);
        Vec3 cp = swap ? cb : ca;
        Vec3 cm = swap ? ca : cb;
        if (plus.size() > 2 || minus.size() > 2) {
            return failed(plus, minus, "too_many");
        }
        if (spread(plus) > SIDE_SPREAD || spread(minus) > SIDE_SPREAD) {
            return failed(plus, minus, "scattered");
        }
        double dx = cp.x - cm.x;
        double dz = cp.z - cm.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float snapped = Math.round(yaw / 90f) * 90f;
        if (Math.abs(yaw - snapped) < 8) {
            yaw = snapped;
        }
        Vec3 axis = Vec3.directionFromRotation(0, yaw);
        Vec3 across = new Vec3(-axis.z, 0, axis.x);
        Vec3 mid = cp.add(cm).scale(0.5);
        int top = Math.max(maxY(plus), maxY(minus)) + 1;
        int bottom = Math.min(minY(plus), minY(minus)) - 6;

        // The floor: the height most of the ground between the sides is at.
        Map<Integer, Integer> floors = new HashMap<>();
        int reach = (int) Math.max(1, distance / 2 - 1);
        for (int t = -reach; t <= reach; t++) {
            for (int s = -2; s <= 2; s += 2) {
                Vec3 p = mid.add(axis.scale(t)).add(across.scale(s));
                int floor = floorAt(level, pos, Mth.floor(p.x), Mth.floor(p.z), top, bottom);
                if (floor != Integer.MIN_VALUE) {
                    floors.merge(floor, 1, Integer::sum);
                }
            }
        }
        int floor = floors.entrySet().stream().max(Map.Entry.<Integer, Integer>comparingByValue()
                .thenComparing(Map.Entry.comparingByKey())).map(Map.Entry::getKey).orElse(top - 1);
        if (floors.isEmpty()) {
            notes.add("no_floor");
        }
        Vec3 center = new Vec3(mid.x, floor, mid.z);

        // As big as the gap between the sides allows, then as the walls allow.
        double fits = (distance / 2 - MARGIN) / MAT_HALF_LENGTH;
        double max = JadmServerConfig.ARENA_MAX_FIELD_SIZE.get();
        double size = Math.min(fits, max);
        if (sizeSetting > 0) {
            size = Math.min(size, sizeSetting / 100.0);
        }
        for (int i = 0; i < 4; i++) {
            double half = halfWidth(level, pos, center, axis, across, MAT_HALF_LENGTH * size);
            double wide = (half - 0.1) / MAT_HALF_WIDTH;
            fits = Math.min(fits, wide);
            if (wide >= size) {
                break;
            }
            size = wide;
        }
        if (sizeSetting > 0 && sizeSetting / 100.0 > fits + 0.01) {
            notes.add("size_capped");
        }
        double ceiling = ceiling(level, pos, center, axis, across, size);
        if (distance < 2 * (MARGIN + 1)) {
            problems.add("too_close");
        } else if (size < JadmServerConfig.ARENA_MIN_FIELD_SIZE.get()) {
            problems.add("too_narrow");
        }
        if (ceiling > 0 && ceiling < MIN_CEILING) {
            problems.add("too_low");
        }
        int lift = liftSetting;
        for (BlockPos p : podiums) {
            lift = Math.min(lift, headroom(level, p.above(), liftSetting + 2) - 2);
        }
        lift = Math.max(lift, 0);
        if (lift < liftSetting) {
            notes.add("lift_capped");
        }
        return new Survey(List.copyOf(plus), List.copyOf(minus), center, yaw, Math.max(size, 0), Math.max(fits, 0),
                ceiling, lift, List.copyOf(problems), List.copyOf(notes));
    }

    private static Survey failed(List<BlockPos> plus, List<BlockPos> minus, String problem) {
        return new Survey(List.copyOf(plus), List.copyOf(minus), Vec3.ZERO, 0, 0, 0, 0, 0, List.of(problem),
                List.of());
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static List<BlockPos> sort(List<BlockPos> podiums) {
        podiums.sort(Comparator.comparingInt((BlockPos p) -> p.getX()).thenComparingInt(p -> p.getZ())
                .thenComparingInt(p -> p.getY()));
        return podiums;
    }

    /** The middle of the tops of these podiums, where their duelists' feet are. */
    private static Vec3 centroid(List<BlockPos> podiums) {
        Vec3 sum = Vec3.ZERO;
        for (BlockPos p : podiums) {
            sum = sum.add(Vec3.atBottomCenterOf(p.above()));
        }
        return sum.scale(1.0 / podiums.size());
    }

    private static double spread(List<BlockPos> podiums) {
        double out = 0;
        for (BlockPos p : podiums) {
            for (BlockPos q : podiums) {
                out = Math.max(out, horizontal(p, q));
            }
        }
        return out;
    }

    private static int maxY(List<BlockPos> podiums) {
        return podiums.stream().mapToInt(BlockPos::getY).max().orElse(0);
    }

    private static int minY(List<BlockPos> podiums) {
        return podiums.stream().mapToInt(BlockPos::getY).min().orElse(0);
    }

    /** Whether something solid fills this block. The core itself is no obstacle; it sits under the mat. */
    private static boolean solid(Level level, BlockPos core, BlockPos pos) {
        if (pos.equals(core)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.is(DuelArena.SOLID.get()) && !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The height of the ground in this column (the top of the highest solid block from {@code top} down), or
     * {@link Integer#MIN_VALUE} if the column starts inside a wall or has no ground.
     */
    private static int floorAt(Level level, BlockPos core, int x, int z, int top, int bottom) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, top, z);
        if (solid(level, core, at)) {
            return Integer.MIN_VALUE;
        }
        for (int y = top - 1; y >= bottom; y--) {
            at.setY(y);
            if (solid(level, core, at) || at.equals(core)) {
                return y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** How far the floor stays free of walls to both sides of the field's middle line, the narrower side counted. */
    private static double halfWidth(Level level, BlockPos core, Vec3 center, Vec3 axis, Vec3 across, double length) {
        double out = MAX_SIDE;
        int reach = (int) Math.ceil(length);
        for (int t = -reach; t <= reach; t++) {
            for (int dir = -1; dir <= 1; dir += 2) {
                for (int d = 0; d <= MAX_SIDE; d++) {
                    if (d - 0.5 >= out) {
                        break;
                    }
                    Vec3 p = center.add(axis.scale(t)).add(across.scale(dir * d));
                    BlockPos at = BlockPos.containing(p.x, center.y + 0.5, p.z);
                    if (solid(level, core, at) || solid(level, core, at.above())) {
                        out = Math.min(out, Math.max(d - 0.5, 0));
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** The least room over the mat, in blocks, or 0 if the sky is open everywhere. */
    private static double ceiling(Level level, BlockPos core, Vec3 center, Vec3 axis, Vec3 across, double size) {
        int out = 0;
        for (int t = -2; t <= 2; t++) {
            for (int s = -2; s <= 2; s++) {
                Vec3 p = center.add(axis.scale(t * MAT_HALF_LENGTH * size / 2.5))
                        .add(across.scale(s * MAT_HALF_WIDTH * size / 2.5));
                BlockPos base = BlockPos.containing(p.x, center.y + 0.5, p.z);
                int room = headroom(level, base, MAX_ROOF);
                if (room < MAX_ROOF && (out == 0 || room < out)) {
                    out = room;
                }
            }
        }
        return out;
    }

    /** Free blocks over {@code from} (itself included), up to {@code max}. */
    private static int headroom(Level level, BlockPos from, int max) {
        for (int up = 0; up < max; up++) {
            BlockPos at = from.above(up);
            BlockState state = level.getBlockState(at);
            if (!state.is(DuelArena.SOLID.get()) && !state.getCollisionShape(level, at).isEmpty()) {
                return up;
            }
        }
        return max;
    }

    /** A player changed the settings of an Arena Core (or asked to see its field again) in its screen. */
    public static void settings(ServerPlayer player, io.github.zancrow321.jadm.network.ArenaCoreSettingsPayload p) {
        Level level = player.level();
        ArenaCoreBlockEntity core = core(level, p.pos());
        if (core == null || player.distanceToSqr(Vec3.atCenterOf(p.pos())) > 64 || !player.mayBuild()
                || !level.mayInteract(player, p.pos())) {
            return;
        }
        if (!p.preview() && !core.raised()) {
            int size = 0;
            for (int option : SIZES) {
                if (option == p.size()) {
                    size = option;
                }
            }
            core.settings(size, p.lift(), p.outline());
        }
        core.preview();
    }

    // ------------------------------------------------------------------ duels

    /** The core whose arena has a podium at {@code podium}, and its end, or {@code null}. */
    static DuelArena.Podium podiumAt(Level level, BlockPos podium) {
        Set<BlockPos> cores = CORES.get(level);
        if (cores == null) {
            return null;
        }
        for (BlockPos at : List.copyOf(cores)) {
            ArenaCoreBlockEntity core = core(level, at);
            if (core != null && at.distManhattan(podium) <= 3 * SEARCH) {
                int end = fresh(core).endOf(podium);
                if (end != 0) {
                    return new DuelArena.Podium(at, end);
                }
            }
        }
        return null;
    }

    /** The podium block this player stands on (or just jumped from), or {@code null}. */
    static BlockPos podiumUnder(ServerPlayer player) {
        BlockPos below = player.blockPosition().below();
        for (BlockPos at : List.of(below, below.below())) {
            if (player.level().getBlockState(at).is(DuelDome.PLATFORM.get())) {
                return at;
            }
        }
        // A podium that is up: the player stands on the floor over it.
        if (player.level().getBlockState(below).is(DuelArena.SOLID.get())) {
            for (int down = 1; down <= MAX_LIFT + 1; down++) {
                if (player.level().getBlockState(below.below(down)).is(DuelDome.PLATFORM.get())) {
                    return below.below(down);
                }
            }
        }
        return null;
    }

    /** The core of the built arena around {@code pos}: on its podiums or over its field. */
    static BlockPos arenaAround(Level level, BlockPos pos) {
        Set<BlockPos> cores = CORES.get(level);
        if (cores == null) {
            return null;
        }
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;
        for (BlockPos at : cores) {
            ArenaCoreBlockEntity core = core(level, at);
            if (core == null) {
                continue;
            }
            Survey survey = fresh(core);
            if (survey.plus().isEmpty() || survey.minus().isEmpty()) {
                continue;
            }
            Vec3 c = survey.center();
            double reach = Math.max(horizontal(survey.plus().get(0), survey.minus().get(0)) / 2 + 3,
                    MAT_HALF_WIDTH * survey.size());
            double d = Math.sqrt(Math.pow(pos.getX() + 0.5 - c.x, 2) + Math.pow(pos.getZ() + 0.5 - c.z, 2));
            if (d <= reach && Math.abs(pos.getY() - c.y) <= 8 && d < nearest) {
                nearest = d;
                best = at;
            }
        }
        return best;
    }

    /** Whether a core stands at {@code pos}, its arena measures up and no duel is using it. */
    static boolean available(Level level, BlockPos pos) {
        ArenaCoreBlockEntity core = core(level, pos);
        return core != null && !core.raised() && fresh(core).ok() && !DuelArena.inUse(level, pos);
    }

    /** Where the duelist on the first podium at {@code end} stands, looking at the field. */
    static DuelArena.Spot spot(Level level, BlockPos pos, int end) {
        ArenaCoreBlockEntity core = core(level, pos);
        Survey survey = core == null ? Survey.NONE : fresh(core);
        List<BlockPos> side = survey.side(end);
        if (side.isEmpty()) {
            return new DuelArena.Spot(Vec3.atBottomCenterOf(pos.above()), 0);
        }
        return new DuelArena.Spot(Vec3.atBottomCenterOf(side.get(0).above()), survey.yaw() + (end > 0 ? 180 : 0));
    }

    /**
     * The field for a duel on a built arena, if every person in it stands on a podium of the same arena, team 0 on
     * one side and team 1 on the other (a team without people needs no podium; the NPC is put on a free one). The
     * podiums start going up.
     */
    static DuelFieldPayload claim(List<ServerPlayer> team0, List<ServerPlayer> team1, Entity npc) {
        List<ServerPlayer> all = new ArrayList<>(team0);
        all.addAll(team1);
        if (all.isEmpty() || !(all.get(0).level() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos first = podiumUnder(all.get(0));
        DuelArena.Podium podium = first == null ? null : podiumAt(level, first);
        ArenaCoreBlockEntity core = podium == null ? null : core(level, podium.arena());
        if (core == null || core.raised() || DuelArena.inUse(level, podium.arena())) {
            return null;
        }
        core.measure();
        Survey survey = core.survey();
        if (survey == null || !survey.ok()) {
            return null;
        }
        int end0 = team0.isEmpty() ? -end(team1, level, survey) : end(team0, level, survey);
        if (end0 == 0 || !team1.isEmpty() && end(team1, level, survey) != -end0) {
            return null;
        }
        Set<BlockPos> taken = new HashSet<>();
        for (ServerPlayer player : all) {
            BlockPos under = podiumUnder(player);
            taken.add(under);
            Vec3 base = Vec3.atBottomCenterOf(under.above());
            float yaw = survey.yaw() + (survey.endOf(under) > 0 ? 180 : 0);
            player.connection.teleport(base.x, base.y, base.z, yaw, player.getXRot());
            DuelArena.ride(level, podium.arena(), player, base, survey.lift());
        }
        if (npc != null && npc.level() == level) {
            int npcEnd = team0.isEmpty() ? end0 : -end0;
            BlockPos free = survey.side(npcEnd).stream().filter(p -> !taken.contains(p)).findFirst()
                    .orElse(survey.side(npcEnd).get(0));
            Vec3 spot = Vec3.atBottomCenterOf(free.above());
            float yaw = survey.yaw() + (npcEnd > 0 ? 180 : 0);
            npc.moveTo(spot.x, spot.y, spot.z, yaw, 0);
            npc.setYHeadRot(yaw);
            DuelArena.ride(level, podium.arena(), npc, spot, survey.lift());
            taken.add(free);
        }
        core.raise(List.copyOf(taken));
        // Team 0 looks from its side toward the other one.
        float yaw = survey.yaw() + (end0 > 0 ? 180 : 0);
        Vec3 c = survey.center();
        return new DuelFieldPayload(true, c.x, c.y, c.z, yaw).fitted(survey.size(), survey.ceiling(),
                core.outline());
    }

    /** The end (+1 or -1) all these players stand on a podium at, or 0. */
    private static int end(List<ServerPlayer> players, Level level, Survey survey) {
        int end = 0;
        for (ServerPlayer player : players) {
            BlockPos under = player.level() == level ? podiumUnder(player) : null;
            int at = under == null ? 0 : survey.endOf(under);
            if (at == 0 || end != 0 && at != end) {
                return 0;
            }
            end = at;
        }
        return end;
    }
}
