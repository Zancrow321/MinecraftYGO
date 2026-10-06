package io.github.zancrow321.minecraftygo.collection;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.data.DeckRules;
import io.github.zancrow321.minecraftygo.item.BinderItem;
import io.github.zancrow321.minecraftygo.item.CardItem;
import io.github.zancrow321.minecraftygo.item.DeckBoxItem;
import io.github.zancrow321.minecraftygo.item.YgoComponents;
import io.github.zancrow321.minecraftygo.item.YgoComponents.CardCollection;
import io.github.zancrow321.minecraftygo.item.YgoComponents.DeckList;
import io.github.zancrow321.minecraftygo.item.YgoItems;
import io.github.zancrow321.minecraftygo.network.CollectionActionPayload;
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
                if (held.is(YgoItems.BINDER.get())) {
                    depositAll(player, held);
                }
            }
            case WITHDRAW -> {
                if (held.is(YgoItems.BINDER.get())) {
                    withdraw(player, held, action.code(), action.all());
                }
            }
            case DECK_ADD -> {
                if (held.is(YgoItems.DECK_BOX.get())) {
                    deckAdd(player, held, action.code());
                }
            }
            case DECK_REMOVE -> {
                if (held.is(YgoItems.DECK_BOX.get())) {
                    deckRemove(player, held, action.code());
                }
            }
            case DECK_CLEAR -> {
                if (held.is(YgoItems.DECK_BOX.get())) {
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
        CardCollection collection = BinderItem.collection(binder);
        Inventory inventory = player.getInventory();
        int moved = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            int code = CardItem.code(stack);
            if (stack.is(YgoItems.CARD.get()) && code != 0) {
                collection = collection.add(code, stack.getCount());
                moved += stack.getCount();
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        binder.set(YgoComponents.COLLECTION.get(), collection);
        player.displayClientMessage(Component.translatable("message.minecraftygo.binder.deposited", moved), true);
    }

    private static void withdraw(ServerPlayer player, ItemStack binder, int code, boolean all) {
        CardCollection collection = BinderItem.collection(binder);
        int n = all ? collection.count(code) : Math.min(1, collection.count(code));
        if (n == 0) {
            return;
        }
        binder.set(YgoComponents.COLLECTION.get(), collection.add(code, -n));
        give(player, CardItem.of(code), n);
    }

    private static void deckAdd(ServerPlayer player, ItemStack box, int code) {
        var card = YgoData.cards().card(code);
        if (card == null) {
            return;
        }
        DeckList deck = DeckBoxItem.deck(box);
        boolean extra = DeckRules.isExtra(card);
        int copies = (int) (deck.main().stream().filter(c -> c == code).count()
                + deck.extra().stream().filter(c -> c == code).count());
        if (!YgoData.playable(player).test(code)) {
            player.displayClientMessage(Component.translatable("message.minecraftygo.deck.locked", card.name()), true);
            return;
        }
        int limit = YgoData.banlist(player).limit(code);
        if (copies >= limit) {
            player.displayClientMessage(Component.translatable(limit == 0 ? "message.minecraftygo.deck.forbidden"
                    : "message.minecraftygo.deck.limit", card.name(), limit), true);
            return;
        }
        if (extra ? deck.extra().size() >= DeckRules.EXTRA_MAX : deck.main().size() >= DeckRules.MAIN_MAX) {
            player.displayClientMessage(Component.translatable("message.minecraftygo.deck.full"), true);
            return;
        }
        if (!take(player, code)) {
            player.displayClientMessage(Component.translatable("message.minecraftygo.deck.missing", card.name()), true);
            return;
        }
        List<Integer> main = new ArrayList<>(deck.main());
        List<Integer> extraList = new ArrayList<>(deck.extra());
        (extra ? extraList : main).add(code);
        box.set(YgoComponents.DECK.get(), new DeckList(main, extraList));
    }

    private static void deckRemove(ServerPlayer player, ItemStack box, int code) {
        DeckList deck = DeckBoxItem.deck(box);
        List<Integer> main = new ArrayList<>(deck.main());
        List<Integer> extra = new ArrayList<>(deck.extra());
        if (!main.remove((Integer) code) && !extra.remove((Integer) code)) {
            return;
        }
        box.set(YgoComponents.DECK.get(), new DeckList(main, extra));
        ItemStack binder = findBinder(player);
        if (binder.isEmpty()) {
            give(player, CardItem.of(code), 1);
        } else {
            binder.set(YgoComponents.COLLECTION.get(), BinderItem.collection(binder).add(code, 1));
        }
    }

    /** Takes one copy of a card from the first binder that has it, or else from a loose card item. */
    private static boolean take(ServerPlayer player, int code) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(YgoItems.BINDER.get()) && BinderItem.collection(stack).count(code) > 0) {
                stack.set(YgoComponents.COLLECTION.get(), BinderItem.collection(stack).add(code, -1));
                return true;
            }
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(YgoItems.CARD.get()) && CardItem.code(stack) == code) {
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
            if (inventory.getItem(slot).is(YgoItems.BINDER.get())) {
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
