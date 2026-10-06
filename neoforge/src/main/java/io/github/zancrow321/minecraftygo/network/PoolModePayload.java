package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client on login: the server's pool mode (e.g. {@code all}), so deck boxes and pack names on the client
 * match the cards the server plays with.
 */
public record PoolModePayload(String mode) implements CustomPacketPayload {
    public static final Type<PoolModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "pool_mode"));
    public static final StreamCodec<ByteBuf, PoolModePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PoolModePayload::mode, PoolModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
