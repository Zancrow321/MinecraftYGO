package io.github.zancrow321.minecraftygo.item;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.arena.DuelDome;
import io.github.zancrow321.minecraftygo.village.YgoVillagers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import io.github.zancrow321.minecraftygo.entity.YgoEntities;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YgoItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MinecraftYgo.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MinecraftYgo.MOD_ID);

    public static final DeferredItem<DuelDiskItem> DUEL_DISK = ITEMS.registerItem("duel_disk", DuelDiskItem::new,
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<CardItem> CARD = ITEMS.registerItem("card", CardItem::new,
            new Item.Properties().stacksTo(64));
    public static final DeferredItem<BoosterPackItem> BOOSTER_PACK = ITEMS.registerItem("booster_pack",
            BoosterPackItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BinderItem> BINDER = ITEMS.registerItem("binder", BinderItem::new,
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<DeckBoxItem> DECK_BOX = ITEMS.registerItem("deck_box", DeckBoxItem::new,
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<DeferredSpawnEggItem> DUELIST_SPAWN_EGG = ITEMS.registerItem("duelist_spawn_egg",
            props -> new DeferredSpawnEggItem(YgoEntities.DUELIST, 0x2B3A67, 0xE0C060, props));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.minecraftygo"))
                    .icon(() -> DUEL_DISK.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(DUEL_DISK.get());
                        output.accept(BINDER.get());
                        output.accept(DECK_BOX.get());
                        output.accept(starterYugi());
                        output.accept(starterKaiba());
                        output.accept(YgoVillagers.CARD_SHOP_ITEM.get());
                        output.accept(DuelDome.KIT.get());
                        output.accept(DuelDome.CORE_ITEM.get());
                        output.accept(DuelDome.PLATFORM_ITEM.get());
                        output.accept(DUELIST_SPAWN_EGG.get());
                        output.accept(BOOSTER_PACK.get());
                        YgoData.allSets().sets().keySet().forEach(id -> output.accept(BoosterPackItem.of(id)));
                    })
                    .build());

    private YgoItems() {
    }

    public static net.minecraft.world.item.ItemStack starterYugi() {
        return DeckBoxItem.of("starter_yugi", Component.translatable("item.minecraftygo.deck_box.starter_yugi"));
    }

    public static net.minecraft.world.item.ItemStack starterKaiba() {
        return DeckBoxItem.of("starter_kaiba", Component.translatable("item.minecraftygo.deck_box.starter_kaiba"));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    static DeferredRegister.Items items() {
        return ITEMS;
    }
}
