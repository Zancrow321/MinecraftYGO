package io.github.zancrow321.jadm.village;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.points.PointShopMenu;
import io.github.zancrow321.jadm.points.PointShops;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;

/**
 * A card trader's shop with {@code currency = "points"}: its offers as a points shop, each price times {@code
 * pricePoints}, and the paper it buys for points. Trades count for the trader as usual (experience, levels, demand).
 */
public final class TraderShop implements PointShopMenu.Seller {
    private final Villager trader;

    private TraderShop(Villager trader) {
        this.trader = trader;
    }

    /** Right-clicking a card trader with points as the currency opens its points shop; @return whether it did */
    public static boolean interact(Player player, Villager trader, InteractionHand hand) {
        if (player.level().isClientSide() || !Points.active() || hand != InteractionHand.MAIN_HAND
                || trader.getVillagerData().getProfession() != JadmVillagers.CARD_TRADER.get()
                || !trader.isAlive() || trader.isBaby() || trader.isSleeping() || trader.isTrading()
                || player.getMainHandItem().is(Items.NAME_TAG) || player.getMainHandItem().is(Items.LEAD)
                || player.getMainHandItem().is(Items.VILLAGER_SPAWN_EGG)) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer && !trader.getOffers().isEmpty()) {
            player.awardStat(net.minecraft.stats.Stats.TALKED_TO_VILLAGER);
            TraderShop shop = new TraderShop(trader);
            trader.setTradingPlayer(serverPlayer);
            PointShopMenu.open(serverPlayer, trader.getDisplayName(), shop);
        }
        return true;
    }

    @Override
    public List<PointShopMenu.Entry> entries(ServerPlayer player) {
        List<PointShopMenu.Entry> entries = new ArrayList<>();
        for (MerchantOffer offer : trader.getOffers()) {
            int left = Math.max(0, offer.getMaxUses() - offer.getUses());
            if (paper(offer)) {
                ItemStack paper = offer.getCostA();
                entries.add(new PointShopMenu.Entry(paper, points(offer.getResult().getCount()),
                        Math.min(left, PointShops.count(player, s -> s.is(Items.PAPER)) / paper.getCount()), true));
            } else {
                entries.add(new PointShopMenu.Entry(offer.getResult().copy(), points(offer.getCostA().getCount()),
                        left, false));
            }
        }
        return entries;
    }

    @Override
    public boolean trade(ServerPlayer player, int index) {
        MerchantOffers offers = trader.getOffers();
        if (index < 0 || index >= offers.size()) {
            return false;
        }
        MerchantOffer offer = offers.get(index);
        if (offer.isOutOfStock()) {
            return false;
        }
        if (paper(offer)) {
            if (!PointShops.take(player, s -> s.is(Items.PAPER), offer.getCostA().getCount())) {
                return false;
            }
            PointShops.earn(player, points(offer.getResult().getCount()));
        } else {
            if (!PointShops.pay(player, points(offer.getCostA().getCount()))) {
                return false;
            }
            PointShops.give(player, offer.getResult());
        }
        trader.notifyTrade(offer);
        trader.playSound(trader.getNotifyTradeSound(), 1, 1);
        return true;
    }

    /** Whether an offer is the trader buying paper. */
    private static boolean paper(MerchantOffer offer) {
        return offer.getBaseCostA().is(Items.PAPER);
    }

    private static long points(int price) {
        return (long) price * JadmServerConfig.POINTS.pricePoints.get();
    }

    @Override
    public boolean valid(Player player) {
        return trader.isAlive() && trader.getTradingPlayer() == player && player.distanceToSqr(trader) <= 64;
    }

    @Override
    public void closed(Player player) {
        if (trader.getTradingPlayer() == player) {
            trader.setTradingPlayer(null);
        }
    }
}
