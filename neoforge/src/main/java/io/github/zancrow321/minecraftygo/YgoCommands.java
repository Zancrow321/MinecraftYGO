package io.github.zancrow321.minecraftygo;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.zancrow321.minecraftygo.duel.DuelManager;
import io.github.zancrow321.minecraftygo.engine.OcgCore;
import net.minecraft.commands.CommandSourceStack;
import io.github.zancrow321.minecraftygo.cosmetics.PlayerCosmetics;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * The {@code /ygo} command tree.
 */
final class YgoCommands {
    private static final String BOT = "bot";
    private YgoCommands() {
    }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ygo")
                .then(Commands.literal("version").executes(YgoCommands::version))
                .then(io.github.zancrow321.minecraftygo.progression.ProgressionCommands.build())
                .then(io.github.zancrow321.minecraftygo.tournament.TournamentCommands.build())
                .then(io.github.zancrow321.minecraftygo.duel.StatsCommands.build())
                .then(Commands.literal("duel")
                        .then(Commands.literal("bot").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            manager(ctx).duelBot(player);
                            return 1;
                        }))
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            manager(ctx).challenge(ctx.getSource().getPlayerOrException(),
                                    EntityArgument.getPlayer(ctx, "player"), false);
                            return 1;
                        }).then(Commands.literal("ante").executes(ctx -> {
                            manager(ctx).challenge(ctx.getSource().getPlayerOrException(),
                                    EntityArgument.getPlayer(ctx, "player"), true);
                            return 1;
                        }))))
                .then(Commands.literal("tag")
                        .then(Commands.argument("partner", StringArgumentType.word()).suggests(YgoCommands::duelists)
                                .then(Commands.argument("opponent1", StringArgumentType.word())
                                        .suggests(YgoCommands::duelists)
                                        .then(Commands.argument("opponent2", StringArgumentType.word())
                                                .suggests(YgoCommands::duelists)
                                                .executes(ctx -> tag(ctx, false))))))
                .then(Commands.literal("battlecity")
                        .then(Commands.argument("partner", StringArgumentType.word()).suggests(YgoCommands::duelists)
                                .then(Commands.argument("opponent1", StringArgumentType.word())
                                        .suggests(YgoCommands::duelists)
                                        .then(Commands.argument("opponent2", StringArgumentType.word())
                                                .suggests(YgoCommands::duelists)
                                                .executes(ctx -> tag(ctx, true))))))
                .then(Commands.literal("gallery")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> Gallery.show(ctx.getSource(), 1))
                        .then(Commands.literal("clear").executes(ctx -> Gallery.clear(ctx.getSource())))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> Gallery.show(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("starter").executes(ctx -> io.github.zancrow321.minecraftygo.progression
                        .StarterDecks.command(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("shop").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    if (!YgoServerConfig.MACHINE.command.get() && !ctx.getSource().hasPermission(2)) {
                        ctx.getSource().sendFailure(
                                Component.translatable("message.minecraftygo.card_machine.no_command"));
                        return 0;
                    }
                    io.github.zancrow321.minecraftygo.village.MachineShop.open(player, null);
                    return 1;
                }))
                .then(io.github.zancrow321.minecraftygo.points.PointsCommands.build())
                .then(Commands.literal("cosmetics").executes(ctx -> {
                    PlayerCosmetics.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("accept").executes(ctx -> {
                    manager(ctx).accept(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("forfeit").executes(ctx -> {
                    manager(ctx).forfeit(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("watch").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            manager(ctx).watch(ctx.getSource().getPlayerOrException(),
                                    EntityArgument.getPlayer(ctx, "player"));
                            return 1;
                        })))
                .then(Commands.literal("unwatch").executes(ctx -> {
                    manager(ctx).unwatch(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    /**
     * {@code /ygo tag|battlecity <partner> <opponent1> <opponent2>}, each a player name or {@code bot}; Battle City
     * gives each partner their own half of the field.
     */
    private static int tag(CommandContext<CommandSourceStack> ctx, boolean split) throws CommandSyntaxException {
        ServerPlayer host = ctx.getSource().getPlayerOrException();
        ServerPlayer[] others = new ServerPlayer[3];
        String[] args = {"partner", "opponent1", "opponent2"};
        for (int i = 0; i < 3; i++) {
            String name = StringArgumentType.getString(ctx, args[i]);
            if (name.equalsIgnoreCase(BOT)) {
                continue;
            }
            others[i] = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
            if (others[i] == null) {
                ctx.getSource().sendFailure(Component.literal("No player named " + name + " is online."));
                return 0;
            }
        }
        manager(ctx).tag(host, others[0], others[1], others[2], split);
        return 1;
    }

    private static CompletableFuture<Suggestions> duelists(CommandContext<CommandSourceStack> ctx,
                                                           SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Stream.concat(Stream.of(BOT),
                Stream.of(ctx.getSource().getServer().getPlayerNames())), builder);
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
