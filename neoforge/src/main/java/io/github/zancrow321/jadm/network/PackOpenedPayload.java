package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the cards a booster pack just gave, to show them being revealed.
 */
public record PackOpenedPayload(String setName, List<JadmComponents.CardStack> cards) implements CustomPacketPayload {
    public static final Type<PackOpenedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "pack_opened"));

    public static final StreamCodec<ByteBuf, PackOpenedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PackOpenedPayload::setName,
            JadmComponents.CardStack.STREAM_CODEC.apply(ByteBufCodecs.list()), PackOpenedPayload::cards,
            PackOpenedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
