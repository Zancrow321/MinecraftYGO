package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: where the duel field is projected. Player 0's side faces {@code yaw} (degrees, Minecraft
 * convention), player 1 stands across from them. {@code active == false} removes the field.
 */
public record DuelFieldPayload(boolean active, double x, double y, double z, float yaw, Layout layout)
        implements CustomPacketPayload {
    /**
     * How the mat is split and dressed. Normally {@code sleeves} has one card sleeve per team. In a Battle City duel
     * ({@code split}) each partner has their own half of the team's zones and {@code sleeves} has one per duelist:
     * team 0's first and second partner, then team 1's.
     */
    public record Layout(boolean split, List<String> sleeves) {
        public static final Layout NONE = new Layout(false, List.of());
        public static final StreamCodec<ByteBuf, Layout> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Layout::split,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Layout::sleeves,
                Layout::new);
    }

    public static final Type<DuelFieldPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "duel_field"));
    public static final StreamCodec<ByteBuf, DuelFieldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DuelFieldPayload::active,
            ByteBufCodecs.DOUBLE, DuelFieldPayload::x,
            ByteBufCodecs.DOUBLE, DuelFieldPayload::y,
            ByteBufCodecs.DOUBLE, DuelFieldPayload::z,
            ByteBufCodecs.FLOAT, DuelFieldPayload::yaw,
            Layout.STREAM_CODEC, DuelFieldPayload::layout,
            DuelFieldPayload::new);

    public DuelFieldPayload(boolean active, double x, double y, double z, float yaw) {
        this(active, x, y, z, yaw, Layout.NONE);
    }

    public DuelFieldPayload withLayout(boolean split, List<String> sleeves) {
        return new DuelFieldPayload(active, x, y, z, yaw, new Layout(split, List.copyOf(sleeves)));
    }

    public static DuelFieldPayload none() {
        return new DuelFieldPayload(false, 0, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
