package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: a Sealed or Draft tournament as one duelist sees it while the cards are drafted and the decks
 * built (a {@code LimitedView} as JSON; empty once that is over, which closes the window).
 *
 * @param open whether to open the window, or only to update it if it is open
 */
public record LimitedPayload(boolean open, String json) implements CustomPacketPayload {
    public static final Type<LimitedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "limited"));

    public static final StreamCodec<ByteBuf, LimitedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, LimitedPayload::open, ByteBufCodecs.stringUtf8(1 << 20), LimitedPayload::json,
            LimitedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
