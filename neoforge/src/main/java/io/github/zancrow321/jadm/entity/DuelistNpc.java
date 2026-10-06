package io.github.zancrow321.jadm.entity;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.cosmetics.Cosmetics;
import io.github.zancrow321.jadm.duel.DuelManager;
import io.github.zancrow321.jadm.engine.data.Banlist;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.data.DeckBuilder;
import io.github.zancrow321.jadm.engine.data.DeckRules;
import io.github.zancrow321.jadm.engine.data.PoolMode;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.engine.data.TournamentDecks;
import io.github.zancrow321.jadm.progression.StarterDecks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A wandering duelist. Right-click it to duel; beat it and it hands over a booster pack, then wants a day to
 * rebuild its deck before it will duel you again. Each one has its own name, look and deck, kept with the entity.
 */
public final class DuelistNpc extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SKIN =
            SynchedEntityData.defineId(DuelistNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DISK_SKIN =
            SynchedEntityData.defineId(DuelistNpc.class, EntityDataSerializers.STRING);
    public static final int SKINS = 9;
    public static final List<String> TITLES = List.of("Rare Hunter", "Card Shark", "Wandering Duelist",
            "Duel Monk", "Tournament Hopeful", "Puzzle Duelist", "Dragon Tamer", "Bug Collector", "Ghoul");
    private static final Pattern STRUCTURE = Pattern.compile("starter deck|structure deck");

    private long deckSeed;
    private String sleeve = Cosmetics.DEFAULT_SLEEVE;
    private boolean dueling;
    /** player -> game time they last beat this duelist */
    private final Map<UUID, Long> beatenBy = new HashMap<>();

    public DuelistNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        // A fresh duelist gets a look, a title and a deck; one loaded from disk overwrites them with its own.
        if (!level.isClientSide()) {
            RandomSource random = level.getRandom();
            entityData.set(SKIN, random.nextInt(SKINS));
            deckSeed = random.nextLong();
            entityData.set(DISK_SKIN, Cosmetics.SKINS.get(random.nextInt(Cosmetics.SKINS.size())).id());
            sleeve = Cosmetics.SLEEVES.get(random.nextInt(Cosmetics.SLEEVES.size())).id();
            setCustomName(Component.literal(TITLES.get(random.nextInt(TITLES.size()))));
            setCustomNameVisible(true);
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
        builder.define(DISK_SKIN, Cosmetics.DEFAULT_SKIN);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() {
                return !dueling && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    public String diskSkin() {
        return entityData.get(DISK_SKIN);
    }

    public String sleeve() {
        return sleeve;
    }

    public String duelistName() {
        return getName().getString();
    }

    /**
     * The NPC's deck at a progression step: the same seed always picks the same kind of deck from what is out by
     * then. About a third of the duelists play a tournament deck of an era that has come, another third a starter
     * or structure deck, the rest a deck {@link DeckBuilder} builds from the pool. With only the modeled cards in play
     * they all build their own.
     */
    public Deck deck(int step) {
        return deckFor(duelistName(), deckSeed, step, JadmData.banlist(step));
    }

    /** The deck a duelist with this name and deck seed plays at a progression step, see {@link #deck(int)}. */
    public static Deck deckFor(String name, long deckSeed, int step, Banlist banlist) {
        PoolMode mode = JadmData.poolMode();
        Random random = new Random(deckSeed);
        int kind = random.nextInt(3);
        if (mode != PoolMode.MODELED && kind < 2) {
            Deck curated = kind == 0 ? tournamentDeck(step, banlist, random) : structureDeck(step, banlist, random);
            if (curated != null) {
                return curated;
            }
        }
        return DeckBuilder.random(name, deckSeed, JadmData.cards(),
                mode == PoolMode.PROGRESSION ? JadmData.progression().pool(step) : JadmData.pool(), banlist);
    }

    private static Deck tournamentDeck(int step, Banlist banlist, Random random) {
        LocalDate date = JadmData.poolMode() == PoolMode.PROGRESSION ? JadmData.progression().date(step) : LocalDate.MAX;
        List<TournamentDecks.Entry> decks = JadmData.tournamentDecks().by(date);
        if (decks.isEmpty()) {
            return null;
        }
        // The newest era's deck most often: that is what the duelists of the day play.
        TournamentDecks.Entry entry = random.nextInt(2) == 0 ? decks.get(decks.size() - 1)
                : decks.get(random.nextInt(decks.size()));
        return entry.legal(banlist, JadmData.cards());
    }

    private static Deck structureDeck(int step, Banlist banlist, Random random) {
        List<Products.Product> products = JadmData.products().products();
        int last = JadmData.poolMode() == PoolMode.PROGRESSION ? Math.min(step, products.size() - 1)
                : products.size() - 1;
        List<Deck> decks = new ArrayList<>();
        for (Products.Product p : products.subList(0, last + 1)) {
            if (p.kind() == Products.Kind.DECK && STRUCTURE.matcher(p.name().toLowerCase(Locale.ROOT)).find()) {
                Deck deck = StarterDecks.deckOf(p, banlist);
                if (DeckRules.problems(deck, JadmData.cards(), banlist).isEmpty()) {
                    decks.add(new Deck(p.name(), deck.main(), deck.extra(), List.of()));
                }
            }
        }
        if (decks.isEmpty()) {
            return null;
        }
        // Lean towards the newer decks, as with the tournament decks.
        int newest = decks.size() - 1;
        return decks.get(random.nextInt(2) == 0 ? newest : random.nextInt(decks.size()));
    }

    /** Gives this duelist the name, look and sleeve that belong to {@code seed}, for a tournament stand-in. */
    public void dressAs(String name, long seed) {
        Random random = new Random(seed);
        entityData.set(SKIN, random.nextInt(SKINS));
        entityData.set(DISK_SKIN, Cosmetics.SKINS.get(random.nextInt(Cosmetics.SKINS.size())).id());
        sleeve = Cosmetics.SLEEVES.get(random.nextInt(Cosmetics.SLEEVES.size())).id();
        deckSeed = seed;
        setCustomName(Component.literal(name));
    }

    public boolean isDueling() {
        return dueling;
    }

    public void setDueling(boolean dueling) {
        this.dueling = dueling;
        getNavigation().stop();
        if (dueling) {
            setPersistenceRequired();
        }
    }

    /** @return why this duelist won't duel {@code player} right now, or {@code null} if it will */
    public Component refusal(ServerPlayer player) {
        if (dueling) {
            return Component.literal(duelistName() + " is already dueling.");
        }
        Long beaten = beatenBy.get(player.getUUID());
        if (beaten != null && level().getGameTime() - beaten < JadmServerConfig.NPC_REMATCH_MINUTES.get() * 1200L) {
            return Component.literal(duelistName() + ": \"You beat me fair and square. Let me rebuild my deck, "
                    + "come back later!\"");
        }
        return null;
    }

    /**
     * The duel is over: the NPC says so and, if it lost, won't duel that player again for a while.
     *
     * @param gifts whether the player gets something for it (handed out by the duel manager)
     */
    public void duelEnded(ServerPlayer player, boolean playerWon, boolean gifts) {
        setDueling(false);
        if (player == null) {
            return;
        }
        if (playerWon) {
            beatenBy.put(player.getUUID(), level().getGameTime());
        }
        player.sendSystemMessage(Component.literal(duelistName() + ": \"" + (playerWon
                ? gifts ? "Well played! Take this." : "Well played!"
                : gifts ? "Better luck next time! Here, for your next deck." : "Better luck next time!") + "\""));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (isDueling()) {
                DuelManager.get(serverPlayer.server).watchNpc(serverPlayer, this);
            } else {
                DuelManager.get(serverPlayer.server).duelNpc(serverPlayer, this);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !dueling && super.hurt(source, amount);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Skin", skin());
        tag.putLong("DeckSeed", deckSeed);
        tag.putString("DiskSkin", diskSkin());
        tag.putString("Sleeve", sleeve);
        ListTag beaten = new ListTag();
        beatenBy.forEach((id, time) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putLong("Time", time);
            beaten.add(entry);
        });
        tag.put("BeatenBy", beaten);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SKIN, Math.floorMod(tag.getInt("Skin"), SKINS));
        deckSeed = tag.getLong("DeckSeed");
        entityData.set(DISK_SKIN, Cosmetics.skin(tag.getString("DiskSkin")).id());
        sleeve = Cosmetics.sleeve(tag.getString("Sleeve")).id();
        for (Tag t : tag.getList("BeatenBy", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            beatenBy.put(entry.getUUID("Player"), entry.getLong("Time"));
        }
    }

    /** Natural spawns: rarely, on grass in the light, like animals. */
    public static boolean canSpawn(EntityType<DuelistNpc> type, ServerLevelAccessor level, MobSpawnType reason,
                                   BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
                && level.getRawBrightness(pos, 0) > 8;
    }
}
