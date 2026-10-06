package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client, when a duel is over: how it went for the receiver and what they won or lost (a booster pack,
 * ante cards, unlocked cosmetics), one line each, for the result screen.
 *
 * @param outcome {@link #WON}, {@link #LOST} or {@link #DRAW}
 * @param record  the receiver's win/loss record after this duel, e.g. "12 wins, 5 losses, 1 draw", or empty when the
 *                server doesn't keep records
 */
public record DuelResultPayload(int outcome, List<String> rewards, String record) implements CustomPacketPayload {
    public static final int LOST = 0;
    public static final int WON = 1;
    public static final int DRAW = 2;

    public static final Type<DuelResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "duel_result"));
    public static final StreamCodec<ByteBuf, DuelResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DuelResultPayload::outcome,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DuelResultPayload::rewards,
            ByteBufCodecs.STRING_UTF8, DuelResultPayload::record,
            DuelResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
