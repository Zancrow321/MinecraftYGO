package io.github.zancrow321.minecraftygo;

import com.mojang.logging.LogUtils;
import io.github.zancrow321.minecraftygo.arena.DuelArena;
import io.github.zancrow321.minecraftygo.arena.DuelDome;
import io.github.zancrow321.minecraftygo.client.YgoClientConfig;
import io.github.zancrow321.minecraftygo.cosmetics.PlayerCosmetics;
import io.github.zancrow321.minecraftygo.duel.DuelManager;
import io.github.zancrow321.minecraftygo.progression.Progress;
import io.github.zancrow321.minecraftygo.tournament.TournamentManager;
import io.github.zancrow321.minecraftygo.engine.OcgCore;
import io.github.zancrow321.minecraftygo.duel.DuelDisks;
import io.github.zancrow321.minecraftygo.entity.YgoEntities;
import io.github.zancrow321.minecraftygo.item.YgoComponents;
import io.github.zancrow321.minecraftygo.item.YgoItems;
import io.github.zancrow321.minecraftygo.loot.RandomCardFunction;
import io.github.zancrow321.minecraftygo.village.YgoVillagers;
import io.github.zancrow321.minecraftygo.network.YgoNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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
        YgoItems.register(modBus);
        YgoComponents.register(modBus);
        PlayerCosmetics.register(modBus);
        YgoVillagers.register(modBus);
        io.github.zancrow321.minecraftygo.village.PlayerShops.register(modBus);
        io.github.zancrow321.minecraftygo.points.Points.register(modBus);
        RandomCardFunction.register(modBus);
        DuelDome.register(modBus);
        DuelArena.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, YgoServerConfig.SPEC);
        modBus.addListener(YgoNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerConfig(ModConfig.Type.CLIENT, YgoClientConfig.SPEC);
        }
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            DuelManager.shutdown();
            TournamentManager.shutdown();
            Progress.stopped();
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            DuelManager.get(event.getServer()).tick();
            TournamentManager.get(event.getServer()).tick();
            io.github.zancrow321.minecraftygo.village.CardShop.tick(event.getServer());
            io.github.zancrow321.minecraftygo.points.Points.tick(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Progress.sync(player);
                io.github.zancrow321.minecraftygo.progression.StarterDecks.offer(player);
                DuelManager.get(player.server).onLogin(player);
                TournamentManager.get(player.server).onLogin(player);
                io.github.zancrow321.minecraftygo.points.Points.login(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                DuelManager.get(player.server).onLogout(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event) -> {
            if (event.getEntity() instanceof ServerPlayer tracker) {
                DuelManager.get(tracker.server).onStartTracking(tracker, event.getTarget());
            }
        });
        NeoForge.EVENT_BUS.addListener(this::onInteractPlayer);
        // With points as the currency, card traders open a points shop instead of the trade window.
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            if (event.getTarget() instanceof net.minecraft.world.entity.npc.Villager villager
                    && io.github.zancrow321.minecraftygo.village.TraderShop.interact(event.getEntity(), villager,
                    event.getHand())) {
                event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
                event.setCanceled(true);
            }
        });
        // People at a duel stand still at their end of the field: nothing hurts them and mobs leave them be.
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && DuelManager.get(player.server).protects(player)
                    && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((LivingChangeTargetEvent event) -> {
            if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player
                    && DuelManager.get(player.server).protects(player)) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(YgoCommands::register);
    }

    /** Right-clicking another player while wearing or holding a duel disk challenges them (or accepts). */
    private void onInteractPlayer(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof Player other)) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer && other instanceof ServerPlayer serverOther
                && DuelManager.get(serverPlayer.server).inDuel(serverOther)) {
            // Anyone can watch a duel, disk or not.
            DuelManager.get(serverPlayer.server).watch(serverPlayer, serverOther);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        if (!DuelDisks.has(player)) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer && other instanceof ServerPlayer serverOther) {
            DuelManager.get(serverPlayer.server).diskInteract(serverPlayer, serverOther);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private void onServerStarting(ServerStartingEvent event) {
        YgoServerConfig.migrate();
        Progress.started(event.getServer());
        // Duels run on the server, so load the engine there and fail loudly but harmlessly if it's unavailable.
        try {
            LOGGER.info("Loaded OCG-Core {} with {} cards", OcgCore.get().version(), YgoData.cards().all().size());
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            LOGGER.error("Could not load OCG-Core; duels are disabled on this server", e);
        }
    }
}
