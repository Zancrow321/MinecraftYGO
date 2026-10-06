package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Server to client: open the handbook. Carries the server's settings by their path in the config file (such as
 * {@code pool.mode}), so the book shows the values this world actually plays with.
 */
public record GuidePayload(Map<String, String> settings) implements CustomPacketPayload {
    public static final Type<GuidePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "guide"));
    public static final StreamCodec<ByteBuf, GuidePayload> STREAM_CODEC = ByteBufCodecs
            .<ByteBuf, String, String, Map<String, String>>map(HashMap::new, ByteBufCodecs.STRING_UTF8,
                    ByteBufCodecs.STRING_UTF8)
            .map(GuidePayload::new, GuidePayload::settings);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
