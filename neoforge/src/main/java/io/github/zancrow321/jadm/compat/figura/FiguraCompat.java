package io.github.zancrow321.jadm.compat.figura;

import io.github.zancrow321.jadm.engine.duel.DuelView;
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

    /** Client setup: hooks the {@code jadm} events into Figura. */
    public static void init() {
        if (LOADED) {
            FiguraBridge.registerEvents();
        }
    }

    /** The local player's duel view changed; fires the {@code jadm} events on their avatar. */
    public static void onView(DuelView previous, DuelView next) {
        if (LOADED) {
            FiguraBridge.onView(previous, next);
        }
    }

    /** Whether this entity's avatar asked to hide the duel disk ({@code jadm:setDiskVisible(false)}). */
    public static boolean diskHidden(Entity entity) {
        return LOADED && JadmLuaApi.diskHidden(entity.getUUID());
    }
}
