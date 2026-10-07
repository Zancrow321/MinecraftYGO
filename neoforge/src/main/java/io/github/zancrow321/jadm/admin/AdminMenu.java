package io.github.zancrow321.jadm.admin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.item.BoosterPackItem;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.SealedProductItem;
import io.github.zancrow321.jadm.network.AdminActionPayload;
import io.github.zancrow321.jadm.network.AdminPayload;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The admin menu ({@code /jadm admin}, operators only): hands out any product or card, in any rarity, and changes
 * Duel Points, for yourself, another player or everyone online. The client only asks; every click is checked and
 * carried out here.
 */
public final class AdminMenu {
    /** The permission level the menu needs, as for {@code /give}. */
    public static final int LEVEL = 2;
    /** The most items one click gives: nine stacks. */
    public static final int MAX_ITEMS = 576;
    /** The target that stands for everyone online. */
    public static final String EVERYONE = "*";

    private AdminMenu() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("admin").requires(source -> source.hasPermission(LEVEL)).executes(ctx -> {
            send(ctx.getSource().getPlayerOrException(), true);
            return 1;
        });
    }

    /** Opens the menu, or with {@code open} false refreshes it (the balances after a change). */
    public static void send(ServerPlayer player, boolean open) {
        Points points = Points.get(player.server);
        List<AdminPayload.Player> players = new ArrayList<>();
        for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
            players.add(new AdminPayload.Player(online.getScoreboardName(), points.balance(online.getUUID())));
        }
        PacketDistributor.sendToPlayer(player, new AdminPayload(open, players, Points.active(),
                JadmServerConfig.POINTS.symbol.get()));
    }

    /**
     * The item that stands for a product: its sealed deck or tin, or a booster pack of it; empty for products that
     * come as neither (promos without a set of their own).
     */
    public static ItemStack item(Products.Product product) {
        if (SealedProductItem.sealed(product)) {
            return SealedProductItem.of(product);
        }
        if (JadmData.allSets().get(product.id()) != null) {
            return BoosterPackItem.of(product.id());
        }
        return ItemStack.EMPTY;
    }

    public static void handle(ServerPlayer admin, AdminActionPayload payload) {
        if (!admin.hasPermissions(LEVEL)) {
            Jadm.LOGGER.warn("{} used the admin menu without being an operator", admin.getScoreboardName());
            return;
        }
        CommandSourceStack source = admin.createCommandSourceStack();
        List<ServerPlayer> targets = targets(admin, payload.target());
        if (targets.isEmpty()) {
            source.sendFailure(Component.translatable("message.jadm.admin.not_online", payload.target()));
            return;
        }
        Component who = targets.size() == 1 ? targets.get(0).getDisplayName()
                : Component.translatable("message.jadm.admin.everyone", targets.size());
        switch (payload.action()) {
            case PRODUCT -> {
                Products.Product product = JadmData.products().get(payload.id());
                give(source, admin, targets, who, product == null ? ItemStack.EMPTY : item(product),
                        payload.amount());
            }
            case RANDOM_PACK -> give(source, admin, targets, who, BoosterPackItem.of(null), payload.amount());
            case CARD -> {
                CardInfo card = JadmData.cards().card(payload.code());
                give(source, admin, targets, who, card == null ? ItemStack.EMPTY
                        : CardItem.of(payload.code(), payload.rarity()), payload.amount());
            }
            case POINTS_GIVE, POINTS_TAKE, POINTS_SET -> points(source, admin, targets, payload);
        }
    }

    private static List<ServerPlayer> targets(ServerPlayer admin, String target) {
        if (target.isEmpty()) {
            return List.of(admin);
        }
        if (target.equals(EVERYONE)) {
            return List.copyOf(admin.server.getPlayerList().getPlayers());
        }
        ServerPlayer player = admin.server.getPlayerList().getPlayerByName(target);
        return player == null ? List.of() : List.of(player);
    }

    private static void give(CommandSourceStack source, ServerPlayer admin, List<ServerPlayer> targets, Component who,
                             ItemStack item, int amount) {
        if (item.isEmpty()) {
            source.sendFailure(Component.translatable("message.jadm.admin.unknown"));
            return;
        }
        int count = Math.max(1, Math.min(MAX_ITEMS, amount));
        for (ServerPlayer target : targets) {
            int left = count;
            while (left > 0) {
                ItemStack stack = item.copyWithCount(Math.min(left, item.getMaxStackSize()));
                left -= stack.getCount();
                if (!target.getInventory().add(stack)) {
                    target.drop(stack, false);
                }
            }
            if (target != admin) {
                target.sendSystemMessage(Component.translatable("message.jadm.admin.received",
                        admin.getDisplayName(), count, item.getHoverName()).withStyle(ChatFormatting.GOLD));
            }
        }
        source.sendSuccess(() -> Component.translatable("message.jadm.admin.gave", count, item.getHoverName(), who),
                true);
    }

    private static void points(CommandSourceStack source, ServerPlayer admin, List<ServerPlayer> targets,
                               AdminActionPayload payload) {
        if (!Points.active()) {
            source.sendFailure(Component.translatable("message.jadm.points.inactive",
                    JadmServerConfig.SHOP_CURRENCY.get()));
            return;
        }
        long amount = Math.max(0, payload.amount());
        Points points = Points.get(admin.server);
        for (ServerPlayer target : targets) {
            long balance = points.balance(target.getUUID());
            points.set(admin.server, target.getUUID(), switch (payload.action()) {
                case POINTS_GIVE -> balance + amount;
                case POINTS_TAKE -> balance - amount;
                default -> amount;
            });
            long now = points.balance(target.getUUID());
            source.sendSuccess(() -> Component.translatable("message.jadm.points.balance_of",
                    target.getDisplayName(), Points.format(now)), true);
            if (target != admin && payload.action() == AdminActionPayload.Action.POINTS_GIVE && amount > 0) {
                target.sendSystemMessage(Component.translatable("message.jadm.points.received",
                        admin.getDisplayName(), Points.format(amount)).withStyle(ChatFormatting.GOLD));
            }
        }
        send(admin, false);
    }
}
