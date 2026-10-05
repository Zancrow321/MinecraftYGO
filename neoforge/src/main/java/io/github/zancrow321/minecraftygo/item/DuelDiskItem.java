package io.github.zancrow321.minecraftygo.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Worn on the left arm (the Curios "duel_disk" slot, or the off hand). Right-clicking another duelist with it
 * challenges them, and it unfolds when the duel starts.
 */
public final class DuelDiskItem extends Item {
    public DuelDiskItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.minecraftygo.duel_disk.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
