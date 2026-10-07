package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: a click in the admin menu. The server checks that the sender is an operator.
 *
 * @param target a player's name, empty for the sender, or {@code *} for everyone online
 * @param id     the product to give
 * @param code   the card to give
 * @param rarity the rarity of the card
 * @param amount how many items, or how many Duel Points
 */
public record AdminActionPayload(Action action, String target, String id, int code, Rarity rarity, int amount)
        implements CustomPacketPayload {
    public enum Action {
        PRODUCT, RANDOM_PACK, CARD, POINTS_GIVE, POINTS_TAKE, POINTS_SET
    }

    public static final Type<AdminActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "admin_action"));

    public static final StreamCodec<ByteBuf, AdminActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), AdminActionPayload::action,
            ByteBufCodecs.STRING_UTF8, AdminActionPayload::target,
            ByteBufCodecs.STRING_UTF8, AdminActionPayload::id,
            ByteBufCodecs.VAR_INT, AdminActionPayload::code,
            ByteBufCodecs.VAR_INT.map(i -> Rarity.values()[i], Rarity::ordinal), AdminActionPayload::rarity,
            ByteBufCodecs.VAR_INT, AdminActionPayload::amount,
            AdminActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
