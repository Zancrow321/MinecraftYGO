package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: a click in the binder or deck box screen. {@code offhand} says which hand holds the binder or
 * deck box the screen was opened from. {@code rarity} is the copy a binder click takes out, or {@code null} for
 * commons first.
 */
public record CollectionActionPayload(Action action, boolean offhand, int code, boolean all, Rarity rarity)
        implements CustomPacketPayload {
    public enum Action {
        /** Put every loose card in the inventory into the binder. */
        DEPOSIT_ALL,
        /** Take a card (or all copies) out of the binder. */
        WITHDRAW,
        /** Move a card from the binder (or a loose card) into the deck. */
        DECK_ADD,
        /** Move a card from the deck back to the binder. */
        DECK_REMOVE,
        /** Move the whole deck back to the binder. */
        DECK_CLEAR
    }

    public CollectionActionPayload(Action action, boolean offhand, int code, boolean all) {
        this(action, offhand, code, all, null);
    }

    public static final Type<CollectionActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "collection_action"));

    public static final StreamCodec<ByteBuf, CollectionActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), CollectionActionPayload::action,
            ByteBufCodecs.BOOL, CollectionActionPayload::offhand,
            ByteBufCodecs.VAR_INT, CollectionActionPayload::code,
            ByteBufCodecs.BOOL, CollectionActionPayload::all,
            // 0 for none, else the rarity's ordinal + 1.
            ByteBufCodecs.VAR_INT.map(i -> i == 0 ? null : Rarity.values()[i - 1],
                    r -> r == null ? 0 : r.ordinal() + 1), CollectionActionPayload::rarity,
            CollectionActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
