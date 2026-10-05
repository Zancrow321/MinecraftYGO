package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.client.ClientDuel;
import io.github.zancrow321.minecraftygo.client.field.ClientField;
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
        registrar.playToServer(DuelResponsePayload.TYPE, DuelResponsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DuelManager.get(player.server).respond(player, payload.response());
                    }
                }));
    }
}
