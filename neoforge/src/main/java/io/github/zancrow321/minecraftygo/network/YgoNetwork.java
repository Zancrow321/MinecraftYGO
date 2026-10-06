package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.client.ClientDuel;
import io.github.zancrow321.minecraftygo.client.ClientScreens;
import io.github.zancrow321.minecraftygo.collection.CollectionActions;
import io.github.zancrow321.minecraftygo.client.disk.DiskClient;
import io.github.zancrow321.minecraftygo.client.field.ClientField;
import io.github.zancrow321.minecraftygo.cosmetics.PlayerCosmetics;
import io.github.zancrow321.minecraftygo.duel.DuelManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class YgoNetwork {
    private YgoNetwork() {
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
                (payload, context) -> context.enqueueWork(() -> io.github.zancrow321.minecraftygo.YgoData
                        .joined(payload)));
        registrar.playToServer(SelectCosmeticPayload.TYPE, SelectCosmeticPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PlayerCosmetics.select(player, payload.skin(), payload.id());
                    }
                }));
        registrar.playToClient(TournamentPayload.TYPE, TournamentPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.tournament(payload)));
        registrar.playToClient(StarterChoicesPayload.TYPE, StarterChoicesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.starterChoices(payload)));
        registrar.playToServer(StarterPickPayload.TYPE, StarterPickPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        io.github.zancrow321.minecraftygo.progression.StarterDecks.pick(player, payload.id());
                    }
                }));
        registrar.playToServer(CollectionActionPayload.TYPE, CollectionActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CollectionActions.handle(player, payload);
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
