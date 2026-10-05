package io.github.zancrow321.minecraftygo.compat.figura;

import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/**
 * The mod's side of the optional Figura integration. Safe to call without Figura: the classes that touch Figura
 * load only when it is installed.
 */
public final class FiguraCompat {
    private static final boolean LOADED = ModList.get().isLoaded("figura");

    private FiguraCompat() {
    }

    /** Client setup: hooks the {@code ygo} events into Figura. */
    public static void init() {
        if (LOADED) {
            FiguraBridge.registerEvents();
        }
    }

    /** The local player's duel view changed; fires the {@code ygo} events on their avatar. */
    public static void onView(DuelView previous, DuelView next) {
        if (LOADED) {
            FiguraBridge.onView(previous, next);
        }
    }

    /** Whether this entity's avatar asked to hide the duel disk ({@code ygo:setDiskVisible(false)}). */
    public static boolean diskHidden(Entity entity) {
        return LOADED && YgoLuaApi.diskHidden(entity.getUUID());
    }
}
