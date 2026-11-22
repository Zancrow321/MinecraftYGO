package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client on login and after {@code /reload}: the server's own booster packs, structure decks and tins from
 * its datapacks, as a JSON array of {@code CustomProducts} (every card by passcode), empty for none.
 */
public record CustomProductsPayload(String json) implements CustomPacketPayload {
    public static final Type<CustomProductsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "custom_products"));
    public static final StreamCodec<ByteBuf, CustomProductsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(1 << 20), CustomProductsPayload::json, CustomProductsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
