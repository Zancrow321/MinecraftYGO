package io.github.zancrow321.jadm.display;

import io.github.zancrow321.jadm.Jadm;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Collector displays: decoration blocks that show cards and sealed products with their real pictures, a card frame,
 * a 3x3 showcase, a toploader stand and a pack display.
 */
public final class Displays {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Jadm.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Jadm.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Jadm.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Jadm.MOD_ID);

    public static final DeferredBlock<DisplayBlock> CARD_FRAME = block(DisplayKind.CARD_FRAME, SoundType.WOOD);
    public static final DeferredBlock<DisplayBlock> SHOWCASE = block(DisplayKind.SHOWCASE, SoundType.WOOD);
    public static final DeferredBlock<DisplayBlock> TOPLOADER_STAND = block(DisplayKind.TOPLOADER_STAND,
            SoundType.GLASS);
    public static final DeferredBlock<DisplayBlock> PACK_DISPLAY = block(DisplayKind.PACK_DISPLAY, SoundType.WOOD);

    public static final DeferredItem<BlockItem> CARD_FRAME_ITEM = ITEMS.registerSimpleBlockItem(CARD_FRAME);
    public static final DeferredItem<BlockItem> SHOWCASE_ITEM = ITEMS.registerSimpleBlockItem(SHOWCASE);
    public static final DeferredItem<BlockItem> TOPLOADER_STAND_ITEM = ITEMS.registerSimpleBlockItem(TOPLOADER_STAND);
    public static final DeferredItem<BlockItem> PACK_DISPLAY_ITEM = ITEMS.registerSimpleBlockItem(PACK_DISPLAY);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayBlockEntity>> ENTITY =
            BLOCK_ENTITIES.register("collector_display", () -> BlockEntityType.Builder.of(DisplayBlockEntity::new,
                    CARD_FRAME.get(), SHOWCASE.get(), TOPLOADER_STAND.get(), PACK_DISPLAY.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<DisplayMenu>> MENU = MENUS.register(
            "collector_display", () -> IMenuTypeExtension.create(DisplayMenu::client));

    private Displays() {
    }

    private static DeferredBlock<DisplayBlock> block(DisplayKind kind, SoundType sound) {
        return BLOCKS.registerBlock(kind.id(), properties -> new DisplayBlock(kind, properties),
                BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1f, 3f).sound(sound)
                        .noOcclusion().lightLevel(state -> state.getValue(DisplayBlock.LIT) ? 12 : 0));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }
}
