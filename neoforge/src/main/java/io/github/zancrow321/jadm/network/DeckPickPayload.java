package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the deck a player picked from a {@link DeckChoicePayload}, by its index there. */
public record DeckPickPayload(int index) implements CustomPacketPayload {
    public static final Type<DeckPickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "deck_pick"));
    public static final StreamCodec<ByteBuf, DeckPickPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, DeckPickPayload::index, DeckPickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
