package io.github.zancrow321.jadm.progression;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.engine.data.Progression;
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
 * {@code /jadm progression}: status, next, until, set, list and date (operators only).
 */
public final class ProgressionCommands {
    private static final int PAGE = 10;
    private static final SimpleCommandExceptionType SHARED = new SimpleCommandExceptionType(Component.literal(
            "Progress is shared by the whole world here (progression.scope = \"server\"); leave out the player."));
    private static final SimpleCommandExceptionType WHO = new SimpleCommandExceptionType(Component.literal(
            "Each player has their own progress here (progression.scope = \"player\"); name one."));
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.literal(
            "No such product: give a set code such as MRD or a date such as 2005-03-01."));
    private static final SimpleCommandExceptionType UNKNOWN_SET = new SimpleCommandExceptionType(Component.literal(
            "No such product: give a set code such as MRD or a product id."));
    private static final SimpleCommandExceptionType BAD_DATE = new SimpleCommandExceptionType(Component.literal(
            "Give a date such as 2002-04-01, or \"reset\" for the real release date."));

    private ProgressionCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("progression")
                .requires(source -> source.hasPermission(2))
                .then(withPlayer(Commands.literal("status"), (ctx, player) -> status(ctx, player)))
                .then(withPlayer(Commands.literal("next"), (ctx, player) -> move(ctx, player, step ->
                        JadmData.progression().next(step, 1)))
                        .then(withPlayer(Commands.argument("count", IntegerArgumentType.integer(1, 10000)),
                                (ctx, player) -> move(ctx, player, step -> JadmData.progression()
                                        .next(step, IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("until").then(withPlayer(target(),
                        (ctx, player) -> move(ctx, player, step -> Math.max(step, find(ctx))))))
                .then(Commands.literal("set").then(withPlayer(target(),
                        (ctx, player) -> move(ctx, player, step -> find(ctx)))))
                .then(Commands.literal("date").executes(ProgressionCommands::dates)
                        .then(target().executes(ProgressionCommands::date)
                                .then(Commands.argument("date", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("reset", "2002-04-01"), builder))
                                        .executes(ProgressionCommands::setDate))))
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
                SharedSuggestionProvider.suggest(JadmData.progression().products().products().stream()
                        .filter(p -> !p.newCards().isEmpty()).map(Products.Product::code).distinct(), builder));
    }

    private static int find(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int step = JadmData.progression().find(StringArgumentType.getString(ctx, "product"));
        if (step < 0) {
            throw UNKNOWN.create();
        }
        return step;
    }

    private static Products.Product product(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Products.Product product = JadmData.bundledProducts().resolve(StringArgumentType.getString(ctx, "product"));
        if (product == null) {
            throw UNKNOWN_SET.create();
        }
        return product;
    }

    /** {@code date}: the sets released on other days than in the TCG. */
    private static int dates(CommandContext<CommandSourceStack> ctx) {
        java.util.Map<String, java.time.LocalDate> moved = JadmData.releaseDates();
        Component head = moved.isEmpty() ? Component.literal("Every set comes out on its real TCG release date. "
                + "/jadm progression date <set> <YYYY-MM-DD> moves one.").withStyle(ChatFormatting.GRAY)
                : Component.literal("Sets released on other days ([progression] releaseDates):")
                        .withStyle(ChatFormatting.GOLD);
        ctx.getSource().sendSuccess(() -> head, false);
        moved.forEach((id, date) -> {
            Products.Product real = JadmData.bundledProducts().get(id);
            Component line = Component.literal("· " + real.code() + "  " + real.name() + ": " + date + " (real: "
                    + real.date() + ")").withStyle(ChatFormatting.YELLOW);
            ctx.getSource().sendSuccess(() -> line, false);
        });
        return moved.size();
    }

    /** {@code date <set>}: when a set comes out here. */
    private static int date(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Products.Product real = product(ctx);
        java.time.LocalDate moved = JadmData.releaseDates().get(real.id());
        Component line = Component.literal(Progress.describe(real) + (moved == null ? " comes out on its real date."
                : " comes out on " + moved + " here.")).withStyle(ChatFormatting.YELLOW);
        ctx.getSource().sendSuccess(() -> line, false);
        return 1;
    }

    /** {@code date <set> <YYYY-MM-DD|reset>}: moves a set's release date, saved in the server config. */
    private static int setDate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Products.Product real = product(ctx);
        String text = StringArgumentType.getString(ctx, "date");
        java.time.LocalDate date = null;
        if (!"reset".equalsIgnoreCase(text)) {
            date = ReleaseDates.date(text);
            if (date == null) {
                throw BAD_DATE.create();
            }
        }
        java.time.LocalDate now = ReleaseDates.set(ctx.getSource().getServer(), real, date);
        Component line = Component.literal(real.name() + " (" + real.code() + ") now comes out on " + now
                + (date == null ? ", its real release date." : "."));
        ctx.getSource().sendSuccess(() -> line, true);
        return 1;
    }

    private static int stepOf(ServerPlayer player) {
        return player == null ? Progress.worldStep() : Progress.step(player);
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        for (Component line : Progress.status(ctx.getSource().getServer(), player, stepOf(player),
                player == null ? "World" : player.getScoreboardName())) {
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
        Progression progression = JadmData.progression();
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
                + " (/jadm progression list <page>)").withStyle(ChatFormatting.GOLD), false);
        for (int i = (p - 1) * PAGE; i < Math.min(steps.size(), p * PAGE); i++) {
            int step = steps.get(i);
            Products.Product product = progression.product(step);
            String mark = step < current ? "✔ " : step == current ? "▶ " : "· ";
            ChatFormatting color = step <= current ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            boolean moved = JadmData.releaseDates().containsKey(product.id());
            Component line = Component.literal(mark + product.date() + (moved ? "*" : "") + "  " + product.code()
                    + "  " + product.name()
                    + "  (" + product.newCards().size() + " new)").withStyle(color);
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }
}
