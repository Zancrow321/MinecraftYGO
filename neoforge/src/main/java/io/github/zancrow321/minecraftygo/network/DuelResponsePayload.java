package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the player's answer to their current prompt, as raw OCG-Core response bytes. The core
 * validates every answer, so a bad buffer only earns a retry.
 */
public record DuelResponsePayload(byte[] response) implements CustomPacketPayload {
    public static final int MAX_SIZE = 4096;
    public static final Type<DuelResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "duel_response"));
    public static final StreamCodec<ByteBuf, DuelResponsePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.byteArray(MAX_SIZE), DuelResponsePayload::response, DuelResponsePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
