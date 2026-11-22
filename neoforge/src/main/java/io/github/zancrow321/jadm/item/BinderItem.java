package io.github.zancrow321.jadm.item;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.client.ClientScreens;
import io.github.zancrow321.jadm.collection.BinderStore;
import io.github.zancrow321.jadm.collection.CollectionActions;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds a card collection. Right-click to browse it, put loose cards in or take cards out.
 */
public final class BinderItem extends Item {
    public BinderItem(Properties properties) {
        super(properties);
    }

    /**
     * The cards in a binder. A binder bound to a player shows their collection, which the server keeps (see
     * {@link BinderStore}); on the client, and on the server for binders away from their owner, it shows the copy
     * last put on the item.
     */
    public static JadmComponents.CardCollection collection(ItemStack stack) {
        UUID owner = owner(stack);
        if (owner != null && BinderStore.onServer()) {
            return BinderStore.get(owner).cards();
        }
        return stack.getOrDefault(JadmComponents.COLLECTION.get(), JadmComponents.CardCollection.EMPTY);
    }

    private static JadmComponents.CardFoils foilComponent(ItemStack stack) {
        UUID owner = owner(stack);
        if (owner != null && BinderStore.onServer()) {
            return BinderStore.get(owner).foils();
        }
        return stack.getOrDefault(JadmComponents.FOILS.get(), JadmComponents.CardFoils.EMPTY);
    }

    /** The player a binder is bound to, or {@code null} for an ordinary binder. */
    public static UUID owner(ItemStack stack) {
        JadmComponents.BinderOwner owner = stack.get(JadmComponents.BINDER_OWNER.get());
        return owner == null ? null : owner.id();
    }

    /** Whether the player may open and use a binder: it is theirs, or bound to nobody. */
    public static boolean usableBy(ItemStack stack, Player player) {
        UUID owner = owner(stack);
        return owner == null || owner.equals(player.getUUID());
    }

    /** Replaces everything in a binder (its owner's collection, for a bound one). */
    public static void set(ItemStack binder, JadmComponents.CardCollection cards, JadmComponents.CardFoils foils) {
        UUID owner = owner(binder);
        if (owner != null && BinderStore.onServer()) {
            BinderStore.Entry entry = BinderStore.get(owner);
            BinderStore.put(owner, new BinderStore.Entry(entry.name(), cards, foils));
            show(binder);
            BinderStore.changed(owner);
            return;
        }
        binder.set(JadmComponents.COLLECTION.get(), cards);
        binder.set(JadmComponents.FOILS.get(), foils);
    }

    /** Copies a bound binder's collection onto the item, so the client (and the tooltip) sees it. */
    public static void show(ItemStack binder) {
        UUID owner = owner(binder);
        if (owner == null || !BinderStore.onServer()) {
            return;
        }
        BinderStore.Entry entry = BinderStore.get(owner);
        // The store hands out the same record until it changes, so this is cheap when nothing did.
        if (binder.get(JadmComponents.COLLECTION.get()) != entry.cards()) {
            binder.set(JadmComponents.COLLECTION.get(), entry.cards());
        }
        if (binder.get(JadmComponents.FOILS.get()) != entry.foils()) {
            binder.set(JadmComponents.FOILS.get(), entry.foils());
        }
    }

    /**
     * Binds a binder to a player: the cards it holds join their collection, and from then on it shows that
     * collection.
     */
    public static void bindTo(ItemStack binder, Player player) {
        UUID id = player.getUUID();
        JadmComponents.CardCollection own = collection(binder);
        JadmComponents.CardFoils ownFoils = foilComponent(binder);
        BinderStore.Entry entry = BinderStore.get(id);
        Map<Integer, Integer> cards = new HashMap<>(entry.cards().counts());
        own.counts().forEach((code, n) -> cards.merge(code, n, Integer::sum));
        Map<String, Integer> foils = new HashMap<>(entry.foils().copies());
        ownFoils.copies().forEach((key, n) -> foils.merge(key, n, Integer::sum));
        BinderStore.put(id, new BinderStore.Entry(player.getGameProfile().getName(),
                new JadmComponents.CardCollection(cards), new JadmComponents.CardFoils(foils)));
        binder.set(JadmComponents.BINDER_OWNER.get(),
                new JadmComponents.BinderOwner(id, player.getGameProfile().getName()));
        show(binder);
        if (!own.counts().isEmpty()) {
            BinderStore.changed(id);
            player.displayClientMessage(Component.translatable("message.jadm.binder.bound", own.total()), true);
        }
    }

    /**
     * Called for binders the player carries: binds an ordinary one to them if binders are bound to players, and keeps
     * the item's copy of their collection up to date.
     */
    public static void claim(ItemStack binder, Player player) {
        if (!BinderStore.onServer()) {
            return;
        }
        UUID owner = owner(binder);
        if (owner == null) {
            if (JadmServerConfig.BIND_BINDERS.get()) {
                bindTo(binder, player);
            }
        } else if (owner.equals(player.getUUID())) {
            show(binder);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide() && entity instanceof Player player) {
            claim(stack, player);
        }
    }

    /** Copies of one card at one rarity. */
    public record Copies(int code, Rarity rarity, int count) {
    }

    /** Every card in a binder, one entry per card and rarity. */
    public static List<Copies> copies(ItemStack binder) {
        Map<Integer, EnumMap<Rarity, Integer>> foils = foils(binder);
        List<Copies> list = new ArrayList<>();
        collection(binder).counts().forEach((code, total) ->
                split(total, foils.get(code)).forEach((rarity, n) -> list.add(new Copies(code, rarity, n))));
        return list;
    }

    /** How many copies of a card a binder has at each rarity. */
    public static Map<Rarity, Integer> copies(ItemStack binder, int code) {
        return split(collection(binder).count(code), foils(binder).get(code));
    }

    /**
     * Splits a card's copies into its foils and the commons left over. Rarest first, so if the counts ever disagree
     * (a copy taken out by code that doesn't know about foils) the best copies are the ones kept.
     */
    static Map<Rarity, Integer> split(int total, Map<Rarity, Integer> foils) {
        Map<Rarity, Integer> copies = new EnumMap<>(Rarity.class);
        Rarity[] rarities = Rarity.values();
        for (int i = rarities.length - 1; i > 0 && foils != null; i--) {
            int n = Math.min(foils.getOrDefault(rarities[i], 0), total);
            if (n > 0) {
                copies.put(rarities[i], n);
                total -= n;
            }
        }
        if (total > 0) {
            copies.put(Rarity.COMMON, total);
        }
        return copies;
    }

    private static Map<Integer, EnumMap<Rarity, Integer>> foils(ItemStack binder) {
        return foils(foilComponent(binder).copies());
    }

    /** Foil counts keyed {@code "<passcode>:<rarity>"}, by passcode and rarity. */
    static Map<Integer, EnumMap<Rarity, Integer>> foils(Map<String, Integer> copies) {
        Map<Integer, EnumMap<Rarity, Integer>> foils = new HashMap<>();
        copies.forEach((key, n) -> {
            int colon = key.indexOf(':');
            try {
                int code = Integer.parseInt(key.substring(0, colon));
                foils.computeIfAbsent(code, c -> new EnumMap<>(Rarity.class))
                        .merge(Rarity.parse(key.substring(colon + 1)), n, Integer::sum);
            } catch (RuntimeException e) {
                // Not a foil entry this version knows; leave it out.
            }
        });
        return foils;
    }

    /** Puts {@code n} copies of a card at a rarity into a binder ({@code n} may be negative to take them out). */
    public static void add(ItemStack binder, int code, Rarity rarity, int n) {
        JadmComponents.CardCollection collection = collection(binder).add(code, n);
        Map<String, Integer> foils = new HashMap<>(foilComponent(binder).copies());
        if (rarity != Rarity.COMMON) {
            foils.merge(code + ":" + rarity.id(), n, Integer::sum);
        }
        if (collection.count(code) == 0) {
            foils.keySet().removeIf(key -> key.startsWith(code + ":"));
        }
        set(binder, collection, new JadmComponents.CardFoils(foils));
    }

    /** Puts commons into a binder: passcode to copies. */
    public static void addAll(ItemStack binder, Map<Integer, Integer> commons) {
        Map<Integer, Integer> merged = new HashMap<>(collection(binder).counts());
        commons.forEach((code, n) -> merged.merge(code, n, Integer::sum));
        set(binder, new JadmComponents.CardCollection(merged), foilComponent(binder));
    }

    /** Takes up to {@code n} copies of a card at a rarity out of a binder; returns how many it took. */
    public static int remove(ItemStack binder, int code, Rarity rarity, int n) {
        int taken = Math.min(n, copies(binder, code).getOrDefault(rarity, 0));
        if (taken > 0) {
            add(binder, code, rarity, -taken);
        }
        return taken;
    }

    /** Takes one copy of a card out of a binder, commons first so the foils stay; returns its rarity, or null. */
    public static Rarity removeOne(ItemStack binder, int code) {
        for (Rarity rarity : Rarity.values()) {
            if (remove(binder, code, rarity, 1) == 1) {
                return rarity;
            }
        }
        return null;
    }

    /**
     * The binder freshly opened cards go into: the first one the player carries, or {@link ItemStack#EMPTY} without
     * one or with {@code packsIntoBinder} off.
     */
    public static ItemStack forPulls(Player player) {
        return JadmServerConfig.PACKS_INTO_BINDER.get() ? CollectionActions.findBinder(player) : ItemStack.EMPTY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!usableBy(stack, player)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.jadm.binder.foreign",
                        stack.get(JadmComponents.BINDER_OWNER.get()).name()), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide()) {
            ClientScreens.openBinder(hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        JadmComponents.CardCollection c = collection(stack);
        tooltip.add(Component.translatable("item.jadm.binder.tooltip", c.total(), c.counts().size())
                .withStyle(ChatFormatting.GRAY));
        JadmComponents.BinderOwner owner = stack.get(JadmComponents.BINDER_OWNER.get());
        if (owner != null) {
            tooltip.add(Component.translatable("item.jadm.binder.bound", owner.name())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
