package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the player's Duel Points, whether the shops take them, and what they are called. */
public record PointsPayload(boolean active, long balance, String symbol) implements CustomPacketPayload {
    public static final Type<PointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "points"));
    public static final StreamCodec<ByteBuf, PointsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PointsPayload::active, ByteBufCodecs.VAR_LONG, PointsPayload::balance,
            ByteBufCodecs.STRING_UTF8, PointsPayload::symbol, PointsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
