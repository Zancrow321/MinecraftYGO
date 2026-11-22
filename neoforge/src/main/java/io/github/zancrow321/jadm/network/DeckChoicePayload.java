package io.github.zancrow321.jadm.network;

import io.github.zancrow321.jadm.Jadm;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client, when a duel is about to start and the player carries more than one legal deck box: which deck
 * to play. The answer is a {@link DeckPickPayload}.
 *
 * @param matchup  who plays whom, e.g. "Yugi vs Kaiba"
 * @param decks    the legal deck boxes the player carries
 * @param selected the index of the deck picked last time (or the first one), ready to confirm
 * @param seconds  how long the player has before that deck is played anyway
 */
public record DeckChoicePayload(String matchup, List<Option> decks, int selected, int seconds)
        implements CustomPacketPayload {
    public static final Type<DeckChoicePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "deck_choice"));

    /**
     * @param name  the deck box's name
     * @param main  cards in the Main Deck
     * @param extra cards in the Extra Deck
     * @param cover the card shown for it: its boss monster, else its first card (0 for none)
     * @param boss  whether {@code cover} is the deck's boss monster
     */
    public record Option(String name, int main, int extra, int cover, boolean boss) {
        public static final StreamCodec<ByteBuf, Option> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Option::name, ByteBufCodecs.VAR_INT, Option::main,
                ByteBufCodecs.VAR_INT, Option::extra, ByteBufCodecs.VAR_INT, Option::cover,
                ByteBufCodecs.BOOL, Option::boss, Option::new);
    }

    public static final StreamCodec<ByteBuf, DeckChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DeckChoicePayload::matchup,
            Option.STREAM_CODEC.apply(ByteBufCodecs.list()), DeckChoicePayload::decks,
            ByteBufCodecs.VAR_INT, DeckChoicePayload::selected,
            ByteBufCodecs.VAR_INT, DeckChoicePayload::seconds,
            DeckChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
