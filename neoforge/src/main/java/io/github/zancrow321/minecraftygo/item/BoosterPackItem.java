package io.github.zancrow321.minecraftygo.item;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.data.BoosterSets;
import io.github.zancrow321.minecraftygo.network.PackOpenedPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * A sealed booster pack. Right-click to open it: nine cards from its set, one of them rare or better.
 */
public final class BoosterPackItem extends Item {
    public BoosterPackItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(String setId) {
        ItemStack stack = new ItemStack(YgoItems.BOOSTER_PACK.get());
        if (setId != null) {
            stack.set(YgoComponents.PACK_SET.get(), setId);
        }
        return stack;
    }

    /** The pack's set, or {@code null} for a pack from a random era set. */
    public static BoosterSets.BoosterSet set(ItemStack stack) {
        String id = stack.get(YgoComponents.PACK_SET.get());
        return id == null ? null : YgoData.sets().get(id);
    }

    @Override
    public Component getName(ItemStack stack) {
        BoosterSets.BoosterSet set = set(stack);
        return set == null ? super.getName(stack)
                : Component.translatable("item.minecraftygo.booster_pack.of", set.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BoosterSets.BoosterSet set = set(stack);
        tooltip.add(Component.translatable(set == null ? "item.minecraftygo.booster_pack.random"
                : "item.minecraftygo.booster_pack.tooltip", set == null ? "" : set.code()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        BoosterSets.BoosterSet set = set(stack);
        if (set == null) {
            set = randomSet(level.getRandom());
        }
        if (set == null) {
            return InteractionResultHolder.fail(stack);
        }
        RandomSource random = level.getRandom();
        List<BoosterSets.Card> cards = set.open(new java.util.Random(random.nextLong()));
        List<YgoComponents.CardStack> shown = new ArrayList<>();
        for (BoosterSets.Card card : cards) {
            ItemStack item = CardItem.of(card.code(), card.rarity());
            if (!player.getInventory().add(item)) {
                player.drop(item, false);
            }
            shown.add(new YgoComponents.CardStack(card.code(), card.rarity().id()));
        }
        stack.consume(1, player);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 0.8f);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new PackOpenedPayload(set.name(), shown));
        }
        return InteractionResultHolder.consume(stack);
    }

    /** A random booster set, each as likely as its number of cards. */
    public static BoosterSets.BoosterSet randomSet(RandomSource random) {
        List<BoosterSets.BoosterSet> sets = List.copyOf(YgoData.sets().sets().values());
        if (sets.isEmpty()) {
            return null;
        }
        int total = sets.stream().mapToInt(s -> s.cards().size()).sum();
        int pick = random.nextInt(total);
        for (BoosterSets.BoosterSet s : sets) {
            pick -= s.cards().size();
            if (pick < 0) {
                return s;
            }
        }
        return sets.get(0);
    }
}
