package io.github.zancrow321.minecraftygo.entity;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A card's monster model standing in the world. For now it only backs the debug gallery ({@code /ygo gallery});
 * the duel field draws monsters itself in M3.
 */
public final class MonsterEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> CODE =
            SynchedEntityData.defineId(MonsterEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Client-only drawing state for duel field stand-ins: opacity and an RGB tint (white = none). */
    public float alpha = 1;
    public int tint = 0xFFFFFF;
    /** Client-only: how far through an attack this monster is, 0 to 1, or negative when it isn't attacking. */
    public float attack = -1;
    /** Client-only: the tick a gallery monster was last clicked, to show off its attack. */
    private int attackClicked = Integer.MIN_VALUE;

    /** How long a gallery monster's attack plays, matching an attack on the field. */
    public static final int ATTACK_TICKS = 28;

    public MonsterEntity(EntityType<? extends MonsterEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public int code() {
        return entityData.get(CODE);
    }

    public void setCode(int code) {
        entityData.set(CODE, code);
    }

    /** @return this card's model, or {@code null} if it has none */
    public CardPool.Model model() {
        return YgoData.model(code());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CODE, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setCode(tag.getInt("Code"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Code", code());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /** Clicking a gallery monster plays its attack. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            attackClicked = tickCount;
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** @return the attack's progress this frame, 0 to 1, or negative when not attacking */
    public float attackProgress(float partialTick) {
        if (attack >= 0) {
            return attack;
        }
        float t = (tickCount - attackClicked + partialTick) / ATTACK_TICKS;
        return t >= 0 && t <= 1 ? t : -1;
    }

    /** Creative players can knock gallery monsters away with one hit. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof Player player && player.isCreative()) {
            discard();
            return true;
        }
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            CardPool.Model model = model();
            if (model == null || !model.animations().contains("idle")) {
                return PlayState.STOP;
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
