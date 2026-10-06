package io.github.zancrow321.minecraftygo.points;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.zancrow321.minecraftygo.YgoServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * {@code /ygo dp}: your Duel Points; {@code pay <player> <amount>} gives some to another player (with {@code
 * transfers}); operators look at anyone's with {@code /ygo dp <player>} and change them with {@code give}, {@code take}
 * and {@code set}.
 */
public final class PointsCommands {
    private PointsCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dp")
                .executes(ctx -> show(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                        .executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, "player"))))
                .then(Commands.literal("pay").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(PointsCommands::pay))))
                .then(change("give", (balance, amount) -> balance + amount))
                .then(change("take", (balance, amount) -> balance - amount))
                .then(change("set", (balance, amount) -> amount));
    }

    private static boolean inactive(CommandContext<CommandSourceStack> ctx) {
        if (Points.active()) {
            return false;
        }
        ctx.getSource().sendFailure(Component.translatable("message.minecraftygo.points.inactive",
                YgoServerConfig.SHOP_CURRENCY.get()));
        return true;
    }

    private static int show(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (inactive(ctx)) {
            return 0;
        }
        long balance = Points.get(player.server).balance(player.getUUID());
        ctx.getSource().sendSuccess(() -> player == ctx.getSource().getPlayer()
                ? Component.translatable("message.minecraftygo.points.balance", Points.format(balance))
                : Component.translatable("message.minecraftygo.points.balance_of", player.getDisplayName(),
                Points.format(balance)), false);
        return (int) Math.min(balance, Integer.MAX_VALUE);
    }

    private static int pay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (inactive(ctx)) {
            return 0;
        }
        ServerPlayer from = ctx.getSource().getPlayerOrException();
        ServerPlayer to = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        if (!YgoServerConfig.POINTS.transfers.get()) {
            ctx.getSource().sendFailure(Component.translatable("message.minecraftygo.points.no_transfers"));
            return 0;
        }
        if (from == to) {
            ctx.getSource().sendFailure(Component.translatable("message.minecraftygo.points.self"));
            return 0;
        }
        Points points = Points.get(from.server);
        if (!points.take(from.server, from.getUUID(), amount)) {
            ctx.getSource().sendFailure(Component.translatable("message.minecraftygo.points.not_enough",
                    Points.format(points.balance(from.getUUID()))));
            return 0;
        }
        points.add(from.server, to.getUUID(), amount);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.minecraftygo.points.paid",
                Points.format(amount), to.getDisplayName()), false);
        to.sendSystemMessage(Component.translatable("message.minecraftygo.points.received", from.getDisplayName(),
                Points.format(amount)).withStyle(ChatFormatting.GOLD));
        return amount;
    }

    private interface Change {
        long apply(long balance, long amount);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, Change change) {
        return Commands.literal(name).requires(s -> s.hasPermission(2))
                .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(ctx -> {
                            if (inactive(ctx)) {
                                return 0;
                            }
                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                            for (ServerPlayer player : players) {
                                Points points = Points.get(player.server);
                                points.set(player.server, player.getUUID(),
                                        change.apply(points.balance(player.getUUID()), amount));
                                long now = points.balance(player.getUUID());
                                ctx.getSource().sendSuccess(() -> Component.translatable(
                                        "message.minecraftygo.points.balance_of", player.getDisplayName(),
                                        Points.format(now)), true);
                            }
                            return players.size();
                        })));
    }
}
