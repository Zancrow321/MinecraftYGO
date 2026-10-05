package io.github.zancrow321.minecraftygo;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.zancrow321.minecraftygo.duel.DuelManager;
import io.github.zancrow321.minecraftygo.engine.OcgCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /ygo} command tree.
 */
final class YgoCommands {
    private YgoCommands() {
    }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ygo")
                .then(Commands.literal("version").executes(YgoCommands::version))
                .then(Commands.literal("duel")
                        .then(Commands.literal("bot").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            manager(ctx).duelBot(player);
                            return 1;
                        }))
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            manager(ctx).challenge(ctx.getSource().getPlayerOrException(),
                                    EntityArgument.getPlayer(ctx, "player"));
                            return 1;
                        })))
                .then(Commands.literal("accept").executes(ctx -> {
                    manager(ctx).accept(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("forfeit").executes(ctx -> {
                    manager(ctx).forfeit(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    private static DuelManager manager(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return DuelManager.get(context.getSource().getServer());
    }

    private static int version(CommandContext<CommandSourceStack> context) {
        try {
            OcgCore.Version version = OcgCore.get().version();
            context.getSource().sendSuccess(() -> Component.translatable("commands.minecraftygo.version",
                    version.toString()), false);
            return 1;
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            context.getSource().sendFailure(Component.translatable("commands.minecraftygo.version.unavailable",
                    e.getMessage()));
            return 0;
        }
    }
}
