package io.github.zancrow321.jadm.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** A player owns every card of a set for the first time and got its Set Collection Book reward. */
public final class SetCompleteEvent extends Event {
    private final ServerPlayer player;
    private final String setId;
    private final String setName;
    private final int cards;

    public SetCompleteEvent(ServerPlayer player, String setId, String setName, int cards) {
        this.player = player;
        this.setId = setId;
        this.setName = setName;
        this.cards = cards;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public String getSetId() {
        return setId;
    }

    public String getSetName() {
        return setName;
    }

    /** How many different cards the set has. */
    public int getCards() {
        return cards;
    }
}
