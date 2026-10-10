package io.github.zancrow321.jadm.item;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.network.PackOpenedPayload;
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
 * A sealed booster pack. Right-click to open it: cards from its set as its pack format says (nine for a core
 * booster, one of them rare or better).
 */
public final class BoosterPackItem extends Item {
    public BoosterPackItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(String setId) {
        ItemStack stack = new ItemStack(JadmItems.BOOSTER_PACK.get());
        if (setId != null) {
            stack.set(JadmComponents.PACK_SET.get(), setId);
        }
        return stack;
    }

    /** The pack's set, or {@code null} for a pack from a random era set. */
    public static BoosterSets.BoosterSet set(ItemStack stack) {
        String id = stack.get(JadmComponents.PACK_SET.get());
        return id == null ? null : JadmData.set(id);
    }

    /** The product a pack is from, whether or not its set is in this server's pool; {@code null} if random. */
    private static Products.Product product(ItemStack stack) {
        String id = stack.get(JadmComponents.PACK_SET.get());
        return id == null ? null : JadmData.products().get(id);
    }

    @Override
    public Component getName(ItemStack stack) {
        Products.Product product = product(stack);
        return product == null ? super.getName(stack)
                : Component.translatable("item.jadm.booster_pack.of", product.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Products.Product product = product(stack);
        BoosterSets.BoosterSet set = set(stack);
        tooltip.add(Component.translatable(product == null ? "item.jadm.booster_pack.random"
                : "item.jadm.booster_pack.tooltip", product == null ? "" : product.code(),
                set == null ? BoosterSets.PACK_SIZE : set.profile().size()).withStyle(ChatFormatting.GRAY));
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
        List<JadmComponents.CardStack> shown = new ArrayList<>();
        for (BoosterSets.Card card : cards) {
            ItemStack item = CardItem.of(card.code(), card.rarity());
            if (!player.getInventory().add(item)) {
                player.drop(item, false);
            }
            shown.add(new JadmComponents.CardStack(card.code(), card.rarity().id()));
        }
        stack.consume(1, player);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 0.8f);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new PackOpenedPayload(set.name(), shown));
            io.github.zancrow321.jadm.cosmetics.PlayerCosmetics.openedPack(serverPlayer);
            io.github.zancrow321.jadm.quest.Quests.packOpened(serverPlayer);
        }
        return InteractionResultHolder.consume(stack);
    }

    /**
     * A random booster set for loot and random packs: in the modeled pool each as likely as its number of cards,
     * otherwise newer sets more often (the newest {@code n} times as likely as the oldest of {@code n}).
     */
    public static BoosterSets.BoosterSet randomSet(RandomSource random) {
        List<BoosterSets.BoosterSet> sets = List.copyOf(JadmData.sets().sets().values());
        if (sets.isEmpty()) {
            return null;
        }
        boolean byAge = JadmData.poolMode() != io.github.zancrow321.jadm.engine.data.PoolMode.MODELED;
        long total = 0;
        for (int i = 0; i < sets.size(); i++) {
            total += byAge ? i + 1 : sets.get(i).cards().size();
        }
        long pick = (long) (random.nextDouble() * total);
        for (int i = 0; i < sets.size(); i++) {
            pick -= byAge ? i + 1 : sets.get(i).cards().size();
            if (pick < 0) {
                return sets.get(i);
            }
        }
        return sets.get(sets.size() - 1);
    }
}
