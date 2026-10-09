package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: new settings for the Arena Core at {@code pos} (field size in percent or 0 for automatic, how
 * far its podiums go up, whether the mat is only outlined), or with {@code preview} just a fresh look at the field.
 */
public record ArenaCoreSettingsPayload(BlockPos pos, int size, int lift, boolean outline, boolean preview)
        implements CustomPacketPayload {
    public static final Type<ArenaCoreSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "arena_core_settings"));
    public static final StreamCodec<ByteBuf, ArenaCoreSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArenaCoreSettingsPayload::pos,
            ByteBufCodecs.VAR_INT, ArenaCoreSettingsPayload::size,
            ByteBufCodecs.VAR_INT, ArenaCoreSettingsPayload::lift,
            ByteBufCodecs.BOOL, ArenaCoreSettingsPayload::outline,
            ByteBufCodecs.BOOL, ArenaCoreSettingsPayload::preview,
            ArenaCoreSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
