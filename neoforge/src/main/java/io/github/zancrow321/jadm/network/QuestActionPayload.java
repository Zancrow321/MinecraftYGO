package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: a click in the quest window. The server checks the quest is done (or may be rerolled) first.
 *
 * @param id the quest; unused for {@link Action#REFRESH} and {@link Action#CLAIM_ALL}
 */
public record QuestActionPayload(Action action, String id) implements CustomPacketPayload {
    public enum Action {
        REFRESH,
        CLAIM,
        CLAIM_ALL,
        /** Swap an unfinished quest for another. */
        REROLL
    }

    public static final Type<QuestActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "quest_action"));
    public static final StreamCodec<ByteBuf, QuestActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
            QuestActionPayload::action,
            ByteBufCodecs.STRING_UTF8, QuestActionPayload::id,
            QuestActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
