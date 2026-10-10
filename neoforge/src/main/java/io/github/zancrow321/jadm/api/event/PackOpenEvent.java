package io.github.zancrow321.jadm.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * A player opened a booster pack. The list of cards can be changed: what is in it when the event is done is what the
 * player gets and sees in the pack opening; any item may go in, only cards are shown.
 */
public final class PackOpenEvent extends Event {
    private final ServerPlayer player;
    private final String setId;
    private final String setCode;
    private final String setName;
    private final List<ItemStack> cards;

    public PackOpenEvent(ServerPlayer player, String setId, String setCode, String setName, List<ItemStack> cards) {
        this.player = player;
        this.setId = setId;
        this.setCode = setCode;
        this.setName = setName;
        this.cards = new ArrayList<>(cards);
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** The booster set's id, e.g. {@code legend_of_blue_eyes_white_dragon}. */
    public String getSetId() {
        return setId;
    }

    /** The set's printed code, e.g. {@code LOB}. */
    public String getSetCode() {
        return setCode;
    }

    public String getSetName() {
        return setName;
    }

    /** The pulled cards, in pack order (the rarest last); change it to change what the player gets. */
    public List<ItemStack> getCards() {
        return cards;
    }
}
