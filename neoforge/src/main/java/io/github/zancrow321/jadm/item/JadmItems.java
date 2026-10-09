package io.github.zancrow321.jadm.item;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.arena.DuelArena;
import io.github.zancrow321.jadm.arena.DuelDome;
import io.github.zancrow321.jadm.village.JadmVillagers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import io.github.zancrow321.jadm.entity.JadmEntities;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JadmItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Jadm.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Jadm.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Jadm.MOD_ID);

    public static final DeferredItem<DuelDiskItem> DUEL_DISK = ITEMS.registerItem("duel_disk", DuelDiskItem::new,
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<CardItem> CARD = ITEMS.registerItem("card", CardItem::new,
            new Item.Properties().stacksTo(64));
    public static final DeferredItem<BoosterPackItem> BOOSTER_PACK = ITEMS.registerItem("booster_pack",
            BoosterPackItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<SealedProductItem> STRUCTURE_DECK = ITEMS.registerItem("structure_deck",
            SealedProductItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<SealedProductItem> TIN = ITEMS.registerItem("tin",
            SealedProductItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    public static final DeferredItem<BinderItem> BINDER = ITEMS.registerItem("binder", BinderItem::new,
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<DeckBoxItem> DECK_BOX = ITEMS.registerItem("deck_box", DeckBoxItem::new,
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<GuideBookItem> GUIDE_BOOK = ITEMS.registerItem("guide_book",
            GuideBookItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<SetBookItem> SET_BOOK = ITEMS.registerItem("set_book", SetBookItem::new,
            new Item.Properties().stacksTo(1));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GuideBookRecipe>> GUIDE_BOOK_RECIPE =
            RECIPE_SERIALIZERS.register("guide_book", () -> new RecipeSerializer<>() {
                @Override
                public com.mojang.serialization.MapCodec<GuideBookRecipe> codec() {
                    return GuideBookRecipe.CODEC;
                }

                @Override
                public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,
                        GuideBookRecipe> streamCodec() {
                    return GuideBookRecipe.STREAM_CODEC;
                }
            });

    public static final DeferredItem<DeferredSpawnEggItem> DUELIST_SPAWN_EGG = ITEMS.registerItem("duelist_spawn_egg",
            props -> new DeferredSpawnEggItem(JadmEntities.DUELIST, 0x2B3A67, 0xE0C060, props));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.jadm"))
                    .icon(() -> DUEL_DISK.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(GUIDE_BOOK.get());
                        output.accept(DUEL_DISK.get());
                        output.accept(BINDER.get());
                        output.accept(SET_BOOK.get());
                        output.accept(DECK_BOX.get());
                        output.accept(starterYugi());
                        output.accept(starterKaiba());
                        output.accept(JadmVillagers.CARD_SHOP_ITEM.get());
                        output.accept(JadmVillagers.CARD_MACHINE_ITEM.get());
                        output.accept(io.github.zancrow321.jadm.village.PlayerShops.STAND_ITEM.get());
                        output.accept(DuelArena.KIT.get());
                        output.accept(DuelDome.KIT.get());
                        output.accept(io.github.zancrow321.jadm.ranking.RankingBoard.ITEM.get());
                        output.accept(DuelDome.CORE_ITEM.get());
                        output.accept(DuelDome.PLATFORM_ITEM.get());
                        output.accept(DUELIST_SPAWN_EGG.get());
                        output.accept(BOOSTER_PACK.get());
                        JadmData.allSets().sets().keySet().forEach(id -> output.accept(BoosterPackItem.of(id)));
                        JadmData.products().products().stream().filter(SealedProductItem::sealed)
                                .forEach(p -> output.accept(SealedProductItem.of(p)));
                    })
                    .build());

    private JadmItems() {
    }

    public static net.minecraft.world.item.ItemStack starterYugi() {
        return DeckBoxItem.of("starter_yugi", Component.translatable("item.jadm.deck_box.starter_yugi"));
    }

    public static net.minecraft.world.item.ItemStack starterKaiba() {
        return DeckBoxItem.of("starter_kaiba", Component.translatable("item.jadm.deck_box.starter_kaiba"));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        TABS.register(modBus);
    }

    static DeferredRegister.Items items() {
        return ITEMS;
    }
}
