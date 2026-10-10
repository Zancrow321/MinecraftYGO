package io.github.zancrow321.jadm.clan;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The clan window's contents, sent to the client as JSON: the viewer's clan with its members, wars and history, the
 * invitations they have, and the clan ranking.
 */
public final class ClanView {
    /** How many clans the ranking lists. */
    public static final int ROWS = 100;

    public static final class CrestView {
        /** A dye color id, or -1 for no crest yet. */
        public int base = -1;
        public List<String> patterns = new ArrayList<>();
        public List<Integer> colors = new ArrayList<>();
    }

    public static final class ClanRow {
        public String name;
        public String tag;
        public int color;
        public int rating;
        public int members;
        public int won;
        public int lost;
        public int drawn;
        public boolean open;
        public boolean atWar;
        public CrestView crest;
    }

    public static final class MemberRow {
        public String name;
        public String role;
        public boolean online;
        public int warWins;
        public int warLosses;
    }

    public static final class WarRow {
        public String otherTag;
        public String otherName;
        public int otherColor;
        public CrestView otherCrest;
        public int mine;
        public int theirs;
        public boolean running;
        /** Declared on the viewer's clan and waiting for its answer. */
        public boolean incoming;
        /** Milliseconds until it ends (running) or until the declaration runs out. */
        public long millisLeft;
        public int target;
        public long stake;
        /** The viewer's clan's best duelist in it so far, or "". */
        public String best = "";
        /** An arena battle rather than a race for points. */
        public boolean battle;
        public List<String> myLineup = new ArrayList<>();
        public List<String> theirLineup = new ArrayList<>();
        public boolean myReady;
        public boolean theirReady;
        /** The viewer is in their clan's lineup. */
        public boolean inLineup;
        /** Whether the battle has begun. */
        public boolean begun;
        /** "Bout 2 of 3: Yugi vs Kaiba", or "". */
        public String status = "";
    }

    public static final class HistoryRow {
        public String otherTag;
        public String otherName;
        public int otherColor;
        public int mine;
        public int theirs;
        /** 1 won, 0 drawn, -1 lost. */
        public int result;
        public String how;
        public long millisAgo;
        public int ratingChange;
        public String mvp;
    }

    public boolean enabled;
    public boolean points;
    public String symbol;
    public int season;
    public long createPrice;
    public int maxMembers;
    public long maxStake;
    public long bannerPrice;
    public int battleDuelists;

    /** The viewer's clan, or {@code null}. */
    public ClanRow clan;
    public String role = "";
    public String motto = "";
    public long treasury;
    public int place;
    public int peak;
    public List<MemberRow> members = new ArrayList<>();
    public List<WarRow> wars = new ArrayList<>();
    public List<HistoryRow> history = new ArrayList<>();
    /** Tags of the clans that invited the viewer. */
    public List<String> invites = new ArrayList<>();
    public List<ClanRow> ranking = new ArrayList<>();
    public int total;

    public static ClanView of(MinecraftServer server, UUID viewer) {
        Clans clans = Clans.get(server);
        var config = JadmServerConfig.CLANS;
        ClanView v = new ClanView();
        v.enabled = config.enabled.get();
        v.points = Points.active();
        v.symbol = JadmServerConfig.POINTS.symbol.get();
        v.season = clans.season();
        v.createPrice = v.points ? config.createPrice.get() : 0;
        v.maxMembers = config.maxMembers.get();
        v.maxStake = v.points ? config.maxStake.get() : 0;
        v.bannerPrice = v.points ? config.bannerPrice.get() : 0;
        v.battleDuelists = config.battleDuelists.get();
        List<Clans.Clan> standings = clans.standings();
        v.total = standings.size();
        for (int i = 0; i < Math.min(ROWS, standings.size()); i++) {
            v.ranking.add(row(clans, standings.get(i)));
        }
        long tick = server.getTickCount();
        Map<UUID, Long> invited = clans.invites.getOrDefault(viewer, Map.of());
        invited.forEach((id, until) -> {
            Clans.Clan c = clans.byId(id);
            if (c != null && until >= tick) {
                v.invites.add(c.tag);
            }
        });
        Clans.Clan clan = clans.of(viewer);
        if (clan == null) {
            return v;
        }
        v.clan = row(clans, clan);
        v.role = clan.role(viewer).title;
        v.motto = clan.motto;
        v.treasury = clan.treasury;
        v.place = standings.indexOf(clan) + 1;
        v.peak = clan.peak;
        clan.members.entrySet().stream()
                .sorted((x, y) -> y.getValue().role.ordinal() - x.getValue().role.ordinal())
                .forEach(e -> {
                    MemberRow m = new MemberRow();
                    m.name = e.getValue().name;
                    m.role = e.getValue().role.title;
                    m.online = server.getPlayerList().getPlayer(e.getKey()) != null;
                    m.warWins = e.getValue().warWins;
                    m.warLosses = e.getValue().warLosses;
                    v.members.add(m);
                });
        long now = System.currentTimeMillis();
        long accept = config.warAcceptHours.get() * 3_600_000L;
        for (Clans.War w : clans.warsOf(clan.id)) {
            Clans.Clan other = clans.byId(w.other(clan.id));
            if (other == null) {
                continue;
            }
            WarRow r = new WarRow();
            r.otherTag = other.tag;
            r.otherName = other.name;
            r.otherColor = ClanText.rgb(other.color);
            r.otherCrest = crest(other.crest);
            r.mine = w.score(clan.id);
            r.theirs = w.score(other.id);
            r.running = w.running();
            r.incoming = !w.running() && w.defender.equals(clan.id);
            r.millisLeft = w.running() ? w.endsAt - now : w.declaredAt + accept - now;
            r.target = w.running() ? w.target : config.warTarget.get();
            r.stake = w.stake;
            int most = 0;
            for (Map.Entry<UUID, Integer> e : w.wins.entrySet()) {
                Clans.Member m = clan.members.get(e.getKey());
                if (m != null && e.getValue() > most) {
                    most = e.getValue();
                    r.best = m.name + " (" + most + ")";
                }
            }
            r.battle = w.battle();
            if (r.battle) {
                w.lineup(clan.id).forEach(id -> r.myLineup.add(nameIn(clan, id)));
                w.lineup(other.id).forEach(id -> r.theirLineup.add(nameIn(other, id)));
                r.myReady = w.ready(clan.id);
                r.theirReady = w.ready(other.id);
                r.inLineup = w.lineup(clan.id).contains(viewer);
                r.begun = w.bout >= 0;
                r.status = ClanBattles.status(clans, w);
            }
            v.wars.add(r);
        }
        for (Clans.Result res : clans.history()) {
            if (!res.involves(clan.id)) {
                continue;
            }
            boolean attacker = res.attacker().equals(clan.id);
            HistoryRow h = new HistoryRow();
            h.otherTag = attacker ? res.defenderTag() : res.attackerTag();
            h.otherName = attacker ? res.defenderName() : res.attackerName();
            h.otherColor = ClanText.rgb(attacker ? res.defenderColor() : res.attackerColor());
            h.mine = attacker ? res.attackerScore() : res.defenderScore();
            h.theirs = attacker ? res.defenderScore() : res.attackerScore();
            h.result = res.winner() < 0 ? 0 : (res.winner() == 0) == attacker ? 1 : -1;
            h.how = res.how();
            h.millisAgo = now - res.endedAt();
            h.ratingChange = attacker ? res.ratingChange() : -res.ratingChange();
            h.mvp = res.mvp();
            v.history.add(h);
        }
        return v;
    }

    private static String nameIn(Clans.Clan clan, UUID id) {
        Clans.Member m = clan.members.get(id);
        return m == null ? "?" : m.name;
    }

    private static ClanRow row(Clans clans, Clans.Clan c) {
        ClanRow r = new ClanRow();
        r.name = c.name;
        r.tag = c.tag;
        r.color = ClanText.rgb(c.color);
        r.rating = c.rating;
        r.members = c.members.size();
        r.won = c.warsWon;
        r.lost = c.warsLost;
        r.drawn = c.warsDrawn;
        r.open = c.open;
        r.atWar = clans.warsOf(c.id).stream().anyMatch(Clans.War::running);
        r.crest = crest(c.crest);
        return r;
    }

    private static CrestView crest(Clans.Crest crest) {
        CrestView v = new CrestView();
        v.base = crest.base();
        for (Clans.Crest.Layer layer : crest.layers()) {
            v.patterns.add(layer.pattern());
            v.colors.add(layer.color());
        }
        return v;
    }
}
