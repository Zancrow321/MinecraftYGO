package io.github.zancrow321.jadm.clan;

import io.github.zancrow321.jadm.JadmServerConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every clan on the server: its members and their roles, its crest, treasury and rating, and the clan wars that are
 * declared, running or over. Saved with the world.
 */
public final class Clans extends SavedData {
    private static final String NAME = "jadm_clans";
    /** How many finished wars are kept for the clan window. */
    private static final int HISTORY = 60;

    public enum Role {
        MEMBER("Member"), OFFICER("Officer"), LEADER("Leader");

        public final String title;

        Role(String title) {
            this.title = title;
        }

        /** Officers and the leader invite, kick members and answer war declarations. */
        public boolean manages() {
            return this != MEMBER;
        }
    }

    public static final class Member {
        public String name;
        public Role role;
        /** When they joined, in milliseconds since 1970. */
        public long joined;
        /** Duels they won and lost in clan wars for this clan. */
        public int warWins;
        public int warLosses;

        Member(String name, Role role, long joined) {
            this.name = name;
            this.role = role;
            this.joined = joined;
        }
    }

    /**
     * A clan's crest: a banner's base color and pattern layers, kept as ids so they survive registry changes.
     *
     * @param base the banner's color (a dye color id), or -1 while the clan has no crest
     */
    public record Crest(int base, List<Layer> layers) {
        public static final Crest NONE = new Crest(-1, List.of());

        public record Layer(String pattern, int color) {
        }

        public boolean isSet() {
            return base >= 0;
        }
    }

    public static final class Clan {
        public final UUID id;
        public String name;
        public String tag;
        /** A chat color name, e.g. "gold". */
        public String color = "gold";
        public String motto = "";
        /** Anyone can join without an invitation. */
        public boolean open;
        public Crest crest = Crest.NONE;
        public long treasury;
        public int rating;
        public int peak;
        public int warsWon;
        public int warsLost;
        public int warsDrawn;
        public long created;
        /** The FTB Teams party that mirrors this clan, or {@code null}; see {@link ClanParties}. */
        public UUID party;
        /** In order of joining; the leader is one of them. */
        public final Map<UUID, Member> members = new LinkedHashMap<>();

        Clan(UUID id) {
            this.id = id;
        }

        public int wars() {
            return warsWon + warsLost + warsDrawn;
        }

        public UUID leader() {
            return members.entrySet().stream().filter(e -> e.getValue().role == Role.LEADER).map(Map.Entry::getKey)
                    .findFirst().orElse(null);
        }

        public Role role(UUID player) {
            Member m = members.get(player);
            return m == null ? null : m.role;
        }
    }

    /**
     * A clan war: declared by the attacker, running once the defender accepted.
     *
     * @see ClanWars
     */
    public static final class War {
        /** A race for points: every duel won against the other clan counts. */
        public static final String RACE = "race";
        /** An arena battle: fixed lineups fight one bout after another on a tournament arena. */
        public static final String BATTLE = "battle";

        public final UUID id;
        public final UUID attacker;
        public final UUID defender;
        /** Duel Points each side put up from its treasury (the attacker's is held from the declaration on). */
        public final long stake;
        public final long declaredAt;
        /** 0 while it waits to be accepted. */
        public long startedAt;
        public long endsAt;
        /** Points that win it early, 0 for none; fixed when it starts. */
        public int target;
        public int attackerScore;
        public int defenderScore;
        /** Counted duels per pair of duelists, "smaller uuid|larger uuid". */
        public final Map<String, Integer> pairs = new HashMap<>();
        /** Duels each duelist won in this war. */
        public final Map<UUID, Integer> wins = new HashMap<>();
        /** {@link #RACE} or {@link #BATTLE}. */
        public String format = RACE;
        /** An arena battle's duelists, in the order they fight. */
        public final List<UUID> attackerLineup = new ArrayList<>();
        public final List<UUID> defenderLineup = new ArrayList<>();
        public boolean attackerReady;
        public boolean defenderReady;
        /** The arena battle's bout being fought (from 0), or -1 before it begins. */
        public int bout = -1;

        War(UUID id, UUID attacker, UUID defender, long stake, long declaredAt) {
            this.id = id;
            this.attacker = attacker;
            this.defender = defender;
            this.stake = stake;
            this.declaredAt = declaredAt;
        }

        public boolean running() {
            return startedAt > 0;
        }

        public boolean involves(UUID clan) {
            return attacker.equals(clan) || defender.equals(clan);
        }

        public UUID other(UUID clan) {
            return attacker.equals(clan) ? defender : attacker;
        }

        public int score(UUID clan) {
            return attacker.equals(clan) ? attackerScore : defenderScore;
        }

        public boolean battle() {
            return BATTLE.equals(format);
        }

        public List<UUID> lineup(UUID clan) {
            return attacker.equals(clan) ? attackerLineup : defenderLineup;
        }

        public boolean ready(UUID clan) {
            return attacker.equals(clan) ? attackerReady : defenderReady;
        }

        public void setReady(UUID clan, boolean ready) {
            if (attacker.equals(clan)) {
                attackerReady = ready;
            } else {
                defenderReady = ready;
            }
        }
    }

    /**
     * A finished war, as the clan window lists it. Names, tags and colors are kept as they were, so the history
     * still reads right after a clan is renamed or disbanded.
     *
     * @param winner 0 the attacker, 1 the defender, -1 a draw
     * @param how    "target", "time", "surrender" or "disband"
     * @param ratingChange the attacker's rating change (the defender's is about the opposite)
     */
    public record Result(UUID attacker, String attackerTag, String attackerName, String attackerColor,
                         UUID defender, String defenderTag, String defenderName, String defenderColor,
                         int attackerScore, int defenderScore, int winner, String how, long endedAt, long stake,
                         int ratingChange, String mvp) {
        public boolean involves(UUID clan) {
            return attacker.equals(clan) || defender.equals(clan);
        }
    }

    private final Map<UUID, Clan> clans = new LinkedHashMap<>();
    private final List<War> wars = new ArrayList<>();
    private final List<Result> history = new ArrayList<>();
    /** When each pair of clans last finished a war, "smaller id|larger id" to milliseconds. */
    private final Map<String, Long> lastWar = new HashMap<>();
    private int season = 1;
    /** player to their clan's id; rebuilt from the members. Not saved. */
    private final Map<UUID, UUID> clanOf = new HashMap<>();
    /** invitee to the clans that invited them, with when each invitation runs out (server ticks). Not saved. */
    final Map<UUID, Map<UUID, Long>> invites = new HashMap<>();
    /** Goes up with every change, so open windows and boards know to refresh. Not saved. */
    private int version;

    public static Clans get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Clans::new, Clans::load, null), NAME);
    }

    public int version() {
        return version;
    }

    public int season() {
        return season;
    }

    public java.util.Collection<Clan> all() {
        return clans.values();
    }

    public Clan byId(UUID id) {
        return id == null ? null : clans.get(id);
    }

    /** The clan {@code player} belongs to, or {@code null}. */
    public Clan of(UUID player) {
        return byId(clanOf.get(player));
    }

    /** A clan by its tag or its name, ignoring case. */
    public Clan find(String tagOrName) {
        for (Clan c : clans.values()) {
            if (c.tag.equalsIgnoreCase(tagOrName)) {
                return c;
            }
        }
        for (Clan c : clans.values()) {
            if (c.name.equalsIgnoreCase(tagOrName)) {
                return c;
            }
        }
        return null;
    }

    public Clan create(UUID leader, String leaderName, String tag, String name) {
        Clan clan = new Clan(UUID.randomUUID());
        clan.tag = tag;
        clan.name = name;
        clan.created = System.currentTimeMillis();
        clan.rating = JadmServerConfig.CLANS.startRating.get();
        clan.peak = clan.rating;
        clan.members.put(leader, new Member(leaderName, Role.LEADER, clan.created));
        clans.put(clan.id, clan);
        clanOf.put(leader, clan.id);
        invites.remove(leader);
        changed();
        return clan;
    }

    public void join(Clan clan, UUID player, String name) {
        clan.members.put(player, new Member(name, Role.MEMBER, System.currentTimeMillis()));
        clanOf.put(player, clan.id);
        invites.remove(player);
        changed();
    }

    public void leave(Clan clan, UUID player) {
        clan.members.remove(player);
        clanOf.remove(player);
        changed();
    }

    /** Removes a clan; its wars must have been settled first. */
    public void disband(Clan clan) {
        clans.remove(clan.id);
        clan.members.keySet().forEach(clanOf::remove);
        invites.values().forEach(m -> m.remove(clan.id));
        changed();
    }

    /** Clans by rating, then wars won, then members, then name. */
    public List<Clan> standings() {
        List<Clan> out = new ArrayList<>(clans.values());
        out.sort(Comparator.<Clan>comparingInt(c -> -c.rating).thenComparingInt(c -> -c.warsWon)
                .thenComparingInt(c -> -c.members.size()).thenComparing(c -> c.name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** A clan's place in the clan ranking, from 1. */
    public int place(Clan clan) {
        return standings().indexOf(clan) + 1;
    }

    public List<War> wars() {
        return wars;
    }

    /** The wars {@code clan} is in or has declared or been offered. */
    public List<War> warsOf(UUID clan) {
        return wars.stream().filter(w -> w.involves(clan)).toList();
    }

    /** The running war between two clans, or {@code null}. */
    public War runningBetween(UUID a, UUID b) {
        for (War w : wars) {
            if (w.running() && w.involves(a) && w.involves(b)) {
                return w;
            }
        }
        return null;
    }

    public War declare(UUID attacker, UUID defender, long stake, String format) {
        War war = new War(UUID.randomUUID(), attacker, defender, stake, System.currentTimeMillis());
        war.format = format;
        wars.add(war);
        changed();
        return war;
    }

    public void removeWar(War war) {
        wars.remove(war);
        changed();
    }

    public List<Result> history() {
        return history;
    }

    void finished(War war, Result result) {
        wars.remove(war);
        history.add(0, result);
        while (history.size() > HISTORY) {
            history.remove(history.size() - 1);
        }
        lastWar.put(pairKey(war.attacker, war.defender), result.endedAt());
        changed();
    }

    /** When the two clans' last war ended, or 0. */
    public long lastWar(UUID a, UUID b) {
        return lastWar.getOrDefault(pairKey(a, b), 0L);
    }

    /** Starts a new clan season: every clan goes back to the start rating and a clean war record. */
    public int newSeason() {
        int start = JadmServerConfig.CLANS.startRating.get();
        for (Clan c : clans.values()) {
            c.rating = start;
            c.peak = start;
            c.warsWon = 0;
            c.warsLost = 0;
            c.warsDrawn = 0;
            c.members.values().forEach(m -> {
                m.warWins = 0;
                m.warLosses = 0;
            });
        }
        history.clear();
        lastWar.clear();
        season++;
        changed();
        return season;
    }

    static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    public void changed() {
        version++;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Clan c : clans.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", c.id);
            t.putString("name", c.name);
            t.putString("tag", c.tag);
            t.putString("color", c.color);
            t.putString("motto", c.motto);
            t.putBoolean("open", c.open);
            t.putLong("treasury", c.treasury);
            t.putInt("rating", c.rating);
            t.putInt("peak", c.peak);
            t.putInt("warsWon", c.warsWon);
            t.putInt("warsLost", c.warsLost);
            t.putInt("warsDrawn", c.warsDrawn);
            t.putLong("created", c.created);
            if (c.party != null) {
                t.putUUID("party", c.party);
            }
            CompoundTag crest = new CompoundTag();
            crest.putInt("base", c.crest.base());
            ListTag layers = new ListTag();
            for (Crest.Layer layer : c.crest.layers()) {
                CompoundTag l = new CompoundTag();
                l.putString("pattern", layer.pattern());
                l.putInt("color", layer.color());
                layers.add(l);
            }
            crest.put("layers", layers);
            t.put("crest", crest);
            ListTag members = new ListTag();
            c.members.forEach((id, m) -> {
                CompoundTag mt = new CompoundTag();
                mt.putUUID("id", id);
                mt.putString("name", m.name);
                mt.putString("role", m.role.name());
                mt.putLong("joined", m.joined);
                mt.putInt("warWins", m.warWins);
                mt.putInt("warLosses", m.warLosses);
                members.add(mt);
            });
            t.put("members", members);
            list.add(t);
        }
        tag.put("clans", list);
        ListTag warList = new ListTag();
        for (War w : wars) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", w.id);
            t.putUUID("attacker", w.attacker);
            t.putUUID("defender", w.defender);
            t.putLong("stake", w.stake);
            t.putLong("declaredAt", w.declaredAt);
            t.putLong("startedAt", w.startedAt);
            t.putLong("endsAt", w.endsAt);
            t.putInt("target", w.target);
            t.putInt("attackerScore", w.attackerScore);
            t.putInt("defenderScore", w.defenderScore);
            CompoundTag pairs = new CompoundTag();
            w.pairs.forEach(pairs::putInt);
            t.put("pairs", pairs);
            CompoundTag wins = new CompoundTag();
            w.wins.forEach((id, n) -> wins.putInt(id.toString(), n));
            t.put("wins", wins);
            t.putString("format", w.format);
            t.put("attackerLineup", uuids(w.attackerLineup));
            t.put("defenderLineup", uuids(w.defenderLineup));
            t.putBoolean("attackerReady", w.attackerReady);
            t.putBoolean("defenderReady", w.defenderReady);
            t.putInt("bout", w.bout);
            warList.add(t);
        }
        tag.put("wars", warList);
        ListTag results = new ListTag();
        for (Result r : history) {
            CompoundTag t = new CompoundTag();
            t.putUUID("attacker", r.attacker());
            t.putString("attackerTag", r.attackerTag());
            t.putString("attackerName", r.attackerName());
            t.putString("attackerColor", r.attackerColor());
            t.putUUID("defender", r.defender());
            t.putString("defenderTag", r.defenderTag());
            t.putString("defenderName", r.defenderName());
            t.putString("defenderColor", r.defenderColor());
            t.putInt("attackerScore", r.attackerScore());
            t.putInt("defenderScore", r.defenderScore());
            t.putInt("winner", r.winner());
            t.putString("how", r.how());
            t.putLong("endedAt", r.endedAt());
            t.putLong("stake", r.stake());
            t.putInt("ratingChange", r.ratingChange());
            t.putString("mvp", r.mvp());
            results.add(t);
        }
        tag.put("history", results);
        CompoundTag last = new CompoundTag();
        lastWar.forEach(last::putLong);
        tag.put("lastWar", last);
        tag.putInt("season", season);
        return tag;
    }

    private static ListTag uuids(List<UUID> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
        return list;
    }

    private static List<UUID> uuids(ListTag list) {
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            try {
                out.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // A broken entry is dropped.
            }
        }
        return out;
    }

    private static Clans load(CompoundTag tag, HolderLookup.Provider registries) {
        Clans out = new Clans();
        for (Tag raw : tag.getList("clans", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Clan c = new Clan(t.getUUID("id"));
            c.name = t.getString("name");
            c.tag = t.getString("tag");
            c.color = t.getString("color");
            c.motto = t.getString("motto");
            c.open = t.getBoolean("open");
            c.treasury = t.getLong("treasury");
            c.rating = t.getInt("rating");
            c.peak = t.getInt("peak");
            c.warsWon = t.getInt("warsWon");
            c.warsLost = t.getInt("warsLost");
            c.warsDrawn = t.getInt("warsDrawn");
            c.created = t.getLong("created");
            c.party = t.hasUUID("party") ? t.getUUID("party") : null;
            CompoundTag crest = t.getCompound("crest");
            List<Crest.Layer> layers = new ArrayList<>();
            for (Tag l : crest.getList("layers", Tag.TAG_COMPOUND)) {
                CompoundTag lt = (CompoundTag) l;
                layers.add(new Crest.Layer(lt.getString("pattern"), lt.getInt("color")));
            }
            c.crest = crest.contains("base") ? new Crest(crest.getInt("base"), List.copyOf(layers)) : Crest.NONE;
            for (Tag m : t.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag mt = (CompoundTag) m;
                Role role;
                try {
                    role = Role.valueOf(mt.getString("role"));
                } catch (IllegalArgumentException e) {
                    role = Role.MEMBER;
                }
                Member member = new Member(mt.getString("name"), role, mt.getLong("joined"));
                member.warWins = mt.getInt("warWins");
                member.warLosses = mt.getInt("warLosses");
                UUID id = mt.getUUID("id");
                c.members.put(id, member);
                out.clanOf.put(id, c.id);
            }
            out.clans.put(c.id, c);
        }
        for (Tag raw : tag.getList("wars", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            War w = new War(t.getUUID("id"), t.getUUID("attacker"), t.getUUID("defender"), t.getLong("stake"),
                    t.getLong("declaredAt"));
            w.startedAt = t.getLong("startedAt");
            w.endsAt = t.getLong("endsAt");
            w.target = t.getInt("target");
            w.attackerScore = t.getInt("attackerScore");
            w.defenderScore = t.getInt("defenderScore");
            CompoundTag pairs = t.getCompound("pairs");
            pairs.getAllKeys().forEach(k -> w.pairs.put(k, pairs.getInt(k)));
            CompoundTag wins = t.getCompound("wins");
            wins.getAllKeys().forEach(k -> w.wins.put(UUID.fromString(k), wins.getInt(k)));
            w.format = War.BATTLE.equals(t.getString("format")) ? War.BATTLE : War.RACE;
            w.attackerLineup.addAll(uuids(t.getList("attackerLineup", Tag.TAG_STRING)));
            w.defenderLineup.addAll(uuids(t.getList("defenderLineup", Tag.TAG_STRING)));
            w.attackerReady = t.getBoolean("attackerReady");
            w.defenderReady = t.getBoolean("defenderReady");
            w.bout = t.contains("bout") ? t.getInt("bout") : -1;
            out.wars.add(w);
        }
        for (Tag raw : tag.getList("history", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            out.history.add(new Result(t.getUUID("attacker"), t.getString("attackerTag"), t.getString("attackerName"),
                    t.getString("attackerColor"), t.getUUID("defender"), t.getString("defenderTag"),
                    t.getString("defenderName"), t.getString("defenderColor"), t.getInt("attackerScore"),
                    t.getInt("defenderScore"), t.getInt("winner"), t.getString("how"), t.getLong("endedAt"),
                    t.getLong("stake"), t.getInt("ratingChange"), t.getString("mvp")));
        }
        CompoundTag last = tag.getCompound("lastWar");
        last.getAllKeys().forEach(k -> out.lastWar.put(k, last.getLong(k)));
        out.season = Math.max(1, tag.getInt("season"));
        return out;
    }
}
