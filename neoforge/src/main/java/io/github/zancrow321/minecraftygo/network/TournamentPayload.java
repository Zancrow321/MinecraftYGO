package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the tournament as the window shows it (a {@code TournamentView} as JSON, empty for none).
 *
 * @param open the window's tab to open it at ("bracket", "table", "matches", "info", or "default"), or "" to only
 *             update it if it is open
 */
public record TournamentPayload(String open, String json) implements CustomPacketPayload {
    public static final Type<TournamentPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "tournament"));

    public static final StreamCodec<ByteBuf, TournamentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TournamentPayload::open, ByteBufCodecs.stringUtf8(1 << 20), TournamentPayload::json,
            TournamentPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
