package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to everyone near a duel that was just won: fireworks over the winner's half of the field, from the mat at
 * ({@code x}, {@code y}, {@code z}), in {@code colors} (RGB; empty for gold and white). {@code spread} is the field's
 * size, {@code ceiling} the room over the mat (0 for open sky), so they burst under a roof.
 */
public record VictoryFireworksPayload(double x, double y, double z, double spread, double ceiling, List<Integer> colors)
        implements CustomPacketPayload {
    public static final Type<VictoryFireworksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "victory_fireworks"));
    public static final StreamCodec<ByteBuf, VictoryFireworksPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, VictoryFireworksPayload::x,
            ByteBufCodecs.DOUBLE, VictoryFireworksPayload::y,
            ByteBufCodecs.DOUBLE, VictoryFireworksPayload::z,
            ByteBufCodecs.DOUBLE, VictoryFireworksPayload::spread,
            ByteBufCodecs.DOUBLE, VictoryFireworksPayload::ceiling,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(16)), VictoryFireworksPayload::colors,
            VictoryFireworksPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
