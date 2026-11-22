package io.github.zancrow321.jadm.custom;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.item.BoosterPackItem;
import io.github.zancrow321.jadm.item.SealedProductItem;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * {@code /jadm products}: the server's own products from its datapacks and what was wrong with their files;
 * {@code /jadm products give <product> [player]} hands one out.
 */
public final class CustomProductCommands {
    private CustomProductCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("products")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> list(ctx.getSource()))
                .then(Commands.literal("give")
                        .then(Commands.argument("product", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        JadmData.customProducts().stream().map(Products.Product::id), builder))
                                .executes(ctx -> give(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> give(ctx, EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int list(CommandSourceStack source) {
        List<Products.Product> products = JadmData.customProducts();
        source.sendSuccess(() -> Component.literal(products.isEmpty()
                ? "No custom products. Put them in data/<namespace>/jadm/products/ of a datapack and /reload."
                : products.size() + " custom products from datapacks:").withStyle(ChatFormatting.GOLD), false);
        for (Products.Product p : products) {
            String kind = switch (p.kind()) {
                case DECK -> "deck";
                case TIN -> "tin";
                default -> "booster";
            };
            String detail;
            if (p.kind() == Products.Kind.BOOSTER) {
                BoosterSets.BoosterSet set = JadmData.set(p.id());
                detail = set == null ? "no playable cards" : set.cards().size() + " cards, packs of "
                        + set.profile().size();
            } else if (p.kind() == Products.Kind.TIN) {
                detail = p.cards().size() + " promos, " + SealedProductItem.tinPacks(p).size() + " packs";
            } else {
                detail = p.custom().counts().values().stream().mapToInt(Integer::intValue).sum() + " cards";
            }
            String date = p.custom().dated() ? p.date().toString() : "from the start";
            source.sendSuccess(() -> Component.literal(" " + p.id() + " (" + kind + ", " + p.code() + "): "
                    + p.name() + " · " + detail + " · " + date).withStyle(ChatFormatting.GRAY), false);
        }
        List<String> problems = CustomProductLoader.problems();
        if (!problems.isEmpty()) {
            source.sendSuccess(() -> Component.literal(problems.size() + " problems:")
                    .withStyle(ChatFormatting.RED), false);
            problems.stream().limit(30).forEach(line -> source.sendSuccess(() -> Component.literal(" " + line)
                    .withStyle(ChatFormatting.RED), false));
            if (problems.size() > 30) {
                source.sendSuccess(() -> Component.literal(" ... and " + (problems.size() - 30)
                        + " more in the server log").withStyle(ChatFormatting.RED), false);
            }
        }
        return products.size();
    }

    private static int give(CommandContext<CommandSourceStack> ctx, ServerPlayer player)
            throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "product");
        Products.Product product = JadmData.products().resolve(id);
        if (product == null) {
            ctx.getSource().sendFailure(Component.literal("No product " + id + "."));
            return 0;
        }
        ItemStack stack;
        if (SealedProductItem.sealed(product)) {
            stack = SealedProductItem.of(product);
        } else if (JadmData.set(product.id()) != null) {
            stack = BoosterPackItem.of(product.id());
        } else {
            ctx.getSource().sendFailure(Component.literal(product.name() + " can't be handed out."));
            return 0;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + product.name() + " to "
                + player.getScoreboardName() + "."), true);
        return 1;
    }
}
