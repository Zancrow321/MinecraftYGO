package io.github.zancrow321.jadm.quest;

import com.google.gson.Gson;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.engine.data.PoolMode;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.item.BoosterPackItem;
import io.github.zancrow321.jadm.network.QuestActionPayload;
import io.github.zancrow321.jadm.network.QuestPayload;
import io.github.zancrow321.jadm.points.Points;
import io.github.zancrow321.jadm.progression.Progress;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Daily and weekly quests: each player draws theirs from the pool in {@code config/jadm/quests.json} when a new day
 * or week begins (on the server's clock, at {@code [quests] resetHour}), things they do count towards them, and they
 * claim the rewards in the quest window. Rewards left unclaimed are handed out when the quests are replaced. Only
 * online players count, so everything runs on the server thread.
 */
public final class Quests {
    private static final Gson GSON = new Gson();

    private Quests() {
    }

    // ---- days and weeks

    /** Today, as quests count days: the date on the server's clock {@code resetHour} hours ago, as an epoch day. */
    static long dayKey() {
        return LocalDateTime.now().minusHours(JadmServerConfig.QUESTS.resetHour.get()).toLocalDate().toEpochDay();
    }

    /** The epoch day the current quest week began on. */
    static long weekKey() {
        LocalDate today = LocalDate.ofEpochDay(dayKey());
        DayOfWeek reset = JadmServerConfig.Quests.day(JadmServerConfig.QUESTS.weeklyResetDay.get());
        int back = (today.getDayOfWeek().getValue() - (reset == null ? 1 : reset.getValue()) + 7) % 7;
        return today.minusDays(back).toEpochDay();
    }

    private static long millisUntil(long epochDay) {
        LocalDateTime at = LocalDate.ofEpochDay(epochDay).atTime(JadmServerConfig.QUESTS.resetHour.get(), 0);
        return Math.max(0, Duration.between(LocalDateTime.now(), at).toMillis());
    }

    // ---- drawing quests

    /**
     * The player's quests, drawn anew if a day or week has passed (handing out rewards they didn't claim) and topped
     * up if the pool or the settings changed.
     */
    static QuestData.PlayerQuests ensure(ServerPlayer player) {
        QuestData data = QuestData.get(player.server);
        QuestData.PlayerQuests quests = data.player(player.getUUID());
        long day = dayKey();
        long week = weekKey();
        List<String> leftovers = new ArrayList<>();
        boolean changed = false;
        if (quests.dailyKey != day) {
            leftovers.addAll(handOutLeftovers(player, quests.daily));
            quests.daily = new ArrayList<>();
            quests.dailyKey = day;
            changed = true;
        }
        if (quests.weeklyKey != week) {
            leftovers.addAll(handOutLeftovers(player, quests.weekly));
            quests.weekly = new ArrayList<>();
            quests.weeklyKey = week;
            changed = true;
        }
        if (JadmServerConfig.QUESTS.sameForEveryone.get()) {
            QuestData.Shared shared = data.shared();
            if (shared.dailyKey != day) {
                shared.daily = draw(player, false, List.of(), JadmServerConfig.QUESTS.daily.get());
                shared.dailyKey = day;
                changed = true;
            }
            if (shared.weeklyKey != week) {
                shared.weekly = draw(player, true, List.of(), JadmServerConfig.QUESTS.weekly.get());
                shared.weeklyKey = week;
                changed = true;
            }
            changed |= follow(quests.daily, shared.daily);
            changed |= follow(quests.weekly, shared.weekly);
        } else {
            changed |= topUp(player, quests, false, JadmServerConfig.QUESTS.daily.get());
            changed |= topUp(player, quests, true, JadmServerConfig.QUESTS.weekly.get());
        }
        if (changed) {
            data.setDirty();
        }
        if (!leftovers.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.jadm.quests.leftovers",
                    String.join(", ", leftovers)).withStyle(ChatFormatting.GOLD));
        }
        return quests;
    }

    /** Gives out the rewards of finished quests nobody claimed. @return what was given */
    private static List<String> handOutLeftovers(ServerPlayer player, List<QuestData.Active> list) {
        List<String> given = new ArrayList<>();
        for (QuestData.Active active : list) {
            QuestDef def = QuestPool.quest(active.id);
            if (def != null && !active.claimed && active.progress >= def.goal) {
                active.claimed = true;
                given.addAll(give(player, def));
            }
        }
        return given;
    }

    /** Drops quests no longer in the pool and draws more until the player has {@code count}. */
    private static boolean topUp(ServerPlayer player, QuestData.PlayerQuests quests, boolean weekly, int count) {
        List<QuestData.Active> list = quests.list(weekly);
        boolean changed = list.removeIf(a -> QuestPool.quest(a.id) == null);
        if (list.size() < count) {
            List<String> held = list.stream().map(a -> a.id).toList();
            for (String id : draw(player, weekly, held, count - list.size())) {
                list.add(new QuestData.Active(id));
                changed = true;
            }
        }
        return changed;
    }

    /** Makes a player's list the shared quests, keeping progress on the ones they already had. */
    private static boolean follow(List<QuestData.Active> list, List<String> ids) {
        List<String> have = list.stream().map(a -> a.id).toList();
        if (have.equals(ids)) {
            return false;
        }
        List<QuestData.Active> next = new ArrayList<>();
        for (String id : ids) {
            next.add(list.stream().filter(a -> a.id.equals(id)).findFirst().orElseGet(() -> new QuestData.Active(id)));
        }
        list.clear();
        list.addAll(next);
        return true;
    }

    /** Up to {@code count} quests of a period, drawn by weight, none of them in {@code exclude}. */
    private static List<String> draw(ServerPlayer player, boolean weekly, List<String> exclude, int count) {
        List<QuestDef> candidates = new ArrayList<>();
        for (QuestDef def : QuestPool.get().values()) {
            if (def.weekly() == weekly && def.weight > 0 && !exclude.contains(def.id) && available(player, def)) {
                candidates.add(def);
            }
        }
        List<String> picked = new ArrayList<>();
        var random = player.getRandom();
        while (picked.size() < count && !candidates.isEmpty()) {
            int total = candidates.stream().mapToInt(d -> d.weight).sum();
            int roll = random.nextInt(total);
            for (int i = 0; i < candidates.size(); i++) {
                roll -= candidates.get(i).weight;
                if (roll < 0) {
                    picked.add(candidates.remove(i).id);
                    break;
                }
            }
        }
        return picked;
    }

    /** Whether a quest can be done on this server now: its cards are out, and ranked duels are on if it needs them. */
    private static boolean available(ServerPlayer player, QuestDef def) {
        if (Boolean.TRUE.equals(def.ranked) && !JadmServerConfig.RANKING.enabled.get()) {
            return false;
        }
        if (def.type.equals("trade") && !JadmServerConfig.TRADE.enabled.get()) {
            return false;
        }
        if (def.unlockedBy != null && JadmData.poolMode() == PoolMode.PROGRESSION) {
            int step = JadmData.progression().find(def.unlockedBy);
            return step >= 0 && step <= Progress.step(player);
        }
        return true;
    }

    // ---- counting

    /**
     * Counts towards the player's quests: {@code amount} says how much a quest moves (0 if it doesn't apply).
     * Tells the client about every quest that moved, and the player about every one finished.
     */
    private static void count(ServerPlayer player, ToIntFunction<QuestDef> amount) {
        if (!JadmServerConfig.QUESTS.enabled.get()) {
            return;
        }
        QuestData.PlayerQuests quests = ensure(player);
        List<QuestView.Entry> moved = new ArrayList<>();
        for (boolean weekly : new boolean[]{false, true}) {
            for (QuestData.Active active : quests.list(weekly)) {
                QuestDef def = QuestPool.quest(active.id);
                if (def == null || active.claimed || active.progress >= def.goal) {
                    continue;
                }
                int n = amount.applyAsInt(def);
                if (n <= 0) {
                    continue;
                }
                active.progress = (int) Math.min(def.goal, (long) active.progress + n);
                moved.add(entry(def, active));
                if (active.progress >= def.goal) {
                    finished(player, def);
                }
            }
        }
        if (!moved.isEmpty()) {
            QuestData.get(player.server).setDirty();
            send(player, false, moved);
        }
    }

    private static void finished(ServerPlayer player, QuestDef def) {
        String title = title(def, player.clientInformation().language());
        Component name = Component.literal(title == null ? "" : title);
        player.sendSystemMessage(Component.translatable((def.weekly() ? "message.jadm.quests.done_weekly"
                        : "message.jadm.quests.done") + (title == null ? ".untitled" : ""), name)
                .withStyle(ChatFormatting.GREEN).append(" ").append(button()));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        if (def.weekly() && JadmServerConfig.QUESTS.announceWeekly.get()) {
            Component text = Component.translatable("message.jadm.quests.announce", player.getDisplayName(),
                    Component.literal(title == null ? def.id : title))
                    .withStyle(ChatFormatting.YELLOW);
            for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
                if (other != player) {
                    other.sendSystemMessage(text);
                }
            }
        }
    }

    /** A chat button that opens the quest window. */
    private static Component button() {
        return Component.translatable("message.jadm.quests.button").withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + Jadm.COMMAND + " quests"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("message.jadm.quests.button.hover"))));
    }

    /** The quest's own title in a language (e.g. "de_de"), else in English, or {@code null} if it has none. */
    static String title(QuestDef def, String language) {
        Map<String, String> titles = def.titles();
        for (String key : new String[]{language == null ? "" : language.toLowerCase(Locale.ROOT), "en_us", ""}) {
            if (titles.containsKey(key)) {
                return titles.get(key);
            }
        }
        return titles.isEmpty() ? null : titles.values().iterator().next();
    }

    /** A duel ended: counts it for one of its people. */
    public static void duelEnded(ServerPlayer player, DuelFacts facts) {
        if (facts.turns() < JadmServerConfig.QUESTS.minTurns.get()
                || facts.opponent().equals("bots") && !JadmServerConfig.QUESTS.botDuels.get()) {
            return;
        }
        count(player, def -> def.duel() && matches(def, facts) ? duelAmount(def, facts) : 0);
    }

    public static void packOpened(ServerPlayer player) {
        count(player, def -> def.type.equals("open_packs") ? 1 : 0);
    }

    public static void traded(ServerPlayer player) {
        count(player, def -> def.type.equals("trade") ? 1 : 0);
    }

    /** A tournament the player was in is over; {@code place} 1 is the winner. */
    public static void tournamentFinished(ServerPlayer player, int place) {
        count(player, def -> def.type.equals("tournament") && (def.maxPlace <= 0 || place <= def.maxPlace) ? 1 : 0);
    }

    private static boolean matches(QuestDef def, DuelFacts facts) {
        boolean against = switch (def.against) {
            case "players" -> facts.opponent().equals("players");
            case "npcs" -> facts.opponent().equals("npcs");
            case "bots" -> facts.opponent().equals("bots");
            case "computer" -> !facts.opponent().equals("players");
            default -> true;
        };
        return against
                && (!Boolean.TRUE.equals(def.ranked) || facts.ranked())
                && (def.tournament == null || def.tournament == facts.tournament())
                && (!Boolean.TRUE.equals(def.won) || facts.won())
                && (def.minLifePoints <= 0 || facts.lifePoints() >= def.minLifePoints)
                && (def.maxTurns <= 0 || facts.turns() <= def.maxTurns)
                && (!def.hasDeckFilter() || deckMatches(def, facts));
    }

    private static boolean deckMatches(QuestDef def, DuelFacts facts) {
        if (facts.deck() == null) {
            return false;
        }
        String name = def.name == null ? null : def.name.toLowerCase(Locale.ROOT);
        int matching = 0;
        List<Integer> codes = new ArrayList<>(facts.deck().main());
        codes.addAll(facts.deck().extra());
        for (int code : codes) {
            CardInfo card = JadmData.cards().card(code);
            if (card == null) {
                continue;
            }
            boolean monster = card.is(OcgConstants.TYPE_MONSTER);
            if ((def.raceBits == 0 || monster && (card.data().race() & def.raceBits) != 0)
                    && (def.attributeBits == 0 || monster && (card.data().attribute() & def.attributeBits) != 0)
                    && (name == null || card.name().toLowerCase(Locale.ROOT).contains(name))
                    && (def.card == null || card.code() == def.card || card.data().alias() == def.card)) {
                matching++;
            }
        }
        return matching >= def.deckCards();
    }

    private static int duelAmount(QuestDef def, DuelFacts facts) {
        var tally = facts.tally();
        return switch (def.type) {
            case "duel_win" -> facts.won() ? 1 : 0;
            case "duel_play" -> 1;
            case "damage" -> tally.damageDealt();
            case "summon" -> switch (def.summon) {
                case "normal" -> tally.normalSummons().size();
                case "flip" -> tally.flipSummons().size();
                case "special" -> tally.specialSummons().size();
                case "fusion" -> typed(tally.specialSummons(), OcgConstants.TYPE_FUSION);
                case "ritual" -> typed(tally.specialSummons(), OcgConstants.TYPE_RITUAL);
                case "synchro" -> typed(tally.specialSummons(), OcgConstants.TYPE_SYNCHRO);
                case "xyz" -> typed(tally.specialSummons(), OcgConstants.TYPE_XYZ);
                case "pendulum" -> typed(tally.specialSummons(), OcgConstants.TYPE_PENDULUM);
                case "link" -> typed(tally.specialSummons(), OcgConstants.TYPE_LINK);
                default -> tally.normalSummons().size() + tally.flipSummons().size()
                        + tally.specialSummons().size();
            };
            case "activate" -> switch (def.cardType) {
                case "monster" -> typed(tally.activations(), OcgConstants.TYPE_MONSTER);
                case "spell" -> typed(tally.activations(), OcgConstants.TYPE_SPELL);
                case "trap" -> typed(tally.activations(), OcgConstants.TYPE_TRAP);
                default -> tally.activations().size();
            };
            default -> 0;
        };
    }

    private static int typed(List<Integer> codes, int type) {
        int n = 0;
        for (int code : codes) {
            CardInfo card = JadmData.cards().card(code);
            if (card != null && card.is(type)) {
                n++;
            }
        }
        return n;
    }

    // ---- the window

    /** Opens the quest window for a player, or with {@code open} off refreshes it if it is open. */
    public static int send(ServerPlayer player, boolean open) {
        send(player, open, List.of());
        return 1;
    }

    private static void send(ServerPlayer player, boolean open, List<QuestView.Entry> toasts) {
        QuestView view = new QuestView();
        view.enabled = JadmServerConfig.QUESTS.enabled.get();
        view.shared = JadmServerConfig.QUESTS.sameForEveryone.get();
        if (view.enabled) {
            QuestData.PlayerQuests quests = ensure(player);
            view.rerollsLeft = view.shared ? 0 : rerollsLeft(quests);
            for (boolean weekly : new boolean[]{false, true}) {
                for (QuestData.Active active : quests.list(weekly)) {
                    QuestDef def = QuestPool.quest(active.id);
                    if (def != null) {
                        (weekly ? view.weekly : view.daily).add(entry(def, active));
                    }
                }
            }
        }
        view.dailyResetMs = millisUntil(dayKey() + 1);
        view.weeklyResetMs = millisUntil(weekKey() + 7);
        view.toasts.addAll(toasts);
        PacketDistributor.sendToPlayer(player, new QuestPayload(open, GSON.toJson(view)));
    }

    private static int rerollsLeft(QuestData.PlayerQuests quests) {
        int used = quests.rerollDay == dayKey() ? quests.rerolls : 0;
        return Math.max(0, JadmServerConfig.QUESTS.rerollsPerDay.get() - used);
    }

    static QuestView.Entry entry(QuestDef def, QuestData.Active active) {
        QuestView.Entry e = new QuestView.Entry();
        e.id = def.id;
        e.weekly = def.weekly();
        e.type = def.type;
        e.goal = def.goal;
        e.progress = active.progress;
        e.claimed = active.claimed;
        e.title.putAll(def.titles());
        e.filters.putAll(def.filters());
        QuestDef.Reward r = def.reward;
        e.reward.points = Points.active() ? r.points : 0;
        e.reward.symbol = JadmServerConfig.POINTS.symbol.get();
        e.reward.packs = r.packs;
        Products.Product packs = packProduct(r.packSet);
        if (r.packs > 0 && packs != null) {
            e.reward.packName = packs.name();
        }
        e.reward.emeralds = r.emeralds;
        e.reward.xp = r.xp;
        for (String spec : r.items) {
            ItemStack stack = parseItem(spec);
            if (!stack.isEmpty()) {
                QuestView.Item item = new QuestView.Item();
                item.id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                item.count = stack.getCount();
                e.reward.items.add(item);
            }
        }
        return e;
    }

    // ---- clicks in the window

    public static void handle(ServerPlayer player, QuestActionPayload action) {
        if (JadmServerConfig.QUESTS.enabled.get()) {
            switch (action.action()) {
                case REFRESH -> {
                }
                case CLAIM -> claim(player, action.id());
                case CLAIM_ALL -> {
                    QuestData.PlayerQuests quests = ensure(player);
                    List<String> ids = new ArrayList<>();
                    quests.daily.forEach(a -> ids.add(a.id));
                    quests.weekly.forEach(a -> ids.add(a.id));
                    ids.forEach(id -> claim(player, id));
                }
                case REROLL -> reroll(player, action.id());
            }
        }
        send(player, false);
    }

    private static void claim(ServerPlayer player, String id) {
        QuestData.PlayerQuests quests = ensure(player);
        QuestData.Active active = find(quests, id);
        QuestDef def = QuestPool.quest(id);
        if (active == null || def == null || active.claimed || active.progress < def.goal) {
            return;
        }
        active.claimed = true;
        QuestData.get(player.server).setDirty();
        List<String> got = give(player, def);
        player.sendSystemMessage(Component.translatable("message.jadm.quests.claimed",
                got.isEmpty() ? "-" : String.join(", ", got)).withStyle(ChatFormatting.GOLD));
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /** Swaps an unfinished quest for another of its period that the player doesn't have. */
    private static void reroll(ServerPlayer player, String id) {
        if (JadmServerConfig.QUESTS.sameForEveryone.get()) {
            return;
        }
        QuestData.PlayerQuests quests = ensure(player);
        QuestData.Active active = find(quests, id);
        QuestDef def = QuestPool.quest(id);
        if (active == null || def == null || active.claimed || active.progress >= def.goal
                || rerollsLeft(quests) <= 0) {
            return;
        }
        List<QuestData.Active> list = quests.list(def.weekly());
        List<String> held = new ArrayList<>(list.stream().map(a -> a.id).toList());
        List<String> next = draw(player, def.weekly(), held, 1);
        if (next.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.jadm.quests.no_other")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        list.set(list.indexOf(active), new QuestData.Active(next.get(0)));
        long today = dayKey();
        if (quests.rerollDay != today) {
            quests.rerollDay = today;
            quests.rerolls = 0;
        }
        quests.rerolls++;
        QuestData.get(player.server).setDirty();
    }

    private static QuestData.Active find(QuestData.PlayerQuests quests, String id) {
        for (boolean weekly : new boolean[]{false, true}) {
            for (QuestData.Active active : quests.list(weekly)) {
                if (active.id.equals(id)) {
                    return active;
                }
            }
        }
        return null;
    }

    // ---- rewards

    /** @return one short line per thing given, e.g. "150 DP" */
    static List<String> give(ServerPlayer player, QuestDef def) {
        QuestDef.Reward reward = def.reward;
        List<String> lines = new ArrayList<>();
        if (reward.points > 0 && Points.active()) {
            Points.get(player.server).add(player.server, player.getUUID(), reward.points);
            lines.add(Points.format(reward.points));
        }
        Products.Product product = packProduct(reward.packSet);
        for (int i = 0; i < reward.packs; i++) {
            String set = product == null ? null : product.id();
            if (set == null) {
                BoosterSets.BoosterSet random = BoosterPackItem.randomSet(player.getRandom());
                if (random == null) {
                    break;
                }
                set = random.id();
            }
            ItemStack pack = BoosterPackItem.of(set);
            lines.add(pack.getHoverName().getString());
            hand(player, pack);
        }
        if (reward.emeralds > 0) {
            lines.add(reward.emeralds + (reward.emeralds == 1 ? " emerald" : " emeralds"));
            for (int left = reward.emeralds; left > 0; left -= 64) {
                hand(player, new ItemStack(Items.EMERALD, Math.min(64, left)));
            }
        }
        if (reward.xp > 0) {
            player.giveExperiencePoints(reward.xp);
            lines.add(reward.xp + " experience");
        }
        for (String spec : reward.items) {
            ItemStack stack = parseItem(spec);
            if (stack.isEmpty()) {
                continue;
            }
            lines.add(stack.getCount() + "x " + stack.getHoverName().getString());
            int count = stack.getCount();
            while (count > 0) {
                int n = Math.min(count, stack.getMaxStackSize());
                hand(player, stack.copyWithCount(n));
                count -= n;
            }
        }
        return lines;
    }

    /** The booster a {@code packSet} names, by product id or set code ("MRD"), or {@code null}. */
    private static Products.Product packProduct(String packSet) {
        if (packSet == null || packSet.isBlank()) {
            return null;
        }
        Products.Product byId = JadmData.products().get(packSet.strip().toLowerCase(Locale.ROOT));
        if (byId != null) {
            return byId;
        }
        for (Products.Product p : JadmData.products().products()) {
            if (p.code().equalsIgnoreCase(packSet.strip()) && JadmData.set(p.id()) != null) {
                return p;
            }
        }
        return null;
    }

    /** "minecraft:diamond 2" (the count may be left out); empty if the item doesn't exist. */
    static ItemStack parseItem(String spec) {
        if (spec == null || spec.isBlank()) {
            return ItemStack.EMPTY;
        }
        String[] parts = spec.strip().split("\\s+");
        ResourceLocation id = ResourceLocation.tryParse(parts[0]);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        int count = 1;
        if (parts.length > 1) {
            try {
                count = Math.max(1, Math.min(6400, Integer.parseInt(parts[1])));
            } catch (NumberFormatException ignored) {
                // Just one, then.
            }
        }
        return new ItemStack(item, count);
    }

    private static void hand(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ---- logging in, operators

    /** On login: draws new quests if a day has passed and says so, with a button for the window. */
    public static void onLogin(ServerPlayer player) {
        if (!JadmServerConfig.QUESTS.enabled.get()) {
            return;
        }
        QuestData.PlayerQuests quests = QuestData.get(player.server).player(player.getUUID());
        boolean fresh = quests.dailyKey != dayKey();
        quests = ensure(player);
        int claimable = 0;
        for (boolean weekly : new boolean[]{false, true}) {
            for (QuestData.Active active : quests.list(weekly)) {
                QuestDef def = QuestPool.quest(active.id);
                if (def != null && !active.claimed && active.progress >= def.goal) {
                    claimable++;
                }
            }
        }
        if (fresh && !quests.daily.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.jadm.quests.new").withStyle(ChatFormatting.GOLD)
                    .append(" ").append(button()));
        } else if (claimable > 0) {
            player.sendSystemMessage(Component.translatable(claimable == 1 ? "message.jadm.quests.claimable.one"
                            : "message.jadm.quests.claimable", claimable)
                    .withStyle(ChatFormatting.GOLD).append(" ").append(button()));
        }
    }

    /** Draws new quests for a player now (their progress and unclaimed rewards are gone). */
    public static void reset(ServerPlayer player) {
        QuestData.get(player.server).reset(player.getUUID());
        send(player, false);
    }

    /** Finishes one of a player's quests, for operators. @return whether they had it */
    public static boolean complete(ServerPlayer player, String id) {
        QuestData.PlayerQuests quests = ensure(player);
        QuestData.Active active = find(quests, id);
        QuestDef def = QuestPool.quest(id);
        if (active == null || def == null) {
            return false;
        }
        if (active.progress < def.goal) {
            count(player, d -> d.id.equals(id) ? def.goal : 0);
        }
        return true;
    }

    /** The ids of a player's current quests. */
    public static List<String> current(ServerPlayer player) {
        QuestData.PlayerQuests quests = ensure(player);
        Set<String> ids = new HashSet<>();
        quests.daily.forEach(a -> ids.add(a.id));
        quests.weekly.forEach(a -> ids.add(a.id));
        return List.copyOf(ids);
    }
}
