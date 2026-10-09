package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: what the Set Collection Book shows. The client works out the sets itself (see
 * {@link io.github.zancrow321.jadm.collection.SetBook}); the server says which cards the player owns, which sets'
 * rewards they have claimed and what a completed set brings.
 *
 * @param open    open the book; otherwise only refresh it if it is open
 * @param owned   the different cards the player owns, as original passcodes
 * @param claimed the product ids of the sets whose reward the player has claimed
 */
public record SetBookPayload(boolean open, List<Integer> owned, List<String> claimed, Reward reward)
        implements CustomPacketPayload {
    /**
     * What completing a set brings.
     *
     * @param pointsPerCard Duel Points per card in the set; 0 when points aren't the currency
     * @param symbol        the points' symbol, e.g. "DP"
     */
    public record Reward(int pointsPerCard, String symbol, int packs, int emeralds, int xp) {
        public static final StreamCodec<ByteBuf, Reward> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reward::pointsPerCard,
                ByteBufCodecs.STRING_UTF8, Reward::symbol,
                ByteBufCodecs.VAR_INT, Reward::packs,
                ByteBufCodecs.VAR_INT, Reward::emeralds,
                ByteBufCodecs.VAR_INT, Reward::xp,
                Reward::new);

        /** Whether completing a set brings anything at all. */
        public boolean any() {
            return pointsPerCard > 0 || packs > 0 || emeralds > 0 || xp > 0;
        }
    }

    public static final Type<SetBookPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "set_book"));
    public static final StreamCodec<ByteBuf, SetBookPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SetBookPayload::open,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), SetBookPayload::owned,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SetBookPayload::claimed,
            Reward.STREAM_CODEC, SetBookPayload::reward,
            SetBookPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
