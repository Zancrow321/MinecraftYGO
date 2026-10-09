package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: a click in the Set Collection Book. The server checks the set is complete before it hands out
 * anything.
 *
 * @param id the product id of the set to claim; unused for {@link Action#REFRESH} and {@link Action#CLAIM_ALL}
 */
public record SetBookActionPayload(Action action, String id) implements CustomPacketPayload {
    public enum Action {
        /** Send the book's contents again, e.g. after cards moved while it was open. */
        REFRESH,
        CLAIM,
        CLAIM_ALL
    }

    public static final Type<SetBookActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "set_book_action"));
    public static final StreamCodec<ByteBuf, SetBookActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), SetBookActionPayload::action,
            ByteBufCodecs.STRING_UTF8, SetBookActionPayload::id,
            SetBookActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
