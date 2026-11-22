package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the Rematch button on the result screen, which asks for (or accepts) the same duel again. */
public record RematchPayload() implements CustomPacketPayload {
    public static final Type<RematchPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "rematch"));
    public static final StreamCodec<ByteBuf, RematchPayload> STREAM_CODEC = StreamCodec.unit(new RematchPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
