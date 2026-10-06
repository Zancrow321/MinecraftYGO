package io.github.zancrow321.minecraftygo.duel;

import io.github.zancrow321.minecraftygo.item.CardItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Holds ante cards while their duel runs, saved with the world so a crash or restart can't lose them. When the duel
 * ends the cards are owed to the winner (or back to their owners after a draw, a crash or a restart) and handed
 * over as soon as that player is online.
 */
public final class AnteEscrow extends SavedData {
    private static final String NAME = "minecraftygo_ante";

    /**
     * @param duel  the running duel, or {@code null} once settled
     * @param payTo who gets the card when it is handed over
     */
    private record Entry(UUID duel, UUID owner, int code, UUID payTo) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public static AnteEscrow get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AnteEscrow::new, AnteEscrow::load, null), NAME);
    }

    /** Puts {@code owner}'s card up for {@code duel}. */
    public void put(UUID duel, UUID owner, int code) {
        entries.add(new Entry(duel, owner, code, owner));
        setDirty();
    }

    /**
     * Ends {@code duel}'s ante: every card goes to {@code winner}, or back to its owner if {@code winner} is
     * {@code null}. Hands over what it can right away.
     */
    /** A card put up for a duel and the person it came from. */
    public record Stake(int code, UUID owner) {
    }

    /** @return the cards held for {@code duel} */
    public List<Stake> stakes(UUID duel) {
        List<Stake> out = new ArrayList<>();
        for (Entry e : entries) {
            if (duel.equals(e.duel())) {
                out.add(new Stake(e.code(), e.owner()));
            }
        }
        return out;
    }

    public void settle(MinecraftServer server, UUID duel, UUID winner) {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (duel.equals(e.duel())) {
                entries.set(i, new Entry(null, e.owner(), e.code(), winner == null ? e.owner() : winner));
            }
        }
        setDirty();
        server.getPlayerList().getPlayers().forEach(this::deliver);
    }

    /** After a restart no duel is running any more, so whatever is still held goes back to its owner. */
    public void refundUnsettled() {
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.duel() != null) {
                entries.set(i, new Entry(null, e.owner(), e.code(), e.owner()));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    /** Hands {@code player} every settled card owed to them. */
    public void deliver(ServerPlayer player) {
        boolean changed = false;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.duel() != null || !e.payTo().equals(player.getUUID())) {
                continue;
            }
            ItemStack card = CardItem.of(e.code());
            Component name = card.getHoverName();
            if (!player.getInventory().add(card)) {
                player.drop(card, false);
            }
            player.sendSystemMessage(Component.literal(e.owner().equals(player.getUUID())
                    ? "Your ante card came back: " : "You won the ante card ").append(name));
            it.remove();
            changed = true;
        }
        if (changed) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag entry = new CompoundTag();
            if (e.duel() != null) {
                entry.putUUID("duel", e.duel());
            }
            entry.putUUID("owner", e.owner());
            entry.putInt("code", e.code());
            entry.putUUID("payTo", e.payTo());
            list.add(entry);
        }
        tag.put("entries", list);
        return tag;
    }

    private static AnteEscrow load(CompoundTag tag, HolderLookup.Provider registries) {
        AnteEscrow escrow = new AnteEscrow();
        for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            escrow.entries.add(new Entry(entry.hasUUID("duel") ? entry.getUUID("duel") : null,
                    entry.getUUID("owner"), entry.getInt("code"), entry.getUUID("payTo")));
        }
        return escrow;
    }
}
