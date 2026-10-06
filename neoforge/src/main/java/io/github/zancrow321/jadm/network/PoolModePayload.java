package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client on login and whenever the player's progress changes: the server's pool mode (e.g. {@code all}),
 * the player's newest unlocked product, whether locked cards stay out of decks and the banlist setting, so binders
 * and deck boxes on the client match the cards the server plays with.
 */
public record PoolModePayload(String mode, String product, boolean lockedInDeck, String banlist)
        implements CustomPacketPayload {
    public static final Type<PoolModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "pool_mode"));
    public static final StreamCodec<ByteBuf, PoolModePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PoolModePayload::mode,
                    ByteBufCodecs.STRING_UTF8, PoolModePayload::product,
                    ByteBufCodecs.BOOL, PoolModePayload::lockedInDeck,
                    ByteBufCodecs.STRING_UTF8, PoolModePayload::banlist, PoolModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
