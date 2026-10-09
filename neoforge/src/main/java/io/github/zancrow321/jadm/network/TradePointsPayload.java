package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the Duel Points a player typed into their side of the open trade window. */
public record TradePointsPayload(int containerId, int points) implements CustomPacketPayload {
    public static final Type<TradePointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "trade_points"));
    public static final StreamCodec<ByteBuf, TradePointsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TradePointsPayload::containerId, ByteBufCodecs.VAR_INT, TradePointsPayload::points,
            TradePointsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
