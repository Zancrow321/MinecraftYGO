package io.github.zancrow321.minecraftygo;

import com.mojang.logging.LogUtils;
import io.github.zancrow321.minecraftygo.engine.OcgCore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(MinecraftYgo.MOD_ID)
public final class MinecraftYgo {
    public static final String MOD_ID = "minecraftygo";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecraftYgo(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(YgoCommands::register);
    }

    private void onServerStarting(ServerStartingEvent event) {
        // Duels run on the server, so load the engine there and fail loudly but harmlessly if it's unavailable.
        try {
            LOGGER.info("Loaded OCG-Core {}", OcgCore.get().version());
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            LOGGER.error("Could not load OCG-Core; duels are disabled on this server", e);
        }
    }
}
