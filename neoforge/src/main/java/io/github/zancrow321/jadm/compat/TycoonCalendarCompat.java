package io.github.zancrow321.jadm.compat;

import io.github.zancrow321.jadm.Jadm;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.LocalDate;

/**
 * Tycoon Calendar is optional: one calendar date for the whole world. Its API is called by reflection (the mod is
 * All Rights Reserved and published nowhere to compile against), so nothing of it is resolved without it.
 */
public final class TycoonCalendarCompat {
    public static final boolean LOADED = ModList.get().isLoaded("tycooncalendar");
    private static final String API = "com.rhille.tycooncalendar.api.CalendarAPI";

    /** {@code CalendarAPI.today(MinecraftServer)} and {@code startConfirmed(MinecraftServer)}, or null. */
    private static volatile MethodHandle today;
    private static volatile MethodHandle confirmed;
    /** Set once the API couldn't be found or failed, so it isn't tried (and logged) again. */
    private static volatile boolean broken;

    private TycoonCalendarCompat() {
    }

    /**
     * @return the world's calendar date, or {@code null} without Tycoon Calendar or while its start date isn't
     *         settled yet (on a new server the first operator is asked for it)
     */
    public static LocalDate today(MinecraftServer server) {
        if (!LOADED || broken) {
            return null;
        }
        try {
            if (today == null) {
                Class<?> api = Class.forName(API);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                confirmed = lookup.findStatic(api, "startConfirmed", MethodType.methodType(boolean.class,
                        MinecraftServer.class));
                today = lookup.findStatic(api, "today", MethodType.methodType(LocalDate.class,
                        MinecraftServer.class));
            }
            if (!(boolean) confirmed.invoke(server)) {
                return null;
            }
            return (LocalDate) today.invoke(server);
        } catch (Throwable e) {
            broken = true;
            Jadm.LOGGER.warn("Tycoon Calendar is installed but its date couldn't be read; sets unlock by "
                    + "[progression] daysPerSet instead", e);
            return null;
        }
    }
}
