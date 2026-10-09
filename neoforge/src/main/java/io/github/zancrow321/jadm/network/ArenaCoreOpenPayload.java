package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: open the settings of the Arena Core at {@code pos}. */
public record ArenaCoreOpenPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ArenaCoreOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "arena_core_open"));
    public static final StreamCodec<ByteBuf, ArenaCoreOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArenaCoreOpenPayload::pos, ArenaCoreOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
