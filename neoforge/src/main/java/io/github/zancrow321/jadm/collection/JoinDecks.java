package io.github.zancrow321.jadm.collection;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.item.DeckBoxItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.github.zancrow321.jadm.item.JadmItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Decks a server hands to particular players: every {@code .ydk} file in {@code config/jadm/join_decks/<uuid>/} (or
 * a single {@code config/jadm/join_decks/<uuid>.ydk}) becomes a deck box with those cards, given to that player on
 * their next join. Each file is given once per world, so dropping in a new file later gives just that deck.
 * {@code /jadm joindecks give <player>} gives them all again.
 *
 * <p>The mod also brings decks of its own for particular players, in {@code /jadm/join_decks/<uuid>/} inside the
 * jar and listed in {@link #BUNDLED}; {@code bundledJoinDecks} in the server config turns them off.
 */
public final class JoinDecks {
    private static final String README = """
            Decks for particular players, given once per world when they join.

            Put a YDK deck list (as YGOPro, EDOPro, YGOPRODeck or the deck box's Export button write it) into a
            folder named after the player's UUID:

                config/jadm/join_decks/<uuid>/<deck name>.ydk

            or name the file itself after the UUID: config/jadm/join_decks/<uuid>.ydk. The player gets a deck box
            named after the file, holding the cards themselves (they can take them out like any deck box's cards).
            Each file is given once per world; a file added later is given on the player's next join, and
            /jadm joindecks give <player> gives all of a player's decks again.
            """;

    /** The decks inside the jar, by player: the mod author's own deck. */
    private static final Map<UUID, List<String>> BUNDLED = Map.of(
            UUID.fromString("e4c7e475-c1ff-4f94-956c-ac5be02ce04a"), List.of("dARKLORD"));

    private JoinDecks() {
    }

    /** A deck to give: its name (the deck box's), the key it is remembered by, and how to read it. */
    private record Source(String name, String key, Reader reader) {
    }

    private interface Reader {
        String read() throws IOException;
    }

    static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve("jadm").resolve("join_decks");
    }

    /** Creates the folder with a short explanation, so server owners find it. */
    public static void prepare() {
        Path folder = folder();
        try {
            Files.createDirectories(folder);
            Path readme = folder.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, README, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Jadm.LOGGER.warn("Could not create {}", folder, e);
        }
    }

    /** A player's decks: the mod's own first (if turned on), then the config folder's in name order. */
    private static List<Source> sources(UUID player) {
        List<Source> sources = new ArrayList<>();
        if (JadmServerConfig.BUNDLED_JOIN_DECKS.get()) {
            for (String name : BUNDLED.getOrDefault(player, List.of())) {
                String resource = "/jadm/join_decks/" + player + "/" + name + ".ydk";
                sources.add(new Source(name, "bundled/" + player + "/" + name + ".ydk", () -> {
                    try (InputStream in = JoinDecks.class.getResourceAsStream(resource)) {
                        if (in == null) {
                            throw new IOException("Missing " + resource);
                        }
                        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }));
            }
        }
        Map<String, Path> files = new TreeMap<>();
        Path folder = folder();
        Path single = folder.resolve(player + ".ydk");
        if (Files.isRegularFile(single)) {
            files.put(single.getFileName().toString(), single);
        }
        Path own = folder.resolve(player.toString());
        if (Files.isDirectory(own)) {
            try (Stream<Path> list = Files.list(own)) {
                list.filter(p -> Files.isRegularFile(p)
                                && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ydk"))
                        .forEach(p -> files.put(player + "/" + p.getFileName(), p));
            } catch (IOException e) {
                Jadm.LOGGER.warn("Could not list {}", own, e);
            }
        }
        files.forEach((key, file) -> {
            String name = file.getFileName().toString();
            name = name.substring(0, name.length() - ".ydk".length());
            sources.add(new Source(name, key, () -> Files.readString(file, StandardCharsets.UTF_8)));
        });
        return sources;
    }

    /** Gives the player each of their decks they haven't been given in this world. */
    public static void onLogin(ServerPlayer player) {
        List<Source> sources = sources(player.getUUID());
        if (sources.isEmpty()) {
            return;
        }
        Data data = Data.get(player.server);
        for (Source source : sources) {
            if (!data.given(player.getUUID(), source.key()) && give(player, source)) {
                data.give(player.getUUID(), source.key());
            }
        }
    }

    /** @return how many of the player's decks were given */
    static int giveAll(ServerPlayer player) {
        Data data = Data.get(player.server);
        int given = 0;
        for (Source source : sources(player.getUUID())) {
            if (give(player, source)) {
                data.give(player.getUUID(), source.key());
                given++;
            }
        }
        return given;
    }

    /**
     * Puts the deck from {@code source} into a deck box in the player's inventory. Passcodes the card data doesn't know
     * are left out and named in chat; the side deck isn't kept, as deck boxes have none.
     *
     * @return whether the file could be read
     */
    private static boolean give(ServerPlayer player, Source source) {
        String name = source.name();
        if (name.equalsIgnoreCase(player.getUUID().toString())) {
            name = player.getGameProfile().getName();
        }
        Deck deck;
        try {
            String text = source.reader().read().replace("\uFEFF", "");
            deck = Deck.parseYdk(name, text);
        } catch (IOException | RuntimeException e) {
            Jadm.LOGGER.warn("Could not read the join deck {}", source.key(), e);
            return false;
        }
        Set<String> unknown = new LinkedHashSet<>();
        List<Integer> main = known(deck.main(), unknown);
        List<Integer> extra = known(deck.extra(), unknown);
        ItemStack box = new ItemStack(JadmItems.DECK_BOX.get());
        box.set(JadmComponents.DECK.get(), new JadmComponents.DeckList(main, extra));
        box.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        // Checked before the box goes into the inventory, which empties the stack it is handed.
        List<String> problems = DeckBoxItem.problems(box, player, JadmData.banlist(player));
        if (!player.getInventory().add(box)) {
            player.drop(box, false);
        }
        player.sendSystemMessage(Component.translatable("message.jadm.join_deck.given", name, main.size(),
                extra.size()).withStyle(ChatFormatting.GREEN));
        if (!unknown.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.jadm.join_deck.unknown",
                    String.join(", ", unknown)).withStyle(ChatFormatting.GRAY));
        }
        if (!problems.isEmpty()) {
            player.sendSystemMessage(Component.literal(problems.get(0)).withStyle(ChatFormatting.GRAY));
        }
        Jadm.LOGGER.info("Gave {} the join deck {} ({} + {} cards)", player.getGameProfile().getName(), source.key(),
                main.size(), extra.size());
        return true;
    }

    private static List<Integer> known(List<Integer> codes, Set<String> unknown) {
        List<Integer> known = new ArrayList<>();
        for (int code : codes) {
            CardInfo card = JadmData.cards().card(code);
            if (card == null) {
                unknown.add(String.valueOf(code));
            } else {
                known.add(code);
            }
        }
        return known;
    }

    /** {@code /jadm joindecks give <player>}, for operators. */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("joindecks")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            int given = giveAll(target);
                            if (given == 0) {
                                ctx.getSource().sendFailure(Component.translatable("message.jadm.join_deck.none",
                                        target.getGameProfile().getName(), target.getUUID().toString()));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "message.jadm.join_deck.gave", given, target.getGameProfile().getName()), true);
                            return given;
                        })));
    }

    /** Which deck files each player has been given in this world. */
    static final class Data extends SavedData {
        private static final String NAME = "jadm_join_decks";
        private final Map<UUID, Set<String>> given = new HashMap<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        boolean given(UUID player, String file) {
            return given.getOrDefault(player, Set.of()).contains(file.toLowerCase(Locale.ROOT));
        }

        void give(UUID player, String file) {
            if (given.computeIfAbsent(player, p -> new HashSet<>()).add(file.toLowerCase(Locale.ROOT))) {
                setDirty();
            }
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            CompoundTag players = tag.getCompound("Players");
            for (String key : players.getAllKeys()) {
                Set<String> files = new HashSet<>();
                for (Tag t : players.getList(key, Tag.TAG_STRING)) {
                    files.add(t.getAsString());
                }
                data.given.put(UUID.fromString(key), files);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag players = new CompoundTag();
            given.forEach((player, files) -> {
                ListTag list = new ListTag();
                files.forEach(f -> list.add(StringTag.valueOf(f)));
                players.put(player.toString(), list);
            });
            tag.put("Players", players);
            return tag;
        }
    }
}
