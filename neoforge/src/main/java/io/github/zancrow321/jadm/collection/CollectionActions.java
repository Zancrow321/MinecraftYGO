package io.github.zancrow321.jadm.collection;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.compat.CuriosCompat;
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
import net.minecraft.world.entity.player.Player;
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

    /** The item at a {@link CollectionActionPayload#source}: a hand, the Curios binder slot or an inventory slot. */
    public static ItemStack at(Player player, int source) {
        return switch (source) {
            case CollectionActionPayload.MAIN_HAND -> player.getMainHandItem();
            case CollectionActionPayload.OFF_HAND -> player.getOffhandItem();
            case CollectionActionPayload.CURIO -> CuriosCompat.find(player, JadmItems.BINDER.get());
            default -> source >= 0 && source < player.getInventory().getContainerSize()
                    ? player.getInventory().getItem(source) : ItemStack.EMPTY;
        };
    }

    public static void handle(ServerPlayer player, CollectionActionPayload action) {
        ItemStack held = at(player, action.source());
        if (held.is(JadmItems.BINDER.get()) && !BinderItem.usableBy(held, player)) {
            return;
        }
        switch (action.action()) {
            case DEPOSIT_ALL -> {
                if (held.is(JadmItems.BINDER.get())) {
                    BinderItem.claim(held, player);
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
                    deckAdd(player, held, action.code(), action.rarity());
                }
            }
            case DECK_REMOVE -> {
                if (held.is(JadmItems.DECK_BOX.get())) {
                    deckRemove(player, held, action.code(), action.rarity());
                }
            }
            case DECK_CLEAR -> {
                if (held.is(JadmItems.DECK_BOX.get())) {
                    for (int code : List.copyOf(DeckBoxItem.deck(held).main())) {
                        deckRemove(player, held, code, null);
                    }
                    for (int code : List.copyOf(DeckBoxItem.deck(held).extra())) {
                        deckRemove(player, held, code, null);
                    }
                }
            }
            case DECK_BOSS -> {
                if (held.is(JadmItems.DECK_BOX.get())) {
                    toggleBoss(player, held, action.code());
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

    /** Moves a copy of a card at {@code rarity} into the deck; {@code null} takes the rarest copy the player has. */
    private static void deckAdd(ServerPlayer player, ItemStack box, int code, Rarity rarity) {
        var card = JadmData.cards().card(code);
        if (card == null) {
            return;
        }
        DeckList deck = DeckBoxItem.deck(box);
        boolean extra = DeckRules.isExtra(card);
        // Alternate artworks count as copies of their original.
        int original = JadmData.original(code);
        int copies = (int) (deck.main().stream().filter(c -> JadmData.original(c) == original).count()
                + deck.extra().stream().filter(c -> JadmData.original(c) == original).count());
        if (!JadmData.playable(player).test(code)) {
            player.displayClientMessage(Component.translatable("message.jadm.deck.locked", card.name()), true);
            return;
        }
        int limit = Math.min(JadmData.banlist(player).limit(code), JadmData.banlist(player).limit(original));
        if (copies >= limit) {
            player.displayClientMessage(Component.translatable(limit == 0 ? "message.jadm.deck.forbidden"
                    : "message.jadm.deck.limit", card.name(), limit), true);
            return;
        }
        if (extra ? deck.extra().size() >= DeckRules.EXTRA_MAX : deck.main().size() >= DeckRules.MAIN_MAX) {
            player.displayClientMessage(Component.translatable("message.jadm.deck.full"), true);
            return;
        }
        Rarity taken = take(player, code, rarity == null ? best(player, code) : rarity);
        if (taken == null) {
            player.displayClientMessage(Component.translatable("message.jadm.deck.missing", card.name()), true);
            return;
        }
        DeckBoxItem.add(box, code, taken, extra);
    }

    /** Marks a monster in the deck as its boss, or unmarks it if it already is the boss. */
    private static void toggleBoss(ServerPlayer player, ItemStack box, int code) {
        var card = JadmData.cards().card(code);
        DeckList deck = DeckBoxItem.deck(box);
        if (card == null || !card.is(io.github.zancrow321.jadm.engine.OcgConstants.TYPE_MONSTER)
                || !deck.main().contains(code) && !deck.extra().contains(code)) {
            return;
        }
        int original = JadmData.original(code);
        if (DeckBoxItem.boss(box) == original) {
            box.remove(JadmComponents.DECK_BOSS.get());
            player.displayClientMessage(Component.translatable("message.jadm.deck.boss_cleared", card.name()), true);
        } else {
            box.set(JadmComponents.DECK_BOSS.get(), original);
            player.displayClientMessage(Component.translatable("message.jadm.deck.boss_set", card.name()), true);
        }
    }

    /** Moves a copy of a card at {@code rarity} back out of the deck; {@code null} takes a common first. */
    private static void deckRemove(ServerPlayer player, ItemStack box, int code, Rarity rarity) {
        Rarity taken = DeckBoxItem.remove(box, code, rarity);
        if (taken == null) {
            return;
        }
        // The boss goes with its last copy, so adding the card again later doesn't bring the mark back.
        if (box.has(JadmComponents.DECK_BOSS.get()) && DeckBoxItem.boss(box) == 0) {
            box.remove(JadmComponents.DECK_BOSS.get());
        }
        ItemStack binder = findBinder(player);
        if (binder.isEmpty()) {
            give(player, CardItem.of(code, taken), 1);
        } else {
            BinderItem.add(binder, code, taken, 1);
        }
    }

    /** The rarest copy of a card the player carries, in a binder or loose, or {@code null}. */
    private static Rarity best(ServerPlayer player, int code) {
        Rarity best = null;
        for (ItemStack stack : carried(player)) {
            List<Rarity> have = new ArrayList<>();
            if (stack.is(JadmItems.BINDER.get()) && BinderItem.usableBy(stack, player)) {
                BinderItem.copies(stack, code).forEach((r, n) -> have.add(r));
            } else if (stack.is(JadmItems.CARD.get()) && CardItem.code(stack) == code) {
                have.add(CardItem.rarity(stack));
            }
            for (Rarity r : have) {
                if (best == null || r.compareTo(best) > 0) {
                    best = r;
                }
            }
        }
        return best;
    }

    /**
     * Takes one copy of a card at a rarity from the first binder that has it, or else from a loose card item.
     *
     * @return the rarity taken, or {@code null} if the player has no such copy
     */
    private static Rarity take(ServerPlayer player, int code, Rarity rarity) {
        if (rarity == null) {
            return null;
        }
        for (ItemStack stack : binders(player)) {
            if (BinderItem.remove(stack, code, rarity, 1) == 1) {
                return rarity;
            }
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(JadmItems.CARD.get()) && CardItem.code(stack) == code && CardItem.rarity(stack) == rarity) {
                stack.shrink(1);
                return rarity;
            }
        }
        return null;
    }

    /**
     * The first binder the player may use: in the inventory (off hand included), else in the Curios binder slot;
     * {@link ItemStack#EMPTY} without one. Binders bound to someone else are passed over.
     */
    public static ItemStack findBinder(Player player) {
        List<ItemStack> binders = binders(player);
        if (binders.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack binder = binders.get(0);
        // An ordinary binder that would be bound this tick anyway is bound now, so cards join the right collection.
        BinderItem.claim(binder, player);
        return binder;
    }

    /**
     * Every binder the player may use, inventory first, then the Curios slot. Bound binders show one shared
     * collection, so only the first of the player's own is listed: counting it twice would count their cards twice.
     */
    public static List<ItemStack> binders(Player player) {
        List<ItemStack> binders = new ArrayList<>();
        boolean own = false;
        for (ItemStack stack : carried(player)) {
            if (!stack.is(JadmItems.BINDER.get()) || !BinderItem.usableBy(stack, player)) {
                continue;
            }
            if (BinderItem.owner(stack) != null) {
                if (own) {
                    continue;
                }
                own = true;
            }
            binders.add(stack);
        }
        return binders;
    }

    /** The inventory's items (off hand included), then the binder in the Curios slot if there is one. */
    private static List<ItemStack> carried(Player player) {
        List<ItemStack> items = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            items.add(inventory.getItem(slot));
        }
        ItemStack curio = CuriosCompat.find(player, JadmItems.BINDER.get());
        if (!curio.isEmpty()) {
            items.add(curio);
        }
        return items;
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
