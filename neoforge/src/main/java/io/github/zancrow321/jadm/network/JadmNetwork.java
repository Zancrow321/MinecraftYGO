package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.client.ClientDuel;
import io.github.zancrow321.jadm.client.ClientScreens;
import io.github.zancrow321.jadm.collection.CollectionActions;
import io.github.zancrow321.jadm.client.disk.DiskClient;
import io.github.zancrow321.jadm.client.field.ClientField;
import io.github.zancrow321.jadm.cosmetics.PlayerCosmetics;
import io.github.zancrow321.jadm.client.shop.ClientPoints;
import io.github.zancrow321.jadm.duel.DuelManager;
import io.github.zancrow321.jadm.points.PointShopMenu;
import io.github.zancrow321.jadm.village.ShopStandMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class JadmNetwork {
    private JadmNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // ClientDuel is only touched when a payload arrives, which only happens on a client.
        registrar.playToClient(DuelViewPayload.TYPE, DuelViewPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientDuel.receive(payload.json())));
        registrar.playToClient(DuelFieldPayload.TYPE, DuelFieldPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientField.receive(payload)));
        registrar.playToClient(DuelistStatePayload.TYPE, DuelistStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> DiskClient.receive(payload)));
        registrar.playToClient(DuelResultPayload.TYPE, DuelResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientDuel.result(payload)));
        registrar.playToClient(DuelClockPayload.TYPE, DuelClockPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientDuel.clock(payload.ticksLeft())));
        registrar.playToClient(PackOpenedPayload.TYPE, PackOpenedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.packOpened(payload)));
        registrar.playToClient(CosmeticsPayload.TYPE, CosmeticsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.cosmetics(payload)));
        registrar.playToClient(PoolModePayload.TYPE, PoolModePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> io.github.zancrow321.jadm.JadmData
                        .joined(payload)));
        registrar.playToServer(SelectCosmeticPayload.TYPE, SelectCosmeticPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PlayerCosmetics.select(player, payload.skin(), payload.id());
                    }
                }));
        registrar.playToClient(GuidePayload.TYPE, GuidePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.guide(payload)));
        registrar.playToClient(TournamentPayload.TYPE, TournamentPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.tournament(payload)));
        registrar.playToClient(RankingPayload.TYPE, RankingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.ranking(payload)));
        registrar.playToClient(LimitedPayload.TYPE, LimitedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.limited(payload)));
        registrar.playToServer(LimitedActionPayload.TYPE, LimitedActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        io.github.zancrow321.jadm.tournament.TournamentManager.get(player.server)
                                .limitedAction(player, payload.action(), payload.value());
                    }
                }));
        registrar.playToClient(StarChipsPayload.TYPE, StarChipsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> io.github.zancrow321.jadm.client.starchips
                        .StarChipHud.receive(payload)));
        registrar.playToClient(StarterChoicesPayload.TYPE, StarterChoicesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.starterChoices(payload)));
        registrar.playToServer(StarterPickPayload.TYPE, StarterPickPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        io.github.zancrow321.jadm.progression.StarterDecks.pick(player, payload.id());
                    }
                }));
        registrar.playToServer(CollectionActionPayload.TYPE, CollectionActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CollectionActions.handle(player, payload);
                    }
                }));
        registrar.playToClient(PointsPayload.TYPE, PointsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPoints.receive(payload)));
        registrar.playToClient(PointShopPayload.TYPE, PointShopPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPoints.shop(payload)));
        registrar.playToServer(PointShopTradePayload.TYPE, PointShopTradePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PointShopMenu.handle(player, payload.containerId(), payload.index(), payload.all());
                    }
                }));
        registrar.playToServer(StandPricePayload.TYPE, StandPricePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ShopStandMenu.setPrice(player, payload.column(), payload.price());
                    }
                }));
        registrar.playToServer(TradePointsPayload.TYPE, TradePointsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        io.github.zancrow321.jadm.trade.TradeMenu.setPoints(player, payload.containerId(),
                                payload.points());
                    }
                }));
        registrar.playToClient(AdminPayload.TYPE, AdminPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.admin(payload)));
        registrar.playToServer(AdminActionPayload.TYPE, AdminActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        io.github.zancrow321.jadm.admin.AdminMenu.handle(player, payload);
                    }
                }));
        registrar.playToClient(SetBookPayload.TYPE, SetBookPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.setBook(payload)));
        registrar.playToServer(SetBookActionPayload.TYPE, SetBookActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        io.github.zancrow321.jadm.collection.SetCollection.handle(player, payload);
                    }
                }));
        registrar.playToClient(QuestPayload.TYPE, QuestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.quests(payload)));
        registrar.playToServer(QuestActionPayload.TYPE, QuestActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        io.github.zancrow321.jadm.quest.Quests.handle(player, payload);
                    }
                }));
        registrar.playToClient(ArenaCoreOpenPayload.TYPE, ArenaCoreOpenPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.arenaCore(payload.pos())));
        registrar.playToServer(ArenaCoreSettingsPayload.TYPE, ArenaCoreSettingsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        io.github.zancrow321.jadm.arena.BuiltArena.settings(player, payload);
                    }
                }));
        registrar.playToServer(DuelResponsePayload.TYPE, DuelResponsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DuelManager.get(player.server).respond(player, payload.response());
                    }
                }));
    }
}
