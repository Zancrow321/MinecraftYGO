package io.github.zancrow321.minecraftygo.points;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoServerConfig;
import io.github.zancrow321.minecraftygo.network.PointsPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Duel Points: with {@code [shop] currency = "points"}, every player has a balance instead of paying with an item.
 * Kept per player by UUID, so a stand owner is paid while away too. A player starts with {@code startBalance} and gets
 * {@code dailyBonus} once a day; the client is told the balance whenever it changes.
 */
public final class Points extends SavedData {
    private static final String NAME = "minecraftygo_points";
    private static final int CHECK_TICKS = 1200;
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MinecraftYgo.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<PointShopMenu>> SHOP_MENU = MENUS.register(
            "point_shop", () -> IMenuTypeExtension.create((id, inventory, data) -> new PointShopMenu(id, inventory)));

    private final Map<UUID, Long> balances = new HashMap<>();
    /** The day (days since 1970) each player last got the daily bonus. */
    private final Map<UUID, Long> bonusDays = new HashMap<>();

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }

    /** Whether the shops take points rather than an item. */
    public static boolean active() {
        return "points".equals(YgoServerConfig.SHOP_CURRENCY.get());
    }

    public static Points get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Points::new, Points::load, null), NAME);
    }

    /** An amount with the points' symbol, e.g. "1,250 DP". */
    public static String format(long amount) {
        return String.format("%,d %s", amount, YgoServerConfig.POINTS.symbol.get());
    }

    public long balance(UUID player) {
        return balances.getOrDefault(player, 0L);
    }

    /** Adds (or with a negative amount takes) points, down to 0 at most. */
    public void add(MinecraftServer server, UUID player, long amount) {
        set(server, player, balance(player) + amount);
    }

    /** @return whether the player had {@code amount} points, which are then taken */
    public boolean take(MinecraftServer server, UUID player, long amount) {
        if (balance(player) < amount) {
            return false;
        }
        set(server, player, balance(player) - amount);
        return true;
    }

    public void set(MinecraftServer server, UUID player, long amount) {
        balances.put(player, Math.max(0, amount));
        setDirty();
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            sync(online);
        }
    }

    /** On login: the start balance for a new player, the daily bonus, and the balance for the client. */
    public static void login(ServerPlayer player) {
        if (active()) {
            Points points = get(player.server);
            if (!points.balances.containsKey(player.getUUID())) {
                points.balances.put(player.getUUID(), (long) YgoServerConfig.POINTS.startBalance.get());
                points.setDirty();
            }
            points.bonus(player);
        }
        sync(player);
    }

    /** Hands out the daily bonus to players who play past midnight. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_TICKS != 0 || !active()) {
            return;
        }
        Points points = get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            points.bonus(player);
        }
    }

    private void bonus(ServerPlayer player) {
        int bonus = YgoServerConfig.POINTS.dailyBonus.get();
        long today = LocalDate.now().toEpochDay();
        if (bonus <= 0 || bonusDays.getOrDefault(player.getUUID(), -1L) == today) {
            return;
        }
        bonusDays.put(player.getUUID(), today);
        add(player.server, player.getUUID(), bonus);
        player.sendSystemMessage(Component.translatable("message.minecraftygo.points.daily", format(bonus),
                format(balance(player.getUUID()))).withStyle(ChatFormatting.GOLD));
    }

    /** Tells a player's client their balance, and whether points are the currency. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new PointsPayload(active(),
                get(player.server).balance(player.getUUID()), YgoServerConfig.POINTS.symbol.get()));
    }

    private static Points load(CompoundTag tag, HolderLookup.Provider registries) {
        Points points = new Points();
        CompoundTag balances = tag.getCompound("balances");
        for (String uuid : balances.getAllKeys()) {
            points.balances.put(UUID.fromString(uuid), balances.getLong(uuid));
        }
        CompoundTag days = tag.getCompound("bonusDays");
        for (String uuid : days.getAllKeys()) {
            points.bonusDays.put(UUID.fromString(uuid), days.getLong(uuid));
        }
        return points;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag balances = new CompoundTag();
        this.balances.forEach((uuid, amount) -> balances.putLong(uuid.toString(), amount));
        tag.put("balances", balances);
        CompoundTag days = new CompoundTag();
        bonusDays.forEach((uuid, day) -> days.putLong(uuid.toString(), day));
        tag.put("bonusDays", days);
        return tag;
    }
}
