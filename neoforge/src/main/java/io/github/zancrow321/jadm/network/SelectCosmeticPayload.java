package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: put on a disk skin ({@code skin == true}) or pick a card sleeve. */
public record SelectCosmeticPayload(boolean skin, String id) implements CustomPacketPayload {
    public static final Type<SelectCosmeticPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "select_cosmetic"));
    public static final StreamCodec<ByteBuf, SelectCosmeticPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SelectCosmeticPayload::skin,
            ByteBufCodecs.STRING_UTF8, SelectCosmeticPayload::id,
            SelectCosmeticPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
