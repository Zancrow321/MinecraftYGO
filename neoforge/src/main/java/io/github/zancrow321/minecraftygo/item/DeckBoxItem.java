package io.github.zancrow321.minecraftygo.item;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.client.ClientScreens;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.data.DeckRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Holds one deck. Right-click to build it from the cards in your binder; the first legal deck box you carry is
 * the deck you duel with.
 */
public final class DeckBoxItem extends Item {
    public DeckBoxItem(Properties properties) {
        super(properties);
    }

    public static YgoComponents.DeckList deck(ItemStack stack) {
        return stack.getOrDefault(YgoComponents.DECK.get(), YgoComponents.DeckList.EMPTY);
    }

    /** A deck box holding a bundled deck, e.g. {@code starter_yugi}. */
    public static ItemStack of(String bundledDeck, Component name) {
        Deck deck = Deck.bundled(bundledDeck);
        ItemStack stack = new ItemStack(YgoItems.DECK_BOX.get());
        stack.set(YgoComponents.DECK.get(), new YgoComponents.DeckList(deck.main(), deck.extra()));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    public static Deck toDeck(ItemStack stack) {
        YgoComponents.DeckList list = deck(stack);
        return new Deck(stack.getHoverName().getString(), list.main(), list.extra(), List.of());
    }

    public static List<String> problems(ItemStack stack) {
        return DeckRules.problems(toDeck(stack), YgoData.cards(), YgoData.banlist(), YgoData.pool()::contains);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientScreens.openDeckBox(hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        YgoComponents.DeckList deck = deck(stack);
        tooltip.add(Component.translatable("item.minecraftygo.deck_box.tooltip", deck.main().size(),
                deck.extra().size()).withStyle(ChatFormatting.GRAY));
        List<String> problems = problems(stack);
        tooltip.add(problems.isEmpty()
                ? Component.translatable("item.minecraftygo.deck_box.legal").withStyle(ChatFormatting.GREEN)
                : Component.literal(problems.get(0)).withStyle(ChatFormatting.RED));
    }
}
