package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client on login and after {@code /jadm banlist update}: the official banlists the server downloaded, as
 * {@code OfficialBanlists} JSON (empty when it plays with the bundled ones), so deck boxes on the client check
 * decks against the same lists.
 */
public record OfficialBanlistsPayload(String json) implements CustomPacketPayload {
    public static final Type<OfficialBanlistsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "official_banlists"));
    public static final StreamCodec<ByteBuf, OfficialBanlistsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(1 << 20), OfficialBanlistsPayload::json, OfficialBanlistsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
