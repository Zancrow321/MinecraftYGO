package io.github.zancrow321.jadm.bounty;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bounties: players put Duel Points on someone's head, and whoever beats that player in a duel between people collects
 * them. The points are taken when the bounty is put up and kept here until someone collects them, the one who put
 * them up takes them back, or an operator clears the bounty. Bounties on the same player add up.
 */
public final class Bounties extends SavedData {
    private static final String NAME = "jadm_bounties";

    /** The bounty on one player: who put up how much, in the order they did. */
    public static final class Bounty {
        private String name;
        private final Map<UUID, Long> amounts = new LinkedHashMap<>();
        private final Map<UUID, String> names = new HashMap<>();

        private Bounty(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public long total() {
            return amounts.values().stream().mapToLong(Long::longValue).sum();
        }

        /** How many players put something up. */
        public int backers() {
            return amounts.size();
        }

        public long from(UUID backer) {
            return amounts.getOrDefault(backer, 0L);
        }

        public Map<UUID, Long> amounts() {
            return amounts;
        }

        public String backerName(UUID backer) {
            return names.getOrDefault(backer, "?");
        }
    }

    /** One player's collected share of a bounty, for the result screen. */
    public record Claim(UUID winner, long amount, String target) {
    }

    private final Map<UUID, Bounty> bounties = new HashMap<>();

    public static Bounties get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Bounties::new, Bounties::load, null), NAME);
    }

    public Optional<Bounty> on(UUID target) {
        return Optional.ofNullable(bounties.get(target));
    }

    public long total(UUID target) {
        Bounty bounty = bounties.get(target);
        return bounty == null ? 0 : bounty.total();
    }

    /** The biggest bounties first. */
    public List<Map.Entry<UUID, Bounty>> top(int count) {
        return bounties.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<UUID, Bounty> e) -> e.getValue().total()).reversed())
                .limit(count).toList();
    }

    /** The UUID of a player with a bounty on them, by the name they had when it was last raised. */
    public Optional<UUID> byName(String name) {
        return bounties.entrySet().stream().filter(e -> e.getValue().name.equalsIgnoreCase(name))
                .map(Map.Entry::getKey).findFirst();
    }

    public List<String> names() {
        return bounties.values().stream().map(Bounty::name).toList();
    }

    /** The fee for putting up {@code amount}, rounded up. */
    public static long fee(long amount) {
        return (amount * JadmServerConfig.BOUNTY.feePercent.get() + 99) / 100;
    }

    /** Adds {@code amount} to the bounty on {@code target}; the points (and the fee) must already be taken. */
    public void raise(MinecraftServer server, UUID target, String targetName, ServerPlayer backer, long amount) {
        Bounty bounty = bounties.computeIfAbsent(target, id -> new Bounty(targetName));
        bounty.name = targetName;
        bounty.amounts.merge(backer.getUUID(), amount, Long::sum);
        bounty.names.put(backer.getUUID(), backer.getScoreboardName());
        setDirty();
        refreshTabList(server, target);
    }

    /** Takes back what {@code backer} put on {@code target} and pays it back. @return how much, 0 if nothing */
    public long withdraw(MinecraftServer server, UUID target, UUID backer) {
        Bounty bounty = bounties.get(target);
        if (bounty == null) {
            return 0;
        }
        Long amount = bounty.amounts.remove(backer);
        if (amount == null) {
            return 0;
        }
        bounty.names.remove(backer);
        if (bounty.amounts.isEmpty()) {
            bounties.remove(target);
        }
        setDirty();
        Points.get(server).add(server, backer, amount);
        refreshTabList(server, target);
        return amount;
    }

    /** Removes the bounty on {@code target} and pays everyone back what they put up. @return the total paid back */
    public long clear(MinecraftServer server, UUID target) {
        Bounty bounty = bounties.remove(target);
        if (bounty == null) {
            return 0;
        }
        setDirty();
        Points points = Points.get(server);
        bounty.amounts.forEach((backer, amount) -> {
            points.add(server, backer, amount);
            ServerPlayer online = server.getPlayerList().getPlayer(backer);
            if (online != null) {
                online.sendSystemMessage(Component.literal("The bounty on " + bounty.name + " was called off; you "
                        + "got your " + Points.format(amount) + " back.").withStyle(ChatFormatting.GOLD));
            }
        });
        refreshTabList(server, target);
        return bounty.total();
    }

    /**
     * {@code loser} lost a duel to {@code winners}, all of them people: they share the bounty on {@code loser}
     * equally, the first one getting what doesn't divide.
     *
     * @return each winner's share, or nothing if there was no bounty
     */
    public List<Claim> claim(MinecraftServer server, UUID loser, List<UUID> winners, List<String> winnerNames) {
        Bounty bounty = bounties.get(loser);
        if (bounty == null || winners.isEmpty()) {
            return List.of();
        }
        bounties.remove(loser);
        setDirty();
        long total = bounty.total();
        long share = total / winners.size();
        List<Claim> claims = new ArrayList<>();
        Points points = Points.get(server);
        for (int i = 0; i < winners.size(); i++) {
            long amount = share + (i == 0 ? total % winners.size() : 0);
            points.add(server, winners.get(i), amount);
            claims.add(new Claim(winners.get(i), amount, bounty.name));
        }
        String by = String.join(" & ", winnerNames);
        if (JadmServerConfig.BOUNTY.announce.get()) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(by + " beat " + bounty.name
                    + " and collected the bounty of " + Points.format(total) + "!").withStyle(ChatFormatting.GOLD),
                    false);
        } else {
            for (UUID backer : bounty.amounts.keySet()) {
                ServerPlayer online = server.getPlayerList().getPlayer(backer);
                if (online != null && !winners.contains(backer)) {
                    online.sendSystemMessage(Component.literal(by + " beat " + bounty.name
                            + " and collected your bounty.").withStyle(ChatFormatting.GOLD));
                }
            }
        }
        refreshTabList(server, loser);
        return claims;
    }

    /** On login: tells a player about the bounty on them. */
    public static void login(ServerPlayer player) {
        long total = get(player.server).total(player.getUUID());
        if (total > 0 && JadmServerConfig.BOUNTY.enabled.get()) {
            player.sendSystemMessage(Component.literal("There is a bounty of " + Points.format(total)
                    + " on you. Whoever beats you in a duel collects it.").withStyle(ChatFormatting.RED));
        }
    }

    /** Puts the bounty after a player's name in the player list. */
    public static void tabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !JadmServerConfig.BOUNTY.enabled.get()
                || !JadmServerConfig.BOUNTY.showInTabList.get()) {
            return;
        }
        long total = get(player.server).total(player.getUUID());
        if (total > 0) {
            Component name = event.getDisplayName() != null ? event.getDisplayName() : player.getDisplayName();
            event.setDisplayName(Component.empty().append(name).append(Component.literal(" [" + Points.format(total)
                    + "]").withStyle(ChatFormatting.RED)));
        }
    }

    private static void refreshTabList(MinecraftServer server, UUID target) {
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null) {
            online.refreshTabListName();
        }
    }

    private static Bounties load(CompoundTag tag, HolderLookup.Provider registries) {
        Bounties data = new Bounties();
        CompoundTag all = tag.getCompound("bounties");
        for (String key : all.getAllKeys()) {
            CompoundTag entry = all.getCompound(key);
            Bounty bounty = new Bounty(entry.getString("name"));
            for (Tag t : entry.getList("backers", Tag.TAG_COMPOUND)) {
                CompoundTag backer = (CompoundTag) t;
                UUID id = backer.getUUID("id");
                bounty.amounts.put(id, backer.getLong("amount"));
                bounty.names.put(id, backer.getString("name"));
            }
            if (!bounty.amounts.isEmpty()) {
                data.bounties.put(UUID.fromString(key), bounty);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        bounties.forEach((target, bounty) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", bounty.name);
            ListTag backers = new ListTag();
            bounty.amounts.forEach((id, amount) -> {
                CompoundTag backer = new CompoundTag();
                backer.putUUID("id", id);
                backer.putLong("amount", amount);
                backer.putString("name", bounty.backerName(id));
                backers.add(backer);
            });
            entry.put("backers", backers);
            all.put(target.toString(), entry);
        });
        tag.put("bounties", all);
        return tag;
    }
}
