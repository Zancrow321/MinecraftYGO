package io.github.zancrow321.jadm.starchips;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.duel.DuelManager;
import io.github.zancrow321.jadm.network.StarChipsPayload;
import io.github.zancrow321.jadm.points.Points;
import io.github.zancrow321.jadm.starchips.StarChipData.Duelist;
import io.github.zancrow321.jadm.starchips.StarChipData.Event;
import io.github.zancrow321.jadm.tournament.TournamentManager;
import io.github.zancrow321.jadm.tournament.TournamentOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Star Chip events, as on Duelist Kingdom: everyone who joins gets a few Star Chips and puts them up in their duels
 * (against each other, and against NPC duelists if the server allows it). The winner takes what the loser put up;
 * whoever has none left is out. Collect enough and you qualify; once enough have, the finals are held as a tournament
 * on the tournament arenas. All methods run on the server thread.
 */
public final class StarChips {
    private static final long RETRY_MILLIS = 30_000;

    private static StarChips instance;

    private final MinecraftServer server;
    private final StarChipData data;
    /** person -> what their running duel is for (not saved: after a restart the duel is gone, and so is the bet) */
    private final Map<UUID, Integer> bets = new HashMap<>();
    /** challenger -> the wager they asked for with {@code /jadm starchips duel} */
    private final Map<UUID, Request> requests = new HashMap<>();
    private long lastSecond;
    private long lastTry;
    private String lastFinalsProblem;

    private record Request(UUID target, int chips) {
    }

    private StarChips(MinecraftServer server) {
        this.server = server;
        this.data = StarChipData.get(server);
    }

    public static StarChips get(MinecraftServer server) {
        if (instance == null || instance.server != server) {
            instance = new StarChips(server);
        }
        return instance;
    }

    public static void shutdown() {
        instance = null;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    Event event() {
        return data.event;
    }

    private Event running() {
        Event e = data.event;
        return e != null && e.state.equals(Event.RUNNING) ? e : null;
    }

    /** Whether {@code player} may start, run and end events. */
    static boolean canHost(ServerPlayer player) {
        return player == null || player.hasPermissions(2) || JadmServerConfig.STAR_CHIPS.playersCanHost.get();
    }

    // ---- Hosting --------------------------------------------------------------------------------------------------

    /**
     * Starts an event, open to everyone.
     *
     * @param host {@code null} for the server console
     * @return what went wrong, or {@code null}
     */
    String start(ServerPlayer host, String name) {
        Event old = data.event;
        if (old != null && old.active()) {
            return "\"" + old.name + "\" is still on. End it first with /jadm starchips cancel.";
        }
        var config = JadmServerConfig.STAR_CHIPS;
        Event e = new Event();
        e.name = name != null && !name.isBlank() ? name.strip() : "Duelist Kingdom";
        e.host = host == null ? null : host.getUUID();
        e.startedAt = now();
        int minutes = config.durationMinutes.get();
        e.endsAt = minutes > 0 ? e.startedAt + minutes * 60_000L : 0;
        e.goal = config.goal.get();
        e.startChips = config.startChips.get();
        e.finalistsWanted = config.finalists.get();
        data.event = e;
        bets.clear();
        requests.clear();
        lastFinalsProblem = null;
        data.setDirty();
        announce(e, Component.literal("★ " + e.name + " has begun! Join and get " + chips(e.startChips)
                + ", win duels to take your opponents' chips, and collect " + e.goal + " to reach the finals"
                + (e.endsAt > 0 ? " within " + minutes + (minutes == 1 ? " minute" : " minutes") : "") + ". ")
                .withStyle(ChatFormatting.GOLD).append(joinButton(e)));
        syncAll();
        return null;
    }

    String cancel() {
        Event e = data.event;
        if (e == null || !e.active()) {
            return "No Star Chip event is on.";
        }
        e.state = Event.CANCELLED;
        bets.clear();
        requests.clear();
        data.setDirty();
        announce(e, Component.literal(e.name + " was called off." + (e.finalsStarted
                ? " The finals tournament goes on by itself; /jadm tournament cancel ends it too." : ""))
                .withStyle(ChatFormatting.GOLD));
        syncAll();
        return null;
    }

    /** Begins the finals now, filling empty seats with the duelists who have the most chips. */
    String finals() {
        Event e = running();
        if (e == null) {
            return "No Star Chip event is running.";
        }
        beginFinals(e, true);
        return null;
    }

    /** An operator changes someone's chips: "give", "take" or "set". */
    String adjust(String name, String how, int amount) {
        Event e = data.event;
        if (e == null || !e.active()) {
            return "No Star Chip event is on.";
        }
        Map.Entry<UUID, Duelist> found = find(e, name);
        if (found == null) {
            return "Nobody named " + name + " is in " + e.name + ".";
        }
        Duelist d = found.getValue();
        int before = d.chips;
        d.chips = Math.max(0, switch (how) {
            case "give" -> d.chips + amount;
            case "take" -> d.chips - amount;
            default -> amount;
        });
        if (d.chips > 0 && (d.status.equals(Duelist.OUT) || d.status.equals(Duelist.LEFT))) {
            d.status = Duelist.IN;
        }
        if (e.state.equals(Event.RUNNING)) {
            checkStanding(e, found.getKey(), d);
        }
        data.setDirty();
        sync(found.getKey());
        tell(found.getKey(), Component.literal("Your Star Chips: " + before + " → " + d.chips + ".")
                .withStyle(ChatFormatting.GOLD));
        return null;
    }

    String kick(String name) {
        Event e = data.event;
        if (e == null || !e.active()) {
            return "No Star Chip event is on.";
        }
        Map.Entry<UUID, Duelist> found = find(e, name);
        if (found == null) {
            return "Nobody named " + name + " is in " + e.name + ".";
        }
        drop(e, found.getKey(), found.getValue(), found.getValue().name + " was removed from " + e.name);
        return null;
    }

    private static Map.Entry<UUID, Duelist> find(Event e, String name) {
        for (Map.Entry<UUID, Duelist> entry : e.duelists.entrySet()) {
            if (entry.getValue().name.equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    // ---- Taking part ----------------------------------------------------------------------------------------------

    String join(ServerPlayer player) {
        Event e = data.event;
        if (e == null || !e.active()) {
            return "No Star Chip event is on. An operator starts one with /jadm starchips start.";
        }
        if (!e.state.equals(Event.RUNNING)) {
            return "The finals of " + e.name + " are already being held.";
        }
        Duelist d = e.duelists.get(player.getUUID());
        if (d != null) {
            return d.status.equals(Duelist.IN) || d.status.equals(Duelist.QUALIFIED)
                    ? "You are already in " + e.name + "." : "You already took part in " + e.name + ".";
        }
        if (!JadmServerConfig.STAR_CHIPS.lateJoin.get() && now() - e.startedAt > 60_000) {
            return e.name + " is under way; joining late is turned off.";
        }
        int fee = JadmServerConfig.STAR_CHIPS.entryFee.get();
        if (fee > 0 && Points.active() && !Points.get(server).take(server, player.getUUID(), fee)) {
            return "Joining costs " + Points.format(fee) + ".";
        }
        d = new Duelist();
        d.name = player.getScoreboardName();
        d.chips = e.startChips;
        e.duelists.put(player.getUUID(), d);
        data.setDirty();
        player.sendSystemMessage(Component.literal("Welcome to " + e.name + "! You have " + chips(d.chips) + ". "
                + "Every duel with another duelist of the event" + (JadmServerConfig.STAR_CHIPS.npcDuels.get()
                ? " or an NPC duelist" : "") + " is for Star Chips. Collect " + e.goal + " to reach the finals; "
                + "lose them all and you are out." + (fee > 0 && Points.active() ? " Paid " + Points.format(fee)
                + "." : "")).withStyle(ChatFormatting.GREEN));
        announce(e, Component.literal(d.name + " joined " + e.name + " (" + e.duelists.size()
                + (e.duelists.size() == 1 ? " duelist)." : " duelists).")).withStyle(ChatFormatting.GRAY));
        sync(player.getUUID());
        return null;
    }

    String leave(ServerPlayer player) {
        Event e = data.event;
        Duelist d = e == null || !e.active() ? null : e.duelists.get(player.getUUID());
        if (d == null || d.status.equals(Duelist.LEFT)) {
            return "You aren't in a Star Chip event.";
        }
        drop(e, player.getUUID(), d, d.name + " left " + e.name);
        return null;
    }

    /** Takes a duelist out: their chips are gone, and a finalist's seat in the finals too if they haven't begun. */
    private void drop(Event e, UUID id, Duelist d, String news) {
        d.status = Duelist.LEFT;
        d.chips = 0;
        if (!e.finalsStarted) {
            e.finalists.remove(id);
        }
        data.setDirty();
        announce(e, Component.literal(news + ".").withStyle(ChatFormatting.GRAY));
        sync(id);
    }

    /**
     * {@code /jadm starchips duel <player> <chips>}: challenges another duelist of the event to a duel for that many
     * Star Chips each.
     */
    String challenge(ServerPlayer from, ServerPlayer to, int chips) {
        Event e = running();
        if (e == null) {
            return "No Star Chip event is running.";
        }
        String problem = cantBet(e, from.getUUID(), "You");
        if (problem == null) {
            problem = cantBet(e, to.getUUID(), to.getScoreboardName());
        }
        if (problem != null) {
            return problem;
        }
        int most = wagerLimit(e, from.getUUID(), to.getUUID());
        if (chips > most) {
            return "This duel can be for " + chips(most) + " at most.";
        }
        requests.put(from.getUUID(), new Request(to.getUUID(), chips));
        DuelManager.get(server).challenge(from, to, false);
        return null;
    }

    /** Why this person can't put up chips right now, or {@code null} if they can. */
    private static String cantBet(Event e, UUID id, String who) {
        Duelist d = e.duelists.get(id);
        boolean you = who.equals("You");
        if (d == null || d.status.equals(Duelist.LEFT)) {
            return (you ? "You aren't" : who + " isn't") + " in " + e.name + ".";
        }
        if (d.status.equals(Duelist.QUALIFIED)) {
            return (you ? "You have" : who + " has") + " qualified already and duels again in the finals.";
        }
        if (d.chips <= 0) {
            return (you ? "You have" : who + " has") + " no Star Chips left.";
        }
        return null;
    }

    /** The most two duelists can put up: what the poorer one has, within {@code maxWager}. */
    private int wagerLimit(Event e, UUID a, UUID b) {
        int most = Math.min(e.duelists.get(a).chips, b == null ? Integer.MAX_VALUE : e.duelists.get(b).chips);
        int cap = JadmServerConfig.STAR_CHIPS.maxWager.get();
        return cap > 0 ? Math.min(most, cap) : most;
    }

    /**
     * The wager a duel between these two would be for, or 0 if it wouldn't be a Star Chip duel.
     *
     * @param b {@code null} for an NPC duelist ({@code npc}) or a bot
     */
    private int wager(UUID a, UUID b, boolean npc) {
        Event e = running();
        if (e == null || a == null || cantBet(e, a, "You") != null) {
            return 0;
        }
        var config = JadmServerConfig.STAR_CHIPS;
        if (b == null) {
            return npc && config.npcDuels.get() ? Math.min(config.npcWager.get(), wagerLimit(e, a, null)) : 0;
        }
        if (cantBet(e, b, "They") != null) {
            return 0;
        }
        Request asked = requests.get(a);
        if (asked == null || !asked.target().equals(b)) {
            asked = requests.get(b);
            asked = asked != null && asked.target().equals(a) ? asked : null;
        }
        return Math.max(1, Math.min(asked != null ? asked.chips() : config.defaultWager.get(), wagerLimit(e, a, b)));
    }

    /** What an invitation to a 1v1 duel says about Star Chips: "" if it isn't for any. */
    public String inviteNote(UUID host, UUID target) {
        int chips = wager(host, target, false);
        return chips > 0 ? " It's a Star Chip duel: " + chips(chips) + " each." : "";
    }

    /**
     * A 1v1 duel started: if both sides are in the event (or one is an NPC duelist and those count), it is for Star
     * Chips, and both are told how many.
     *
     * @param a,b the two people, {@code null} for the NPC or bot seat
     */
    public void begin(UUID a, UUID b, boolean npc) {
        if (a == null) {
            a = b;
            b = null;
        }
        int chips = wager(a, b, npc);
        if (chips <= 0) {
            return;
        }
        requests.remove(a);
        if (b != null) {
            requests.remove(b);
        }
        Component text = Component.literal("★ Star Chip duel: " + (b == null
                ? "you put up " + chips(chips) + " against the NPC's " + chips + "."
                : "each side puts up " + chips(chips) + ". The winner takes them all.")).withStyle(ChatFormatting.GOLD);
        for (UUID id : b == null ? List.of(a) : List.of(a, b)) {
            bets.put(id, chips);
            tell(id, text);
        }
    }

    /**
     * The duel {@code person} was in is over: hands over the Star Chips it was for.
     *
     * @return lines for their result screen
     */
    public List<String> settle(UUID person, boolean decided, boolean won) {
        Integer chips = bets.remove(person);
        Event e = running();
        Duelist d = e == null || chips == null ? null : e.duelists.get(person);
        if (d == null || !decided || !d.status.equals(Duelist.IN)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        if (won) {
            d.chips += chips;
            d.wins++;
            lines.add("Star Chips won: +" + chips + " (" + d.chips + "/" + e.goal + ")");
        } else {
            int lost = Math.min(chips, d.chips);
            d.chips -= lost;
            d.losses++;
            lines.add("Star Chips lost: -" + lost + " (" + d.chips + "/" + e.goal + ")");
        }
        String change = checkStanding(e, person, d);
        if (change != null) {
            lines.add(change);
        }
        data.setDirty();
        sync(person);
        return lines;
    }

    /** Qualifies or knocks out a duelist whose chips reached the goal or ran out. @return a line about it, or null */
    private String checkStanding(Event e, UUID id, Duelist d) {
        if (d.status.equals(Duelist.IN) && d.chips >= e.goal) {
            d.status = Duelist.QUALIFIED;
            e.finalists.add(id);
            announce(e, Component.literal("★ " + d.name + " has " + chips(d.chips) + " and qualifies for the "
                    + "finals of " + e.name + "! (" + e.qualified() + "/" + e.finalistsWanted + ")")
                    .withStyle(ChatFormatting.GOLD));
            return "Qualified for the finals!";
        }
        if (d.status.equals(Duelist.QUALIFIED) && d.chips < e.goal) {
            d.status = d.chips > 0 ? Duelist.IN : Duelist.OUT;
            e.finalists.remove(id);
        }
        if (d.status.equals(Duelist.IN) && d.chips <= 0) {
            d.status = Duelist.OUT;
            announce(e, Component.literal(d.name + " lost their last Star Chip and is out of " + e.name + ".")
                    .withStyle(ChatFormatting.GRAY));
            return "No Star Chips left: you are out of the event";
        }
        return null;
    }

    // ---- The finals -----------------------------------------------------------------------------------------------

    public void tick() {
        long now = now();
        if (now - lastSecond < 1000) {
            return;
        }
        lastSecond = now;
        Event e = data.event;
        if (e == null) {
            return;
        }
        if (e.state.equals(Event.RUNNING)) {
            if (e.qualified() >= e.finalistsWanted) {
                beginFinals(e, false);
            } else if (e.endsAt > 0 && now >= e.endsAt) {
                beginFinals(e, JadmServerConfig.STAR_CHIPS.fillFinals.get());
            }
        } else if (e.state.equals(Event.FINALS)) {
            if (!e.finalsStarted) {
                if (now - lastTry >= RETRY_MILLIS) {
                    holdFinals(e);
                }
            } else {
                String winner = TournamentManager.get(server).outcome(e.finalsName);
                if (winner != null) {
                    finish(e, winner.isEmpty() ? null : winner);
                }
            }
        }
    }

    /** Picks the finalists and holds the finals. */
    private void beginFinals(Event e, boolean fill) {
        bets.clear();
        requests.clear();
        if (fill && e.finalists.size() < e.finalistsWanted) {
            List<Map.Entry<UUID, Duelist>> rest = new ArrayList<>(e.duelists.entrySet().stream()
                    .filter(x -> x.getValue().status.equals(Duelist.IN) && x.getValue().chips > 0).toList());
            rest.sort(Comparator.comparingInt((Map.Entry<UUID, Duelist> x) -> -x.getValue().chips)
                    .thenComparingInt(x -> -x.getValue().wins));
            for (Map.Entry<UUID, Duelist> x : rest) {
                if (e.finalists.size() >= e.finalistsWanted) {
                    break;
                }
                x.getValue().status = Duelist.QUALIFIED;
                e.finalists.add(x.getKey());
            }
        }
        e.state = Event.FINALS;
        e.finalsName = e.name + " Finals";
        data.setDirty();
        List<String> names = e.finalists.stream().map(id -> e.duelists.get(id).name).toList();
        if (names.size() < 2) {
            if (names.size() == 1) {
                // Nobody to play: the one finalist wins it.
                UUID id = e.finalists.get(0);
                List<String> got = TournamentManager.get(server).handOut(id, prizes("prizesFirst"));
                tell(id, Component.literal("No one else reached the finals, so you win " + e.name + "!"
                        + (got.isEmpty() ? "" : " " + String.join(", ", got) + ".")).withStyle(ChatFormatting.GOLD));
                finish(e, names.get(0));
            } else {
                finish(e, null);
            }
            return;
        }
        announce(e, Component.literal("★ The finals of " + e.name + " begin: " + String.join(", ", names) + ". "
                + "Finalists are called to the tournament arena when their match is due.")
                .withStyle(ChatFormatting.GOLD));
        syncAll();
        holdFinals(e);
    }

    /** Opens the finals tournament; if it can't be yet, says why (once) and tries again later. */
    private void holdFinals(Event e) {
        lastTry = now();
        List<UUID> ids = List.copyOf(e.finalists);
        List<String> names = ids.stream().map(id -> e.duelists.get(id).name).toList();
        List<String> settings = new ArrayList<>(JadmServerConfig.STAR_CHIPS.finalsSettings.get().stream()
                .map(String::valueOf).toList());
        String problem = TournamentManager.get(server).invitational(e.finalsName, ids, names, settings);
        if (problem == null) {
            e.finalsStarted = true;
            lastFinalsProblem = null;
            data.setDirty();
            return;
        }
        if (!problem.equals(lastFinalsProblem)) {
            lastFinalsProblem = problem;
            announce(e, Component.literal("The finals of " + e.name + " wait: " + problem + ". Trying again "
                    + "every 30 seconds.").withStyle(ChatFormatting.YELLOW));
        }
    }

    /** The prize entries for a place, from the finals settings or else the [tournament] defaults. */
    private static List<String> prizes(String key) {
        for (Object entry : JadmServerConfig.STAR_CHIPS.finalsSettings.get()) {
            String s = String.valueOf(entry);
            int eq = s.indexOf('=');
            if (eq > 0 && s.substring(0, eq).strip().equalsIgnoreCase(key)) {
                return TournamentOptions.splitList(s.substring(eq + 1));
            }
        }
        return TournamentOptions.splitList(TournamentOptions.configured(TournamentOptions.option(key)));
    }

    private void finish(Event e, String champion) {
        e.state = Event.DONE;
        e.champion = champion;
        data.setDirty();
        announce(e, Component.literal(champion == null ? e.name + " is over without a champion."
                : "★ " + champion + " wins " + e.name + "!").withStyle(ChatFormatting.GOLD));
        syncAll();
    }

    // ---- Players --------------------------------------------------------------------------------------------------

    public void onLogin(ServerPlayer player) {
        sync(player.getUUID());
        Event e = running();
        if (e != null && !e.duelists.containsKey(player.getUUID())
                && (JadmServerConfig.STAR_CHIPS.lateJoin.get() || now() - e.startedAt <= 60_000)) {
            player.sendSystemMessage(Component.literal("★ " + e.name + " is on: collect " + e.goal
                    + " Star Chips to reach the finals. ").withStyle(ChatFormatting.GOLD).append(joinButton(e)));
        }
    }

    /** "Duelist Kingdom: running, 3 of 4 finalists so far" and the table, for {@code /jadm starchips}. */
    List<Component> status(ServerPlayer viewer) {
        List<Component> out = new ArrayList<>();
        Event e = data.event;
        if (e == null) {
            out.add(Component.literal("No Star Chip event has been held yet."));
            return out;
        }
        String state = switch (e.state) {
            case Event.RUNNING -> "under way, " + e.qualified() + " of " + e.finalistsWanted + " finalists so far"
                    + (e.endsAt > 0 ? ", finals in " + Math.max(1, (e.endsAt - now() + 59_999) / 60_000) + " min"
                    : "");
            case Event.FINALS -> "the finals are being held" + (e.finalsStarted ? " (/jadm tournament)" : "");
            case Event.DONE -> e.champion != null ? "won by " + e.champion : "over";
            default -> "called off";
        };
        out.add(Component.literal("★ " + e.name + ": " + state + ". Goal: " + e.goal + " Star Chips.")
                .withStyle(ChatFormatting.GOLD));
        List<Map.Entry<UUID, Duelist>> table = new ArrayList<>(e.duelists.entrySet());
        table.sort(Comparator.comparingInt((Map.Entry<UUID, Duelist> x) -> rank(x.getValue().status))
                .thenComparingInt(x -> -x.getValue().chips));
        int shown = 0;
        for (Map.Entry<UUID, Duelist> x : table) {
            Duelist d = x.getValue();
            boolean you = viewer != null && x.getKey().equals(viewer.getUUID());
            if (shown++ >= 10 && !you) {
                continue;
            }
            ChatFormatting color = switch (d.status) {
                case Duelist.QUALIFIED -> ChatFormatting.GREEN;
                case Duelist.IN -> ChatFormatting.WHITE;
                default -> ChatFormatting.DARK_GRAY;
            };
            out.add(Component.literal(" " + (you ? "> " : "") + d.name + ": " + d.chips + " ★  " + d.wins + "W "
                    + d.losses + "L" + switch (d.status) {
                case Duelist.QUALIFIED -> "  finalist";
                case Duelist.OUT -> "  out";
                case Duelist.LEFT -> "  left";
                default -> "";
            }).withStyle(color));
        }
        if (e.duelists.isEmpty()) {
            out.add(Component.literal(" Nobody has joined yet.").withStyle(ChatFormatting.GRAY));
        }
        if (viewer != null && e.state.equals(Event.RUNNING) && !e.duelists.containsKey(viewer.getUUID())) {
            out.add(joinButton(e));
        }
        return out;
    }

    private static int rank(String status) {
        return switch (status) {
            case Duelist.QUALIFIED -> 0;
            case Duelist.IN -> 1;
            case Duelist.OUT -> 2;
            default -> 3;
        };
    }

    private void syncAll() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player.getUUID());
        }
    }

    /** Tells a player's client their chips for the glove (or that they aren't in an event). */
    private void sync(UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            return;
        }
        Event e = data.event;
        Duelist d = e == null || !e.active() ? null : e.duelists.get(id);
        PacketDistributor.sendToPlayer(player, d == null ? StarChipsPayload.none()
                : new StarChipsPayload(d.status, d.chips, e.goal, e.name));
    }

    // ---- Chat -----------------------------------------------------------------------------------------------------

    static String chips(int n) {
        return n + (n == 1 ? " Star Chip" : " Star Chips");
    }

    private static MutableComponent joinButton(Event e) {
        return Component.literal("[Join]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jadm starchips join"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Join " + e.name + " and get " + chips(e.startChips)))));
    }

    private void announce(Event e, Component text) {
        if (JadmServerConfig.STAR_CHIPS.announce.get()) {
            server.getPlayerList().broadcastSystemMessage(text, false);
            return;
        }
        Set<UUID> told = new HashSet<>(e.duelists.keySet());
        if (e.host != null) {
            told.add(e.host);
        }
        told.forEach(id -> tell(id, text));
    }

    private void tell(UUID id, Component text) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            player.sendSystemMessage(text);
        }
    }
}
