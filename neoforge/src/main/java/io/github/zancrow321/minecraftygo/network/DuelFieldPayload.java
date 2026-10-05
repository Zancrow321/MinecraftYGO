package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: where the duel field is projected. Player 0's side faces {@code yaw} (degrees, Minecraft
 * convention), player 1 stands across from them. {@code active == false} removes the field.
 */
public record DuelFieldPayload(boolean active, double x, double y, double z, float yaw) implements CustomPacketPayload {
    public static final Type<DuelFieldPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "duel_field"));
    public static final StreamCodec<ByteBuf, DuelFieldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DuelFieldPayload::active,
            ByteBufCodecs.DOUBLE, DuelFieldPayload::x,
            ByteBufCodecs.DOUBLE, DuelFieldPayload::y,
            ByteBufCodecs.DOUBLE, DuelFieldPayload::z,
            ByteBufCodecs.FLOAT, DuelFieldPayload::yaw,
            DuelFieldPayload::new);

    public static DuelFieldPayload none() {
        return new DuelFieldPayload(false, 0, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
