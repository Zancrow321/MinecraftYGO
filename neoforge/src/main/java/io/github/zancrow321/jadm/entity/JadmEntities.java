package io.github.zancrow321.jadm.entity;

import io.github.zancrow321.jadm.Jadm;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JadmEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Jadm.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<MonsterEntity>> MONSTER = ENTITIES.register("monster",
            () -> EntityType.Builder.<MonsterEntity>of(MonsterEntity::new, MobCategory.MISC)
                    .sized(1.5f, 2.5f)
                    .clientTrackingRange(10)
                    .build("monster"));
    public static final DeferredHolder<EntityType<?>, EntityType<DuelistNpc>> DUELIST = ENTITIES.register("duelist",
            () -> EntityType.Builder.<DuelistNpc>of(DuelistNpc::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(10)
                    .build("duelist"));

    private JadmEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) ->
                event.put(DUELIST.get(), DuelistNpc.attributes().build()));
        modBus.addListener((RegisterSpawnPlacementsEvent event) -> event.register(DUELIST.get(),
                SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, DuelistNpc::canSpawn,
                RegisterSpawnPlacementsEvent.Operation.REPLACE));
    }
}
