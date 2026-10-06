package io.github.zancrow321.minecraftygo.village;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.YgoServerConfig;
import io.github.zancrow321.minecraftygo.engine.data.Products;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The shop of a Card Vending Machine, made for the one player using it: the trade window of a villager, with the
 * {@code [shop]} prices and currency and what {@code [shop.machine]} says it sells. Prices don't change with demand,
 * and with {@code limitPerPlayer} each player can buy the {@code [shop.stock]} amounts a day.
 */
public final class MachineShop implements Merchant {
    /** The supplies a machine sells besides products, from the card trader's level trades. */
    private static final List<CardShop.Trade> SUPPLIES = List.of(CardShop.Trade.RANDOM_PACK_NOVICE,
            CardShop.Trade.BINDER, CardShop.Trade.DECK_BOX, CardShop.Trade.STARTER_YUGI, CardShop.Trade.STARTER_KAIBA,
            CardShop.Trade.DUEL_DISK);

    private final ServerPlayer player;
    /** The machine, or {@code null} when opened with {@code /ygo shop}. */
    private final BlockPos pos;
    private final long day;
    private final MerchantOffers offers = new MerchantOffers();
    /** What each offer is, to count it per player: a product id or a supply's name. */
    private final List<String> keys = new ArrayList<>();
    private Player tradingPlayer;

    private MachineShop(ServerPlayer player, BlockPos pos) {
        this.player = player;
        this.pos = pos;
        this.day = player.server.overworld().getDayTime() / 24000;
        YgoServerConfig.Machine config = YgoServerConfig.MACHINE;
        Map<String, Integer> bought = Bought.get(player.server).bought(player.getUUID(), day);
        for (Products.Product product : products(config.products.get())) {
            add(product.id(), CardShop.offer(product), bought);
        }
        if (config.supplies.get()) {
            for (CardShop.Trade trade : SUPPLIES) {
                add(trade.name(), trade.offer(), bought);
            }
        }
    }

    /** Opens the shop for a player; {@code pos} is the machine, or {@code null} for {@code /ygo shop}. */
    public static void open(ServerPlayer player, BlockPos pos) {
        if (!YgoServerConfig.MACHINE.enabled.get()) {
            player.displayClientMessage(Component.translatable("message.minecraftygo.card_machine.closed"), true);
            return;
        }
        MachineShop shop = new MachineShop(player, pos);
        if (shop.offers.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.minecraftygo.card_machine.empty"), true);
            return;
        }
        String name = YgoServerConfig.MACHINE.name.get();
        Component title = name.isBlank() ? Component.translatable("container.minecraftygo.card_machine")
                : Component.literal(name);
        shop.setTradingPlayer(player);
        OptionalInt id = player.openMenu(new SimpleMenuProvider((containerId, inventory, p) ->
                new ShopMerchantMenu(containerId, inventory, shop, shop::near), title));
        if (id.isPresent()) {
            player.sendMerchantOffers(id.getAsInt(), shop.offers, 0, 0, false, false);
        } else {
            shop.setTradingPlayer(null);
        }
    }

    /** Whether a player is still at the machine, which is still there. */
    private boolean near(Player player) {
        return pos == null || player.level().getBlockState(pos).getBlock() instanceof CardMachine
                && player.distanceToSqr(pos.getCenter()) <= 64;
    }

    /** The products a machine sells, newest first. */
    private List<Products.Product> products(String mode) {
        List<Products.Product> products = YgoData.shopProducts().stream().filter(p -> CardShop.price(p) > 0)
                .toList();
        return switch (mode) {
            case "all" -> {
                List<Products.Product> all = new ArrayList<>(products);
                Collections.reverse(all);
                yield all;
            }
            case "none" -> List.of();
            // Every machine shows the same pick, so the shop is the same wherever you buy.
            default -> CardShop.pick(products, day / YgoServerConfig.SHOP_ROTATION_DAYS.get(), 0);
        };
    }

    private void add(String key, MerchantOffer offer, Map<String, Integer> bought) {
        if (offer == null) {
            return;
        }
        boolean limited = YgoServerConfig.MACHINE.limitPerPlayer.get();
        offers.add(new MerchantOffer(offer.getItemCostA(), offer.getItemCostB(), offer.getResult(),
                limited ? bought.getOrDefault(key, 0) : 0, limited ? offer.getMaxUses() : Integer.MAX_VALUE, 0, 0f,
                0));
        keys.add(key);
    }

    @Override
    public void setTradingPlayer(Player player) {
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
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        int index = offers.indexOf(offer);
        if (index >= 0 && YgoServerConfig.MACHINE.limitPerPlayer.get()) {
            Bought.get(player.server).add(player.getUUID(), day, keys.get(index));
        }
        player.level().playSound(null, pos != null ? pos : player.blockPosition(), getNotifyTradeSound(),
                SoundSource.BLOCKS, 0.6f, 1.4f);
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
        return SoundEvents.NOTE_BLOCK_CHIME.value();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    /** How much each player bought from machines today, for {@code limitPerPlayer}. */
    static final class Bought extends SavedData {
        private static final String NAME = "minecraftygo_card_machine";

        private long day = -1;
        private final Map<UUID, Map<String, Integer>> bought = new HashMap<>();

        static Bought get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Bought::new, Bought::load, null), NAME);
        }

        /** @return what a player bought on {@code day}, by offer key */
        Map<String, Integer> bought(UUID player, long day) {
            newDay(day);
            return bought.getOrDefault(player, Map.of());
        }

        void add(UUID player, long day, String key) {
            newDay(day);
            bought.computeIfAbsent(player, p -> new HashMap<>()).merge(key, 1, Integer::sum);
            setDirty();
        }

        private void newDay(long day) {
            if (day != this.day) {
                this.day = day;
                bought.clear();
                setDirty();
            }
        }

        private static Bought load(CompoundTag tag, HolderLookup.Provider registries) {
            Bought data = new Bought();
            data.day = tag.getLong("day");
            CompoundTag players = tag.getCompound("players");
            for (String uuid : players.getAllKeys()) {
                CompoundTag items = players.getCompound(uuid);
                Map<String, Integer> map = new HashMap<>();
                for (String key : items.getAllKeys()) {
                    map.put(key, items.getInt(key));
                }
                data.bought.put(UUID.fromString(uuid), map);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLong("day", day);
            CompoundTag players = new CompoundTag();
            bought.forEach((uuid, items) -> {
                CompoundTag itemTag = new CompoundTag();
                items.forEach(itemTag::putInt);
                players.put(uuid.toString(), itemTag);
            });
            tag.put("players", players);
            return tag;
        }
    }
}
