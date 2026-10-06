package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the receiver's censored view of their duel, as {@code ViewCodec} JSON.
 */
public record DuelViewPayload(String json) implements CustomPacketPayload {
    public static final Type<DuelViewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "duel_view"));
    public static final StreamCodec<ByteBuf, DuelViewPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(1 << 20), DuelViewPayload::json, DuelViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
