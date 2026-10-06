package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, about once a second while the receiver has a choice to make and the server has a turn time
 * limit: how much of their time for this turn is left, in ticks.
 */
public record DuelClockPayload(int ticksLeft) implements CustomPacketPayload {
    public static final Type<DuelClockPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "duel_clock"));
    public static final StreamCodec<ByteBuf, DuelClockPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, DuelClockPayload::ticksLeft, DuelClockPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
