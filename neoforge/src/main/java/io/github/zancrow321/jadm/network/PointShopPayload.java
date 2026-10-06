package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.points.PointShopMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server to client: the lines of the points shop the player has open. */
public record PointShopPayload(int containerId, List<PointShopMenu.Entry> entries) implements CustomPacketPayload {
    public static final Type<PointShopPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "point_shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PointShopPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, PointShopPayload::containerId,
                    PointShopMenu.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), PointShopPayload::entries,
                    PointShopPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
