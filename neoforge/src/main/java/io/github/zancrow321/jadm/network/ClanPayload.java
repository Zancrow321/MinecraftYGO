package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the clan window's contents (a {@code ClanView} as JSON).
 *
 * @param open whether to open the window; otherwise it is only refreshed if it is open
 */
public record ClanPayload(boolean open, String json) implements CustomPacketPayload {
    public static final Type<ClanPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "clan"));

    public static final StreamCodec<ByteBuf, ClanPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ClanPayload::open, ByteBufCodecs.stringUtf8(1 << 20), ClanPayload::json,
            ClanPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
