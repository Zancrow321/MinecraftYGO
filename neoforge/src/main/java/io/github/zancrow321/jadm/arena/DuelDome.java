package io.github.zancrow321.jadm.arena;

import io.github.zancrow321.jadm.Jadm;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The blocks of arenas players build themselves (see {@link BuiltArena}): the Arena Core and the Duelist Podiums,
 * and the Duel Dome kit, a ready-made one to start from. (They kept the Duel Dome's ids, so domes already built go
 * on working.)
 */
public final class DuelDome {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Jadm.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Jadm.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Jadm.MOD_ID);

    public static final DeferredBlock<ArenaCoreBlock> CORE = BLOCKS.registerBlock("duel_dome_core",
            ArenaCoreBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3f, 6f)
                    .sound(SoundType.METAL).lightLevel(state -> 12).requiresCorrectToolForDrops());
    public static final DeferredBlock<PodiumBlock> PLATFORM = BLOCKS.registerBlock("duelist_platform",
            PodiumBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(3f, 6f)
                    .sound(SoundType.METAL).lightLevel(state -> 6).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArenaCoreBlockEntity>> CORE_ENTITY =
            BLOCK_ENTITIES.register("arena_core",
                    () -> BlockEntityType.Builder.of(ArenaCoreBlockEntity::new, CORE.get()).build(null));
    public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.registerSimpleBlockItem(CORE);
    public static final DeferredItem<BlockItem> PLATFORM_ITEM = ITEMS.registerSimpleBlockItem(PLATFORM);
    public static final DeferredItem<DuelDomeKitItem> KIT = ITEMS.registerItem("duel_dome_kit",
            DuelDomeKitItem::new, new Item.Properties().stacksTo(1));

    private DuelDome() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
