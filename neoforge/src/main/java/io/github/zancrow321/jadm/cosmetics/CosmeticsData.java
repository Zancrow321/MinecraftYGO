package io.github.zancrow321.jadm.cosmetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A player's progress toward cosmetic unlocks, and the sleeve they picked. Kept on the player and through death. */
public record CosmeticsData(int wins, int npcWins, int packs, String sleeve) {
    public static final CosmeticsData NEW = new CosmeticsData(0, 0, 0, Cosmetics.DEFAULT_SLEEVE);
    public static final Codec<CosmeticsData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("wins", 0).forGetter(CosmeticsData::wins),
            Codec.INT.optionalFieldOf("npc_wins", 0).forGetter(CosmeticsData::npcWins),
            Codec.INT.optionalFieldOf("packs", 0).forGetter(CosmeticsData::packs),
            Codec.STRING.optionalFieldOf("sleeve", Cosmetics.DEFAULT_SLEEVE).forGetter(CosmeticsData::sleeve)
    ).apply(i, CosmeticsData::new));
    public static final StreamCodec<ByteBuf, CosmeticsData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CosmeticsData::wins,
            ByteBufCodecs.VAR_INT, CosmeticsData::npcWins,
            ByteBufCodecs.VAR_INT, CosmeticsData::packs,
            ByteBufCodecs.STRING_UTF8, CosmeticsData::sleeve,
            CosmeticsData::new);

    public int stat(Cosmetics.Stat stat) {
        return switch (stat) {
            case NONE -> 0;
            case WINS -> wins;
            case NPC_WINS -> npcWins;
            case PACKS -> packs;
        };
    }

    public CosmeticsData withSleeve(String sleeve) {
        return new CosmeticsData(wins, npcWins, packs, sleeve);
    }
}
