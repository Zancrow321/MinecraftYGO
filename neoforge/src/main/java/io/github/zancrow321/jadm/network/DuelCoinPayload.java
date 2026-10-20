package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, at the start of a duel whose coin toss a person won: who won it and gets to choose whether their
 * team goes first or second, and later that they have chosen.
 *
 * @param chooser the name of whoever chooses
 * @param you     whether the receiver is the one choosing
 * @param seconds how long from now they have before the duel starts with them going first
 * @param decided whether the choice is made and the duel is starting
 * @param turned  once decided: whether the teams traded ends, so the field is seen from the other end now
 */
public record DuelCoinPayload(String chooser, boolean you, int seconds, boolean decided, boolean turned)
        implements CustomPacketPayload {
    public static final Type<DuelCoinPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "duel_coin"));
    public static final StreamCodec<ByteBuf, DuelCoinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DuelCoinPayload::chooser,
            ByteBufCodecs.BOOL, DuelCoinPayload::you,
            ByteBufCodecs.VAR_INT, DuelCoinPayload::seconds,
            ByteBufCodecs.BOOL, DuelCoinPayload::decided,
            ByteBufCodecs.BOOL, DuelCoinPayload::turned,
            DuelCoinPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
