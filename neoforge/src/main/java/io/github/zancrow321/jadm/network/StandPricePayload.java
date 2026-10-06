package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the owner of the open Shop Stand typed a price in points for a ware. */
public record StandPricePayload(int column, int price) implements CustomPacketPayload {
    public static final Type<StandPricePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "stand_price"));
    public static final StreamCodec<ByteBuf, StandPricePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StandPricePayload::column, ByteBufCodecs.VAR_INT, StandPricePayload::price,
            StandPricePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
