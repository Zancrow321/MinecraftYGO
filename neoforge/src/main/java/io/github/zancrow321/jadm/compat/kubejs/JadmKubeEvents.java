package io.github.zancrow321.jadm.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.player.KubePlayerEvent;
import dev.latvian.mods.kubejs.server.ServerKubeEvent;
import io.github.zancrow321.jadm.api.event.DuelEndEvent;
import io.github.zancrow321.jadm.api.event.DuelInfo;
import io.github.zancrow321.jadm.api.event.DuelStartEvent;
import io.github.zancrow321.jadm.api.event.PackOpenEvent;
import io.github.zancrow321.jadm.api.event.RankChangeEvent;
import io.github.zancrow321.jadm.api.event.SetCompleteEvent;
import io.github.zancrow321.jadm.api.event.TournamentEndEvent;
import io.github.zancrow321.jadm.item.CardItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** The {@code JadmEvents} script events; each wraps the mod's own event of the same name. */
public final class JadmKubeEvents {
    private JadmKubeEvents() {
    }

    /** A seat in a duel: {@code seat.name}, {@code seat.team}, {@code seat.bot}, {@code seat.player} (a UUID). */
    public static final class Seat {
        private final DuelInfo.Duelist duelist;

        Seat(DuelInfo.Duelist duelist) {
            this.duelist = duelist;
        }

        public int getTeam() {
            return duelist.team();
        }

        public @Nullable UUID getPlayer() {
            return duelist.player();
        }

        public String getName() {
            return duelist.name();
        }

        public boolean isBot() {
            return duelist.bot();
        }

        @Override
        public String toString() {
            return duelist.name();
        }
    }

    /** Where a duelist finished: {@code placement.name}, {@code placement.place}, {@code placement.player}. */
    public static final class Placement {
        private final TournamentEndEvent.Placement placement;

        Placement(TournamentEndEvent.Placement placement) {
            this.placement = placement;
        }

        /** The person's UUID, or {@code null} for an NPC. */
        public @Nullable UUID getPlayer() {
            return placement.player();
        }

        public String getName() {
            return placement.name();
        }

        public int getPlace() {
            return placement.place();
        }

        @Override
        public String toString() {
            return placement.place() + ". " + placement.name();
        }
    }

    /** What both duel events tell about the duel itself. */
    public abstract static class Duel extends ServerKubeEvent {
        private final DuelInfo duel;

        Duel(DuelInfo duel) {
            super(duel.server());
            this.duel = duel;
        }

        /** The online people in the duel. */
        public List<ServerPlayer> getPlayers() {
            return duel.players();
        }

        /** Every seat, people and bots. */
        public List<Seat> getDuelists() {
            return duel.duelists().stream().map(Seat::new).toList();
        }

        public boolean isRanked() {
            return duel.ranked();
        }

        public boolean isAnte() {
            return duel.ante();
        }

        public boolean isTournament() {
            return duel.tournament();
        }

        public boolean isTag() {
            return duel.tag();
        }

        public boolean isBattleCity() {
            return duel.battleCity();
        }

        /** Whether a bot or an NPC duelist plays one of the seats. */
        public boolean isAgainstBot() {
            return duel.againstBot();
        }

        /** Whether an NPC duelist (not the plain bot) plays. */
        public boolean isAgainstNpc() {
            return duel.npc() != null;
        }

        public @Nullable net.minecraft.world.entity.Entity getNpc() {
            return duel.npc();
        }

        public int getStartingLifePoints() {
            return duel.lifePoints();
        }
    }

    public static final class DuelStart extends Duel {
        private final DuelStartEvent event;

        DuelStart(DuelStartEvent event) {
            super(event.getDuel());
            this.event = event;
        }

        /** What everyone in the duel reads when a script cancels it. */
        public String getCancelMessage() {
            return event.getCancelMessage();
        }

        public void setCancelMessage(String message) {
            event.setCancelMessage(message);
        }
    }

    public static final class DuelEnd extends Duel {
        private final DuelEndEvent event;

        DuelEnd(DuelEndEvent event) {
            super(event.getDuel());
            this.event = event;
        }

        /** 0 or 1, 2 for a draw, -1 if the duel broke off. */
        public int getWinningTeam() {
            return event.getWinningTeam();
        }

        public boolean isDraw() {
            return event.isDraw();
        }

        /** Whether one team won (not a draw, not broken off). */
        public boolean isDecided() {
            return event.isDecided();
        }

        public boolean isFinished() {
            return event.isFinished();
        }

        public int getTurns() {
            return event.getTurns();
        }

        public int getLifePoints(int team) {
            return event.getLifePoints(team);
        }

        public List<ServerPlayer> getWinners() {
            return event.getWinners();
        }

        public List<ServerPlayer> getLosers() {
            return event.getLosers();
        }

        public List<String> getWinnerNames() {
            return event.getWinnerNames();
        }

        public boolean isWinner(ServerPlayer player) {
            return event.isWinner(player);
        }

        /** A line on {@code player}'s result screen, under the rewards. */
        public void addNote(ServerPlayer player, String line) {
            event.addNote(player, line);
        }
    }

    public static final class PackOpened implements KubePlayerEvent {
        private final PackOpenEvent event;

        PackOpened(PackOpenEvent event) {
            this.event = event;
        }

        @Override
        public ServerPlayer getEntity() {
            return event.getPlayer();
        }

        public String getSetId() {
            return event.getSetId();
        }

        public String getSetCode() {
            return event.getSetCode();
        }

        public String getSetName() {
            return event.getSetName();
        }

        /** The pulled cards; add, remove or replace items to change what the player gets. */
        public List<ItemStack> getCards() {
            return event.getCards();
        }

        public void addCard(ItemStack card) {
            event.getCards().add(card);
        }

        /** The passcodes of the pulled cards. */
        public List<Integer> getCodes() {
            return event.getCards().stream().map(CardItem::code).filter(code -> code != 0).toList();
        }
    }

    public static final class TournamentEnd extends ServerKubeEvent {
        private final TournamentEndEvent event;

        TournamentEnd(TournamentEndEvent event) {
            super(event.getServer());
            this.event = event;
        }

        public String getName() {
            return event.getName();
        }

        public String getFormat() {
            return event.getFormat();
        }

        /** Everyone, best first. */
        public List<Placement> getPlacements() {
            return event.getPlacements().stream().map(Placement::new).toList();
        }

        /** The online people who finished in {@code place} (1 for the winner). */
        public List<ServerPlayer> getPlayers(int place) {
            return event.getPlayers(place);
        }

        public List<ServerPlayer> getWinners() {
            return event.getPlayers(1);
        }

        public String getWinnerName() {
            return event.getWinnerName();
        }
    }

    public static final class RankChanged implements KubeEvent {
        private final RankChangeEvent event;

        RankChanged(RankChangeEvent event) {
            this.event = event;
        }

        public UUID getPlayerId() {
            return event.getPlayerId();
        }

        /** The player, or {@code null} if they went offline. */
        public @Nullable ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public String getName() {
            return event.getName();
        }

        public int getRatingBefore() {
            return event.getRatingBefore();
        }

        public int getRating() {
            return event.getRatingAfter();
        }

        public int getChange() {
            return event.getChange();
        }

        /** 0 Bronze, 1 Silver, 2 Gold, 3 Platinum, 4 Diamond, 5 Duel King. */
        public int getRankBefore() {
            return event.getRankBefore();
        }

        public int getRank() {
            return event.getRankAfter();
        }

        public String getRankName() {
            return event.getRankName();
        }

        /** Whether they reached a rank for the first time this season. */
        public boolean isPromoted() {
            return event.isPromoted();
        }
    }

    public static final class SetCompleted implements KubePlayerEvent {
        private final SetCompleteEvent event;

        SetCompleted(SetCompleteEvent event) {
            this.event = event;
        }

        @Override
        public ServerPlayer getEntity() {
            return event.getPlayer();
        }

        public String getSetId() {
            return event.getSetId();
        }

        public String getSetName() {
            return event.getSetName();
        }

        public int getCards() {
            return event.getCards();
        }
    }
}
