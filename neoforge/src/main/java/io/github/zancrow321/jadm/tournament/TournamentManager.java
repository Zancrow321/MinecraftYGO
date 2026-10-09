package io.github.zancrow321.jadm.tournament;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.arena.DuelArena;
import io.github.zancrow321.jadm.duel.DuelManager;
import io.github.zancrow321.jadm.duel.MatchSetup;
import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.Ruleset;
import io.github.zancrow321.jadm.engine.ai.DuelistAi;
import io.github.zancrow321.jadm.engine.data.Banlist;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.duel.DuelTable;
import io.github.zancrow321.jadm.engine.tournament.Bracket;
import io.github.zancrow321.jadm.entity.DuelistNpc;
import io.github.zancrow321.jadm.entity.JadmEntities;
import io.github.zancrow321.jadm.item.DeckBoxItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.github.zancrow321.jadm.item.JadmItems;
import io.github.zancrow321.jadm.network.TournamentPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the server's tournament: registration, NPC fillers, pairings, calling each match to a free duel arena,
 * the games themselves (through {@link DuelManager}), no-shows, prizes and the entry fee pot. One tournament at a
 * time; it is saved with the world, so a restart only replays the games that were being played. All methods run on
 * the server thread.
 */
public final class TournamentManager {
    private static final String STARTER_DECK = "starter_yugi";
    private static final List<String> NPC_NAMES = List.of("Pack Ripper", "Deck Master", "Trap Lover",
            "Fusion Fan", "Ritual Keeper", "Graveyard Digger", "Side Deck Sage", "Combo Kid", "Top Decker",
            "Mirror Force Mike", "Coin Flipper", "Burn Baron");
    /** NPC-only games are played out a few steps per tick; this many at once at most. */
    private static final int BOT_GAMES_AT_ONCE = 2;
    private static final int BOT_TURN_CAP = 80;
    /**
     * How long after a match people stay put before they are sent back: the podiums come down and the last of the
     * duel plays out on their screen.
     */
    private static final long RETURN_DELAY = 8000;

    private static TournamentManager instance;

    private final MinecraftServer server;
    private final TournamentData data;
    /** match id -> an NPC-only game being played out (not saved: a restart plays it again) */
    private final Map<Integer, BotGame> botGames = new HashMap<>();
    /** player -> where to send them, and from when on */
    private final Map<UUID, Long> returnDue = new HashMap<>();
    private long lastSecond;

    private record BotGame(DuelTable table, Tournament tournament) {
    }

    private TournamentManager(MinecraftServer server) {
        this.server = server;
        this.data = TournamentData.get(server);
        Tournament t = data.store.current;
        if (t != null) {
            // Games that were being played when the server stopped are played again.
            for (Tournament.Live live : t.live.values()) {
                if (live.phase.equals(Tournament.Live.DUELING) || live.phase.equals(Tournament.Live.CALLED)) {
                    live.phase = Tournament.Live.PAUSE;
                    live.startsAt = now() + 30_000;
                    live.ready.clear();
                }
            }
        }
    }

    public static TournamentManager get(MinecraftServer server) {
        if (instance == null || instance.server != server) {
            instance = new TournamentManager(server);
        }
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.botGames.values().forEach(g -> g.table().close());
            instance = null;
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    Tournament current() {
        return data.store.current;
    }

    private void changed() {
        data.setDirty();
        Tournament t = current();
        if (t != null) {
            String json = TournamentData.GSON.toJson(TournamentView.of(t, this));
            PacketDistributor.sendToAllPlayers(new TournamentPayload("", json));
        }
    }

    // ---- Hosting and joining ------------------------------------------------------------------------------------

    /** Whether {@code player} may run the host commands for the current (or a new) tournament. */
    boolean canHost(ServerPlayer player) {
        Tournament t = current();
        return player.hasPermissions(2) || TournamentOptions.playersCanHost()
                && (t == null || !t.active() || player.getUUID().equals(t.host));
    }

    /**
     * Opens a tournament for registration.
     *
     * @param host {@code null} for a scheduled one
     * @return what went wrong, or {@code null}
     */
    String create(ServerPlayer host, String format, String name) {
        Tournament old = current();
        if (old != null && old.active()) {
            return "\"" + old.name + "\" is still " + (old.state.equals(Tournament.OPEN) ? "open" : "running")
                    + ". Cancel it first with /jadm tournament cancel.";
        }
        Tournament t = new Tournament();
        t.settings = TournamentOptions.snapshot();
        if (format != null) {
            if (Bracket.Format.parse(format) == null) {
                return "Unknown format \"" + format + "\": single, double, swiss or roundrobin.";
            }
            t.settings.put("format", format);
        }
        t.host = host == null ? null : host.getUUID();
        t.hostName = host == null ? "the server" : host.getScoreboardName();
        t.name = name != null && !name.isBlank() ? name.strip()
                : host == null ? TournamentOptions.scheduledName() : host.getScoreboardName() + "'s Tournament";
        t.openedAt = now();
        int minutes = t.integer("registrationMinutes");
        t.closesAt = minutes > 0 ? t.openedAt + minutes * 60_000L : 0;
        data.store.current = t;
        t.news("Opened by " + t.hostName);
        announce(t, Component.literal("Tournament \"" + t.name + "\" is open: " + summary(t) + ". ")
                .withStyle(ChatFormatting.GOLD).append(button("[Join]", "/jadm tournament join", "Join with your deck box"))
                .append(" ").append(button("[Details]", "/jadm tournament", "Open the tournament window")));
        if (data.store.arenas.isEmpty() && host != null) {
            host.sendSystemMessage(Component.literal("No duel arena belongs to tournaments yet: stand on one and run "
                    + "/jadm tournament arena add, or matches can't begin.").withStyle(ChatFormatting.YELLOW));
        }
        changed();
        return null;
    }

    /** "Single elimination, best of 3, 4 to 16 duelists, closes in 5 minutes" */
    private static String summary(Tournament t) {
        StringBuilder out = new StringBuilder(t.format().displayName);
        int bestOf = t.integer("bestOf");
        if (bestOf > 1) {
            out.append(", best of ").append(bestOf);
        }
        out.append(", ").append(t.integer("minPlayers")).append(" to ").append(t.integer("maxPlayers"))
                .append(" duelists");
        int fee = t.integer("entryFee");
        if (fee > 0) {
            out.append(", entry ").append(Fees.amount(t, fee));
        }
        if (t.closesAt > 0) {
            long minutes = Math.max(1, (t.closesAt - now() + 59_999) / 60_000);
            out.append(", starts in ").append(minutes).append(minutes == 1 ? " minute" : " minutes");
        }
        return out.toString();
    }

    String join(ServerPlayer player) {
        Tournament t = current();
        if (t == null || !t.state.equals(Tournament.OPEN)) {
            return "No tournament is open for registration.";
        }
        if (t.entrant(player.getUUID()) != null) {
            return "You are already registered for \"" + t.name + "\".";
        }
        if (t.entrants.stream().filter(e -> !e.npc()).count() >= t.integer("maxPlayers")) {
            return "\"" + t.name + "\" is full.";
        }
        int step = Math.max(JadmData.step(player), stepOf(t));
        Banlist banlist = banlist(t, step);
        Tournament.Entrant e = new Tournament.Entrant();
        e.player = player.getUUID();
        e.name = player.getScoreboardName();
        Deck deck = deckFor(player, banlist, t);
        if (deck == null) {
            return null; // told why
        }
        if (t.bool("lockDeck")) {
            e.main = List.copyOf(deck.main());
            e.extra = List.copyOf(deck.extra());
        }
        e.deckName = deck.name();
        int fee = t.integer("entryFee");
        if (fee > 0) {
            if (!Fees.take(player, t, fee)) {
                return "Entering costs " + Fees.amount(t, fee) + ".";
            }
            e.feePaid = fee;
            t.pot += fee;
        }
        t.entrants.add(e);
        long people = t.entrants.stream().filter(x -> !x.npc()).count();
        t.news(e.name + " joined");
        player.sendSystemMessage(Component.literal("You are in \"" + t.name + "\"" + (t.bool("lockDeck")
                ? " with " + e.deckName + ". This deck is locked in for the whole tournament." : ".")
                + (fee > 0 ? " Paid " + Fees.amount(t, fee) + "." : ""))
                .withStyle(ChatFormatting.GREEN));
        announce(t, Component.literal(e.name + " joined \"" + t.name + "\" (" + people + "/"
                + t.integer("maxPlayers") + ").").withStyle(ChatFormatting.GRAY));
        changed();
        if (t.bool("startWhenFull") && people >= t.integer("maxPlayers")) {
            start(null);
        }
        return null;
    }

    /**
     * The deck {@code player} joins with: their first legal deck box, or Yugi's starter deck if the tournament and
     * the server allow it. Tells them why not otherwise.
     */
    private static Deck deckFor(ServerPlayer player, Banlist banlist, Tournament t) {
        List<ItemStack> boxes = new ArrayList<>();
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.is(JadmItems.DECK_BOX.get())) {
                boxes.add(stack);
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(JadmItems.DECK_BOX.get()) && !boxes.contains(stack)) {
                boxes.add(stack);
            }
        }
        for (ItemStack box : boxes) {
            if (DeckBoxItem.problems(box, player, banlist).isEmpty()) {
                Deck deck = DeckBoxItem.toDeck(box);
                return new Deck(box.getHoverName().getString(), deck.main(), deck.extra(), deck.side());
            }
        }
        if (t.bool("starterDecks") && JadmServerConfig.STARTER_DECKS.get()) {
            return Deck.bundled(STARTER_DECK);
        }
        player.sendSystemMessage(Component.literal(boxes.isEmpty()
                ? "Bring a deck box with a legal deck to join (under " + banlist.name() + ")."
                : "Your deck box \"" + boxes.get(0).getHoverName().getString() + "\" isn't legal: "
                + DeckBoxItem.problems(boxes.get(0), player, banlist).get(0)).withStyle(ChatFormatting.RED));
        return null;
    }

    String leave(ServerPlayer player) {
        Tournament t = current();
        if (t == null || !t.active() || t.entrant(player.getUUID()) == null) {
            return "You aren't in a tournament.";
        }
        remove(t, t.indexOf(player.getUUID()), player.getScoreboardName() + " left");
        player.sendSystemMessage(Component.literal("You left \"" + t.name + "\".").withStyle(ChatFormatting.GRAY));
        return null;
    }

    /** Takes an entrant out: before the start with their fee back, after it they lose every match still to come. */
    private void remove(Tournament t, int index, String news) {
        Tournament.Entrant e = t.entrants.get(index);
        t.news(news);
        if (t.state.equals(Tournament.OPEN)) {
            t.entrants.remove(index);
            refund(t, e);
        } else {
            t.bracket.drop(index);
            ServerPlayer player = e.npc() ? null : server.getPlayerList().getPlayer(e.player);
            if (player != null && DuelManager.get(server).inDuel(player) && inTournamentDuel(t, index)) {
                DuelManager.get(server).forfeit(player);
            }
            process();
        }
        announce(t, Component.literal(news + ".").withStyle(ChatFormatting.GRAY));
        changed();
    }

    private boolean inTournamentDuel(Tournament t, int index) {
        for (Map.Entry<Integer, Tournament.Live> entry : t.live.entrySet()) {
            Bracket.Match m = t.bracket.match(entry.getKey());
            if (entry.getValue().phase.equals(Tournament.Live.DUELING) && (m.a == index || m.b == index)) {
                return true;
            }
        }
        return false;
    }

    private void refund(Tournament t, Tournament.Entrant e) {
        if (e.feePaid > 0 && !e.npc()) {
            t.pot -= e.feePaid;
            owe(e.player, Fees.payout(t, e.feePaid));
            e.feePaid = 0;
        }
    }

    /** Hands over a prize entry now if the player is online, otherwise when they next join. */
    private List<String> owe(UUID player, String entry) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            String got = Prizes.give(online, entry);
            return got == null ? List.of() : List.of(got);
        }
        data.store.owed.computeIfAbsent(player, k -> new ArrayList<>()).add(entry);
        data.setDirty();
        return List.of();
    }

    String kick(String name) {
        Tournament t = current();
        if (t == null || !t.active()) {
            return "No tournament is running.";
        }
        for (int i = 0; i < t.entrants.size(); i++) {
            if (t.entrants.get(i).name.equalsIgnoreCase(name)) {
                remove(t, i, t.entrants.get(i).name + " was removed");
                return null;
            }
        }
        return "Nobody named " + name + " is in \"" + t.name + "\".";
    }

    /** Adds NPC duelists during registration. */
    String addNpcs(int count) {
        Tournament t = current();
        if (t == null || !t.state.equals(Tournament.OPEN)) {
            return "No tournament is open for registration.";
        }
        for (int i = 0; i < count; i++) {
            addNpc(t, new Random());
        }
        t.news(count + (count == 1 ? " NPC duelist joined" : " NPC duelists joined"));
        announce(t, Component.literal(count + (count == 1 ? " NPC duelist joins" : " NPC duelists join") + " \""
                + t.name + "\".").withStyle(ChatFormatting.GRAY));
        changed();
        return null;
    }

    private void addNpc(Tournament t, Random random) {
        List<String> names = new ArrayList<>(DuelistNpc.TITLES);
        names.addAll(NPC_NAMES);
        Set<String> taken = new HashSet<>();
        t.entrants.forEach(e -> taken.add(e.name));
        Collections.shuffle(names, random);
        String name = null;
        for (String n : names) {
            if (!taken.contains(n)) {
                name = n;
                break;
            }
        }
        for (int i = 2; name == null; i++) {
            String n = names.get(0) + " " + i;
            name = taken.contains(n) ? null : n;
        }
        Tournament.Entrant e = new Tournament.Entrant();
        e.name = name;
        e.seed = random.nextLong();
        t.entrants.add(e);
    }

    String set(String key, String value) {
        Tournament t = current();
        if (t == null || !t.state.equals(Tournament.OPEN)) {
            return "Settings can only be changed while a tournament is open for registration. The defaults are in "
                    + "the [tournament] section of serverconfig/jadm-server.toml.";
        }
        TournamentOptions.Option o = TournamentOptions.option(key);
        if (o == null || !o.perTournament()) {
            return "Unknown setting \"" + key + "\". /jadm tournament settings lists them.";
        }
        String parsed = TournamentOptions.parse(o, value);
        if (parsed == null) {
            return "\"" + value + "\" doesn't work for " + o.key() + ": " + o.comment();
        }
        t.settings.put(o.key(), parsed);
        if (o.key().equals("registrationMinutes")) {
            int minutes = t.integer("registrationMinutes");
            t.closesAt = minutes > 0 ? Math.max(now(), t.openedAt + minutes * 60_000L) : 0;
        }
        t.news(o.key() + " set to " + parsed);
        changed();
        return null;
    }

    String cancel() {
        Tournament t = current();
        if (t == null || !t.active()) {
            return "No tournament is running.";
        }
        t.state = Tournament.CANCELLED;
        t.finishedAt = now();
        t.entrants.forEach(e -> refund(t, e));
        for (Integer id : List.copyOf(t.live.keySet())) {
            close(t, id);
        }
        stopBotGames();
        t.news("Called off");
        announce(t, Component.literal("Tournament \"" + t.name + "\" was called off. Entry fees go back.")
                .withStyle(ChatFormatting.GOLD));
        changed();
        return null;
    }

    private void stopBotGames() {
        botGames.values().forEach(g -> g.table().close());
        botGames.clear();
    }

    /**
     * Closes registration, fills up with NPCs, seeds and pairs.
     *
     * @param by who asked, or {@code null} when the timer ran out or the tournament filled up
     * @return what went wrong, or {@code null}
     */
    String start(ServerPlayer by) {
        Tournament t = current();
        if (t == null || !t.state.equals(Tournament.OPEN)) {
            return "No tournament is open for registration.";
        }
        Random random = new Random();
        long people = t.entrants.stream().filter(e -> !e.npc()).count();
        if (people > 0 && data.store.arenas.isEmpty()) {
            String why = "No duel arena belongs to tournaments yet: stand on one and run /jadm tournament arena add.";
            if (by == null) {
                t.closesAt = 0;
                announce(t, Component.literal("\"" + t.name + "\" can't start: " + why).withStyle(ChatFormatting.RED));
                changed();
            }
            return why;
        }
        for (int i = 0; i < t.integer("npcCount"); i++) {
            addNpc(t, random);
        }
        int min = t.integer("minPlayers");
        String fill = t.setting("npcFill").toLowerCase(Locale.ROOT);
        if (!fill.equals("none")) {
            int target = min;
            if (fill.equals("bracket")) {
                int n = Math.max(min, t.entrants.size());
                Bracket.Format f = t.format();
                if (f == Bracket.Format.SINGLE || f == Bracket.Format.DOUBLE) {
                    target = Integer.highestOneBit(n - 1) * 2;
                } else {
                    target = n + n % 2;
                }
                target = Math.max(target, 2);
            }
            while (t.entrants.size() < target) {
                addNpc(t, random);
            }
        }
        if (t.entrants.size() < Math.max(2, min)) {
            if (by != null) {
                return "\"" + t.name + "\" needs " + Math.max(2, min) + " duelists, it has " + t.entrants.size()
                        + ". Add NPCs with /jadm tournament addnpc or lower minPlayers.";
            }
            t.state = Tournament.CANCELLED;
            t.finishedAt = now();
            t.entrants.forEach(e -> refund(t, e));
            t.news("Called off: not enough duelists");
            announce(t, Component.literal("\"" + t.name + "\" was called off: not enough duelists.")
                    .withStyle(ChatFormatting.GOLD));
            changed();
            return null;
        }
        Collections.shuffle(t.entrants, random);
        t.step = stepOf(t);
        Banlist banlist = banlist(t, t.step);
        for (Tournament.Entrant e : t.entrants) {
            if (e.npc()) {
                Deck deck = DuelistNpc.deckFor(e.name, e.seed, t.step, banlist);
                e.main = List.copyOf(deck.main());
                e.extra = List.copyOf(deck.extra());
                e.deckName = deck.name().equals(e.name) ? "their own deck" : deck.name();
            }
        }
        t.bracket = Bracket.create(t.format(), t.entrants.size(), t.integer("bestOf"), t.integer("swissRounds"),
                t.integer("topCut"), t.bool("thirdPlaceMatch"), t.bool("grandFinalReset"));
        t.state = Tournament.RUNNING;
        t.news("Started with " + t.entrants.size() + " duelists");
        announce(t, Component.literal("\"" + t.name + "\" begins: " + t.entrants.size() + " duelists, "
                + t.format().displayName + ", " + ruleset(t).displayName() + ". ").withStyle(ChatFormatting.GOLD)
                .append(button("[Bracket]", "/jadm tournament", "Open the tournament window")));
        announceRound(t);
        process();
        changed();
        return null;
    }

    /** The furthest progression step among the people who joined (the step of the world if none is online). */
    private int stepOf(Tournament t) {
        int step = JadmData.step(null);
        for (Tournament.Entrant e : t.entrants) {
            ServerPlayer p = e.npc() ? null : server.getPlayerList().getPlayer(e.player);
            if (p != null) {
                step = Math.max(step, JadmData.step(p));
            }
        }
        return step;
    }

    /** The step the tournament is played at: fixed at the start, until then that of who joined so far. */
    int effectiveStep(Tournament t) {
        return t.state.equals(Tournament.OPEN) ? stepOf(t) : t.step;
    }

    Ruleset ruleset(Tournament t) {
        String s = t.setting("ruleset");
        int step = effectiveStep(t);
        return s.equalsIgnoreCase("server") ? JadmData.ruleset(step) : JadmData.ruleset(step, s);
    }

    static Banlist banlist(Tournament t, int step) {
        String s = t.setting("banlist");
        return s.equalsIgnoreCase("server") ? JadmData.banlist(step) : JadmData.banlist(step, s);
    }

    // ---- Invitationals -------------------------------------------------------------------------------------------

    /**
     * Opens and starts a tournament for exactly these people, such as the finals of a Star Chip event: no entry fee,
     * no NPCs, and each duelist plays the deck box they bring to each match.
     *
     * @param settings tournament settings ({@code key=value}) on top of the {@code [tournament]} defaults
     * @return what went wrong, or {@code null} once it has started
     */
    public String invitational(String name, List<UUID> players, List<String> names, List<String> settings) {
        Tournament old = current();
        if (old != null && old.active()) {
            return "the tournament \"" + old.name + "\" isn't over yet";
        }
        if (data.store.arenas.isEmpty()) {
            return "no Duel Arena belongs to tournaments yet (stand on one and run /jadm tournament arena add)";
        }
        if (players.size() < 2) {
            return "it needs at least 2 duelists";
        }
        Tournament t = new Tournament();
        t.settings = TournamentOptions.snapshot();
        for (String entry : settings) {
            int eq = entry.indexOf('=');
            TournamentOptions.Option o = eq < 0 ? null : TournamentOptions.option(entry.substring(0, eq).strip());
            String value = o == null || !o.perTournament() ? null : TournamentOptions.parse(o, entry.substring(eq + 1));
            if (value == null) {
                Jadm.LOGGER.warn("Ignoring the tournament setting \"{}\"", entry);
                continue;
            }
            t.settings.put(o.key(), value);
        }
        // Fixed for an invitational: exactly the invited, nobody else.
        t.settings.put("minPlayers", "2");
        t.settings.put("maxPlayers", String.valueOf(Math.max(2, players.size())));
        t.settings.put("registrationMinutes", "0");
        t.settings.put("startWhenFull", "false");
        t.settings.put("npcFill", "none");
        t.settings.put("npcCount", "0");
        t.settings.put("entryFee", "0");
        t.settings.put("lockDeck", "false");
        t.hostName = "the server";
        t.name = name;
        t.openedAt = now();
        for (int i = 0; i < players.size(); i++) {
            Tournament.Entrant e = new Tournament.Entrant();
            e.player = players.get(i);
            e.name = names.get(i);
            e.deckName = "their deck box";
            t.entrants.add(e);
        }
        t.news("Opened for " + String.join(", ", names));
        data.store.current = t;
        String error = start(null);
        changed();
        return error;
    }

    /**
     * How the current tournament called {@code name} ended: the winner's name, {@code ""} if it was called off, or
     * {@code null} while it is still on (or if the current tournament is another one).
     */
    public String outcome(String name) {
        Tournament t = current();
        if (t == null || !t.name.equals(name) || t.active()) {
            return null;
        }
        if (t.state.equals(Tournament.CANCELLED)) {
            return "";
        }
        return t.entrants.stream().filter(e -> e.place == 1).map(e -> e.name).findFirst().orElse("");
    }

    /** Hands prize entries ("pack 5", "points 1000", ...) to a player, now or when they are next online. */
    public List<String> handOut(UUID player, List<String> entries) {
        List<String> got = new ArrayList<>();
        entries.forEach(entry -> got.addAll(owe(player, entry)));
        return got;
    }

    // ---- Running ------------------------------------------------------------------------------------------------

    public void tick() {
        tickBotGames();
        long now = now();
        if (now - lastSecond < 1000) {
            return;
        }
        lastSecond = now;
        returnPeople(now);
        Tournament t = current();
        if (t != null && t.state.equals(Tournament.OPEN) && t.closesAt > 0 && now >= t.closesAt) {
            start(null);
        } else if (t != null && t.state.equals(Tournament.RUNNING)) {
            process();
        }
        schedule(now);
    }

    /** Opens a scheduled tournament once its minute comes. */
    private void schedule(long now) {
        long minute = now / 60_000;
        if (minute == data.store.lastScheduled) {
            return;
        }
        List<String> schedule = TournamentOptions.schedule();
        LocalDateTime time = LocalDateTime.now();
        if (schedule.stream().anyMatch(s -> Schedule.due(s, time))) {
            data.store.lastScheduled = minute;
            data.setDirty();
            Tournament t = current();
            if (t == null || !t.active()) {
                create(null, null, null);
            }
        }
    }

    /** Moves every playable match along and finishes the tournament once the last one is decided. */
    private void process() {
        Tournament t = current();
        if (t == null || !t.state.equals(Tournament.RUNNING)) {
            return;
        }
        boolean dirty = false;
        int round = t.bracket.round;
        int before = t.bracket.matches.size();
        t.bracket.advance();
        for (Bracket.Match m : t.bracket.playable()) {
            Tournament.Live live = t.live.get(m.id);
            if (live == null) {
                live = new Tournament.Live();
                live.since = now();
                t.live.put(m.id, live);
                dirty = true;
            }
            dirty |= step(t, m, live);
        }
        for (Integer id : List.copyOf(t.live.keySet())) {
            Bracket.Match m = t.bracket.match(id);
            if (m.decided()) {
                Tournament.Live live = t.live.get(id);
                if (!live.phase.equals("done")) {
                    live.phase = "done";
                    live.startsAt = now() + RETURN_DELAY;
                } else if (now() >= live.startsAt) {
                    close(t, id);
                    dirty = true;
                }
            }
        }
        if (t.bracket.matches.size() != before || t.bracket.round != round) {
            announceRound(t);
            dirty = true;
        }
        if (t.bracket.finished() && t.live.isEmpty()) {
            finish(t);
            return;
        }
        if (dirty) {
            changed();
        }
    }

    /** One playable match: find it an arena, call its duelists, start its games, count no-shows. */
    private boolean step(Tournament t, Bracket.Match m, Tournament.Live live) {
        Tournament.Entrant a = t.entrants.get(m.a);
        Tournament.Entrant b = t.entrants.get(m.b);
        if (a.npc() && b.npc()) {
            return botMatch(t, m, live);
        }
        long now = now();
        switch (live.phase) {
            case Tournament.Live.DUELING -> {
                return false;
            }
            case Tournament.Live.WAITING, Tournament.Live.PAUSE -> {
                if (missing(t, m, live, a, b)) {
                    return true;
                }
                if (live.phase.equals(Tournament.Live.PAUSE)) {
                    if (now >= live.startsAt) {
                        beginGame(t, m, live);
                        return true;
                    }
                    return false;
                }
                int arena = freeArena(t);
                if (arena < 0) {
                    if (live.startsAt == 0) {
                        live.startsAt = 1;
                        tell(m, t, Component.literal("Your match is next; waiting for a free duel arena.")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                    return false;
                }
                live.arena = arena;
                bringToArena(t, m, live);
                live.phase = Tournament.Live.CALLED;
                live.since = now;
                live.startsAt = now + t.integer("callSeconds") * 1000L;
                live.ready.clear();
                Component call = Component.literal(t.bracket.title(m) + ": " + a.name + " vs " + b.name + " on arena "
                        + (arena + 1) + ", starting in " + t.integer("callSeconds") + " seconds. ")
                        .withStyle(ChatFormatting.GOLD).append(button("[Ready]", "/jadm tournament ready",
                                "Start as soon as both duelists are ready"));
                tell(m, t, call);
                return true;
            }
            case Tournament.Live.CALLED -> {
                if (missing(t, m, live, a, b)) {
                    return true;
                }
                boolean allReady = (a.npc() || live.ready.contains(a.player)) && (b.npc() || live.ready.contains(b.player));
                if (now >= live.startsAt || allReady) {
                    beginGame(t, m, live);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Whether a person in the match is offline or in another duel. After {@code noShowMinutes} the match goes to
     * whoever is there (both lose if neither is).
     */
    private boolean missing(Tournament t, Bracket.Match m, Tournament.Live live, Tournament.Entrant a,
                            Tournament.Entrant b) {
        boolean goneA = absent(a);
        boolean goneB = absent(b);
        if (!goneA && !goneB) {
            live.missingSince = 0;
            return false;
        }
        long now = now();
        if (live.missingSince == 0) {
            live.missingSince = now;
            String who = goneA && goneB ? a.name + " and " + b.name : goneA ? a.name : b.name;
            tell(m, t, Component.literal("Waiting for " + who + " (offline or in another duel). After "
                    + t.integer("noShowMinutes") + " minutes the match is lost.").withStyle(ChatFormatting.YELLOW));
            return true;
        }
        if (now - live.missingSince >= t.integer("noShowMinutes") * 60_000L) {
            int side = goneA && goneB ? -1 : goneA ? 1 : 0;
            t.news((side < 0 ? a.name + " and " + b.name + " didn't show up"
                    : (side == 1 ? a.name : b.name) + " didn't show up"));
            t.bracket.award(m.id, side);
            announceResult(t, m);
        }
        return true;
    }

    private boolean absent(Tournament.Entrant e) {
        if (e.npc()) {
            return false;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(e.player);
        return p == null || DuelManager.get(server).inDuel(p) || !p.isAlive();
    }

    /** The first registered arena that stands, is free and isn't taken by another match, or -1. */
    private int freeArena(Tournament t) {
        Set<Integer> taken = new HashSet<>();
        t.live.values().forEach(l -> taken.add(l.arena));
        for (int i = 0; i < data.store.arenas.size(); i++) {
            ServerLevel level = level(data.store.arenas.get(i).dim);
            if (!taken.contains(i) && level != null && DuelArena.available(level, pos(data.store.arenas.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    /** Remembers where the duelists stand and puts them on the arena's podiums; an NPC appears on its podium. */
    private void bringToArena(Tournament t, Bracket.Match m, Tournament.Live live) {
        TournamentData.ArenaRef ref = data.store.arenas.get(live.arena);
        ServerLevel level = level(ref.dim);
        int side = 0;
        for (int index : new int[]{m.a, m.b}) {
            Tournament.Entrant e = t.entrants.get(index);
            DuelArena.Spot spot = DuelArena.podium(level, pos(ref), side == 0 ? 1 : -1);
            if (e.npc()) {
                discardNpc(live);
                DuelistNpc npc = JadmEntities.DUELIST.get().create(level);
                if (npc != null) {
                    npc.dressAs(e.name, e.seed);
                    npc.setCustomNameVisible(true);
                    npc.moveTo(spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), 0);
                    npc.finalizeSpawn(level, level.getCurrentDifficultyAt(npc.blockPosition()), MobSpawnType.EVENT,
                            null);
                    npc.dressAs(e.name, e.seed);
                    level.addFreshEntity(npc);
                    live.npcEntity = npc.getUUID();
                }
            } else {
                ServerPlayer p = server.getPlayerList().getPlayer(e.player);
                if (p != null) {
                    if (!live.back.containsKey(p.getUUID())) {
                        Tournament.Back back = new Tournament.Back();
                        back.dim = p.level().dimension().location().toString();
                        back.x = p.getX();
                        back.y = p.getY();
                        back.z = p.getZ();
                        back.yaw = p.getYRot();
                        back.pitch = p.getXRot();
                        live.back.put(p.getUUID(), back);
                    }
                    p.teleportTo(level, spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), 10);
                }
            }
            side++;
        }
    }

    private void beginGame(Tournament t, Bracket.Match m, Tournament.Live live) {
        TournamentData.ArenaRef ref = live.arena >= 0 && live.arena < data.store.arenas.size()
                ? data.store.arenas.get(live.arena) : null;
        ServerLevel level = ref == null ? null : level(ref.dim);
        if (level == null || !DuelArena.available(level, pos(ref))) {
            // The arena went away (or is busy): look for another one.
            live.phase = Tournament.Live.WAITING;
            live.arena = -1;
            live.startsAt = 0;
            return;
        }
        // Back onto the podiums, in case anyone walked off during the call.
        int side = 0;
        for (int index : new int[]{m.a, m.b}) {
            Tournament.Entrant e = t.entrants.get(index);
            DuelArena.Spot spot = DuelArena.podium(level, pos(ref), side++ == 0 ? 1 : -1);
            ServerPlayer p = e.npc() ? null : server.getPlayerList().getPlayer(e.player);
            if (p != null) {
                p.teleportTo(level, spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), 10);
                p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            }
        }
        Entity npcEntity = live.npcEntity == null ? null : level.getEntity(live.npcEntity);
        if ((t.entrants.get(m.a).npc() || t.entrants.get(m.b).npc()) && !(npcEntity instanceof DuelistNpc)) {
            bringToArena(t, m, live);
            npcEntity = live.npcEntity == null ? null : level.getEntity(live.npcEntity);
        }
        List<MatchSetup.Seat> seats = new ArrayList<>();
        for (int index : new int[]{m.a, m.b}) {
            Tournament.Entrant e = t.entrants.get(index);
            Deck deck = e.main == null ? null : new Deck(e.deckName == null ? e.name : e.deckName, e.main, e.extra,
                    List.of());
            seats.add(new MatchSetup.Seat(e.player, e.name, deck));
        }
        MatchSetup.Rules rules = new MatchSetup.Rules(t.integer("startingLifePoints"), ruleset(t),
                banlist(t, t.step), t.integer("turnTimeLimit"), t.integer("maxTurns"));
        int matchId = m.id;
        String error = DuelManager.get(server).startMatch(new MatchSetup(seats, rules, live.first,
                npcEntity instanceof DuelistNpc npc ? npc : null, winner -> gameOver(t, matchId, winner)));
        if (error != null) {
            tell(m, t, Component.literal("The game couldn't start: " + error + ". Trying again shortly.")
                    .withStyle(ChatFormatting.RED));
            live.phase = Tournament.Live.PAUSE;
            live.startsAt = now() + 5000;
            return;
        }
        live.phase = Tournament.Live.DUELING;
        live.since = now();
        int game = m.games() + 1;
        if (t.bracket.bestOf > 1 || game > 1) {
            tell(m, t, Component.literal("Game " + game + (m.games() > 0 ? " (" + m.winsA + "-" + m.winsB
                    + (m.draws > 0 ? "-" + m.draws : "") + ")" : "") + ".").withStyle(ChatFormatting.GOLD));
        }
    }

    /** A tournament game ended: {@code winner} 0 for side a, 1 for b, 2 for a draw, -1 if it broke off. */
    private void gameOver(Tournament t, int matchId, int winner) {
        Bracket.Match m = t.bracket == null ? null : t.bracket.match(matchId);
        Tournament.Live live = t.live.get(matchId);
        if (current() != t || !t.state.equals(Tournament.RUNNING) || m == null || !m.playable() || live == null) {
            return;
        }
        live.phase = Tournament.Live.PAUSE;
        live.startsAt = now() + t.integer("gamePauseSeconds") * 1000L;
        live.ready.clear();
        if (winner < 0) {
            tell(m, t, Component.literal("The game broke off; it will be played again.").withStyle(ChatFormatting.RED));
            changed();
            return;
        }
        t.bracket.reportGame(matchId, winner);
        live.first = winner == 0 ? 1 : winner == 1 ? 0 : -1;
        if (m.decided()) {
            announceResult(t, m);
        } else {
            Tournament.Entrant a = t.entrants.get(m.a);
            Tournament.Entrant b = t.entrants.get(m.b);
            tell(m, t, Component.literal(a.name + " " + m.winsA + " - " + m.winsB + " " + b.name
                    + (m.draws > 0 ? " (" + m.draws + (m.draws == 1 ? " draw)" : " draws)") : "")
                    + ". Next game in " + t.integer("gamePauseSeconds") + " seconds.").withStyle(ChatFormatting.GOLD));
        }
        process();
        changed();
    }

    /** NPC against NPC: played out by two bots unseen (or a coin flip), a game at a time. */
    private boolean botMatch(Tournament t, Bracket.Match m, Tournament.Live live) {
        if (t.setting("npcMatches").equalsIgnoreCase("coinflip")) {
            t.bracket.award(m.id, new Random().nextInt(2));
            announceResult(t, m);
            return true;
        }
        if (botGames.containsKey(m.id) || botGames.size() >= BOT_GAMES_AT_ONCE
                || now() < live.startsAt) {
            return false;
        }
        Random random = new Random();
        long[] seed = {random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong() | 1};
        List<DuelTable.Seat> seats = new ArrayList<>();
        List<Deck> decks = new ArrayList<>();
        int[] order = live.first == 1 ? new int[]{m.b, m.a} : new int[]{m.a, m.b};
        for (int i = 0; i < 2; i++) {
            Tournament.Entrant e = t.entrants.get(order[i]);
            seats.add(new DuelTable.Seat(i, e.name, new DuelistAi(random.nextLong(), JadmData.cards())));
            decks.add(new Deck(e.name, e.main, e.extra, List.of()));
        }
        int lp = t.integer("startingLifePoints") > 0 ? t.integer("startingLifePoints")
                : JadmServerConfig.STARTING_LIFE_POINTS.get();
        DuelSettings.Team team = new DuelSettings.Team(lp, 5, 1);
        try {
            DuelTable table = new DuelTable(JadmData.text(), new BundledScripts(),
                    new DuelSettings(seed, ruleset(t).flags(), team, team), seats, decks, (type, message) -> {
                    });
            table.start();
            botGames.put(m.id, new BotGame(table, t));
            live.phase = Tournament.Live.DUELING;
            live.since = now();
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Jadm.LOGGER.warn("NPC tournament game couldn't be played, flipping a coin", e);
            t.bracket.reportGame(m.id, random.nextInt(2));
            if (m.decided()) {
                announceResult(t, m);
            }
        }
        return true;
    }

    private void tickBotGames() {
        // Copied: reporting a result can pair the next match and start its game.
        for (Map.Entry<Integer, BotGame> entry : List.copyOf(botGames.entrySet())) {
            BotGame game = entry.getValue();
            Tournament t = game.tournament();
            DuelTable table = game.table();
            int winner;
            try {
                table.pump();
                int cap = t.integer("maxTurns") > 0 ? t.integer("maxTurns") : BOT_TURN_CAP;
                if (!table.finished() && table.turn() > cap) {
                    table.endOnLifePoints("Turn limit reached");
                }
                if (!table.finished()) {
                    continue;
                }
                winner = table.winner();
            } catch (RuntimeException e) {
                Jadm.LOGGER.warn("NPC tournament game broke off, flipping a coin", e);
                winner = new Random().nextInt(2);
            }
            table.close();
            botGames.remove(entry.getKey());
            Bracket.Match m = t.bracket.match(entry.getKey());
            Tournament.Live live = t.live.get(entry.getKey());
            if (current() != t || !t.state.equals(Tournament.RUNNING) || !m.playable() || live == null) {
                continue;
            }
            // The bot seats were swapped if side b went first.
            int side = winner == 2 ? 2 : live.first == 1 ? 1 - winner : winner;
            t.bracket.reportGame(m.id, side);
            live.first = side == 0 ? 1 : side == 1 ? 0 : -1;
            live.phase = Tournament.Live.WAITING;
            live.startsAt = now() + 2000;
            if (m.decided()) {
                announceResult(t, m);
            }
            process();
            changed();
        }
    }

    /** The match is over and everyone left the podiums: send people back where they came from. */
    private void close(Tournament t, int id) {
        Tournament.Live live = t.live.remove(id);
        if (live == null) {
            return;
        }
        discardNpc(live);
        BotGame game = botGames.remove(id);
        if (game != null) {
            game.table().close();
        }
        live.back.forEach((player, back) -> {
            data.store.owedReturns().put(player, back);
            returnDue.put(player, now());
        });
        data.setDirty();
    }

    private void discardNpc(Tournament.Live live) {
        if (live.npcEntity == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(live.npcEntity);
            if (e != null) {
                e.discard();
            }
        }
        live.npcEntity = null;
    }

    /** People whose match is over go back to where they were called from, once they're out of any duel. */
    private void returnPeople(long now) {
        Map<UUID, Tournament.Back> owed = data.store.owedReturns();
        for (Iterator<Map.Entry<UUID, Tournament.Back>> it = owed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Tournament.Back> entry = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || DuelManager.get(server).inDuel(p) || now < returnDue.getOrDefault(entry.getKey(), 0L)
                    || playing(entry.getKey())) {
                continue;
            }
            Tournament.Back back = entry.getValue();
            ServerLevel level = level(back.dim);
            if (level != null) {
                p.teleportTo(level, back.x, back.y, back.z, back.yaw, back.pitch);
            }
            it.remove();
            data.setDirty();
        }
    }

    /** Whether a person has a match on the arena right now (then they stay). */
    private boolean playing(UUID player) {
        Tournament t = current();
        return t != null && t.state.equals(Tournament.RUNNING) && t.live.values().stream()
                .anyMatch(l -> !l.phase.equals("done") && l.arena >= 0 && l.back.containsKey(player));
    }

    private void finish(Tournament t) {
        t.state = Tournament.DONE;
        t.finishedAt = now();
        List<Bracket.Placement> places = t.bracket.placements();
        for (Bracket.Placement p : places) {
            t.entrants.get(p.entrant()).place = p.place();
        }
        // The pot: each place its share; duelists who share a place split the shares of the places they cover.
        List<Integer> shares = new ArrayList<>();
        for (String part : t.setting("potShare").split(",")) {
            if (!part.isBlank()) {
                shares.add(Integer.parseInt(part.strip()));
            }
        }
        Map<Integer, Integer> groupSize = new HashMap<>();
        places.forEach(p -> groupSize.merge(p.place(), 1, Integer::sum));
        StringBuilder podium = new StringBuilder();
        for (Bracket.Placement p : places) {
            Tournament.Entrant e = t.entrants.get(p.entrant());
            if (p.place() <= 3) {
                podium.append(podium.isEmpty() ? "" : ", ").append(ordinal(p.place())).append(" ").append(e.name);
            }
            if (e.npc()) {
                continue;
            }
            List<String> got = new ArrayList<>();
            int size = groupSize.get(p.place());
            int percent = 0;
            for (int place = p.place(); place < p.place() + size; place++) {
                percent += place - 1 < shares.size() ? shares.get(place - 1) : 0;
            }
            int coins = t.pot * percent / 100 / size;
            if (coins > 0) {
                got.addAll(owe(e.player, Fees.payout(t, coins)));
            }
            for (String prize : prizesFor(t, p.place())) {
                got.addAll(owe(e.player, prize));
            }
            ServerPlayer player = server.getPlayerList().getPlayer(e.player);
            if (player != null) {
                player.sendSystemMessage(Component.literal("You finished " + ordinal(p.place()) + " in \"" + t.name
                        + "\"" + (got.isEmpty() ? "." : ": " + String.join(", ", got) + "."))
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        t.news("Finished: " + podium);
        announce(t, Component.literal("\"" + t.name + "\" is over! " + podium + ".").withStyle(ChatFormatting.GOLD)
                .append(" ").append(button("[Results]", "/jadm tournament", "Open the tournament window")));
        changed();
    }

    /** The prize entries for a place: its own list, then the one everyone gets. */
    static List<String> prizesFor(Tournament t, int place) {
        String key = switch (place) {
            case 1 -> "prizesFirst";
            case 2 -> "prizesSecond";
            case 3 -> "prizesThird";
            case 4 -> "prizesFourth";
            default -> place <= 8 ? "prizesTop8" : null;
        };
        List<String> out = new ArrayList<>(key == null ? List.of() : t.list(key));
        out.addAll(t.list("prizesEveryone"));
        return out;
    }

    static String ordinal(int n) {
        int mod = n % 100;
        String suffix = mod >= 11 && mod <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    // ---- Player hooks -------------------------------------------------------------------------------------------

    String ready(ServerPlayer player) {
        Tournament t = current();
        if (t == null || !t.state.equals(Tournament.RUNNING)) {
            return "No tournament is running.";
        }
        int index = t.indexOf(player.getUUID());
        for (Map.Entry<Integer, Tournament.Live> entry : t.live.entrySet()) {
            Bracket.Match m = t.bracket.match(entry.getKey());
            if ((m.a == index || m.b == index) && entry.getValue().phase.equals(Tournament.Live.CALLED)) {
                entry.getValue().ready.add(player.getUUID());
                tell(m, t, Component.literal(player.getScoreboardName() + " is ready.").withStyle(ChatFormatting.GRAY));
                process();
                return null;
            }
        }
        return "You have no match waiting to start.";
    }

    /** An operator gives the match {@code name} is in to them. */
    String award(String name) {
        Tournament t = current();
        if (t == null || !t.state.equals(Tournament.RUNNING)) {
            return "No tournament is running.";
        }
        for (Bracket.Match m : t.bracket.playable()) {
            for (int side = 0; side < 2; side++) {
                if (t.entrants.get(side == 0 ? m.a : m.b).name.equalsIgnoreCase(name)) {
                    t.bracket.award(m.id, side);
                    t.news(t.bracket.title(m) + " given to " + t.entrants.get(side == 0 ? m.a : m.b).name);
                    announceResult(t, m);
                    process();
                    changed();
                    return null;
                }
            }
        }
        return name + " has no match being played.";
    }

    public void onLogin(ServerPlayer player) {
        List<String> owed = data.store.owed.remove(player.getUUID());
        if (owed != null) {
            List<String> got = new ArrayList<>();
            owed.forEach(entry -> {
                String line = Prizes.give(player, entry);
                if (line != null) {
                    got.add(line);
                }
            });
            if (!got.isEmpty()) {
                player.sendSystemMessage(Component.literal("Tournament prizes and refunds: " + String.join(", ", got)
                        + ".").withStyle(ChatFormatting.GOLD));
            }
            data.setDirty();
        }
        Tournament t = current();
        if (t != null) {
            PacketDistributor.sendToPlayer(player, new TournamentPayload("",
                    TournamentData.GSON.toJson(TournamentView.of(t, this))));
            if (t.state.equals(Tournament.OPEN) && t.entrant(player.getUUID()) == null) {
                player.sendSystemMessage(Component.literal("Tournament \"" + t.name + "\" is open: " + summary(t)
                        + ". ").withStyle(ChatFormatting.GOLD).append(button("[Join]", "/jadm tournament join",
                        "Join with your deck box")));
            }
        }
    }

    /** Sends the tournament window to {@code player}, at {@code tab} ("default" for the usual one). */
    void open(ServerPlayer player, String tab) {
        Tournament t = current();
        PacketDistributor.sendToPlayer(player, new TournamentPayload(tab,
                t == null ? "" : TournamentData.GSON.toJson(TournamentView.of(t, this))));
    }

    // ---- Arenas -------------------------------------------------------------------------------------------------

    /**
     * Whether this arena belongs to tournaments while one is open or running: it is kept for the matches, so duels
     * don't start there by themselves.
     */
    public boolean holds(Level level, BlockPos arena) {
        Tournament t = current();
        if (t == null || !t.active()) {
            return false;
        }
        String dim = level.dimension().location().toString();
        return data.store.arenas.stream().anyMatch(ref -> ref.dim.equals(dim) && pos(ref).equals(arena));
    }

    String addArena(ServerPlayer player) {
        BlockPos arena = DuelArena.arenaUnder(player.level(), player.blockPosition());
        if (arena == null) {
            return "Stand on a Duel Arena to add it.";
        }
        String dim = player.level().dimension().location().toString();
        for (TournamentData.ArenaRef ref : data.store.arenas) {
            if (ref.dim.equals(dim) && pos(ref).equals(arena)) {
                return "This arena already belongs to tournaments.";
            }
        }
        TournamentData.ArenaRef ref = new TournamentData.ArenaRef();
        ref.dim = dim;
        ref.x = arena.getX();
        ref.y = arena.getY();
        ref.z = arena.getZ();
        data.store.arenas.add(ref);
        data.setDirty();
        player.sendSystemMessage(Component.literal("Arena " + data.store.arenas.size() + " added at "
                + arena.toShortString() + ".").withStyle(ChatFormatting.GREEN));
        changed();
        return null;
    }

    String removeArena(ServerPlayer player, int number) {
        int index = number - 1;
        if (number <= 0) {
            BlockPos arena = DuelArena.arenaUnder(player.level(), player.blockPosition());
            String dim = player.level().dimension().location().toString();
            for (int i = 0; i < data.store.arenas.size(); i++) {
                if (arena != null && data.store.arenas.get(i).dim.equals(dim) && pos(data.store.arenas.get(i)).equals(arena)) {
                    index = i;
                }
            }
        }
        if (index < 0 || index >= data.store.arenas.size()) {
            return "Stand on a tournament arena or give its number (/jadm tournament arena list).";
        }
        Tournament t = current();
        if (t != null && t.live.values().stream().anyMatch(l -> l.arena >= 0)) {
            return "Arenas can't be removed while matches are on them.";
        }
        data.store.arenas.remove(index);
        data.setDirty();
        player.sendSystemMessage(Component.literal("Arena " + (index + 1) + " no longer belongs to tournaments.")
                .withStyle(ChatFormatting.GRAY));
        changed();
        return null;
    }

    List<String> arenaLines() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < data.store.arenas.size(); i++) {
            TournamentData.ArenaRef ref = data.store.arenas.get(i);
            ServerLevel level = level(ref.dim);
            String state = level == null ? "world missing" : !level.isLoaded(pos(ref)) ? "not loaded"
                    : !level.getBlockState(pos(ref)).is(DuelArena.ARENA.get()) ? "gone"
                    : DuelArena.available(level, pos(ref)) ? "free" : "in use";
            out.add("Arena " + (i + 1) + ": " + ref.x + " " + ref.y + " " + ref.z + " in " + ref.dim + " (" + state + ")");
        }
        return out;
    }

    int arenaCount() {
        return data.store.arenas.size();
    }

    // ---- Helpers ------------------------------------------------------------------------------------------------

    private static BlockPos pos(TournamentData.ArenaRef ref) {
        return new BlockPos(ref.x, ref.y, ref.z);
    }

    private ServerLevel level(String dim) {
        ResourceLocation id = ResourceLocation.tryParse(dim);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? Items.EMERALD : BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? Items.EMERALD : item;
    }

    /** "emeralds", "diamond" */
    static String itemName(String id, int count) {
        String name = new ItemStack(item(id)).getHoverName().getString().toLowerCase(Locale.ROOT);
        return count == 1 || name.endsWith("s") ? name : name + "s";
    }

    private static MutableComponent button(String text, String command, String hover) {
        return Component.literal(text).withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private void announce(Tournament t, Component text) {
        if (t.bool("announce")) {
            server.getPlayerList().broadcastSystemMessage(text, false);
            return;
        }
        Set<UUID> told = new HashSet<>();
        for (Tournament.Entrant e : t.entrants) {
            if (!e.npc()) {
                told.add(e.player);
            }
        }
        if (t.host != null) {
            told.add(t.host);
        }
        for (UUID id : told) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(text);
            }
        }
    }

    /** Tells the people in a match. */
    private void tell(Bracket.Match m, Tournament t, Component text) {
        for (int index : new int[]{m.a, m.b}) {
            if (index >= 0 && !t.entrants.get(index).npc()) {
                ServerPlayer p = server.getPlayerList().getPlayer(t.entrants.get(index).player);
                if (p != null) {
                    p.sendSystemMessage(text);
                }
            }
        }
    }

    private void announceRound(Tournament t) {
        List<Bracket.Match> open = t.bracket.playable();
        if (open.isEmpty()) {
            return;
        }
        Bracket.Format f = t.format();
        if (f == Bracket.Format.SWISS || f == Bracket.Format.ROUND_ROBIN) {
            Bracket.Match first = open.get(0);
            announce(t, Component.literal(t.bracket.title(first) + " of \"" + t.name + "\": " + String.join(", ",
                    open.stream().map(m -> t.entrants.get(m.a).name + " vs " + t.entrants.get(m.b).name).toList())
                    + ".").withStyle(ChatFormatting.YELLOW));
            t.news(t.bracket.title(first) + " paired");
        }
    }

    private void announceResult(Tournament t, Bracket.Match m) {
        if (!m.decided() || m.a < 0 || m.b < 0) {
            return;
        }
        String a = t.entrants.get(m.a).name;
        String b = t.entrants.get(m.b).name;
        String score = t.bracket.bestOf > 1 && !m.walkover ? " " + Math.max(m.winsA, m.winsB) + "-"
                + Math.min(m.winsA, m.winsB) + (m.draws > 0 ? "-" + m.draws : "") : "";
        String line = m.winner == Bracket.DRAW ? a + " and " + b + " drew"
                : m.winner < 0 ? a + " and " + b + " both lose"
                : (m.winner == m.a ? a : b) + " beat " + (m.winner == m.a ? b : a) + score
                + (m.walkover ? " (no-show)" : "");
        t.news(t.bracket.title(m) + ": " + line);
        announce(t, Component.literal(t.bracket.title(m) + ": " + line + ".").withStyle(ChatFormatting.YELLOW));
    }

    /** Placement of an entrant's current match for the window: "Playing game 2 on arena 1", ... */
    String liveText(Tournament t, int matchId) {
        Tournament.Live live = t.live.get(matchId);
        if (live == null) {
            return null;
        }
        String arena = live.arena >= 0 ? " on arena " + (live.arena + 1) : "";
        return switch (live.phase) {
            case Tournament.Live.DUELING -> "Playing" + arena;
            case Tournament.Live.CALLED -> "Called" + arena;
            case Tournament.Live.PAUSE -> "Between games" + arena;
            case "done" -> null;
            default -> live.missingSince > 0 ? "Waiting for a duelist" : "Waiting for an arena";
        };
    }
}
