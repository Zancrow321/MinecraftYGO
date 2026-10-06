package io.github.zancrow321.minecraftygo.item;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.data.BoosterSets;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.data.Products;
import io.github.zancrow321.minecraftygo.progression.StarterDecks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A sealed structure or starter deck, or a collector's tin, of one TCG product. Right-click to open it: a deck puts
 * its cards in your binder, a tin gives its promo card and three packs.
 */
public final class SealedProductItem extends Item {
    /** Packs in a tin. */
    public static final int TIN_PACKS = 3;

    public SealedProductItem(Properties properties) {
        super(properties);
    }

    /** The sealed item for a deck or tin product: a structure deck or a tin. */
    public static ItemStack of(Products.Product product) {
        ItemStack stack = new ItemStack(product.kind() == Products.Kind.TIN ? YgoItems.TIN.get()
                : YgoItems.STRUCTURE_DECK.get());
        stack.set(YgoComponents.PACK_SET.get(), product.id());
        return stack;
    }

    /** Whether a product is sold sealed as a deck or tin. */
    public static boolean sealed(Products.Product product) {
        return product.kind() == Products.Kind.TIN
                || product.kind() == Products.Kind.DECK && product.cards().size() >= 20;
    }

    public static Products.Product product(ItemStack stack) {
        String id = stack.get(YgoComponents.PACK_SET.get());
        return id == null ? null : YgoData.products().get(id);
    }

    @Override
    public Component getName(ItemStack stack) {
        Products.Product product = product(stack);
        return product == null ? super.getName(stack) : Component.literal(product.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Products.Product product = product(stack);
        if (product == null) {
            return;
        }
        tooltip.add(Component.literal(product.code() + " · " + product.date()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(product.kind() == Products.Kind.TIN ? "item.minecraftygo.tin.tooltip"
                : "item.minecraftygo.structure_deck.tooltip", product.kind() == Products.Kind.TIN
                ? product.cards().size() : deckSize(product), TIN_PACKS)
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Products.Product product = product(stack);
        if (level.isClientSide() || product == null || !(player instanceof ServerPlayer serverPlayer)) {
            return product == null ? InteractionResultHolder.fail(stack) : InteractionResultHolder.success(stack);
        }
        if (product.kind() == Products.Kind.TIN) {
            openTin(serverPlayer, product);
        } else {
            openDeck(serverPlayer, product);
        }
        stack.consume(1, player);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 1,
                1.2f);
        return InteractionResultHolder.consume(stack);
    }

    private static int deckSize(Products.Product product) {
        Deck deck = StarterDecks.deckOf(product, null);
        return deck.main().size() + deck.extra().size();
    }

    /** Every card of the deck goes into the player's first binder, or loose into the inventory without one. */
    private static void openDeck(ServerPlayer player, Products.Product product) {
        Map<Integer, Integer> counts = new HashMap<>();
        Deck deck = StarterDecks.deckOf(product, null);
        deck.main().forEach(code -> counts.merge(code, 1, Integer::sum));
        deck.extra().forEach(code -> counts.merge(code, 1, Integer::sum));
        ItemStack binder = ItemStack.EMPTY;
        for (ItemStack item : player.getInventory().items) {
            if (item.is(YgoItems.BINDER.get())) {
                binder = item;
                break;
            }
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (!binder.isEmpty()) {
            Map<Integer, Integer> merged = new HashMap<>(BinderItem.collection(binder).counts());
            counts.forEach((code, n) -> merged.merge(code, n, Integer::sum));
            binder.set(YgoComponents.COLLECTION.get(), new YgoComponents.CardCollection(merged));
            player.sendSystemMessage(Component.translatable("message.minecraftygo.structure_deck.binder", total,
                    product.name()).withStyle(ChatFormatting.GREEN));
            return;
        }
        counts.forEach((code, n) -> give(player, CardItem.of(code), n));
        player.sendSystemMessage(Component.translatable("message.minecraftygo.structure_deck.loose", total,
                product.name()).withStyle(ChatFormatting.GREEN));
    }

    /** A random card of the tin with its printed rarity, and its packs. */
    private static void openTin(ServerPlayer player, Products.Product tin) {
        var random = player.getRandom();
        List<Products.Printing> promos = tin.cards().stream()
                .filter(p -> YgoData.cards().card(p.code()) != null).toList();
        Component card = Component.literal("-");
        if (!promos.isEmpty()) {
            Products.Printing promo = promos.get(random.nextInt(promos.size()));
            give(player, CardItem.of(promo.code(), BoosterSets.Rarity.ofPrinted(promo.rarity())), 1);
            card = Component.literal(YgoData.cards().card(promo.code()).name() + " (" + promo.rarity() + ")");
        }
        List<String> packs = tinPacks(tin);
        for (String set : packs) {
            give(player, BoosterPackItem.of(set), 1);
        }
        player.sendSystemMessage(Component.translatable("message.minecraftygo.tin.opened", tin.name(), card,
                packs.size()).withStyle(ChatFormatting.GREEN));
    }

    /**
     * The packs in a tin: three of its own Mega Pack if it has one, else one each of the newest boosters out by
     * its release (the newest again if there are fewer).
     */
    public static List<String> tinPacks(Products.Product tin) {
        List<String> packs = new ArrayList<>();
        if (YgoData.allSets().get(tin.id()) != null) {
            for (int i = 0; i < TIN_PACKS; i++) {
                packs.add(tin.id());
            }
            return packs;
        }
        List<Products.Product> all = YgoData.products().products();
        for (int i = all.size() - 1; i >= 0 && packs.size() < TIN_PACKS; i--) {
            Products.Product p = all.get(i);
            if (p.kind() == Products.Kind.BOOSTER && !p.date().isAfter(tin.date())
                    && YgoData.allSets().get(p.id()) != null) {
                packs.add(p.id());
            }
        }
        while (!packs.isEmpty() && packs.size() < TIN_PACKS) {
            packs.add(packs.get(0));
        }
        return packs;
    }

    private static void give(ServerPlayer player, ItemStack item, int count) {
        item.setCount(count);
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
    }
}
