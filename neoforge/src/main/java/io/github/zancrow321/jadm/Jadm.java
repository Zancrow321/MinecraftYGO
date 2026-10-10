package io.github.zancrow321.jadm;

import com.mojang.logging.LogUtils;
import io.github.zancrow321.jadm.arena.DuelArena;
import io.github.zancrow321.jadm.arena.DuelDome;
import io.github.zancrow321.jadm.client.JadmClientConfig;
import io.github.zancrow321.jadm.cosmetics.PlayerCosmetics;
import io.github.zancrow321.jadm.duel.DuelManager;
import io.github.zancrow321.jadm.progression.Progress;
import io.github.zancrow321.jadm.tournament.TournamentManager;
import io.github.zancrow321.jadm.engine.OcgCore;
import io.github.zancrow321.jadm.duel.DuelDisks;
import io.github.zancrow321.jadm.entity.JadmEntities;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.github.zancrow321.jadm.item.JadmItems;
import io.github.zancrow321.jadm.loot.RandomCardFunction;
import io.github.zancrow321.jadm.village.JadmVillagers;
import io.github.zancrow321.jadm.network.JadmNetwork;
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

@Mod(Jadm.MOD_ID)
public final class Jadm {
    public static final String MOD_ID = "jadm";
    /** The root of the mod's commands, {@code /jadm}. */
    public static final String COMMAND = "jadm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Jadm(IEventBus modBus, ModContainer container) {
        JadmEntities.register(modBus);
        JadmItems.register(modBus);
        JadmComponents.register(modBus);
        PlayerCosmetics.register(modBus);
        JadmVillagers.register(modBus);
        io.github.zancrow321.jadm.village.PlayerShops.register(modBus);
        io.github.zancrow321.jadm.points.Points.register(modBus);
        io.github.zancrow321.jadm.trade.Trades.register(modBus);
        RandomCardFunction.register(modBus);
        DuelDome.register(modBus);
        DuelArena.register(modBus);
        io.github.zancrow321.jadm.ranking.RankingBoard.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, JadmServerConfig.SPEC);
        modBus.addListener(JadmNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerConfig(ModConfig.Type.CLIENT, JadmClientConfig.SPEC);
        }
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            DuelManager.shutdown();
            TournamentManager.shutdown();
            io.github.zancrow321.jadm.starchips.StarChips.shutdown();
            io.github.zancrow321.jadm.arena.ArenaLobby.reset();
            io.github.zancrow321.jadm.trade.Trades.reset();
            io.github.zancrow321.jadm.arena.BuiltArena.reset();
            Progress.stopped();
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            DuelManager.get(event.getServer()).tick();
            TournamentManager.get(event.getServer()).tick();
            io.github.zancrow321.jadm.starchips.StarChips.get(event.getServer()).tick();
            io.github.zancrow321.jadm.arena.ArenaLobby.tick(event.getServer());
            io.github.zancrow321.jadm.village.CardShop.tick(event.getServer());
            io.github.zancrow321.jadm.points.Points.tick(event.getServer());
            io.github.zancrow321.jadm.trade.Trades.tick(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Progress.sync(player);
                io.github.zancrow321.jadm.progression.StarterDecks.offer(player);
                io.github.zancrow321.jadm.guide.GuideBook.onLogin(player);
                DuelManager.get(player.server).onLogin(player);
                TournamentManager.get(player.server).onLogin(player);
                io.github.zancrow321.jadm.starchips.StarChips.get(player.server).onLogin(player);
                io.github.zancrow321.jadm.points.Points.login(player);
                io.github.zancrow321.jadm.quest.Quests.onLogin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                io.github.zancrow321.jadm.trade.Trades.logout(player);
                DuelManager.get(player.server).onLogout(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event) -> {
            if (event.getEntity() instanceof ServerPlayer tracker) {
                DuelManager.get(tracker.server).onStartTracking(tracker, event.getTarget());
            }
        });
        NeoForge.EVENT_BUS.addListener(this::onInteractPlayer);
        NeoForge.EVENT_BUS.addListener(io.github.zancrow321.jadm.ranking.RankedDuels::tabListName);
        // With points as the currency, card traders open a points shop instead of the trade window.
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            if (event.getTarget() instanceof net.minecraft.world.entity.npc.Villager villager
                    && io.github.zancrow321.jadm.village.TraderShop.interact(event.getEntity(), villager,
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
        NeoForge.EVENT_BUS.addListener(JadmCommands::register);
    }

    /**
     * Right-clicking another player while wearing or holding a duel disk challenges them (or accepts); sneaking with a
     * card, binder or deck box in hand asks them to trade instead.
     */
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
        if (io.github.zancrow321.jadm.trade.Trades.interact(player, other)) {
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
        JadmServerConfig.migrate();
        io.github.zancrow321.jadm.quest.QuestPool.load();
        Progress.started(event.getServer());
        // Duels run on the server, so load the engine there and fail loudly but harmlessly if it's unavailable.
        try {
            LOGGER.info("Loaded OCG-Core {} with {} cards", OcgCore.get().version(), JadmData.cards().all().size());
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            LOGGER.error("Could not load OCG-Core; duels are disabled on this server", e);
        }
    }
}
