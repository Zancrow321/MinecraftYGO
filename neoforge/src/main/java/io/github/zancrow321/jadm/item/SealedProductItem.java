package io.github.zancrow321.jadm.item;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.progression.StarterDecks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
 * its cards in your binder, a tin gives its promo card (into your binder too) and three packs.
 */
public final class SealedProductItem extends Item {
    /** Packs in a tin. */
    public static final int TIN_PACKS = 3;

    public SealedProductItem(Properties properties) {
        super(properties);
    }

    /** The sealed item for a deck or tin product: a structure deck or a tin. */
    public static ItemStack of(Products.Product product) {
        ItemStack stack = new ItemStack(product.kind() == Products.Kind.TIN ? JadmItems.TIN.get()
                : JadmItems.STRUCTURE_DECK.get());
        stack.set(JadmComponents.PACK_SET.get(), product.id());
        return stack;
    }

    /** Whether a product is sold sealed as a deck or tin. */
    public static boolean sealed(Products.Product product) {
        if (product.isCustom()) {
            return product.kind() == Products.Kind.TIN || product.kind() == Products.Kind.DECK;
        }
        return product.kind() == Products.Kind.TIN
                || product.kind() == Products.Kind.DECK && product.cards().size() >= 20;
    }

    public static Products.Product product(ItemStack stack) {
        String id = stack.get(JadmComponents.PACK_SET.get());
        return id == null ? null : JadmData.products().get(id);
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
        boolean dated = !product.isCustom() || product.custom().dated();
        tooltip.add(Component.literal(product.code() + (dated ? " · " + product.date() : ""))
                .withStyle(ChatFormatting.GRAY));
        boolean tin = product.kind() == Products.Kind.TIN;
        tooltip.add(Component.translatable(tin ? "item.jadm.tin.tooltip" : "item.jadm.structure_deck.tooltip",
                tin ? product.cards().size() : deckSize(product), tin ? tinPacks(product).size() : TIN_PACKS)
                .withStyle(ChatFormatting.GRAY));
        BoosterPackItem.description(product, tooltip);
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
        Deck deck = StarterDecks.contentsOf(product);
        return deck.main().size() + deck.extra().size();
    }

    /** Every card of the deck goes into the player's first binder, or loose into the inventory without one. */
    private static void openDeck(ServerPlayer player, Products.Product product) {
        Map<Integer, Integer> counts = new HashMap<>();
        Deck deck = StarterDecks.contentsOf(product);
        deck.main().forEach(code -> counts.merge(code, 1, Integer::sum));
        deck.extra().forEach(code -> counts.merge(code, 1, Integer::sum));
        ItemStack binder = BinderItem.forPulls(player);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (!binder.isEmpty()) {
            Map<Integer, Integer> merged = new HashMap<>(BinderItem.collection(binder).counts());
            counts.forEach((code, n) -> merged.merge(code, n, Integer::sum));
            binder.set(JadmComponents.COLLECTION.get(), new JadmComponents.CardCollection(merged));
            player.sendSystemMessage(Component.translatable("message.jadm.structure_deck.binder", total,
                    product.name()).withStyle(ChatFormatting.GREEN));
            return;
        }
        counts.forEach((code, n) -> give(player, CardItem.of(code), n));
        player.sendSystemMessage(Component.translatable(JadmServerConfig.PACKS_INTO_BINDER.get()
                ? "message.jadm.structure_deck.loose" : "message.jadm.structure_deck.inventory", total,
                product.name()).withStyle(ChatFormatting.GREEN));
    }

    /** A random card of the tin with its printed rarity, and its packs. */
    private static void openTin(ServerPlayer player, Products.Product tin) {
        var random = player.getRandom();
        List<Products.Printing> promos = tin.cards().stream()
                .filter(p -> JadmData.cards().card(p.code()) != null).toList();
        int count = tin.isCustom() ? tin.custom().promos() : 1;
        List<Products.Printing> left = new ArrayList<>(promos);
        MutableComponent cards = Component.empty();
        boolean filed = false;
        for (int n = 0; n < count && !promos.isEmpty(); n++) {
            // Different cards while there are any left, then any again.
            if (left.isEmpty()) {
                left.addAll(promos);
            }
            Products.Printing promo = left.remove(random.nextInt(left.size()));
            int code = JadmData.pullArtwork(promo.code(), new java.util.Random(random.nextLong()));
            BoosterSets.Rarity rarity = BoosterSets.Rarity.ofPrinted(promo.rarity());
            ItemStack binder = BinderItem.forPulls(player);
            if (!binder.isEmpty()) {
                BinderItem.add(binder, code, rarity, 1);
                filed = true;
            } else {
                give(player, CardItem.of(code, rarity), 1);
            }
            MutableComponent named = Component.literal(JadmData.cards().card(code).name() + " (" + promo.rarity());
            if (JadmData.isAltArt(code)) {
                named.append(", ").append(Component.translatable("item.jadm.card.alt_art.short"));
            }
            cards.append(n == 0 ? Component.empty() : Component.literal(", ")).append(named.append(")"));
        }
        Component card = cards.getSiblings().isEmpty() ? Component.literal("-") : cards;
        List<String> packs = tinPacks(tin);
        for (String set : packs) {
            give(player, BoosterPackItem.of(set), 1);
        }
        player.sendSystemMessage(Component.translatable(filed ? "message.jadm.tin.opened.binder"
                : "message.jadm.tin.opened", tin.name(), card, packs.size()).withStyle(ChatFormatting.GREEN));
    }

    /**
     * The packs in a tin: three of its own Mega Pack if it has one, else one each of the newest boosters out by
     * its release (the newest again if there are fewer).
     */
    public static List<String> tinPacks(Products.Product tin) {
        List<String> packs = new ArrayList<>();
        if (tin.isCustom() && !tin.custom().tinPacks().isEmpty()) {
            // The packs it names, each a set code or product id; one that names no booster is left out.
            for (String key : tin.custom().tinPacks()) {
                Products.Product p = JadmData.products().resolve(key);
                if (p != null && JadmData.set(p.id()) != null) {
                    packs.add(p.id());
                }
            }
            return packs;
        }
        if (JadmData.allSets().get(tin.id()) != null) {
            for (int i = 0; i < TIN_PACKS; i++) {
                packs.add(tin.id());
            }
            return packs;
        }
        List<Products.Product> all = JadmData.products().products();
        for (int i = all.size() - 1; i >= 0 && packs.size() < TIN_PACKS; i--) {
            Products.Product p = all.get(i);
            if (p.kind() == Products.Kind.BOOSTER && !p.date().isAfter(tin.date())
                    && JadmData.allSets().get(p.id()) != null) {
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
