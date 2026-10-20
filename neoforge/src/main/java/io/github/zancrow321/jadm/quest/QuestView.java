package io.github.zancrow321.jadm.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the quest window shows, sent to the client as JSON. */
public final class QuestView {
    public boolean enabled;
    /** Everyone has the same quests ({@code sameForEveryone}), so there is no rerolling. */
    public boolean shared;
    public int rerollsLeft;
    /** Milliseconds until new daily and weekly quests come. */
    public long dailyResetMs;
    public long weeklyResetMs;
    public List<Entry> daily = new ArrayList<>();
    public List<Entry> weekly = new ArrayList<>();
    /** Quests that just moved on, for the client to pop up. */
    public List<Entry> toasts = new ArrayList<>();

    public static final class Entry {
        public String id;
        public boolean weekly;
        public String type;
        public int goal;
        public int progress;
        public boolean claimed;
        /** The quest's own title per language ("" for any), empty to let the window write one. */
        public Map<String, String> title = new LinkedHashMap<>();
        /** The filters the window spells out, see {@link QuestDef#filters()}. */
        public Map<String, String> filters = new LinkedHashMap<>();
        public Reward reward = new Reward();

        public boolean done() {
            return progress >= goal;
        }
    }

    public static final class Reward {
        /** 0 when points aren't the currency */
        public long points;
        public String symbol = "DP";
        public int packs;
        /** The name of the set the packs are of, or {@code null} for random packs. */
        public String packName;
        public int emeralds;
        public int xp;
        public List<Item> items = new ArrayList<>();
    }

    public static final class Item {
        public String id;
        public int count;
    }
}
