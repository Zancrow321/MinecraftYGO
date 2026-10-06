package io.github.zancrow321.minecraftygo.village;

import com.google.common.collect.ImmutableSet;
import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoServerConfig;
import io.github.zancrow321.minecraftygo.item.BoosterPackItem;
import io.github.zancrow321.minecraftygo.item.YgoItems;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The Card Shop: a counter block that turns a villager into a card trader, who sells booster packs, starter decks,
 * binders, deck boxes and duel disks for emeralds.
 */
public final class YgoVillagers {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MinecraftYgo.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MinecraftYgo.MOD_ID);
    private static final DeferredRegister<PoiType> POIS = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE,
            MinecraftYgo.MOD_ID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, MinecraftYgo.MOD_ID);

    public static final DeferredBlock<Block> CARD_SHOP = BLOCKS.registerSimpleBlock("card_shop",
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> CARD_SHOP_ITEM = ITEMS.registerSimpleBlockItem(CARD_SHOP);

    public static final ResourceKey<PoiType> CARD_SHOP_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "card_shop"));
    private static final DeferredHolder<PoiType, PoiType> CARD_SHOP_POI_TYPE = POIS.register("card_shop",
            () -> new PoiType(ImmutableSet.copyOf(CARD_SHOP.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> CARD_TRADER =
            PROFESSIONS.register("card_trader", () -> new VillagerProfession("card_trader",
                    poi -> poi.is(CARD_SHOP_POI), poi -> poi.is(CARD_SHOP_POI), ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN));

    private YgoVillagers() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        POIS.register(modBus);
        PROFESSIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(YgoVillagers::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(YgoVillagers::onWandererTrades);
    }

    private static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != CARD_TRADER.get()) {
            return;
        }
        // Packs, binders, deck boxes, starter decks and a duel disk as the trader levels up; the packs, decks and
        // tins of particular sets are the trader's stock, which CardShop keeps up to date. Each offer is made with
        // the [shop] settings of the moment, and CardShop updates it when they change.
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
        for (CardShop.Trade trade : CardShop.Trade.values()) {
            trades.get(trade.level).add((trader, random) -> trade.offer());
        }
    }

    private static void onWandererTrades(WandererTradesEvent event) {
        event.getGenericTrades().add((trader, random) -> !YgoServerConfig.SHOP_WANDERING_TRADER.get() ? null
                : CardShop.sell(YgoServerConfig.PRICES.wanderingPack.get(), BoosterPackItem.of(null), 6, 1));
        event.getRareTrades().add((trader, random) -> !YgoServerConfig.SHOP_WANDERING_TRADER.get() ? null
                : CardShop.sell(YgoServerConfig.PRICES.wanderingDuelDisk.get(),
                        new ItemStack(YgoItems.DUEL_DISK.get()), 1, 1));
    }
}
