package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.cosmetics.CosmeticsData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: open (or refresh) the cosmetics screen with the player's progress and current picks. */
public record CosmeticsPayload(CosmeticsData data, String skin, boolean hasDisk) implements CustomPacketPayload {
    public static final Type<CosmeticsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "cosmetics"));
    public static final StreamCodec<ByteBuf, CosmeticsPayload> STREAM_CODEC = StreamCodec.composite(
            CosmeticsData.STREAM_CODEC, CosmeticsPayload::data,
            ByteBufCodecs.STRING_UTF8, CosmeticsPayload::skin,
            ByteBufCodecs.BOOL, CosmeticsPayload::hasDisk,
            CosmeticsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
