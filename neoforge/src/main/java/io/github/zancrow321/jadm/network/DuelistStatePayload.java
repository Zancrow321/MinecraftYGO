package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells a client whether a player it can see is in a duel, so their disk unfolds or folds back up.
 */
public record DuelistStatePayload(int entityId, boolean dueling) implements CustomPacketPayload {
    public static final Type<DuelistStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "duelist_state"));

    public static final StreamCodec<ByteBuf, DuelistStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DuelistStatePayload::entityId,
            ByteBufCodecs.BOOL, DuelistStatePayload::dueling,
            DuelistStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
