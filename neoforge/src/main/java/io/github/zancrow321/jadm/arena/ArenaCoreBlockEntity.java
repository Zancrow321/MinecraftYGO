package io.github.zancrow321.jadm.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Arena Core of a player-built arena: keeps the arena's settings (how big the field is, how far the podiums go
 * up, and how the mat looks), what it found when it last measured the build, and whether its podiums are up. While
 * they are all the way up, an invisible block stands under each duelist so nobody up there is taken for flying.
 */
public final class ArenaCoreBlockEntity extends BlockEntity {
    /** How long the field's outline shows after the core measured the build on request, in ticks. */
    private static final int PREVIEW_TICKS = 200;

    private int sizeSetting;
    private int liftSetting = 2;
    private boolean outline;
    private BuiltArena.Survey survey;
    private long surveyedAt;
    private boolean stale = true;
    private boolean raised;
    private long changedAt = Long.MIN_VALUE / 2;
    /** The invisible blocks standing under raised duelists. */
    private final List<BlockPos> floors = new ArrayList<>();
    /** The podiums someone duels on, which go up (the others stay down). */
    private final List<BlockPos> riding = new ArrayList<>();
    /** Who waits for a duel here: bit 0 for the +1 side, bit 1 for the -1 side. Not saved. */
    private int waiting;
    private long startsAt;
    private long previewUntil;

    public ArenaCoreBlockEntity(BlockPos pos, BlockState state) {
        super(DuelDome.CORE_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ settings

    /** The field's size in percent, or 0 to fit the room. */
    public int sizeSetting() {
        return sizeSetting;
    }

    /** How many blocks the podiums go up in a duel. */
    public int liftSetting() {
        return liftSetting;
    }

    /** Whether the mat shows only its zone frames, so the arena's own floor shows through. */
    public boolean outline() {
        return outline;
    }

    void settings(int size, int lift, boolean outline) {
        this.sizeSetting = size;
        this.liftSetting = Mth.clamp(lift, 0, BuiltArena.MAX_LIFT);
        this.outline = outline;
        measure();
        changed();
    }

    // ------------------------------------------------------------------ measuring

    public BuiltArena.Survey survey() {
        return survey;
    }

    long surveyedAt() {
        return stale ? Long.MIN_VALUE / 2 : surveyedAt;
    }

    /** Something around the arena changed: measure again when next asked. */
    void stale() {
        stale = true;
    }

    /** Measures the build again now, and tells clients if anything came out different. */
    void measure() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BuiltArena.Survey next = BuiltArena.survey(level, worldPosition, sizeSetting, liftSetting);
        surveyedAt = level.getGameTime();
        stale = false;
        if (!next.equals(survey)) {
            survey = next;
            changed();
        }
    }

    /** Measures, and shows the field's outline over the floor for a while. */
    void preview() {
        measure();
        if (level != null) {
            previewUntil = level.getGameTime() + PREVIEW_TICKS;
            changed();
        }
    }

    /** Until when (game time) the field's outline shows. */
    public long previewUntil() {
        return previewUntil;
    }

    // ------------------------------------------------------------------ duels

    public boolean raised() {
        return raised;
    }

    /** How far the podiums are up right now, in blocks. */
    public double lift(float time) {
        int lift = survey == null ? 0 : survey.lift();
        double t = Mth.clamp((time - changedAt) / DuelArena.LIFT_TICKS, 0, 1);
        return lift * (raised ? t : 1 - t);
    }

    public int waiting() {
        return waiting;
    }

    public long startsAt() {
        return startsAt;
    }

    void lobby(int waiting, long startsAt) {
        if (this.waiting != waiting || this.startsAt != startsAt) {
            this.waiting = waiting;
            this.startsAt = startsAt;
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The podiums that go up in this duel. */
    public List<BlockPos> riding() {
        return riding;
    }

    void raise(List<BlockPos> podiums) {
        if (level == null || raised) {
            return;
        }
        raised = true;
        riding.clear();
        riding.addAll(podiums);
        previewUntil = 0;
        changedAt = level.getGameTime();
        if (survey != null && survey.lift() > 0) {
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.5f, 0.6f);
        }
        changed();
    }

    void lower() {
        if (level == null || !raised) {
            return;
        }
        raised = false;
        changedAt = level.getGameTime();
        removeFloors();
        if (survey != null && survey.lift() > 0) {
            level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.5f, 0.6f);
        }
        changed();
    }

    /**
     * Server: once the podiums are all the way up, puts the floors over them (not sooner, or the riders would pass
     * through them on the way). Lowers podiums no duel is using, which happens after a restart.
     */
    static void serverTick(Level level, BlockPos pos, BlockState state, ArenaCoreBlockEntity core) {
        if (core.raised && core.floors.isEmpty() && core.survey != null && core.survey.lift() > 0
                && level.getGameTime() - core.changedAt >= DuelArena.LIFT_TICKS) {
            for (BlockPos podium : core.riding) {
                BlockPos floor = podium.above(core.survey.lift());
                if (level.getBlockState(floor).canBeReplaced()) {
                    level.setBlock(floor, DuelArena.SOLID.get().defaultBlockState(), Block.UPDATE_ALL);
                    core.floors.add(floor);
                }
            }
            core.setChanged();
        }
        if (core.raised && level.getGameTime() % 20 == 0 && !DuelArena.inUse(level, pos)) {
            core.lower();
        }
    }

    private void removeFloors() {
        if (level != null) {
            floors.forEach(p -> DuelArena.clearSolid(level, p));
        }
        floors.clear();
    }

    /** The core is gone: nothing of it may stay behind. */
    void removed() {
        removeFloors();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            BuiltArena.loaded(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            BuiltArena.unloaded(level, worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) {
            BuiltArena.unloaded(level, worldPosition);
        }
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ saving and syncing

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Size", sizeSetting);
        tag.putInt("Lift", liftSetting);
        tag.putBoolean("Outline", outline);
        tag.putBoolean("Raised", raised);
        tag.putLongArray("Floors", floors.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Riding", riding.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sizeSetting = tag.getInt("Size");
        liftSetting = tag.contains("Lift") ? tag.getInt("Lift") : 2;
        outline = tag.getBoolean("Outline");
        raised = tag.getBoolean("Raised");
        floors.clear();
        Arrays.stream(tag.getLongArray("Floors")).mapToObj(BlockPos::of).forEach(floors::add);
        riding.clear();
        Arrays.stream(tag.getLongArray("Riding")).mapToObj(BlockPos::of).forEach(riding::add);
        if (tag.contains("ChangedAt")) {
            changedAt = tag.getLong("ChangedAt");
        }
        waiting = tag.getInt("Waiting");
        startsAt = tag.getLong("StartsAt");
        previewUntil = tag.getLong("PreviewUntil");
        if (tag.contains("Survey")) {
            survey = readSurvey(tag.getCompound("Survey"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Size", sizeSetting);
        tag.putInt("Lift", liftSetting);
        tag.putBoolean("Outline", outline);
        tag.putBoolean("Raised", raised);
        tag.putLong("ChangedAt", changedAt);
        tag.putLongArray("Riding", riding.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("Waiting", waiting);
        tag.putLong("StartsAt", startsAt);
        tag.putLong("PreviewUntil", previewUntil);
        if (survey != null) {
            tag.put("Survey", writeSurvey(survey));
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static CompoundTag writeSurvey(BuiltArena.Survey s) {
        CompoundTag tag = new CompoundTag();
        tag.putLongArray("Plus", s.plus().stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Minus", s.minus().stream().mapToLong(BlockPos::asLong).toArray());
        tag.putDouble("X", s.center().x);
        tag.putDouble("Y", s.center().y);
        tag.putDouble("Z", s.center().z);
        tag.putFloat("Yaw", s.yaw());
        tag.putDouble("FieldSize", s.size());
        tag.putDouble("Fits", s.fits());
        tag.putDouble("Ceiling", s.ceiling());
        tag.putInt("Lift", s.lift());
        tag.put("Problems", strings(s.problems()));
        tag.put("Notes", strings(s.notes()));
        return tag;
    }

    private static BuiltArena.Survey readSurvey(CompoundTag tag) {
        return new BuiltArena.Survey(positions(tag.getLongArray("Plus")), positions(tag.getLongArray("Minus")),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")), tag.getFloat("Yaw"),
                tag.getDouble("FieldSize"), tag.getDouble("Fits"), tag.getDouble("Ceiling"), tag.getInt("Lift"),
                strings(tag.getList("Problems", Tag.TAG_STRING)), strings(tag.getList("Notes", Tag.TAG_STRING)));
    }

    private static List<BlockPos> positions(long[] longs) {
        return Arrays.stream(longs).mapToObj(BlockPos::of).toList();
    }

    private static ListTag strings(List<String> strings) {
        ListTag list = new ListTag();
        strings.forEach(s -> list.add(StringTag.valueOf(s)));
        return list;
    }

    private static List<String> strings(ListTag list) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return List.copyOf(out);
    }
}
