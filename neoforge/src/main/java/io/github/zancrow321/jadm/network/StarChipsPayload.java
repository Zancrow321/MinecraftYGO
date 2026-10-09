package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the player's Star Chips in the event they are in, for the glove on the screen.
 *
 * @param status "" when they aren't in an event (no glove), else "in", "qualified", "out" or "left"
 */
public record StarChipsPayload(String status, int chips, int goal, String event) implements CustomPacketPayload {
    public static final Type<StarChipsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "star_chips"));
    public static final StreamCodec<ByteBuf, StarChipsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, StarChipsPayload::status, ByteBufCodecs.VAR_INT, StarChipsPayload::chips,
            ByteBufCodecs.VAR_INT, StarChipsPayload::goal, ByteBufCodecs.STRING_UTF8, StarChipsPayload::event,
            StarChipsPayload::new);

    public static StarChipsPayload none() {
        return new StarChipsPayload("", 0, 0, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
