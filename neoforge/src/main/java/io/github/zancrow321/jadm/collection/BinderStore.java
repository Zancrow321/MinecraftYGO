package io.github.zancrow321.jadm.collection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.compat.CuriosCompat;
import io.github.zancrow321.jadm.item.BinderItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.github.zancrow321.jadm.item.JadmComponents.CardCollection;
import io.github.zancrow321.jadm.item.JadmComponents.CardFoils;
import io.github.zancrow321.jadm.item.JadmItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The collections of player-bound binders ({@code bindBindersToPlayers}), one per player UUID, kept by the server in
 * {@code jadm/binders/<uuid>.json} outside the world (or inside it, see {@code boundBinderStorage}). A bound binder
 * is only a window onto its owner's collection: every binder bound to a player shows the same cards, so losing one,
 * or the whole world, loses nothing.
 *
 * <p>The files are written when the world saves (autosave, {@code /save-all}, shutdown) and when the player logs
 * out, so after a crash the collection rolls back to the same moment as the world and nothing is duplicated or lost
 * between the two.
 */
public final class BinderStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** One player's collection; {@code name} is their last known name, for people reading the files. */
    public record Entry(String name, CardCollection cards, CardFoils foils) {
        public static final Entry EMPTY = new Entry("", CardCollection.EMPTY, CardFoils.EMPTY);
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name),
                CardCollection.CODEC.optionalFieldOf("cards", CardCollection.EMPTY).forGetter(Entry::cards),
                CardFoils.CODEC.optionalFieldOf("foils", CardFoils.EMPTY).forGetter(Entry::foils)
        ).apply(i, Entry::new));

        public boolean isEmpty() {
            return cards.counts().isEmpty();
        }
    }

    private static volatile MinecraftServer server;
    private static Path dir;
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static int ticks;

    private BinderStore() {
    }

    public static void started(MinecraftServer started) {
        ENTRIES.clear();
        DIRTY.clear();
        dir = null;
        server = started;
    }

    public static void stopped() {
        saveAll();
        server = null;
        dir = null;
        ENTRIES.clear();
        DIRTY.clear();
    }

    /** Whether this is the server thread, the only place the collections are read and changed. */
    public static boolean onServer() {
        MinecraftServer s = server;
        return s != null && s.isSameThread();
    }

    private static Path dir() {
        if (dir == null) {
            dir = ("world".equals(JadmServerConfig.BOUND_BINDER_STORAGE.get())
                    ? server.getWorldPath(LevelResource.ROOT) : server.getServerDirectory())
                    .resolve("jadm").resolve("binders").toAbsolutePath().normalize();
        }
        return dir;
    }

    private static Path file(UUID id) {
        return dir().resolve(id + ".json");
    }

    /** A player's collection, read from its file the first time. */
    public static Entry get(UUID id) {
        return ENTRIES.computeIfAbsent(id, BinderStore::load);
    }

    public static void put(UUID id, Entry entry) {
        ENTRIES.put(id, entry);
        DIRTY.add(id);
    }

    private static Entry load(UUID id) {
        Path file = file(id);
        if (!Files.exists(file)) {
            return Entry.EMPTY;
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return Entry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        } catch (IOException | RuntimeException e) {
            // Keep the broken file (and the .bak beside it) for an admin to look at; the player starts over until then.
            Path broken = file.resolveSibling(id + ".broken-" + System.currentTimeMillis() + ".json");
            LOGGER.error("Can't read the binder collection {}; moved it to {}", file, broken, e);
            try {
                Files.move(file, broken);
            } catch (IOException moveFailed) {
                LOGGER.error("Can't move {} aside", file, moveFailed);
            }
            return Entry.EMPTY;
        }
    }

    /** Writes every changed collection. */
    public static void saveAll() {
        if (server == null) {
            return;
        }
        for (UUID id : List.copyOf(DIRTY)) {
            save(id);
        }
    }

    public static void save(UUID id) {
        if (server == null || !DIRTY.remove(id)) {
            return;
        }
        Entry entry = ENTRIES.getOrDefault(id, Entry.EMPTY);
        Path file = file(id);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(id + ".json.tmp");
            Files.writeString(tmp, GSON.toJson(Entry.CODEC.encodeStart(JsonOps.INSTANCE, entry).getOrThrow()),
                    StandardCharsets.UTF_8);
            if (Files.exists(file)) {
                Files.copy(file, file.resolveSibling(id + ".json.bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            DIRTY.add(id);
            LOGGER.error("Can't save the binder collection {}", file, e);
        }
    }

    /** Every binder bound to {@code player} that they carry: inventory (off hand included) and Curios slot. */
    public static List<ItemStack> carried(Player player) {
        List<ItemStack> binders = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(JadmItems.BINDER.get()) && player.getUUID().equals(BinderItem.owner(stack))) {
                binders.add(stack);
            }
        }
        ItemStack curio = CuriosCompat.find(player, JadmItems.BINDER.get());
        if (player.getUUID().equals(BinderItem.owner(curio))) {
            binders.add(curio);
        }
        return binders;
    }

    /** Shows a changed collection on every binder of it the owner carries, so their screens update at once. */
    public static void changed(UUID owner) {
        MinecraftServer s = server;
        ServerPlayer player = s == null ? null : s.getPlayerList().getPlayer(owner);
        if (player != null) {
            carried(player).forEach(BinderItem::show);
        }
    }

    /** Once a second: binders in a Curios slot don't get inventory ticks, so they are kept up to date here. */
    public static void tick(MinecraftServer s) {
        if (++ticks % 20 != 0 || !CuriosCompat.LOADED) {
            return;
        }
        for (ServerPlayer player : s.getPlayerList().getPlayers()) {
            ItemStack curio = CuriosCompat.find(player, JadmItems.BINDER.get());
            if (!curio.isEmpty()) {
                BinderItem.claim(curio, player);
            }
        }
    }

    /** A player whose collection has cards but who carries no binder of theirs gets one, e.g. in a new world. */
    public static void onLogin(ServerPlayer player) {
        if (get(player.getUUID()).isEmpty() || !carried(player).isEmpty() || carriesInEnderChest(player)) {
            return;
        }
        give(player);
        player.sendSystemMessage(Component.translatable("message.jadm.binder.restored",
                get(player.getUUID()).cards().total()).withStyle(ChatFormatting.GREEN));
    }

    public static void onLogout(ServerPlayer player) {
        save(player.getUUID());
    }

    private static boolean carriesInEnderChest(ServerPlayer player) {
        var chest = player.getEnderChestInventory();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (player.getUUID().equals(BinderItem.owner(chest.getItem(slot)))) {
                return true;
            }
        }
        return false;
    }

    /** Gives the player a binder bound to them. */
    private static void give(ServerPlayer player) {
        ItemStack binder = new ItemStack(JadmItems.BINDER.get());
        BinderItem.bindTo(binder, player);
        if (!player.getInventory().add(binder)) {
            player.drop(binder, false);
        }
    }

    /**
     * {@code /jadm binder}: a new binder showing your collection. {@code /jadm binder give <player>} (operators) does
     * the same for someone else, {@code /jadm binder unbind <player>} turns the binder in their hand back into an
     * ordinary one holding their whole collection.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("binder")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!JadmServerConfig.BIND_BINDERS.get() && get(player.getUUID()).isEmpty()) {
                        ctx.getSource().sendFailure(Component.translatable("message.jadm.binder.not_bound"));
                        return 0;
                    }
                    if (!carried(player).isEmpty()) {
                        ctx.getSource().sendFailure(Component.translatable("message.jadm.binder.already"));
                        return 0;
                    }
                    give(player);
                    ctx.getSource().sendSuccess(() -> Component.translatable("message.jadm.binder.given",
                            get(player.getUUID()).cards().total()), false);
                    return 1;
                })
                .then(Commands.literal("give").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            give(target);
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.jadm.binder.gave",
                                    target.getGameProfile().getName(), get(target.getUUID()).cards().total()), true);
                            return 1;
                        })))
                .then(Commands.literal("unbind").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            if (JadmServerConfig.BIND_BINDERS.get()) {
                                ctx.getSource().sendFailure(Component.translatable(
                                        "message.jadm.binder.unbind_config"));
                                return 0;
                            }
                            ItemStack held = target.getMainHandItem();
                            if (!target.getUUID().equals(BinderItem.owner(held))) {
                                ctx.getSource().sendFailure(Component.translatable(
                                        "message.jadm.binder.unbind_hold", target.getGameProfile().getName()));
                                return 0;
                            }
                            Entry entry = get(target.getUUID());
                            held.remove(JadmComponents.BINDER_OWNER.get());
                            held.set(JadmComponents.COLLECTION.get(), entry.cards());
                            held.set(JadmComponents.FOILS.get(), entry.foils());
                            put(target.getUUID(), new Entry(entry.name(), CardCollection.EMPTY, CardFoils.EMPTY));
                            save(target.getUUID());
                            changed(target.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.jadm.binder.unbound",
                                    target.getGameProfile().getName(), entry.cards().total()), true);
                            return 1;
                        })));
    }
}
