package io.github.zancrow321.minecraftygo.village;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.YgoServerConfig;
import io.github.zancrow321.minecraftygo.engine.data.PackProfile;
import io.github.zancrow321.minecraftygo.engine.data.Products;
import io.github.zancrow321.minecraftygo.item.BoosterPackItem;
import io.github.zancrow321.minecraftygo.item.SealedProductItem;
import io.github.zancrow321.minecraftygo.item.YgoComponents;
import io.github.zancrow321.minecraftygo.item.YgoItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A card trader's stock: the newest few products always, plus a few older ones each trader picks for itself and
 * changes every few days. Restocked when the products out or the rotation change. Prices, currency and stock come
 * from the {@code [shop]} settings, and traders follow changes to them right away.
 */
public final class CardShop {
    private static final String STOCK = "minecraftygo_stock";
    private static final int CHECK_TICKS = 100;

    private CardShop() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        long day = server.overworld().getDayTime() / 24000;
        for (ServerLevel level : server.getAllLevels()) {
            for (Villager villager : level.getEntities(EntityType.VILLAGER,
                    v -> v.getVillagerData().getProfession() == YgoVillagers.CARD_TRADER.get())) {
                // Changing the offers under someone's trade screen would swap what they are buying.
                if (villager.getTradingPlayer() == null) {
                    restock(villager, day);
                    reprice(villager.getOffers());
                }
            }
        }
    }

    /** Puts the current stock in a trader's offers, replacing the previous stock. */
    static void restock(Villager villager, long day) {
        List<Products.Product> products = YgoData.shopProducts().stream().filter(p -> price(p) > 0).toList();
        long period = day / YgoServerConfig.SHOP_ROTATION_DAYS.get();
        List<Products.Product> stock = pick(products, period, villager.getUUID().getLeastSignificantBits());
        String key = stock.stream().map(Products.Product::id).collect(Collectors.joining(","));
        CompoundTag data = villager.getPersistentData();
        if (key.equals(data.getString(STOCK))) {
            return;
        }
        data.putString(STOCK, key);
        MerchantOffers offers = villager.getOffers();
        offers.removeIf(offer -> isStock(offer.getResult()));
        for (Products.Product product : stock) {
            offers.add(offer(product));
        }
    }

    /**
     * Brings a trader's offers in line with the [shop] settings: price, currency and stock, keeping how much was
     * sold since the last restock; offers of things that are no longer sold go.
     */
    static void reprice(MerchantOffers offers) {
        for (int i = offers.size() - 1; i >= 0; i--) {
            MerchantOffer offer = offers.get(i);
            MerchantOffer wanted;
            if (isStock(offer.getResult())) {
                Products.Product product = YgoData.products().get(
                        offer.getResult().getOrDefault(YgoComponents.PACK_SET.get(), ""));
                if (product == null || product.kind() != Products.Kind.TIN
                        && product.kind() != Products.Kind.DECK && YgoData.set(product.id()) == null) {
                    continue;
                }
                wanted = offer(product);
            } else {
                Trade trade = Trade.of(offer);
                if (trade == null) {
                    continue;
                }
                wanted = trade.offer();
            }
            if (wanted == null) {
                offers.remove(i);
            } else if (!same(offer, wanted)) {
                offers.set(i, new MerchantOffer(wanted.getItemCostA(), wanted.getItemCostB(), wanted.getResult(),
                        offer.getUses(), wanted.getMaxUses(), offer.getXp(), wanted.getPriceMultiplier(),
                        offer.getDemand()));
            }
        }
    }

    private static boolean same(MerchantOffer a, MerchantOffer b) {
        return ItemStack.matches(a.getBaseCostA(), b.getBaseCostA()) && ItemStack.matches(a.getResult(), b.getResult())
                && a.getMaxUses() == b.getMaxUses() && a.getPriceMultiplier() == b.getPriceMultiplier();
    }

    /** The newest products and a few random older ones, newest first. */
    static List<Products.Product> pick(List<Products.Product> products, long period, long seed) {
        int newest = Math.min(products.size(), YgoServerConfig.SHOP_NEWEST.get());
        List<Products.Product> stock = new ArrayList<>(products.subList(products.size() - newest, products.size()));
        Collections.reverse(stock);
        List<Products.Product> older = new ArrayList<>(products.subList(0, products.size() - newest));
        Collections.shuffle(older, new Random(seed ^ period * 0x9E3779B97F4A7C15L));
        stock.addAll(older.subList(0, Math.min(older.size(), YgoServerConfig.SHOP_ROTATING.get())));
        return stock;
    }

    private static boolean isStock(ItemStack stack) {
        return stack.is(YgoItems.BOOSTER_PACK.get()) && stack.has(YgoComponents.PACK_SET.get())
                || stack.is(YgoItems.STRUCTURE_DECK.get()) || stack.is(YgoItems.TIN.get());
    }

    /** @return the product's price before {@code priceMultiplier}, 0 if it isn't sold */
    static int price(Products.Product product) {
        YgoServerConfig.Prices prices = YgoServerConfig.PRICES;
        if (product.kind() == Products.Kind.TIN) {
            return prices.tin.get();
        }
        if (product.kind() == Products.Kind.DECK) {
            return prices.structureDeck.get();
        }
        PackProfile profile = YgoData.set(product.id()).profile();
        return (profile.size() >= 9 ? prices.corePack : profile.slots().size() > 1 ? prices.premiumPack
                : prices.smallPack).get();
    }

    /** @return the stock offer of a product, or {@code null} if it isn't sold */
    static MerchantOffer offer(Products.Product product) {
        boolean tin = product.kind() == Products.Kind.TIN;
        if (tin || product.kind() == Products.Kind.DECK) {
            return sell(price(product), SealedProductItem.of(product),
                    (tin ? YgoServerConfig.STOCK.tins : YgoServerConfig.STOCK.structureDecks).get(), 10);
        }
        return sell(price(product), BoosterPackItem.of(product.id()), YgoServerConfig.STOCK.packs.get(), 3);
    }

    /** The item card traders take, from {@code [shop] currency}; emeralds if that names no item. */
    static Item currency() {
        ResourceLocation id = ResourceLocation.tryParse(YgoServerConfig.SHOP_CURRENCY.get());
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? Items.EMERALD : item;
    }

    /** @return an offer of {@code item} for {@code price} (before the multiplier), or {@code null} for price 0 */
    static MerchantOffer sell(int price, ItemStack item, int maxUses, int xp) {
        if (price <= 0) {
            return null;
        }
        Item currency = currency();
        int cost = Mth.clamp((int) Math.round(price * YgoServerConfig.SHOP_PRICE_MULTIPLIER.get()), 1,
                currency.getDefaultMaxStackSize());
        return new MerchantOffer(new ItemCost(currency, cost), item, maxUses, xp, demandFactor());
    }

    private static float demandFactor() {
        return YgoServerConfig.SHOP_DYNAMIC_PRICES.get() ? 0.05f : 0f;
    }

    /**
     * The trades a card trader learns as it levels up, besides its stock. Trade levels pick two of these a level, as
     * for every villager; an offer is told apart by what it sells and the experience it gives.
     */
    enum Trade {
        RANDOM_PACK_NOVICE(1, 2, () -> BoosterPackItem.of(null), p -> p.randomPackNovice, s -> s.packs),
        BINDER(1, 2, () -> new ItemStack(YgoItems.BINDER.get()), p -> p.binder, s -> s.binders),
        DECK_BOX(1, 1, () -> new ItemStack(YgoItems.DECK_BOX.get()), p -> p.deckBox, s -> s.deckBoxes),
        PAPER(1, 2, () -> new ItemStack(Items.PAPER), p -> p.paper, s -> null),
        STARTER_YUGI(2, 10, YgoItems::starterYugi, p -> p.starterDeck, s -> s.starterDecks),
        STARTER_KAIBA(2, 10, YgoItems::starterKaiba, p -> p.starterDeck, s -> s.starterDecks),
        RANDOM_PACK_JOURNEYMAN(3, 10, () -> BoosterPackItem.of(null), p -> p.randomPackJourneyman, s -> s.packs),
        DUEL_DISK(4, 15, () -> new ItemStack(YgoItems.DUEL_DISK.get()), p -> p.duelDisk, s -> s.duelDisks),
        RANDOM_PACK_MASTER(5, 20, () -> BoosterPackItem.of(null), p -> p.randomPackMaster, s -> s.masterPacks);

        /** How many paper trades a trader has before it restocks. */
        private static final int PAPER_USES = 16;

        final int level;
        private final int xp;
        /** What it sells, made once: offers get a copy. */
        private final Supplier<ItemStack> item;
        private final Function<YgoServerConfig.Prices, ModConfigSpec.IntValue> price;
        private final Function<YgoServerConfig.Stock, ModConfigSpec.IntValue> stock;

        Trade(int level, int xp, Supplier<ItemStack> item,
              Function<YgoServerConfig.Prices, ModConfigSpec.IntValue> price,
              Function<YgoServerConfig.Stock, ModConfigSpec.IntValue> stock) {
            this.level = level;
            this.xp = xp;
            this.item = com.google.common.base.Suppliers.memoize(item::get);
            this.price = price;
            this.stock = stock;
        }

        /** @return the offer as the config has it now, or {@code null} if it isn't sold */
        MerchantOffer offer() {
            int price = this.price.apply(YgoServerConfig.PRICES).get();
            if (this == PAPER) {
                // The trader buys paper: the paper is the cost, and stays the same with the multiplier.
                return price <= 0 ? null : new MerchantOffer(new ItemCost(Items.PAPER, price),
                        new ItemStack(currency()), PAPER_USES, xp, demandFactor());
            }
            return sell(price, item.get().copy(), stock.apply(YgoServerConfig.STOCK).get(), xp);
        }

        /** @return the trade an offer was made from, or {@code null} for one that isn't a level trade */
        static Trade of(MerchantOffer offer) {
            for (Trade trade : values()) {
                if (offer.getXp() == trade.xp && (trade == PAPER ? offer.getBaseCostA().is(Items.PAPER)
                        : ItemStack.isSameItemSameComponents(offer.getResult(), trade.item.get()))) {
                    return trade;
                }
            }
            return null;
        }
    }
}
