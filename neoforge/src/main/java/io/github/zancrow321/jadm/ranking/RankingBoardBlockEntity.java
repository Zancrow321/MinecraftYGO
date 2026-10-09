package io.github.zancrow321.jadm.ranking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps a Ranking Board's top ten up to date for the players who can see it. Nothing is saved: the server fills it
 * in from the ranking whenever the board loads or the ranking changes.
 */
public final class RankingBoardBlockEntity extends BlockEntity {
    public static final int ROWS = 10;
    private static final int CHECK_TICKS = 40;

    /** One line on the board. */
    public record Row(String name, int rating, String tier, int color, int wins, int losses) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int season = 1;
    /** The ranking's version the rows were taken from; -1 until the first fill. Server only. */
    private int shown = -1;

    public RankingBoardBlockEntity(BlockPos pos, BlockState state) {
        super(RankingBoard.ENTITY.get(), pos, state);
    }

    public List<Row> rows() {
        return rows;
    }

    public int season() {
        return season;
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, RankingBoardBlockEntity board) {
        // Filled at once when it loads, then checked now and then.
        if (!(level instanceof ServerLevel serverLevel)
                || board.shown >= 0 && level.getGameTime() % CHECK_TICKS != 0) {
            return;
        }
        Ranking ranking = Ranking.get(serverLevel.getServer());
        if (ranking.version() == board.shown && board.season == ranking.season()) {
            return;
        }
        board.shown = ranking.version();
        board.season = ranking.season();
        board.rows.clear();
        List<Map.Entry<UUID, Ranking.Entry>> standings = ranking.standings();
        for (int i = 0; i < Math.min(ROWS, standings.size()); i++) {
            Ranking.Entry e = standings.get(i).getValue();
            int tier = e.tier();
            board.rows.add(new Row(e.name(), e.rating(), Tiers.name(tier), Tiers.color(tier), e.wins(),
                    e.losses()));
        }
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ListTag list = new ListTag();
        for (Row row : rows) {
            CompoundTag r = new CompoundTag();
            r.putString("Name", row.name());
            r.putInt("Rating", row.rating());
            r.putString("Tier", row.tier());
            r.putInt("Color", row.color());
            r.putInt("Wins", row.wins());
            r.putInt("Losses", row.losses());
            list.add(r);
        }
        tag.put("Rows", list);
        tag.putInt("Season", season);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Only update tags carry rows; the saved block has none.
        if (tag.contains("Rows")) {
            rows.clear();
            for (Tag t : tag.getList("Rows", Tag.TAG_COMPOUND)) {
                CompoundTag r = (CompoundTag) t;
                rows.add(new Row(r.getString("Name"), r.getInt("Rating"), r.getString("Tier"), r.getInt("Color"),
                        r.getInt("Wins"), r.getInt("Losses")));
            }
            season = Math.max(1, tag.getInt("Season"));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
