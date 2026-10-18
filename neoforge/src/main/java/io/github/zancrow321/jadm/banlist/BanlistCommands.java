package io.github.zancrow321.jadm.banlist;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.engine.data.Banlist;
import io.github.zancrow321.jadm.engine.data.OfficialBanlists;
import io.github.zancrow321.jadm.network.OfficialBanlistsPayload;
import io.github.zancrow321.jadm.progression.Progress;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * {@code /jadm banlist}: which list the server plays with and the official ones it can pick; for operators
 * {@code update} (downloads the newest official lists and uses them at once), {@code reset} (back to the bundled
 * ones) and {@code use <list>} (sets the server's {@code banlist}).
 */
public final class BanlistCommands {
    private static final String IGNIS = "https://raw.githubusercontent.com/ProjectIgnis/LFLists/master/";
    private static final String YAML_YUGI = "https://dawnbrandbots.github.io/yaml-yugi-limit-regulation/";
    /** A list naming fewer cards than this is taken for a broken download. */
    private static final int MIN_CARDS = 20;

    /** Where each official list comes from; keep in step with tools/carddata/build_banlists.py. */
    private record Source(String id, String name, String url, boolean lflist, boolean points) {
    }

    private static final List<Source> SOURCES = List.of(
            new Source(OfficialBanlists.TCG, "TCG", IGNIS + "0TCG.lflist.conf", true, false),
            new Source("ocg", "OCG", IGNIS + "OCG.lflist.conf", true, false),
            new Source("traditional", "Traditional", IGNIS + "Traditional.lflist.conf", true, false),
            new Source("worlds", "Worlds", IGNIS + "World.lflist.conf", true, false),
            new Source("master-duel", "Master Duel", YAML_YUGI + "master-duel/current.vector.json", false, false),
            new Source(OfficialBanlists.GENESYS, "Genesys", YAML_YUGI + "genesys/current.vector.json", false, true),
            new Source("genesys-ocg", "Genesys (OCG)", YAML_YUGI + "ocg-genesys/current.vector.json", false, true));

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final AtomicBoolean UPDATING = new AtomicBoolean();
    private static volatile Map<Integer, List<Integer>> konamiIds;

    private BanlistCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("banlist")
                .executes(BanlistCommands::show)
                .then(Commands.literal("update").requires(source -> source.hasPermission(2))
                        .executes(BanlistCommands::update))
                .then(Commands.literal("reset").requires(source -> source.hasPermission(2))
                        .executes(BanlistCommands::reset))
                .then(Commands.literal("use").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("list", StringArgumentType.string())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(choices(), builder))
                                .executes(BanlistCommands::use)));
    }

    /** What the {@code banlist} setting can be: auto, none, the official lists and the files in config/jadm/banlists. */
    private static List<String> choices() {
        List<String> out = new ArrayList<>(List.of("auto", "none"));
        SOURCES.forEach(s -> out.add(s.id()));
        Path dir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(Jadm.MOD_ID).resolve("banlists");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
                        .map(n -> n.substring(0, n.length() - ".json".length())).sorted().forEach(out::add);
            } catch (IOException ignored) {
                // Only suggestions.
            }
        }
        return out;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Banlist current = source.getEntity() instanceof ServerPlayer player ? JadmData.banlist(player)
                : JadmData.banlist();
        source.sendSuccess(() -> Component.literal("Banlist: " + current.name() + " (banlist = \""
                + JadmServerConfig.BANLIST.get() + "\")" + (current.genesys() ? ", " + current.pointCap()
                + " Genesys points per deck" : "")).withStyle(ChatFormatting.GOLD), false);
        OfficialBanlists downloaded = JadmData.downloaded();
        for (Source s : SOURCES) {
            String name = s.id().equals(OfficialBanlists.TCG) ? JadmData.banlists().latest().name()
                    : JadmData.official().get(s.id()).name();
            boolean fresh = downloaded.get(s.id()) != null && downloaded.get(s.id()).name().equals(name);
            source.sendSuccess(() -> Component.literal("- " + s.id() + ": " + name
                    + (fresh ? " (downloaded)" : " (bundled)")), false);
        }
        if (source.hasPermission(2)) {
            source.sendSuccess(() -> Component.literal("/" + Jadm.COMMAND + " banlist update fetches the newest lists, "
                    + "/" + Jadm.COMMAND + " banlist use <list> picks one.").withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int use(CommandContext<CommandSourceStack> ctx) {
        String list = StringArgumentType.getString(ctx, "list").strip();
        CommandSourceStack source = ctx.getSource();
        if (!choices().stream().anyMatch(c -> c.equalsIgnoreCase(list))) {
            source.sendFailure(Component.literal("No banlist \"" + list + "\". Pick one of: "
                    + String.join(", ", choices())));
            return 0;
        }
        JadmServerConfig.BANLIST.set(list.toLowerCase(java.util.Locale.ROOT));
        JadmServerConfig.SPEC.save();
        syncAll(source.getServer());
        Banlist now = JadmData.banlist();
        source.sendSuccess(() -> Component.literal("The server now plays with " + now.name() + " ("
                + JadmData.ruleset(Progress.worldStep()).displayName() + " rules).").withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            Files.deleteIfExists(JadmData.DOWNLOADED_BANLISTS);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not delete " + JadmData.DOWNLOADED_BANLISTS + ": " + e));
            return 0;
        }
        JadmData.useDownloaded(new OfficialBanlists(List.of()));
        syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Back to the bundled official banlists; the TCG list is "
                + JadmData.banlists().latest().name() + "."), true);
        return 1;
    }

    private static int update(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!UPDATING.compareAndSet(false, true)) {
            source.sendFailure(Component.literal("The banlists are already being updated."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Downloading the official banlists..."), true);
        MinecraftServer server = source.getServer();
        CompletableFuture.supplyAsync(BanlistCommands::download).whenComplete((results, error) -> server.execute(() -> {
            try {
                if (error != null) {
                    Jadm.LOGGER.error("Banlist update failed", error);
                    source.sendFailure(Component.literal("The banlist update failed: " + error.getMessage()));
                } else {
                    apply(source, results);
                }
            } finally {
                UPDATING.set(false);
            }
        }));
        return 1;
    }

    /** One list's download: the list, or why there is none. */
    private record Result(Source source, OfficialBanlists.Entry entry, String error) {
    }

    /** Runs off the server thread. */
    private static List<Result> download() {
        List<Result> results = new ArrayList<>();
        for (Source s : SOURCES) {
            try {
                HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(s.url()))
                        .timeout(Duration.ofSeconds(30)).header("User-Agent", Jadm.MOD_ID).GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                OfficialBanlists.Entry entry = s.lflist()
                        ? OfficialBanlists.parseLflist(s.id(), s.name(), response.body())
                        : OfficialBanlists.parseVector(s.id(), s.name(), s.points(), response.body(), konamiIds());
                if (entry.size() < MIN_CARDS) {
                    throw new IOException("only " + entry.size() + " cards");
                }
                results.add(new Result(s, entry, null));
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                Jadm.LOGGER.warn("Could not download the {} banlist from {}", s.name(), s.url(), e);
                results.add(new Result(s, null, e.getMessage() == null ? e.getClass().getSimpleName()
                        : e.getMessage()));
            }
        }
        return results;
    }

    private static Map<Integer, List<Integer>> konamiIds() {
        if (konamiIds == null) {
            konamiIds = OfficialBanlists.loadKonamiIds();
        }
        return konamiIds;
    }

    /** On the server thread: keeps what came, tells the operator what changed and every client the new lists. */
    private static void apply(CommandSourceStack source, List<Result> results) {
        OfficialBanlists before = JadmData.official();
        String tcgBefore = JadmData.banlists().latest().name();
        OfficialBanlists kept = JadmData.downloaded();
        List<Component> lines = new ArrayList<>();
        for (Result r : results) {
            if (r.entry() == null) {
                lines.add(Component.literal("- " + r.source().name() + ": download failed (" + r.error()
                        + "), keeping " + currentName(r.source().id()) + ".").withStyle(ChatFormatting.RED));
                continue;
            }
            kept = kept.with(r.entry());
        }
        try {
            Path tmp = JadmData.DOWNLOADED_BANLISTS.resolveSibling(JadmData.DOWNLOADED_BANLISTS.getFileName() + ".tmp");
            Files.createDirectories(tmp.getParent());
            Files.writeString(tmp, kept.toJson(), StandardCharsets.UTF_8);
            Files.move(tmp, JadmData.DOWNLOADED_BANLISTS, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Jadm.LOGGER.error("Could not save {}", JadmData.DOWNLOADED_BANLISTS, e);
            lines.add(Component.literal("Could not save the lists (" + e.getMessage()
                    + "); they are used until the server stops.").withStyle(ChatFormatting.RED));
        }
        JadmData.useDownloaded(kept);
        for (Result r : results) {
            if (r.entry() == null) {
                continue;
            }
            String id = r.source().id();
            String now = currentName(id);
            boolean changed = id.equals(OfficialBanlists.TCG) ? !now.equals(tcgBefore)
                    : !JadmData.official().get(id).equals(before.get(id));
            boolean older = !now.equals(r.entry().name());
            lines.add(Component.literal("- " + r.source().name() + ": " + now + (older
                    ? " (the download, " + r.entry().name() + ", is older)"
                    : changed ? " (new)" : " (unchanged)")).withStyle(changed ? ChatFormatting.GREEN
                    : ChatFormatting.GRAY));
        }
        syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Official banlists updated:").withStyle(ChatFormatting.GOLD), true);
        lines.forEach(line -> {
            source.sendSuccess(() -> line, false);
            Jadm.LOGGER.info("Banlist update {}", line.getString());
        });
        source.sendSuccess(() -> Component.literal("The server plays with " + JadmData.banlist().name() + ".")
                .withStyle(ChatFormatting.GOLD), false);
    }

    private static String currentName(String id) {
        if (id.equals(OfficialBanlists.TCG)) {
            return JadmData.banlists().latest().name();
        }
        OfficialBanlists.Entry e = JadmData.official().get(id);
        return e == null ? "nothing" : e.name();
    }

    /** Tells a player's client which lists the server downloaded. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OfficialBanlistsPayload(
                JadmData.downloaded().lists().isEmpty() ? "" : JadmData.downloaded().toJson()));
    }

    private static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
            Progress.sync(player);
        }
    }
}
