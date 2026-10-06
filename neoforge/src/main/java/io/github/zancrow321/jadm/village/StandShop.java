package io.github.zancrow321.jadm.village;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.points.PointShopMenu;
import io.github.zancrow321.jadm.points.PointShops;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The buyer's view of a Shop Stand: the villager trade window, with an offer for each ware the owner set a price for.
 * A sale takes the ware out of the stock and puts the price (less {@code taxPercent}) in the till. An offer is sold
 * out when the stock runs short or the till is full. With {@code currency = "points"} it is a points shop instead, and
 * the price (less the tax) goes to the owner's points.
 */
final class StandShop implements Merchant {
    private final ShopStandBlockEntity stand;
    private final ServerPlayer player;
    private final MerchantOffers offers = new MerchantOffers();
    /** The column of each offer, which is where its ware and price are. */
    private final List<Integer> columns = new ArrayList<>();
    private Player tradingPlayer;

    private StandShop(ShopStandBlockEntity stand, ServerPlayer player) {
        this.stand = stand;
        this.player = player;
        Container slots = stand.slots();
        for (int column = 0; column < ShopStandMenu.OFFERS; column++) {
            ItemStack ware = slots.getItem(column);
            ItemStack price = slots.getItem(ShopStandMenu.PRICES + column);
            if (ware.isEmpty() || price.isEmpty() || !sellable(ware, price)) {
                continue;
            }
            int sales = Math.min(count(slots, ware) / ware.getCount(), room(slots, kept(price)) / Math.max(1,
                    kept(price).getCount()));
            offers.add(new MerchantOffer(new ItemCost(price.getItemHolder(), price.getCount(),
                    DataComponentPredicate.allOf(price.getComponents())), Optional.empty(), ware.copy(), 0, sales,
                    0, 0f, 0));
            columns.add(column);
        }
    }

    /** Opens the stand's shop for a buyer. */
    static void open(ShopStandBlockEntity stand, ServerPlayer player) {
        if (Points.active()) {
            PointsSeller seller = new PointsSeller(stand);
            if (seller.entries(player).isEmpty()) {
                player.displayClientMessage(Component.translatable("message.jadm.shop_stand.empty"), true);
                stand.release(player);
            } else {
                PointShopMenu.open(player, stand.title(), seller);
            }
            return;
        }
        StandShop shop = new StandShop(stand, player);
        if (shop.offers.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.jadm.shop_stand.empty"), true);
            stand.release(player);
            return;
        }
        shop.setTradingPlayer(player);
        OptionalInt id = player.openMenu(new SimpleMenuProvider((containerId, inventory, p) ->
                new ShopMerchantMenu(containerId, inventory, shop, shop::near), stand.title()));
        if (id.isPresent()) {
            player.sendMerchantOffers(id.getAsInt(), shop.offers, 0, 0, false, false);
        } else {
            shop.setTradingPlayer(null);
        }
    }

    /** Whether a ware and its price are allowed by {@code currencyOnly} and {@code onlyModItems}. */
    static boolean sellable(ItemStack ware, ItemStack price) {
        JadmServerConfig.PlayerShops config = JadmServerConfig.PLAYER_SHOPS;
        return (!config.currencyOnly.get() || price.is(CardShop.currency())) && allowed(ware);
    }

    /** Whether {@code onlyModItems} lets a stand sell a ware. */
    static boolean allowed(ItemStack ware) {
        return !JadmServerConfig.PLAYER_SHOPS.onlyModItems.get()
                || BuiltInRegistries.ITEM.getKey(ware.getItem()).getNamespace().equals(Jadm.MOD_ID);
    }

    /** The part of a price in points the owner keeps after {@code taxPercent}. */
    private static long kept(long price) {
        return price - price * JadmServerConfig.PLAYER_SHOPS.taxPercent.get() / 100;
    }

    /** The part of a price the owner keeps after {@code taxPercent}. */
    private static ItemStack kept(ItemStack price) {
        int tax = price.getCount() * JadmServerConfig.PLAYER_SHOPS.taxPercent.get() / 100;
        return price.copyWithCount(price.getCount() - tax);
    }

    /** @return how many of {@code ware} the stock holds */
    private static int count(Container slots, ItemStack ware) {
        int count = 0;
        for (int i = ShopStandMenu.STOCK; i < ShopStandMenu.TILL; i++) {
            if (ItemStack.isSameItemSameComponents(slots.getItem(i), ware)) {
                count += slots.getItem(i).getCount();
            }
        }
        return count;
    }

    /** @return how many of {@code stack} still fit in the till */
    private static int room(Container slots, ItemStack stack) {
        if (stack.isEmpty()) {
            return Integer.MAX_VALUE / 2;
        }
        int room = 0;
        for (int i = ShopStandMenu.TILL; i < ShopStandMenu.SIZE; i++) {
            ItemStack slot = slots.getItem(i);
            if (slot.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
                room += Math.max(0, slot.getMaxStackSize() - slot.getCount());
            }
        }
        return room;
    }

    /** Takes a sale's worth of a ware out of the stock. */
    private static void takeStock(Container slots, ItemStack ware) {
        int left = ware.getCount();
        for (int i = ShopStandMenu.STOCK; i < ShopStandMenu.TILL && left > 0; i++) {
            ItemStack stock = slots.getItem(i);
            if (ItemStack.isSameItemSameComponents(stock, ware)) {
                int take = Math.min(left, stock.getCount());
                stock.shrink(take);
                left -= take;
            }
        }
        slots.setChanged();
    }

    private boolean near(Player player) {
        return !stand.isRemoved() && Container.stillValidBlockEntity(stand, player);
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        int index = offers.indexOf(offer);
        if (index < 0) {
            return;
        }
        Container slots = stand.slots();
        int column = columns.get(index);
        ItemStack ware = slots.getItem(column);
        // Take the ware out of the stock...
        takeStock(slots, offer.getResult());
        // ...and put the payment in the till.
        ItemStack pay = kept(slots.getItem(ShopStandMenu.PRICES + column).isEmpty() ? offer.getCostA()
                : slots.getItem(ShopStandMenu.PRICES + column));
        for (int i = ShopStandMenu.TILL; i < ShopStandMenu.SIZE && !pay.isEmpty(); i++) {
            ItemStack till = slots.getItem(i);
            if (till.isEmpty()) {
                slots.setItem(i, pay.split(pay.getMaxStackSize()));
            } else if (ItemStack.isSameItemSameComponents(till, pay)) {
                int put = Math.min(pay.getCount(), till.getMaxStackSize() - till.getCount());
                till.grow(put);
                pay.shrink(put);
            }
        }
        slots.setChanged();
        player.level().playSound(null, stand.getBlockPos(), getNotifyTradeSound(), SoundSource.BLOCKS, 0.6f, 1.2f);
        ServerPlayer owner = stand.owner() == null ? null : player.server.getPlayerList().getPlayer(stand.owner());
        if (owner != null && owner != player && JadmServerConfig.PLAYER_SHOPS.notifyOwner.get()) {
            owner.sendSystemMessage(Component.translatable("message.jadm.shop_stand.sold",
                    player.getDisplayName(), ware.getCount(), ware.getHoverName(),
                    offer.getCostA().getCount(), offer.getCostA().getHoverName()).withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public void setTradingPlayer(Player player) {
        if (player == null && tradingPlayer != null) {
            stand.release(tradingPlayer);
        }
        tradingPlayer = player;
    }

    @Override
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.NOTE_BLOCK_BELL.value();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    /** The stand's shop with points as the currency: each ware with a price in points. */
    private static final class PointsSeller implements PointShopMenu.Seller {
        private final ShopStandBlockEntity stand;
        /** The column of each line of the shop. */
        private final List<Integer> columns = new ArrayList<>();
        /** What the last clicks sold, for one message to the owner: ware columns and the points paid. */
        private final java.util.Map<Integer, Integer> sold = new java.util.TreeMap<>();
        private long earned;

        PointsSeller(ShopStandBlockEntity stand) {
            this.stand = stand;
        }

        @Override
        public List<PointShopMenu.Entry> entries(ServerPlayer player) {
            List<PointShopMenu.Entry> entries = new ArrayList<>();
            columns.clear();
            Container slots = stand.slots();
            for (int column = 0; column < ShopStandMenu.OFFERS; column++) {
                ItemStack ware = slots.getItem(column);
                int price = stand.points(column);
                if (ware.isEmpty() || price <= 0 || !allowed(ware)) {
                    continue;
                }
                entries.add(new PointShopMenu.Entry(ware.copy(), price, count(slots, ware) / ware.getCount(),
                        false));
                columns.add(column);
            }
            return entries;
        }

        @Override
        public boolean trade(ServerPlayer player, int index) {
            if (index < 0 || index >= columns.size()) {
                return false;
            }
            int column = columns.get(index);
            Container slots = stand.slots();
            ItemStack ware = slots.getItem(column).copy();
            int price = stand.points(column);
            if (ware.isEmpty() || price <= 0 || count(slots, ware) < ware.getCount()
                    || !PointShops.pay(player, price)) {
                return false;
            }
            takeStock(slots, ware);
            PointShops.give(player, ware);
            if (stand.owner() != null) {
                Points.get(player.server).add(player.server, stand.owner(), kept(price));
            }
            sold.merge(column, 1, Integer::sum);
            earned += kept(price);
            player.level().playSound(null, stand.getBlockPos(), SoundEvents.NOTE_BLOCK_BELL.value(),
                    SoundSource.BLOCKS, 0.6f, 1.2f);
            return true;
        }

        @Override
        public void traded(ServerPlayer player) {
            ServerPlayer owner = stand.owner() == null ? null
                    : player.server.getPlayerList().getPlayer(stand.owner());
            if (owner != null && owner != player && !sold.isEmpty()
                    && JadmServerConfig.PLAYER_SHOPS.notifyOwner.get()) {
                sold.forEach((column, times) -> {
                    ItemStack ware = stand.slots().getItem(column);
                    owner.sendSystemMessage(Component.translatable("message.jadm.shop_stand.sold_points",
                            player.getDisplayName(), times * ware.getCount(), ware.getHoverName())
                            .withStyle(ChatFormatting.GOLD));
                });
                owner.sendSystemMessage(Component.translatable("message.jadm.shop_stand.earned",
                        Points.format(earned)).withStyle(ChatFormatting.GOLD));
            }
            sold.clear();
            earned = 0;
        }

        @Override
        public boolean valid(Player player) {
            return !stand.isRemoved() && Container.stillValidBlockEntity(stand, player);
        }

        @Override
        public void closed(Player player) {
            stand.release(player);
        }
    }
}
