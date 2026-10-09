package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: a pick in a draft or a change to a Sealed or Draft deck.
 *
 * @param action "pick" (value: the card's place in the pack), "add" or "remove" (value: the card's passcode),
 *               "clear", "auto" (build one from the pool), "done" or "edit"
 */
public record LimitedActionPayload(String action, int value) implements CustomPacketPayload {
    public static final Type<LimitedActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "limited_action"));

    public static final StreamCodec<ByteBuf, LimitedActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), LimitedActionPayload::action, ByteBufCodecs.VAR_INT,
            LimitedActionPayload::value, LimitedActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
