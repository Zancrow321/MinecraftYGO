package io.github.zancrow321.jadm.duel;

import io.github.zancrow321.jadm.compat.CuriosCompat;
import io.github.zancrow321.jadm.item.JadmItems;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Where a player keeps their duel disk.
 */
public final class DuelDisks {
    private DuelDisks() {
    }

    /**
     * The disk worn on the left arm: in the Curios slot, or held in the hand of the left arm (the off hand for
     * right-handed players).
     *
     * @return the disk, or {@link ItemStack#EMPTY}
     */
    public static ItemStack worn(Player player) {
        ItemStack curio = CuriosCompat.find(player, JadmItems.DUEL_DISK.get());
        if (!curio.isEmpty()) {
            return curio;
        }
        ItemStack leftHand = player.getMainArm() == HumanoidArm.LEFT ? player.getMainHandItem() : player.getOffhandItem();
        return leftHand.is(JadmItems.DUEL_DISK.get()) ? leftHand : ItemStack.EMPTY;
    }

    /** Whether the player wears or holds a disk, which is all a challenge needs. */
    public static boolean has(Player player) {
        return !worn(player).isEmpty() || player.getMainHandItem().is(JadmItems.DUEL_DISK.get())
                || player.getOffhandItem().is(JadmItems.DUEL_DISK.get());
    }
}
