package io.github.zancrow321.jadm.item;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.ClientScreens;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.data.DeckRules;
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

    public static JadmComponents.DeckList deck(ItemStack stack) {
        return stack.getOrDefault(JadmComponents.DECK.get(), JadmComponents.DeckList.EMPTY);
    }

    /** A deck box holding a bundled deck, e.g. {@code starter_yugi}. */
    public static ItemStack of(String bundledDeck, Component name) {
        Deck deck = Deck.bundled(bundledDeck);
        ItemStack stack = new ItemStack(JadmItems.DECK_BOX.get());
        stack.set(JadmComponents.DECK.get(), new JadmComponents.DeckList(deck.main(), deck.extra()));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    public static Deck toDeck(ItemStack stack) {
        JadmComponents.DeckList list = deck(stack);
        return new Deck(stack.getHoverName().getString(), list.main(), list.extra(), List.of());
    }

    /** What keeps the deck from being played, for the local player on a client (empty if legal). */
    public static List<String> problems(ItemStack stack) {
        return problems(stack, null, JadmData.banlist());
    }

    /**
     * @param player  whose cards count as unlocked ({@code null}: the world's, or on a client the local player's)
     * @param banlist the banlist the deck is played under
     */
    public static List<String> problems(ItemStack stack, net.minecraft.world.entity.player.Player player,
                                        io.github.zancrow321.jadm.engine.data.Banlist banlist) {
        return DeckRules.problems(toDeck(stack), JadmData.cards(), banlist, JadmData.playable(player));
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
        JadmComponents.DeckList deck = deck(stack);
        tooltip.add(Component.translatable("item.jadm.deck_box.tooltip", deck.main().size(),
                deck.extra().size()).withStyle(ChatFormatting.GRAY));
        List<String> problems = problems(stack);
        tooltip.add(problems.isEmpty()
                ? Component.translatable("item.jadm.deck_box.legal").withStyle(ChatFormatting.GREEN)
                : Component.literal(problems.get(0)).withStyle(ChatFormatting.RED));
    }
}
