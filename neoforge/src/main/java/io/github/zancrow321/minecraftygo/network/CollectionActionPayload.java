package io.github.zancrow321.minecraftygo.network;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: a click in the binder or deck box screen. {@code offhand} says which hand holds the binder or
 * deck box the screen was opened from.
 */
public record CollectionActionPayload(Action action, boolean offhand, int code, boolean all)
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

    public static final Type<CollectionActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "collection_action"));

    public static final StreamCodec<ByteBuf, CollectionActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), CollectionActionPayload::action,
            ByteBufCodecs.BOOL, CollectionActionPayload::offhand,
            ByteBufCodecs.VAR_INT, CollectionActionPayload::code,
            ByteBufCodecs.BOOL, CollectionActionPayload::all,
            CollectionActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
