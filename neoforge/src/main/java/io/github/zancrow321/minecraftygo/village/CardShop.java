package io.github.zancrow321.minecraftygo.village;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.YgoServerConfig;
import io.github.zancrow321.minecraftygo.engine.data.PackProfile;
import io.github.zancrow321.minecraftygo.engine.data.Products;
import io.github.zancrow321.minecraftygo.item.BoosterPackItem;
import io.github.zancrow321.minecraftygo.item.SealedProductItem;
import io.github.zancrow321.minecraftygo.item.YgoComponents;
import io.github.zancrow321.minecraftygo.item.YgoItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A card trader's stock: the newest few products always, plus a few older ones each trader picks for itself and
 * changes every few days. Restocked when the products out or the rotation change.
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
                restock(villager, day);
            }
        }
    }

    /** Puts the current stock in a trader's offers, replacing the previous stock. */
    static void restock(Villager villager, long day) {
        List<Products.Product> products = YgoData.shopProducts();
        if (products.isEmpty()) {
            return;
        }
        long period = day / YgoServerConfig.SHOP_ROTATION_DAYS.get();
        String key = period + "/" + products.get(products.size() - 1).id() + "/" + products.size();
        CompoundTag data = villager.getPersistentData();
        if (key.equals(data.getString(STOCK))) {
            return;
        }
        data.putString(STOCK, key);
        MerchantOffers offers = villager.getOffers();
        offers.removeIf(offer -> isStock(offer.getResult()));
        for (Products.Product product : pick(products, period, villager.getUUID().getLeastSignificantBits())) {
            offers.add(offer(product));
        }
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

    private static MerchantOffer offer(Products.Product product) {
        if (product.kind() == Products.Kind.TIN || product.kind() == Products.Kind.DECK) {
            boolean tin = product.kind() == Products.Kind.TIN;
            return new MerchantOffer(new ItemCost(Items.EMERALD, tin ? 14 : 10), SealedProductItem.of(product),
                    tin ? 3 : 4, 10, 0.05f);
        }
        PackProfile profile = YgoData.set(product.id()).profile();
        int price = profile.size() >= 9 ? 4 : profile.slots().size() > 1 ? 6 : 3;
        return new MerchantOffer(new ItemCost(Items.EMERALD, price), BoosterPackItem.of(product.id()), 16, 3, 0.05f);
    }
}
