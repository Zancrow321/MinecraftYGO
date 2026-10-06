package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the starter and structure decks a new player can pick their first deck from.
 */
public record StarterChoicesPayload(List<Choice> choices) implements CustomPacketPayload {
    public static final Type<StarterChoicesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "starter_choices"));

    /**
     * @param id    a bundled deck ({@code starter_yugi}) or a product id
     * @param cover the card shown for it
     */
    public record Choice(String id, String name, int cards, int cover) {
        public static final StreamCodec<ByteBuf, Choice> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Choice::id, ByteBufCodecs.STRING_UTF8, Choice::name,
                ByteBufCodecs.VAR_INT, Choice::cards, ByteBufCodecs.VAR_INT, Choice::cover, Choice::new);
    }

    public static final StreamCodec<ByteBuf, StarterChoicesPayload> STREAM_CODEC = StreamCodec.composite(
            Choice.STREAM_CODEC.apply(ByteBufCodecs.list()), StarterChoicesPayload::choices,
            StarterChoicesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
