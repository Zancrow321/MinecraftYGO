package io.github.zancrow321.minecraftygo;

import com.mojang.logging.LogUtils;
import io.github.zancrow321.minecraftygo.client.YgoClientConfig;
import io.github.zancrow321.minecraftygo.duel.DuelManager;
import io.github.zancrow321.minecraftygo.engine.OcgCore;
import io.github.zancrow321.minecraftygo.entity.YgoEntities;
import io.github.zancrow321.minecraftygo.network.YgoNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(MinecraftYgo.MOD_ID)
public final class MinecraftYgo {
    public static final String MOD_ID = "minecraftygo";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecraftYgo(IEventBus modBus, ModContainer container) {
        YgoEntities.register(modBus);
        modBus.addListener(YgoNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerConfig(ModConfig.Type.CLIENT, YgoClientConfig.SPEC);
        }
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> DuelManager.shutdown());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> DuelManager.get(event.getServer()).tick());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                DuelManager.get(player.server).onLogout(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(YgoCommands::register);
    }

    private void onServerStarting(ServerStartingEvent event) {
        // Duels run on the server, so load the engine there and fail loudly but harmlessly if it's unavailable.
        try {
            LOGGER.info("Loaded OCG-Core {} with {} cards", OcgCore.get().version(), YgoData.cards().all().size());
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            LOGGER.error("Could not load OCG-Core; duels are disabled on this server", e);
        }
    }
}
