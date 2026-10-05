package io.github.zancrow321.minecraftygo.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * Curios is optional. This class is only loaded once {@link #LOADED} says Curios is there, so its API classes
 * are never resolved without it.
 */
public final class CuriosCompat {
    public static final boolean LOADED = ModList.get().isLoaded("curios");

    private CuriosCompat() {
    }

    /** @return the first {@code item} in any of the entity's curio slots, or {@link ItemStack#EMPTY} */
    public static ItemStack find(LivingEntity entity, Item item) {
        if (!LOADED) {
            return ItemStack.EMPTY;
        }
        return Api.find(entity, item);
    }

    private static final class Api {
        static ItemStack find(LivingEntity entity, Item item) {
            return CuriosApi.getCuriosInventory(entity)
                    .flatMap(inventory -> inventory.findFirstCurio(item))
                    .map(SlotResult::stack)
                    .orElse(ItemStack.EMPTY);
        }
    }
}
