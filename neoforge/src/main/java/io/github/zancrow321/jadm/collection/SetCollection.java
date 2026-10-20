package io.github.zancrow321.jadm.collection;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.api.event.SetCompleteEvent;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.item.BinderItem;
import io.github.zancrow321.jadm.item.BoosterPackItem;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.DeckBoxItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.github.zancrow321.jadm.item.JadmItems;
import io.github.zancrow321.jadm.network.SetBookActionPayload;
import io.github.zancrow321.jadm.network.SetBookPayload;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server side of the Set Collection Book: which cards a player owns, and which sets' rewards each player has
 * claimed (saved by product id, once per player and set).
 */
public final class SetCollection extends SavedData {
    private static final String NAME = "jadm_sets";

    private final Map<UUID, Set<String>> claimed = new HashMap<>();

    public static SetCollection get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SetCollection::new, SetCollection::load, null), NAME);
    }

    public Set<String> claimed(UUID player) {
        return claimed.getOrDefault(player, Set.of());
    }

    /** Opens the book for a player, or with {@code open} off refreshes it if it is open. */
    public static int send(ServerPlayer player, boolean open) {
        SetCollection data = get(player.server);
        PacketDistributor.sendToPlayer(player, new SetBookPayload(open, List.copyOf(owned(player)),
                List.copyOf(data.claimed(player.getUUID())), reward()));
        return 1;
    }

    public static void handle(ServerPlayer player, SetBookActionPayload action) {
        switch (action.action()) {
            case REFRESH -> {
            }
            case CLAIM -> claim(player, action.id());
            case CLAIM_ALL -> {
                Set<Integer> owned = owned(player);
                Set<String> done = get(player.server).claimed(player.getUUID());
                for (SetBook.Entry set : SetBook.sets(player)) {
                    if (!done.contains(set.product().id()) && set.owned(owned) == set.size()) {
                        claim(player, set.product().id());
                    }
                }
            }
        }
        send(player, false);
    }

    /** Hands out a set's reward if the player owns every card of it and hasn't had the reward before. */
    private static void claim(ServerPlayer player, String id) {
        SetBook.Entry set = SetBook.set(player, id);
        SetCollection data = get(player.server);
        if (set == null || data.claimed(player.getUUID()).contains(id) || set.owned(owned(player)) < set.size()
                || !reward().any()) {
            return;
        }
        data.claimed.computeIfAbsent(player.getUUID(), u -> new HashSet<>()).add(id);
        data.setDirty();
        List<String> got = give(player, set);
        NeoForge.EVENT_BUS.post(new SetCompleteEvent(player, id, set.product().name(), set.size()));
        player.sendSystemMessage(Component.translatable("message.jadm.sets.claimed", set.product().name(),
                String.join(", ", got)).withStyle(ChatFormatting.GOLD));
        if (JadmServerConfig.SET_ANNOUNCE.get()) {
            Component text = Component.translatable("message.jadm.sets.announce", player.getDisplayName(),
                    set.product().name(), set.size()).withStyle(ChatFormatting.YELLOW);
            for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
                if (other != player) {
                    other.sendSystemMessage(text);
                }
            }
        }
    }

    /** What completing a set brings, as the server config says. */
    private static SetBookPayload.Reward reward() {
        return new SetBookPayload.Reward(Points.active() ? JadmServerConfig.SET_POINTS_PER_CARD.get() : 0,
                JadmServerConfig.POINTS.symbol.get(), JadmServerConfig.SET_PACKS.get(),
                JadmServerConfig.SET_EMERALDS.get(), JadmServerConfig.SET_XP.get());
    }

    /** @return one short line per thing given, e.g. "1,260 DP" */
    private static List<String> give(ServerPlayer player, SetBook.Entry set) {
        SetBookPayload.Reward reward = reward();
        List<String> lines = new ArrayList<>();
        long points = (long) reward.pointsPerCard() * set.size();
        if (points > 0) {
            Points.get(player.server).add(player.server, player.getUUID(), points);
            lines.add(Points.format(points));
        }
        for (int i = 0; i < reward.packs(); i++) {
            BoosterSets.BoosterSet pack = BoosterPackItem.randomSet(player.getRandom());
            if (pack == null) {
                break;
            }
            ItemStack stack = BoosterPackItem.of(pack.id());
            lines.add(stack.getHoverName().getString());
            hand(player, stack);
        }
        if (reward.emeralds() > 0) {
            lines.add(reward.emeralds() + (reward.emeralds() == 1 ? " emerald" : " emeralds"));
            for (int left = reward.emeralds(); left > 0; left -= 64) {
                hand(player, new ItemStack(Items.EMERALD, Math.min(64, left)));
            }
        }
        if (reward.xp() > 0) {
            player.giveExperiencePoints(reward.xp());
            lines.add(reward.xp() + " experience");
        }
        return lines;
    }

    private static void hand(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ---- what a player owns

    /**
     * The different cards a player owns, as original passcodes: in binders, deck boxes and loose cards in the
     * inventory and the ender chest, also inside shulker boxes there.
     */
    public static Set<Integer> owned(ServerPlayer player) {
        Set<Integer> owned = new HashSet<>();
        collect(player.getInventory(), owned);
        collect(player.getEnderChestInventory(), owned);
        return owned;
    }

    private static void collect(Container container, Set<Integer> owned) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            collect(container.getItem(slot), owned, true);
        }
    }

    private static void collect(ItemStack stack, Set<Integer> owned, boolean nested) {
        if (stack.isEmpty()) {
            return;
        }
        if (stack.is(JadmItems.CARD.get())) {
            int code = CardItem.code(stack);
            if (code != 0) {
                owned.add(SetBook.original(code));
            }
        } else if (stack.is(JadmItems.BINDER.get())) {
            BinderItem.collection(stack).counts().forEach((code, n) -> {
                if (n > 0) {
                    owned.add(SetBook.original(code));
                }
            });
        } else if (stack.is(JadmItems.DECK_BOX.get())) {
            JadmComponents.DeckList deck = DeckBoxItem.deck(stack);
            deck.main().forEach(code -> owned.add(SetBook.original(code)));
            deck.extra().forEach(code -> owned.add(SetBook.original(code)));
        } else if (nested) {
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) {
                contents.nonEmptyItems().forEach(inner -> collect(inner, owned, false));
            }
        }
    }

    // ---- saving

    private static SetCollection load(CompoundTag tag, HolderLookup.Provider registries) {
        SetCollection data = new SetCollection();
        CompoundTag players = tag.getCompound("Claimed");
        for (String key : players.getAllKeys()) {
            Set<String> ids = new HashSet<>();
            for (Tag t : players.getList(key, Tag.TAG_STRING)) {
                ids.add(t.getAsString());
            }
            data.claimed.put(UUID.fromString(key), ids);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        claimed.forEach((uuid, ids) -> {
            ListTag list = new ListTag();
            ids.forEach(id -> list.add(StringTag.valueOf(id)));
            players.put(uuid.toString(), list);
        });
        tag.put("Claimed", players);
        return tag;
    }
}
