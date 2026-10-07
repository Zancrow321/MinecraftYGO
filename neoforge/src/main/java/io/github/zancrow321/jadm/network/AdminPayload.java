package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: opens the admin menu ({@code open}) or refreshes it, with who is online and their Duel Points.
 * {@code points} says whether Duel Points are the shops' currency.
 */
public record AdminPayload(boolean open, List<Player> players, boolean points, String symbol)
        implements CustomPacketPayload {
    public static final Type<AdminPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "admin"));

    public record Player(String name, long balance) {
        public static final StreamCodec<ByteBuf, Player> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Player::name, ByteBufCodecs.VAR_LONG, Player::balance, Player::new);
    }

    public static final StreamCodec<ByteBuf, AdminPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AdminPayload::open,
            Player.STREAM_CODEC.apply(ByteBufCodecs.list()), AdminPayload::players,
            ByteBufCodecs.BOOL, AdminPayload::points,
            ByteBufCodecs.STRING_UTF8, AdminPayload::symbol,
            AdminPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
