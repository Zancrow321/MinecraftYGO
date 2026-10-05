package io.github.zancrow321.minecraftygo.item;

import io.github.zancrow321.minecraftygo.client.ClientScreens;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Holds a card collection. Right-click to browse it, put loose cards in or take cards out.
 */
public final class BinderItem extends Item {
    public BinderItem(Properties properties) {
        super(properties);
    }

    public static YgoComponents.CardCollection collection(ItemStack stack) {
        return stack.getOrDefault(YgoComponents.COLLECTION.get(), YgoComponents.CardCollection.EMPTY);
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
        YgoComponents.CardCollection c = collection(stack);
        tooltip.add(Component.translatable("item.minecraftygo.binder.tooltip", c.total(), c.counts().size())
                .withStyle(ChatFormatting.GRAY));
    }
}
