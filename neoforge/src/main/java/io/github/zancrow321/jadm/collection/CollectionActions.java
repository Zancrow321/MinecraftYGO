package io.github.zancrow321.jadm.collection;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.engine.data.DeckRules;
import io.github.zancrow321.jadm.item.BinderItem;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.DeckBoxItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.github.zancrow321.jadm.item.JadmComponents.DeckList;
import io.github.zancrow321.jadm.item.JadmItems;
import io.github.zancrow321.jadm.network.CollectionActionPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves cards between loose card items, binders and deck boxes, on the server. Cards are never created or lost:
 * every move takes a copy from one place before putting it in another.
 */
public final class CollectionActions {
    private CollectionActions() {
    }

    public static void handle(ServerPlayer player, CollectionActionPayload action) {
        ItemStack held = player.getItemInHand(action.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        switch (action.action()) {
            case DEPOSIT_ALL -> {
                if (held.is(JadmItems.BINDER.get())) {
                    depositAll(player, held);
                }
            }
            case WITHDRAW -> {
                if (held.is(JadmItems.BINDER.get())) {
                    withdraw(player, held, action.code(), action.rarity(), action.all());
                }
            }
            case DECK_ADD -> {
                if (held.is(JadmItems.DECK_BOX.get())) {
                    deckAdd(player, held, action.code());
                }
            }
            case DECK_REMOVE -> {
                if (held.is(JadmItems.DECK_BOX.get())) {
                    deckRemove(player, held, action.code());
                }
            }
            case DECK_CLEAR -> {
                if (held.is(JadmItems.DECK_BOX.get())) {
                    for (int code : List.copyOf(DeckBoxItem.deck(held).main())) {
                        deckRemove(player, held, code);
                    }
                    for (int code : List.copyOf(DeckBoxItem.deck(held).extra())) {
                        deckRemove(player, held, code);
                    }
                }
            }
        }
    }

    private static void depositAll(ServerPlayer player, ItemStack binder) {
        Inventory inventory = player.getInventory();
        int moved = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            int code = CardItem.code(stack);
            if (stack.is(JadmItems.CARD.get()) && code != 0) {
                BinderItem.add(binder, code, CardItem.rarity(stack), stack.getCount());
                moved += stack.getCount();
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        player.displayClientMessage(Component.translatable("message.jadm.binder.deposited", moved), true);
    }

    /** Takes one copy (or all copies) of a card at a rarity out of the binder; {@code null} means commons first. */
    private static void withdraw(ServerPlayer player, ItemStack binder, int code, Rarity rarity, boolean all) {
        if (rarity == null) {
            Rarity taken = BinderItem.removeOne(binder, code);
            if (taken != null) {
                give(player, CardItem.of(code, taken), 1);
            }
            return;
        }
        int n = BinderItem.remove(binder, code, rarity, all ? Integer.MAX_VALUE : 1);
        if (n > 0) {
            give(player, CardItem.of(code, rarity), n);
        }
    }

    private static void deckAdd(ServerPlayer player, ItemStack box, int code) {
        var card = JadmData.cards().card(code);
        if (card == null) {
            return;
        }
        DeckList deck = DeckBoxItem.deck(box);
        boolean extra = DeckRules.isExtra(card);
        int copies = (int) (deck.main().stream().filter(c -> c == code).count()
                + deck.extra().stream().filter(c -> c == code).count());
        if (!JadmData.playable(player).test(code)) {
            player.displayClientMessage(Component.translatable("message.jadm.deck.locked", card.name()), true);
            return;
        }
        int limit = JadmData.banlist(player).limit(code);
        if (copies >= limit) {
            player.displayClientMessage(Component.translatable(limit == 0 ? "message.jadm.deck.forbidden"
                    : "message.jadm.deck.limit", card.name(), limit), true);
            return;
        }
        if (extra ? deck.extra().size() >= DeckRules.EXTRA_MAX : deck.main().size() >= DeckRules.MAIN_MAX) {
            player.displayClientMessage(Component.translatable("message.jadm.deck.full"), true);
            return;
        }
        if (!take(player, code)) {
            player.displayClientMessage(Component.translatable("message.jadm.deck.missing", card.name()), true);
            return;
        }
        List<Integer> main = new ArrayList<>(deck.main());
        List<Integer> extraList = new ArrayList<>(deck.extra());
        (extra ? extraList : main).add(code);
        box.set(JadmComponents.DECK.get(), new DeckList(main, extraList));
    }

    private static void deckRemove(ServerPlayer player, ItemStack box, int code) {
        DeckList deck = DeckBoxItem.deck(box);
        List<Integer> main = new ArrayList<>(deck.main());
        List<Integer> extra = new ArrayList<>(deck.extra());
        if (!main.remove((Integer) code) && !extra.remove((Integer) code)) {
            return;
        }
        box.set(JadmComponents.DECK.get(), new DeckList(main, extra));
        ItemStack binder = findBinder(player);
        if (binder.isEmpty()) {
            give(player, CardItem.of(code), 1);
        } else {
            binder.set(JadmComponents.COLLECTION.get(), BinderItem.collection(binder).add(code, 1));
        }
    }

    /** Takes one copy of a card from the first binder that has it, or else from a loose card item. */
    private static boolean take(ServerPlayer player, int code) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(JadmItems.BINDER.get()) && BinderItem.removeOne(stack, code) != null) {
                return true;
            }
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(JadmItems.CARD.get()) && CardItem.code(stack) == code) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** The first binder in the inventory (off hand included), or {@link ItemStack#EMPTY}. */
    public static ItemStack findBinder(net.minecraft.world.entity.player.Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(JadmItems.BINDER.get())) {
                return inventory.getItem(slot);
            }
        }
        return ItemStack.EMPTY;
    }

    private static void give(ServerPlayer player, ItemStack card, int count) {
        while (count > 0) {
            int n = Math.min(count, card.getMaxStackSize());
            ItemStack stack = card.copyWithCount(n);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            count -= n;
        }
    }
}
