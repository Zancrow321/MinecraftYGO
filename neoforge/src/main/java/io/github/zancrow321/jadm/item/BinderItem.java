package io.github.zancrow321.jadm.item;

import io.github.zancrow321.jadm.client.ClientScreens;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds a card collection. Right-click to browse it, put loose cards in or take cards out.
 */
public final class BinderItem extends Item {
    public BinderItem(Properties properties) {
        super(properties);
    }

    public static JadmComponents.CardCollection collection(ItemStack stack) {
        return stack.getOrDefault(JadmComponents.COLLECTION.get(), JadmComponents.CardCollection.EMPTY);
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
    private static Map<Rarity, Integer> split(int total, Map<Rarity, Integer> foils) {
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
        Map<Integer, EnumMap<Rarity, Integer>> foils = new HashMap<>();
        binder.getOrDefault(JadmComponents.FOILS.get(), JadmComponents.CardFoils.EMPTY).copies().forEach((key, n) -> {
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
        binder.set(JadmComponents.COLLECTION.get(), collection);
        Map<String, Integer> foils = new HashMap<>(
                binder.getOrDefault(JadmComponents.FOILS.get(), JadmComponents.CardFoils.EMPTY).copies());
        if (rarity != Rarity.COMMON) {
            foils.merge(code + ":" + rarity.id(), n, Integer::sum);
        }
        if (collection.count(code) == 0) {
            foils.keySet().removeIf(key -> key.startsWith(code + ":"));
        }
        binder.set(JadmComponents.FOILS.get(), new JadmComponents.CardFoils(foils));
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

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
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
    }
}
