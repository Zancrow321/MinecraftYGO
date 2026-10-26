package io.github.zancrow321.jadm.item;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.ClientScreens;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        Map<Integer, List<Rarity>> foils = new HashMap<>();
        for (BinderItem.Copies c : copies(list)) {
            if (c.rarity() != Rarity.COMMON) {
                foils.computeIfAbsent(c.code(), code -> new ArrayList<>()).addAll(Collections.nCopies(c.count(),
                        c.rarity()));
            }
        }
        return new Deck(stack.getHoverName().getString(), list.main(), list.extra(), List.of(), foils);
    }

    /** Every card in a deck, one entry per card and rarity. */
    public static List<BinderItem.Copies> copies(JadmComponents.DeckList deck) {
        Map<Integer, Integer> counts = new TreeMap<>();
        deck.main().forEach(code -> counts.merge(code, 1, Integer::sum));
        deck.extra().forEach(code -> counts.merge(code, 1, Integer::sum));
        Map<Integer, EnumMap<Rarity, Integer>> foils = BinderItem.foils(deck.foils());
        List<BinderItem.Copies> list = new ArrayList<>();
        counts.forEach((code, total) -> BinderItem.split(total, foils.get(code))
                .forEach((rarity, n) -> list.add(new BinderItem.Copies(code, rarity, n))));
        return list;
    }

    /** How many copies of a card a deck has at each rarity. */
    public static Map<Rarity, Integer> copies(JadmComponents.DeckList deck, int code) {
        int total = (int) (deck.main().stream().filter(c -> c == code).count()
                + deck.extra().stream().filter(c -> c == code).count());
        return BinderItem.split(total, BinderItem.foils(deck.foils()).get(code));
    }

    /** Puts a copy of a card at a rarity into the deck box's main or extra deck. */
    public static void add(ItemStack box, int code, Rarity rarity, boolean extra) {
        JadmComponents.DeckList deck = deck(box);
        List<Integer> main = new ArrayList<>(deck.main());
        List<Integer> extraList = new ArrayList<>(deck.extra());
        (extra ? extraList : main).add(code);
        Map<String, Integer> foils = new HashMap<>(deck.foils());
        if (rarity != Rarity.COMMON) {
            foils.merge(code + ":" + rarity.id(), 1, Integer::sum);
        }
        box.set(JadmComponents.DECK.get(), new JadmComponents.DeckList(main, extraList, foils));
    }

    /**
     * Takes a copy of a card out of the deck box: one at {@code rarity}, or with {@code null} a common first so the
     * foils stay.
     *
     * @return the rarity of the copy taken out, or {@code null} if the deck has no such copy
     */
    public static Rarity remove(ItemStack box, int code, Rarity rarity) {
        JadmComponents.DeckList deck = deck(box);
        Map<Rarity, Integer> copies = copies(deck, code);
        Rarity taken = rarity;
        if (taken == null) {
            taken = copies.keySet().stream().findFirst().orElse(null);
        }
        if (taken == null || copies.getOrDefault(taken, 0) == 0) {
            return null;
        }
        List<Integer> main = new ArrayList<>(deck.main());
        List<Integer> extra = new ArrayList<>(deck.extra());
        if (!main.remove((Integer) code)) {
            extra.remove((Integer) code);
        }
        Map<String, Integer> foils = new HashMap<>(deck.foils());
        if (taken != Rarity.COMMON) {
            foils.merge(code + ":" + taken.id(), -1, Integer::sum);
        }
        if (!main.contains(code) && !extra.contains(code)) {
            foils.keySet().removeIf(key -> key.startsWith(code + ":"));
        }
        box.set(JadmComponents.DECK.get(), new JadmComponents.DeckList(main, extra, foils));
        return taken;
    }

    /** What keeps the deck from being played, for the local player on a client (empty if legal). */
    public static List<String> problems(ItemStack stack) {
        return problems(stack, null, JadmData.banlist(JadmData.step(null), JadmData.localBanlistSetting()));
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
