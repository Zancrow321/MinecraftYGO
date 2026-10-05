package io.github.zancrow321.minecraftygo.entity;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YgoEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MinecraftYgo.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<MonsterEntity>> MONSTER = ENTITIES.register("monster",
            () -> EntityType.Builder.<MonsterEntity>of(MonsterEntity::new, MobCategory.MISC)
                    .sized(1.5f, 2.5f)
                    .clientTrackingRange(10)
                    .build("monster"));

    private YgoEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
