package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the winner of the coin toss chose to go first, or second. */
public record DuelOrderPayload(boolean first) implements CustomPacketPayload {
    public static final Type<DuelOrderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "duel_order"));
    public static final StreamCodec<ByteBuf, DuelOrderPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, DuelOrderPayload::first, DuelOrderPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
