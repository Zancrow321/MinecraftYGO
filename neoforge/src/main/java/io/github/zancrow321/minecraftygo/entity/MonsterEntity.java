package io.github.zancrow321.minecraftygo.entity;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
        return YgoData.pool().model(code());
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
