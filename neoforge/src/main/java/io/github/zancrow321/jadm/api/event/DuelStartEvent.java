package io.github.zancrow321.jadm.api.event;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * A duel is about to start; decks and the field are ready. Cancel it to stop the duel: everyone in it reads the
 * {@linkplain #setCancelMessage cancel message}.
 */
public final class DuelStartEvent extends Event implements ICancellableEvent {
    private final DuelInfo duel;
    private String cancelMessage = "The duel was stopped by the server.";

    public DuelStartEvent(DuelInfo duel) {
        this.duel = duel;
    }

    public DuelInfo getDuel() {
        return duel;
    }

    public String getCancelMessage() {
        return cancelMessage;
    }

    public void setCancelMessage(String message) {
        cancelMessage = message;
    }
}
