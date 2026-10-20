package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the player's quests (a {@code QuestView} as JSON), with the ones that just moved on to pop up.
 *
 * @param open open the quest window; otherwise only refresh it if it is open
 */
public record QuestPayload(boolean open, String json) implements CustomPacketPayload {
    public static final Type<QuestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "quests"));

    public static final StreamCodec<ByteBuf, QuestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, QuestPayload::open,
            ByteBufCodecs.stringUtf8(1 << 20), QuestPayload::json,
            QuestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
