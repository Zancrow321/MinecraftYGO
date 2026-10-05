package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.item.YgoComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the cards a booster pack just gave, to show them being revealed.
 */
public record PackOpenedPayload(String setName, List<YgoComponents.CardStack> cards) implements CustomPacketPayload {
    public static final Type<PackOpenedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "pack_opened"));

    public static final StreamCodec<ByteBuf, PackOpenedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PackOpenedPayload::setName,
            YgoComponents.CardStack.STREAM_CODEC.apply(ByteBufCodecs.list()), PackOpenedPayload::cards,
            PackOpenedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
