package io.github.zancrow321.minecraftygo.progression;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.data.Products;
import io.github.zancrow321.minecraftygo.engine.data.Progression;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /ygo progression}: status, next, until, set and list (operators only).
 */
public final class ProgressionCommands {
    private static final int PAGE = 10;
    private static final SimpleCommandExceptionType SHARED = new SimpleCommandExceptionType(Component.literal(
            "Progress is shared by the whole world here (progression.scope = \"server\"); leave out the player."));
    private static final SimpleCommandExceptionType WHO = new SimpleCommandExceptionType(Component.literal(
            "Each player has their own progress here (progression.scope = \"player\"); name one."));
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.literal(
            "No such product: give a set code such as MRD or a date such as 2005-03-01."));

    private ProgressionCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("progression")
                .requires(source -> source.hasPermission(2))
                .then(withPlayer(Commands.literal("status"), (ctx, player) -> status(ctx, player)))
                .then(withPlayer(Commands.literal("next"), (ctx, player) -> move(ctx, player, step ->
                        YgoData.progression().next(step, 1)))
                        .then(withPlayer(Commands.argument("count", IntegerArgumentType.integer(1, 10000)),
                                (ctx, player) -> move(ctx, player, step -> YgoData.progression()
                                        .next(step, IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("until").then(withPlayer(target(),
                        (ctx, player) -> move(ctx, player, step -> Math.max(step, find(ctx))))))
                .then(Commands.literal("set").then(withPlayer(target(),
                        (ctx, player) -> move(ctx, player, step -> find(ctx)))))
                .then(Commands.literal("list").executes(ctx -> list(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "page")))));
    }

    private interface Action {
        int run(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException;
    }

    private interface StepFunction {
        int apply(int step) throws CommandSyntaxException;
    }

    /** Runs the action for the world (or, per player, the person typing), or for a named player. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withPlayer(T node, Action action) {
        return node.executes(ctx -> action.run(ctx, defaultPlayer(ctx)))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> action.run(ctx, namedPlayer(ctx))));
    }

    private static ServerPlayer defaultPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!Progress.perPlayer()) {
            return null;
        }
        ServerPlayer self = ctx.getSource().getPlayer();
        if (self == null) {
            throw WHO.create();
        }
        return self;
    }

    private static ServerPlayer namedPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!Progress.perPlayer()) {
            throw SHARED.create();
        }
        return EntityArgument.getPlayer(ctx, "player");
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> target() {
        return Commands.argument("product", StringArgumentType.word()).suggests((ctx, builder) ->
                SharedSuggestionProvider.suggest(YgoData.progression().products().products().stream()
                        .filter(p -> !p.newCards().isEmpty()).map(Products.Product::code).distinct(), builder));
    }

    private static int find(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int step = YgoData.progression().find(StringArgumentType.getString(ctx, "product"));
        if (step < 0) {
            throw UNKNOWN.create();
        }
        return step;
    }

    private static int stepOf(ServerPlayer player) {
        return player == null ? Progress.worldStep() : Progress.step(player);
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        for (Component line : Progress.status(stepOf(player), player == null ? "World" : player.getScoreboardName())) {
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int move(CommandContext<CommandSourceStack> ctx, ServerPlayer player, StepFunction to)
            throws CommandSyntaxException {
        int step = to.apply(stepOf(player));
        Component summary = Progress.move(ctx.getSource().getServer(), player, step);
        ctx.getSource().sendSuccess(() -> summary, true);
        return step + 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, int page) throws CommandSyntaxException {
        Progression progression = YgoData.progression();
        int current = stepOf(Progress.perPlayer() ? ctx.getSource().getPlayer() : null);
        List<Integer> steps = new ArrayList<>();
        for (int i = 0; i < progression.size(); i++) {
            if (!progression.product(i).newCards().isEmpty()) {
                steps.add(i);
            }
        }
        int pages = (steps.size() + PAGE - 1) / PAGE;
        int p = Math.min(page, pages);
        ctx.getSource().sendSuccess(() -> Component.literal("Products with new cards, page " + p + " of " + pages
                + " (/ygo progression list <page>)").withStyle(ChatFormatting.GOLD), false);
        for (int i = (p - 1) * PAGE; i < Math.min(steps.size(), p * PAGE); i++) {
            int step = steps.get(i);
            Products.Product product = progression.product(step);
            String mark = step < current ? "✔ " : step == current ? "▶ " : "· ";
            ChatFormatting color = step <= current ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            Component line = Component.literal(mark + product.date() + "  " + product.code() + "  " + product.name()
                    + "  (" + product.newCards().size() + " new)").withStyle(color);
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }
}
