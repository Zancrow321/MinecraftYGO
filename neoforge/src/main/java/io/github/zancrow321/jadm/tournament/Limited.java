package io.github.zancrow321.jadm.tournament;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.data.DeckRules;
import io.github.zancrow321.jadm.engine.tournament.Draft;
import io.github.zancrow321.jadm.engine.tournament.LimitedDecks;
import io.github.zancrow321.jadm.item.BinderItem;
import io.github.zancrow321.jadm.item.BoosterPackItem;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.JadmItems;
import io.github.zancrow321.jadm.network.LimitedPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Sealed and Draft tournaments: decks built on the spot from fresh packs. At the start everyone opens the same
 * packs ({@code limitedPacks}); in a Draft they take one card from their pack and pass the rest on, all at once,
 * until every pack is drafted. Then everyone builds a deck from their pool in the deck building window, and the
 * bracket is drawn as in any tournament. NPCs (and whoever left, is offline or runs out of time) pick and build by
 * themselves.
 */
final class Limited {
    private final TournamentManager manager;
    private final MinecraftServer server;

    Limited(TournamentManager manager, MinecraftServer server) {
        this.manager = manager;
        this.server = server;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /** "pack", "pack 3", "pack:MRD", "pack:metal_raiders 3" */
    static boolean validPacks(String entry) {
        String[] p = entry.strip().toLowerCase(Locale.ROOT).split("\\s+");
        if (p.length > 2 || p.length == 2 && !p[1].matches("[1-9]\\d?")) {
            return false;
        }
        return p[0].equals("pack") || p[0].startsWith("pack:") && p[0].length() > 5;
    }

    /** "5 packs of Legend of Blue Eyes White Dragon", "5 packs (3 Metal Raiders, 2 Pharaoh's Servant)" */
    static String describePacks(Tournament t) {
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        if (t.packSets != null) {
            for (String id : t.packSets) {
                String name = setName(id);
                if (!names.isEmpty() && names.get(names.size() - 1).equals(name)) {
                    counts.set(counts.size() - 1, counts.get(counts.size() - 1) + 1);
                } else {
                    names.add(name);
                    counts.add(1);
                }
            }
        } else {
            for (String entry : t.list("limitedPacks")) {
                String[] p = entry.strip().split("\\s+");
                names.add(p[0].contains(":") ? setName(p[0].substring(5)) : "a random set");
                counts.add(p.length > 1 ? Integer.parseInt(p[1]) : 1);
            }
        }
        int total = counts.stream().mapToInt(Integer::intValue).sum();
        String packs = total + (total == 1 ? " pack" : " packs");
        if (names.size() == 1) {
            return packs + " of " + names.get(0);
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            parts.add(counts.get(i) + " " + names.get(i));
        }
        return packs + " (" + String.join(", ", parts) + ")";
    }

    private static String setName(String id) {
        BoosterSets.BoosterSet set = findSet(id);
        return set == null ? id : set.name();
    }

    /** A booster set by its id ("metal_raiders") or its code ("MRD"), or {@code null}. */
    static BoosterSets.BoosterSet findSet(String key) {
        BoosterSets.BoosterSet set = JadmData.set(key.toLowerCase(Locale.ROOT));
        if (set != null) {
            return set;
        }
        for (BoosterSets sets : List.of(JadmData.sets(), JadmData.allSets())) {
            for (BoosterSets.BoosterSet s : sets.sets().values()) {
                if (s.code().equalsIgnoreCase(key)) {
                    return s;
                }
            }
        }
        return null;
    }

    /** The set of each pack, picking the random ones now so that everyone opens the same. */
    private List<String> resolvePacks(Tournament t) {
        List<String> out = new ArrayList<>();
        var random = server.overworld().getRandom();
        for (String entry : t.list("limitedPacks")) {
            String[] p = entry.strip().toLowerCase(Locale.ROOT).split("\\s+");
            int n = p.length > 1 ? Integer.parseInt(p[1]) : 1;
            BoosterSets.BoosterSet set = p[0].startsWith("pack:") ? findSet(p[0].substring(5)) : null;
            if (set == null) {
                if (p[0].startsWith("pack:")) {
                    Jadm.LOGGER.warn("Tournament pack set {} doesn't exist, a random set is opened instead", p[0]);
                }
                set = BoosterPackItem.randomSet(random);
            }
            if (set == null) {
                continue;
            }
            for (int i = 0; i < n; i++) {
                out.add(set.id());
            }
        }
        return out;
    }

    private static List<Draft.Card> open(String setId, Random random) {
        List<Draft.Card> out = new ArrayList<>();
        BoosterSets.BoosterSet set = JadmData.set(setId);
        if (set != null) {
            set.open(random).forEach(c -> out.add(new Draft.Card(c.code(), c.rarity().id())));
        }
        return out;
    }

    // ---- The start ----------------------------------------------------------------------------------------------

    /** The duelists are in and shuffled: open the packs. @return what went wrong, or {@code null} */
    String begin(Tournament t) {
        t.packSets = resolvePacks(t);
        if (t.packSets.isEmpty()) {
            return "There are no booster sets to open packs of.";
        }
        Random random = new Random();
        if (t.deckMode().equals("sealed")) {
            for (Tournament.Entrant e : t.entrants) {
                e.pool = new ArrayList<>();
                for (String set : t.packSets) {
                    e.pool.addAll(open(set, random));
                }
            }
            t.news("Everyone opened " + describePacks(t));
            manager.announce(t, Component.literal("\"" + t.name + "\" begins: Sealed, " + t.entrants.size()
                    + " duelists. Everyone opens " + describePacks(t) + " and builds a deck of at least "
                    + t.integer("deckMinimum") + " cards in " + t.integer("buildMinutes") + " minutes. ")
                    .withStyle(ChatFormatting.GOLD).append(TournamentManager.button("[Build deck]",
                            "/jadm tournament deck", "Open your pool and build your deck")));
            beginBuild(t);
            return null;
        }
        t.draft = new Draft(t.entrants.size(), t.packSets.size());
        t.state = Tournament.DRAFT;
        openRound(t, random);
        t.news("The draft began: " + describePacks(t));
        manager.announce(t, Component.literal("\"" + t.name + "\" begins: Draft, " + t.entrants.size()
                + " duelists, " + describePacks(t) + ". Take one card, pass the rest on; then build a deck of at least "
                + t.integer("deckMinimum") + " cards. ").withStyle(ChatFormatting.GOLD)
                .append(TournamentManager.button("[Draft]", "/jadm tournament deck", "Open the draft")));
        sendAll(t, true);
        advance(t);
        return null;
    }

    private void openRound(Tournament t, Random random) {
        List<List<Draft.Card>> packs = new ArrayList<>();
        for (int i = 0; i < t.entrants.size(); i++) {
            packs.add(open(t.packSets.get(t.draft.round), random));
        }
        t.draft.open(packs);
        t.phaseEndsAt = now() + t.integer("pickSeconds") * 1000L;
    }

    // ---- Drafting -----------------------------------------------------------------------------------------------

    /** Picks for those who don't pick themselves and passes the packs on as long as everyone has picked. */
    private void advance(Tournament t) {
        Draft d = t.draft;
        boolean moved = false;
        while (!d.finished()) {
            autoPicks(t, now() >= t.phaseEndsAt);
            if (!d.everyoneChose()) {
                break;
            }
            if (d.pass() && !d.finished()) {
                openRound(t, new Random());
                t.news("Pack " + (d.round + 1) + " opened");
            }
            t.phaseEndsAt = now() + t.integer("pickSeconds") * 1000L;
            moved = true;
        }
        if (d.finished()) {
            for (int i = 0; i < t.entrants.size(); i++) {
                t.entrants.get(i).pool = new ArrayList<>(d.picked.get(i));
            }
            t.draft = null;
            t.news("The draft is over");
            manager.announce(t, Component.literal("The draft of \"" + t.name + "\" is over: build a deck of at least "
                    + t.integer("deckMinimum") + " cards from your picks in " + t.integer("buildMinutes")
                    + " minutes. ").withStyle(ChatFormatting.GOLD).append(TournamentManager.button("[Build deck]",
                    "/jadm tournament deck", "Open your pool and build your deck")));
            beginBuild(t);
            return;
        }
        if (moved) {
            sendAll(t, false);
            manager.changed();
        }
    }

    /** NPCs, those who left or are offline pick at once; the rest when their time is up. */
    private void autoPicks(Tournament t, boolean timeUp) {
        Draft d = t.draft;
        Random random = new Random();
        for (int i = 0; i < d.seats; i++) {
            if (!d.waitingFor(i)) {
                continue;
            }
            Tournament.Entrant e = t.entrants.get(i);
            ServerPlayer player = e.npc() ? null : server.getPlayerList().getPlayer(e.player);
            if (e.npc() || e.left || player == null || timeUp) {
                int index = Draft.botPick(d.packs.get(i), JadmData.cards(), random);
                d.choose(i, index);
                if (player != null && !e.left) {
                    player.sendSystemMessage(Component.literal("Time's up: " + JadmData.cards().name(
                            d.packs.get(i).get(index).code) + " was picked for you.").withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    // ---- Building -----------------------------------------------------------------------------------------------

    private void beginBuild(Tournament t) {
        t.state = Tournament.BUILD;
        t.phaseEndsAt = now() + t.integer("buildMinutes") * 60_000L;
        for (Tournament.Entrant e : t.entrants) {
            e.main = new ArrayList<>();
            e.extra = new ArrayList<>();
            e.built = e.npc() || e.left;
            if (e.built) {
                autoBuild(t, e);
            }
        }
        sendAll(t, true);
        manager.changed();
        checkBuilt(t);
    }

    private static List<Integer> codes(List<Draft.Card> pool) {
        return pool == null ? List.of() : pool.stream().map(c -> c.code).toList();
    }

    /** The deck needs this many cards: {@code deckMinimum}, or every main deck card if the pool has fewer. */
    private static int minimum(Tournament t, Tournament.Entrant e) {
        int mainCards = (int) codes(e.pool).stream().filter(c -> !DeckRules.isExtra(JadmData.cards().card(c)))
                .count();
        return Math.max(1, Math.min(t.integer("deckMinimum"), mainCards));
    }

    private static List<String> problems(Tournament t, Tournament.Entrant e) {
        return LimitedDecks.problems(e.main, e.extra, codes(e.pool), JadmData.cards(), minimum(t, e));
    }

    private static void autoBuild(Tournament t, Tournament.Entrant e) {
        Deck deck = LimitedDecks.build(e.name, codes(e.pool), JadmData.cards(), minimum(t, e));
        e.main = new ArrayList<>(deck.main());
        e.extra = new ArrayList<>(deck.extra());
    }

    /** Draws the bracket once everyone is done (or the time is up), building the decks of those who aren't. */
    private void checkBuilt(Tournament t) {
        boolean everyone = t.entrants.stream().allMatch(e -> e.built);
        if (!everyone && now() < t.phaseEndsAt) {
            return;
        }
        String kind = t.deckMode().equals("sealed") ? "Sealed deck" : "Draft deck";
        for (Tournament.Entrant e : t.entrants) {
            if (!e.built || !problems(t, e).isEmpty()) {
                if (problems(t, e).isEmpty()) {
                    tell(e, "Time's up: you play the deck as it is.");
                } else {
                    autoBuild(t, e);
                    tell(e, "Time's up: a deck was built from your pool for you.");
                }
            }
            e.built = true;
            e.main = List.copyOf(e.main);
            e.extra = List.copyOf(e.extra);
            e.deckName = kind + ", " + e.main.size() + " cards";
        }
        t.news("Decks are built");
        for (Tournament.Entrant e : t.entrants) {
            ServerPlayer player = e.npc() ? null : server.getPlayerList().getPlayer(e.player);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new LimitedPayload(false, ""));
            }
        }
        manager.startBracket(t);
    }

    private void tell(Tournament.Entrant e, String text) {
        ServerPlayer player = e.npc() ? null : server.getPlayerList().getPlayer(e.player);
        if (player != null && !e.left) {
            player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.YELLOW));
        }
    }

    // ---- Hooks --------------------------------------------------------------------------------------------------

    /** Every second while drafting or building. */
    void tick(Tournament t) {
        if (t.state.equals(Tournament.DRAFT)) {
            advance(t);
        } else {
            checkBuilt(t);
        }
    }

    /** Someone left or was removed after the start: bots pick and build for them, and they drop out at the draw. */
    void leave(Tournament t, Tournament.Entrant e) {
        e.left = true;
        ServerPlayer player = e.npc() ? null : server.getPlayerList().getPlayer(e.player);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new LimitedPayload(false, ""));
        }
        if (t.state.equals(Tournament.DRAFT)) {
            advance(t);
        } else if (t.state.equals(Tournament.BUILD)) {
            if (!e.built) {
                autoBuild(t, e);
                e.built = true;
            }
            checkBuilt(t);
        }
    }

    /** A pick or a deck change from the window. */
    void action(Tournament t, ServerPlayer player, String action, int value) {
        int index = t.indexOf(player.getUUID());
        if (index < 0 || !t.limitedPhase()) {
            return;
        }
        Tournament.Entrant e = t.entrants.get(index);
        if (e.left) {
            return;
        }
        if (t.state.equals(Tournament.DRAFT)) {
            if (action.equals("pick") && t.draft.choose(index, value)) {
                advance(t);
                if (t.state.equals(Tournament.DRAFT)) {
                    sendAll(t, false);
                }
            }
            return;
        }
        boolean all = false;
        switch (action) {
            case "add" -> {
                if (e.built) {
                    return;
                }
                long inPool = codes(e.pool).stream().filter(c -> c == value).count();
                long inDeck = e.main.stream().filter(c -> c == value).count()
                        + e.extra.stream().filter(c -> c == value).count();
                boolean toExtra = DeckRules.isExtra(JadmData.cards().card(value));
                List<Integer> part = toExtra ? e.extra : e.main;
                if (inDeck < inPool && part.size() < (toExtra ? DeckRules.EXTRA_MAX : DeckRules.MAIN_MAX)) {
                    part.add(value);
                }
            }
            case "remove" -> {
                if (!e.built && !e.main.remove(Integer.valueOf(value))) {
                    e.extra.remove(Integer.valueOf(value));
                }
            }
            case "clear" -> {
                if (!e.built) {
                    e.main.clear();
                    e.extra.clear();
                }
            }
            case "auto" -> {
                if (!e.built) {
                    autoBuild(t, e);
                }
            }
            case "done" -> {
                List<String> problems = problems(t, e);
                if (!problems.isEmpty()) {
                    player.sendSystemMessage(Component.literal(problems.get(0)).withStyle(ChatFormatting.RED));
                    return;
                }
                e.built = true;
                all = true;
                t.news(e.name + " finished their deck");
            }
            case "edit" -> {
                e.built = false;
                all = true;
            }
            default -> {
                return;
            }
        }
        if (all) {
            sendAll(t, false);
            manager.changed();
            checkBuilt(t);
        } else {
            send(t, index, false);
        }
    }

    /** {@code /jadm tournament deck}: opens the window. @return what went wrong, or {@code null} */
    String open(Tournament t, ServerPlayer player) {
        int index = t == null ? -1 : t.indexOf(player.getUUID());
        if (index < 0 || !t.limitedPhase()) {
            return "You have no draft or deck to build right now.";
        }
        send(t, index, true);
        return null;
    }

    void sendAll(Tournament t, boolean open) {
        for (int i = 0; i < t.entrants.size(); i++) {
            if (!t.entrants.get(i).npc() && !t.entrants.get(i).left) {
                send(t, i, open);
            }
        }
    }

    void send(Tournament t, int index, boolean open) {
        Tournament.Entrant e = t.entrants.get(index);
        ServerPlayer player = e.npc() ? null : server.getPlayerList().getPlayer(e.player);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new LimitedPayload(open,
                    TournamentData.GSON.toJson(view(t, index))));
        }
    }

    private LimitedView view(Tournament t, int index) {
        Tournament.Entrant e = t.entrants.get(index);
        LimitedView v = new LimitedView();
        v.tournament = t.name;
        v.id = t.openedAt;
        v.mode = t.deckMode();
        v.phase = t.state;
        v.secondsLeft = (int) Math.max(0, (t.phaseEndsAt - now() + 999) / 1000);
        v.minimum = t.integer("deckMinimum");
        if (t.state.equals(Tournament.DRAFT)) {
            Draft d = t.draft;
            v.round = d.round;
            v.rounds = d.rounds;
            v.pick = d.pick;
            v.setName = setName(t.packSets.get(d.round));
            v.pack = d.packs.get(index);
            v.chosen = d.chosen.get(index) >= 0;
            v.passTo = t.entrants.get(d.passesTo(index)).name;
            v.pool = d.picked.get(index);
            for (int i = 0; i < d.seats; i++) {
                v.waiting += d.waitingFor(i) ? 1 : 0;
            }
            return v;
        }
        v.minimum = minimum(t, e);
        v.pool = e.pool == null ? List.of() : e.pool;
        v.main = e.main;
        v.extra = e.extra;
        v.built = e.built;
        v.problems = problems(t, e);
        v.waiting = (int) t.entrants.stream().filter(x -> !x.built).count();
        if (t.deckMode().equals("sealed") && e.pool != null) {
            int at = 0;
            for (String id : t.packSets) {
                BoosterSets.BoosterSet set = JadmData.set(id);
                int size = set == null ? 0 : set.profile().size();
                if (at + size > e.pool.size()) {
                    break;
                }
                v.packs.add(e.pool.subList(at, at + size));
                v.packNames.add(setName(id));
                at += size;
            }
        }
        return v;
    }

    /** "Draft: pack 2 of 5, pick 3", for the tournament window */
    static String status(Tournament t) {
        if (t.state.equals(Tournament.DRAFT) && t.draft != null) {
            return "Draft: pack " + (t.draft.round + 1) + " of " + t.draft.rounds + ", pick " + (t.draft.pick + 1);
        }
        long done = t.entrants.stream().filter(e -> !e.npc() && e.built).count();
        long people = t.entrants.stream().filter(e -> !e.npc()).count();
        long minutes = Math.max(1, (t.phaseEndsAt - now() + 59_999) / 60_000);
        return "Deck building: " + done + " of " + people + " done, " + minutes
                + (minutes == 1 ? " minute" : " minutes") + " left";
    }

    // ---- Keeping the cards --------------------------------------------------------------------------------------

    /**
     * With {@code keepCards}, everyone gets the cards they opened or drafted: into a binder they carry, or else the
     * inventory; people who are offline get them when they are next online.
     */
    void handOut(Tournament t) {
        if (!t.bool("keepCards") || t.poolsHandedOut) {
            return;
        }
        t.poolsHandedOut = true;
        for (Tournament.Entrant e : t.entrants) {
            List<Draft.Card> pool = e.pool;
            if (pool == null && t.draft != null) {
                pool = t.draft.picked.get(t.entrants.indexOf(e));
            }
            if (e.npc() || pool == null || pool.isEmpty()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(e.player);
            if (player == null) {
                for (Draft.Card c : pool) {
                    manager.owe(e.player, "card " + c.code + " 1 " + c.rarity);
                }
                continue;
            }
            ItemStack binder = ItemStack.EMPTY;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(JadmItems.BINDER.get())) {
                    binder = stack;
                    break;
                }
            }
            for (Draft.Card c : pool) {
                BoosterSets.Rarity rarity = rarity(c.rarity);
                if (!binder.isEmpty()) {
                    BinderItem.add(binder, c.code, rarity, 1);
                } else {
                    Prizes.hand(player, CardItem.of(c.code, rarity));
                }
            }
            player.sendSystemMessage(Component.literal("You keep the " + pool.size() + " cards of your "
                    + (t.deckMode().equals("sealed") ? "Sealed" : "Draft") + " pool" + (binder.isEmpty() ? "."
                    : ": they went into your binder.")).withStyle(ChatFormatting.GREEN));
        }
    }

    static BoosterSets.Rarity rarity(String id) {
        try {
            return BoosterSets.Rarity.parse(id);
        } catch (RuntimeException e) {
            return BoosterSets.Rarity.COMMON;
        }
    }
}
