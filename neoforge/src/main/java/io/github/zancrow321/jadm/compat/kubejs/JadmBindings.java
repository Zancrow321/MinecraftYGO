package io.github.zancrow321.jadm.compat.kubejs;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.duel.DuelManager;
import io.github.zancrow321.jadm.duel.DuelRecords;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.item.BoosterPackItem;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.points.Points;
import io.github.zancrow321.jadm.ranking.Ranking;
import io.github.zancrow321.jadm.ranking.Tiers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The {@code Jadm} helpers for scripts, e.g. {@code Jadm.card(89631139, 'ultra')}, {@code Jadm.booster('LOB')} or
 * {@code Jadm.giveDuelPoints(player, 50)}.
 */
public final class JadmBindings {
    private static final java.util.Random RANDOM = new java.util.Random();

    private JadmBindings() {
    }

    /** What scripts read about a card: {@code info.name}, {@code info.atk} and so on. */
    public static final class Card {
        private final CardInfo info;

        Card(CardInfo info) {
            this.info = info;
        }

        public int getCode() {
            return info.code();
        }

        public String getName() {
            return info.name();
        }

        public String getText() {
            return info.description();
        }

        /** The line under the name in the card panel, e.g. "LIGHT Dragon / Normal · Level 8 · ATK 3000". */
        public String getType() {
            return CardItem.typeLine(info);
        }

        public boolean isMonster() {
            return info.is(OcgConstants.TYPE_MONSTER);
        }

        public boolean isSpell() {
            return info.is(OcgConstants.TYPE_SPELL);
        }

        public boolean isTrap() {
            return info.is(OcgConstants.TYPE_TRAP);
        }

        /** The level or rank; 0 for spells and traps. */
        public int getLevel() {
            return isMonster() ? info.data().level() & 0xff : 0;
        }

        public int getAtk() {
            return isMonster() ? info.data().attack() : 0;
        }

        public int getDef() {
            return isMonster() ? info.data().defense() : 0;
        }

        @Override
        public String toString() {
            return info.name();
        }
    }

    // ---- cards and packs

    /** A common print of the card with that passcode. */
    public static ItemStack card(int code) {
        return CardItem.of(code);
    }

    /** @param rarity common, rare, super, ultra or secret; printed names like "Ultra Rare" work too */
    public static ItemStack card(int code, String rarity) {
        return CardItem.of(code, rarity(rarity));
    }

    /** The passcode of a card item, or 0 for anything else. */
    public static int cardCode(ItemStack stack) {
        return CardItem.code(stack);
    }

    public static boolean isCard(ItemStack stack) {
        return CardItem.code(stack) != 0;
    }

    /** common, rare, super, ultra or secret. */
    public static String cardRarity(ItemStack stack) {
        return CardItem.rarity(stack).id();
    }

    /** The card's name, or "" for an unknown passcode. */
    public static String cardName(int code) {
        CardInfo info = JadmData.cards().card(code);
        return info == null ? "" : info.name();
    }

    /** Everything about a card, or {@code null} for an unknown passcode. */
    public static @Nullable Card cardInfo(int code) {
        CardInfo info = JadmData.cards().card(code);
        if (info == null) {
            return null;
        }
        return new Card(info);
    }

    /** A random card from a random set of this server's pool, at the rarity it has in that set. */
    public static ItemStack randomCard() {
        BoosterSets.BoosterSet set = BoosterPackItem.randomSet(RandomSource.create());
        if (set == null || set.cards().isEmpty()) {
            return ItemStack.EMPTY;
        }
        BoosterSets.Card card = set.cards().get(RANDOM.nextInt(set.cards().size()));
        return CardItem.of(card.code(), card.rarity());
    }

    /** A random card printed at {@code rarity} (common, rare, super, ultra or secret) in a set of the pool. */
    public static ItemStack randomCard(String rarity) {
        BoosterSets.Rarity wanted = rarity(rarity);
        List<BoosterSets.Card> cards = JadmData.sets().sets().values().stream()
                .flatMap(set -> set.cards().stream()).filter(card -> card.rarity() == wanted).toList();
        if (cards.isEmpty()) {
            return ItemStack.EMPTY;
        }
        BoosterSets.Card card = cards.get(RANDOM.nextInt(cards.size()));
        return CardItem.of(card.code(), card.rarity());
    }

    /**
     * A sealed booster pack of a set, by its code ({@code LOB}) or id ({@code legend_of_blue_eyes_white_dragon});
     * a pack of a random set if there is no such set.
     */
    public static ItemStack booster(String set) {
        BoosterSets.BoosterSet found = findSet(set);
        return BoosterPackItem.of(found == null ? null : found.id());
    }

    /** A pack that opens to a random set when it is opened. */
    public static ItemStack randomBooster() {
        return BoosterPackItem.of(null);
    }

    /** The codes of the booster sets this server's packs come from (in a progression world, the unlocked ones). */
    public static List<String> sets() {
        return JadmData.sets().sets().values().stream().map(BoosterSets.BoosterSet::code).toList();
    }

    /** A set's full name, or "" if there is no such set. */
    public static String setName(String set) {
        BoosterSets.BoosterSet found = findSet(set);
        return found == null ? "" : found.name();
    }

    private static @Nullable BoosterSets.BoosterSet findSet(String set) {
        if (set == null || set.isBlank()) {
            return null;
        }
        BoosterSets.BoosterSet byId = JadmData.set(set);
        if (byId != null) {
            return byId;
        }
        return JadmData.allSets().sets().values().stream().filter(s -> s.code().equalsIgnoreCase(set)).findFirst()
                .orElse(null);
    }

    private static BoosterSets.Rarity rarity(String rarity) {
        try {
            return BoosterSets.Rarity.parse(rarity);
        } catch (IllegalArgumentException | NullPointerException e) {
            return rarity == null ? BoosterSets.Rarity.COMMON : BoosterSets.Rarity.ofPrinted(rarity);
        }
    }

    // ---- Duel Points

    /** Whether Duel Points are on (the shops' currency is DP rather than an item). */
    public static boolean duelPointsActive() {
        return Points.active();
    }

    public static long duelPoints(ServerPlayer player) {
        return Points.get(player.server).balance(player.getUUID());
    }

    public static void giveDuelPoints(ServerPlayer player, long amount) {
        Points.get(player.server).add(player.server, player.getUUID(), amount);
    }

    /** @return whether the player had that many, which are then taken */
    public static boolean takeDuelPoints(ServerPlayer player, long amount) {
        return Points.get(player.server).take(player.server, player.getUUID(), amount);
    }

    public static void setDuelPoints(ServerPlayer player, long amount) {
        Points.get(player.server).set(player.server, player.getUUID(), amount);
    }

    // ---- duels, records and ranks

    public static boolean isDueling(ServerPlayer player) {
        return DuelManager.get(player.server).inDuel(player);
    }

    /** Duels won, against people and NPCs. */
    public static int wins(ServerPlayer player) {
        return DuelRecords.get(player.server).of(player.getUUID()).map(DuelRecords.Record::wins).orElse(0);
    }

    public static int losses(ServerPlayer player) {
        return DuelRecords.get(player.server).of(player.getUUID()).map(DuelRecords.Record::losses).orElse(0);
    }

    public static int duels(ServerPlayer player) {
        return DuelRecords.get(player.server).of(player.getUUID()).map(DuelRecords.Record::duels).orElse(0);
    }

    /** The current win streak. */
    public static int winStreak(ServerPlayer player) {
        return DuelRecords.get(player.server).of(player.getUUID()).map(DuelRecords.Record::streak).orElse(0);
    }

    /** The ranked rating. */
    public static int rating(ServerPlayer player) {
        return Ranking.get(player.server).rating(player.getUUID());
    }

    /** 0 Bronze, 1 Silver, 2 Gold, 3 Platinum, 4 Diamond, 5 Duel King. */
    public static int rank(ServerPlayer player) {
        return Ranking.get(player.server).of(player.getUUID()).map(Ranking.Entry::tier).orElse(0);
    }

    /** e.g. "Gold". */
    public static String rankName(ServerPlayer player) {
        return Tiers.name(rank(player));
    }
}
