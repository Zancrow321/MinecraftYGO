package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: buy or sell a line of the open points shop, once or (shift) as often as possible. */
public record PointShopTradePayload(int containerId, int index, boolean all) implements CustomPacketPayload {
    public static final Type<PointShopTradePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "point_shop_trade"));
    public static final StreamCodec<ByteBuf, PointShopTradePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PointShopTradePayload::containerId, ByteBufCodecs.VAR_INT,
            PointShopTradePayload::index, ByteBufCodecs.BOOL, PointShopTradePayload::all,
            PointShopTradePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
