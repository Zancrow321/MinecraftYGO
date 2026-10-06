package io.github.zancrow321.minecraftygo.points;

import io.github.zancrow321.minecraftygo.network.PointShopPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A shop that takes Duel Points: a list of things to buy for points, and things the shop buys for points. The
 * server's menu holds the {@link Seller}; the client's gets the list in a {@link PointShopPayload} after each trade.
 */
public final class PointShopMenu extends AbstractContainerMenu {
    /** Shift-click trades as often as it can, up to this many times. */
    private static final int MAX_TIMES = 64;

    /**
     * A line of the shop.
     *
     * @param item  what you get for the points, or with {@code sell} what you give for them
     * @param price the points it costs, or with {@code sell} pays
     * @param left  how many more times it can be bought (or how many you have to sell); -1 for no limit
     */
    public record Entry(ItemStack item, long price, int left, boolean sell) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Entry::item, ByteBufCodecs.VAR_LONG, Entry::price,
                ByteBufCodecs.VAR_INT, Entry::left, ByteBufCodecs.BOOL, Entry::sell, Entry::new);
    }

    /** What a shop sells and buys, for the player using it. */
    public interface Seller {
        /** The lines of the shop as they are now; the index of each is what {@link #trade} gets. */
        List<Entry> entries(ServerPlayer player);

        /** Buys or sells line {@code index} once; @return whether it did */
        boolean trade(ServerPlayer player, int index);

        /** Whether the player can still use the shop (is close enough to it, it is still there...). */
        boolean valid(Player player);

        /** A click's trades are done (to sum them up for someone, say). */
        default void traded(ServerPlayer player) {
        }

        /** The player closed the shop. */
        default void closed(Player player) {
        }
    }

    /** The shop, on the server; {@code null} on the client. */
    private final Seller seller;
    /** The lines, on the client. */
    private List<Entry> entries = List.of();

    /** The client's menu. */
    public PointShopMenu(int containerId, Inventory inventory) {
        this(containerId, (Seller) null);
    }

    private PointShopMenu(int containerId, Seller seller) {
        super(Points.SHOP_MENU.get(), containerId);
        this.seller = seller;
    }

    /** Opens a points shop for a player. */
    public static void open(ServerPlayer player, Component title, Seller seller) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PointShopMenu(id, seller), title));
        if (player.containerMenu instanceof PointShopMenu menu && menu.seller == seller) {
            menu.refresh(player);
        } else {
            seller.closed(player);
        }
    }

    /** Line {@code index} was clicked, once or (shift) as often as possible. */
    public static void handle(ServerPlayer player, int containerId, int index, boolean all) {
        if (!(player.containerMenu instanceof PointShopMenu menu) || menu.containerId != containerId
                || menu.seller == null || !menu.stillValid(player)) {
            return;
        }
        for (int i = 0; i < (all ? MAX_TIMES : 1) && menu.seller.trade(player, index); i++) {
            // trade until it can't
        }
        menu.seller.traded(player);
        menu.refresh(player);
    }

    private void refresh(ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new PointShopPayload(containerId, new ArrayList<>(seller.entries(player))));
    }

    public List<Entry> entries() {
        return entries;
    }

    /** On the client, the lines the server sent. */
    public void entries(List<Entry> entries) {
        this.entries = entries;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return seller == null || seller.valid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (seller != null) {
            seller.closed(player);
        }
    }
}
