package io.github.zancrow321.jadm.client.field;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.ClientDuel;
import io.github.zancrow321.jadm.client.JadmClientConfig;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.engine.duel.Board;
import io.github.zancrow321.jadm.engine.protocol.CardState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.github.zancrow321.jadm.client.field.FieldLayout.*;
import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * Field Spells transform the ground of the duel field while they are face-up: Umi floods it with rolling water,
 * Yami turns it into creeping shadow, Sogen grows grass that bends in the wind, and so on. The new ground spreads out
 * from the Field Zone when the card lands and draws back into it when the card leaves. Each half shows its owner's
 * Field Spell; a single one covers the whole field. Field Spells without a look of their own tint the field in the
 * colour of the attribute their text is about.
 *
 * <p>Only the field and the ground around it change, never sky or fog. Built from vanilla block textures, particles
 * and sounds, so nothing ships with it.
 */
@EventBusSubscriber(modid = Jadm.MOD_ID, value = Dist.CLIENT)
public final class FieldSpellArena {
    /** How far the new ground reaches past the mat, in field units. */
    private static final double MARGIN = 6;
    /** Ticks for the ground to spread over the whole field, and to draw back into the card. */
    private static final float SPREAD_TICKS = 50;
    private static final float RETREAT_TICKS = 30;
    /** Farthest a ground tile can be from a Field Zone, so the spreading edge reaches every tile. */
    private static final double REACH = 34;
    private static final double GROUND_Y = 0.01;
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    /** Shown and fading layers, oldest first. */
    private static final List<Layer> layers = new ArrayList<>();
    private static final int[] codes = new int[2];

    /**
     * For headless testing: {@code -Djadm.fieldSpells=22702055,59197169/86318356} shows these Field Spells' ground
     * instead of the real ones, one step every {@link #TEST_STEP} ticks once the field is up ("a/b": player 0 has a,
     * player 1 has b; "-" for none), to film every look in one duel.
     */
    private static final String[] TEST_STEPS = System.getProperty("jadm.fieldSpells", "").isBlank() ? null
            : System.getProperty("jadm.fieldSpells").split(",");
    private static final int TEST_STEP = 160;

    private FieldSpellArena() {
    }

    // ------------------------------------------------------------------ the looks

    /** One block texture of a ground, how often it is picked, and its tint. */
    private record Tile(String sprite, int weight, int tint) {
    }

    private enum Shape { CROSS, BOX, PEAK }

    /** What grows out of the ground around the mat. */
    private record PropKind(Shape shape, String sprite, String top, int tint, int count, double minHeight,
                            double maxHeight, double width, double sway) {
    }

    /** One Field Spell's ground, what grows on it and what flies over it. */
    private static final class Look {
        final String name;
        final List<Tile> ground = new ArrayList<>();
        int alpha = 0xE0;
        final List<PropKind> props = new ArrayList<>();
        /** Height of the waves that roll toward the edge (0 for still ground) and the wind's strength. */
        double waves;
        double wind;
        int glow = 0x40C8FF;
        Consumer<Spawn> weather = s -> { };
        SoundEvent[] reveal = {SoundEvents.AMETHYST_BLOCK_RESONATE};
        float pitch = 1;
        SoundEvent ambient;

        Look(String name) {
            this.name = name;
        }

        Look ground(String sprite, int weight, int tint) {
            ground.add(new Tile(sprite, weight, tint));
            return this;
        }

        Look alpha(int alpha) {
            this.alpha = alpha;
            return this;
        }

        Look cross(String sprite, int tint, int count, double minHeight, double maxHeight, double sway) {
            props.add(new PropKind(Shape.CROSS, sprite, null, tint, count, minHeight, maxHeight, 0, sway));
            return this;
        }

        Look box(String side, String top, int tint, int count, double width, double minHeight, double maxHeight) {
            props.add(new PropKind(Shape.BOX, side, top, tint, count, minHeight, maxHeight, width, 0));
            return this;
        }

        Look peaks(String side, String top, int count, double width, double minHeight, double maxHeight) {
            props.add(new PropKind(Shape.PEAK, side, top, 0xFFFFFF, count, minHeight, maxHeight, width, 0));
            return this;
        }

        Look waves(double height) {
            waves = height;
            return this;
        }

        Look wind(double strength) {
            wind = strength;
            return this;
        }

        Look glow(int rgb) {
            glow = rgb;
            return this;
        }

        Look weather(Consumer<Spawn> weather) {
            this.weather = weather;
            return this;
        }

        Look sounds(float pitch, SoundEvent ambient, SoundEvent... reveal) {
            this.pitch = pitch;
            this.ambient = ambient;
            this.reveal = reveal;
            return this;
        }
    }

    static final int UMI = 22702055;
    static final int A_LEGENDARY_OCEAN = 295517;
    static final int UMIIRUKA = 82999629;
    static final int YAMI = 59197169;
    static final int SOGEN = 86318356;
    static final int WASTELAND = 23424603;
    static final int MOUNTAIN = 50913601;
    static final int FOREST = 87430998;
    static final int GAIA_POWER = 56594520;
    static final int MOLTEN_DESTRUCTION = 19384334;
    static final int NECROVALLEY = 47355498;
    static final int RISING_AIR_CURRENT = 45778932;
    static final int LUMINOUS_SPARK = 81777047;
    static final int MYSTIC_PLASMA_ZONE = 18161786;

    private static final Map<Integer, Look> LOOKS = new HashMap<>();

    private static Look ocean(String name, int water, int glow) {
        return new Look(name).ground("block/water_still", 1, water).alpha(0xD8).waves(0.45).glow(glow)
                .cross("block/kelp_plant", 0xFFFFFF, 14, 1.2, 2.6, 0.25)
                .cross("block/seagrass", 0xFFFFFF, 18, 0.6, 1.1, 0.2)
                .cross("block/tube_coral", 0xFFFFFF, 5, 0.6, 0.9, 0)
                .cross("block/brain_coral", 0xFFFFFF, 5, 0.6, 0.9, 0)
                .cross("block/fire_coral", 0xFFFFFF, 4, 0.6, 0.9, 0)
                .weather(s -> {
                    s.edge(ParticleTypes.SPLASH, 10, 0.5, 0.25);
                    s.edge(ParticleTypes.FALLING_WATER, 2, 1.8, 0);
                    s.ground(ParticleTypes.DOLPHIN, 3, 0.2, 0.05);
                    s.ground(ParticleTypes.BUBBLE_POP, 2, 0.1, 0.02);
                })
                .sounds(1, SoundEvents.BOAT_PADDLE_WATER, SoundEvents.GENERIC_SPLASH, SoundEvents.PLAYER_SPLASH_HIGH_SPEED);
    }

    static {
        LOOKS.put(UMI, ocean("umi", 0x4C90F4, 0x60C0FF));
        LOOKS.put(A_LEGENDARY_OCEAN, ocean("legendary_ocean", 0x2850D8, 0x4080FF));
        LOOKS.put(UMIIRUKA, ocean("umiiruka", 0x40C8D8, 0x80FFF0));
        LOOKS.put(YAMI, new Look("yami").ground("block/sculk", 6, 0x7050A0).ground("block/obsidian", 1, 0xFFFFFF)
                .alpha(0xF0).glow(0x8030FF)
                .box("block/crying_obsidian", "block/obsidian", 0xFFFFFF, 10, 0.35, 1.4, 3.2)
                .cross("block/wither_rose", 0xFFFFFF, 12, 0.7, 1.0, 0.05)
                .weather(s -> {
                    s.ground(new DustParticleOptions(new Vector3f(0.22f, 0.05f, 0.32f), 1.6f), 4, 0.8, 0.03);
                    s.ground(ParticleTypes.LARGE_SMOKE, 2, 0.1, 0.04);
                    s.ground(ParticleTypes.PORTAL, 6, 0.3, 0.3);
                    s.ground(ParticleTypes.SCULK_SOUL, 1, 0.1, 0.05);
                    s.air(ParticleTypes.ASH, 10, 3);
                })
                .sounds(0.6f, SoundEvents.SCULK_CATALYST_BLOOM, SoundEvents.SCULK_SHRIEKER_SHRIEK,
                        SoundEvents.WITHER_AMBIENT));
        LOOKS.put(SOGEN, new Look("sogen").ground("block/grass_block_top", 1, 0xB4E05A).alpha(0xF0).wind(1.2)
                .glow(0xA0FF60)
                .cross("block/short_grass", 0xA8DC50, 110, 0.7, 1.3, 0.35)
                .cross("block/tall_grass_top", 0xA8DC50, 50, 1.3, 2.2, 0.5)
                .cross("block/oxeye_daisy", 0xFFFFFF, 12, 0.6, 0.8, 0.3)
                .cross("block/dandelion", 0xFFFFFF, 12, 0.6, 0.8, 0.3)
                .weather(s -> {
                    s.wind(ParticleTypes.WHITE_ASH, 6);
                    s.wind(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SHORT_GRASS.defaultBlockState()), 2);
                    s.ground(ParticleTypes.SMALL_GUST, 1, 0.6, 0);
                })
                .sounds(1.2f, SoundEvents.BREEZE_IDLE_GROUND, SoundEvents.GRASS_PLACE,
                        SoundEvents.BREEZE_WIND_CHARGE_BURST.value()));
        LOOKS.put(WASTELAND, new Look("wasteland").ground("block/red_sand", 7, 0xFFFFFF)
                .ground("block/coarse_dirt", 2, 0xFFFFFF).ground("block/terracotta", 1, 0xFFFFFF).alpha(0xF0)
                .wind(0.6).glow(0xFFA050)
                .cross("block/dead_bush", 0xFFFFFF, 26, 0.6, 1.2, 0.1)
                .box("block/terracotta", "block/terracotta", 0xFFFFFF, 8, 0.45, 0.3, 0.9)
                .box("block/cobblestone", "block/cobblestone", 0xFFFFFF, 4, 0.35, 0.2, 0.5)
                .weather(s -> {
                    s.wind(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.RED_SAND.defaultBlockState()), 8);
                    s.ground(ParticleTypes.DUST_PLUME, 2, 0.1, 0.05);
                })
                .sounds(0.8f, SoundEvents.SAND_BREAK, SoundEvents.ROOTED_DIRT_BREAK, SoundEvents.SAND_PLACE));
        LOOKS.put(MOUNTAIN, new Look("mountain").ground("block/stone", 7, 0xFFFFFF).ground("block/andesite", 2, 0xFFFFFF)
                .ground("block/gravel", 1, 0xFFFFFF).ground("block/snow", 1, 0xFFFFFF).alpha(0xF0).wind(0.5).glow(0xE0F0FF)
                .peaks("block/stone", "block/snow", 7, 1.4, 2.2, 4.2)
                .box("block/andesite", "block/snow", 0xFFFFFF, 10, 0.4, 0.3, 0.8)
                .weather(s -> {
                    s.air(ParticleTypes.SNOWFLAKE, 10, 4);
                    s.peak(ParticleTypes.CLOUD, 2);
                })
                .sounds(0.5f, SoundEvents.POWDER_SNOW_STEP, SoundEvents.DEEPSLATE_BREAK, SoundEvents.STONE_PLACE));
        LOOKS.put(FOREST, new Look("forest").ground("block/moss_block", 1, 0xFFFFFF)
                .ground("block/grass_block_top", 6, 0x4E9A2C).alpha(0xF0).wind(0.3).glow(0x60E060)
                .cross("block/oak_sapling", 0xFFFFFF, 12, 2.2, 3.4, 0.12)
                .cross("block/spruce_sapling", 0xFFFFFF, 10, 2.2, 3.4, 0.12)
                .cross("block/fern", 0x59AE30, 40, 0.7, 1.2, 0.2)
                .cross("block/large_fern_top", 0x59AE30, 14, 1.2, 1.8, 0.2)
                .cross("block/red_mushroom", 0xFFFFFF, 6, 0.4, 0.6, 0)
                .cross("block/brown_mushroom", 0xFFFFFF, 6, 0.4, 0.6, 0)
                .weather(s -> {
                    s.air(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.OAK_LEAVES.defaultBlockState()), 8, 3.5);
                    s.ground(ParticleTypes.SPORE_BLOSSOM_AIR, 3, 1.5, 0);
                    s.ground(ParticleTypes.HAPPY_VILLAGER, 1, 0.8, 0);
                })
                .sounds(0.9f, SoundEvents.AZALEA_LEAVES_STEP, SoundEvents.AZALEA_LEAVES_PLACE, SoundEvents.GRASS_PLACE));
        LOOKS.put(GAIA_POWER, new Look("gaia_power").ground("block/moss_block", 1, 0xFFFFFF).alpha(0xF0).wind(0.2)
                .glow(0x80FF40)
                .cross("block/poppy", 0xFFFFFF, 10, 0.6, 0.9, 0.1)
                .cross("block/dandelion", 0xFFFFFF, 10, 0.6, 0.9, 0.1)
                .cross("block/allium", 0xFFFFFF, 8, 0.6, 0.9, 0.1)
                .cross("block/blue_orchid", 0xFFFFFF, 8, 0.6, 0.9, 0.1)
                .cross("block/cornflower", 0xFFFFFF, 8, 0.6, 0.9, 0.1)
                .cross("block/azure_bluet", 0xFFFFFF, 8, 0.6, 0.9, 0.1)
                .cross("block/oak_sapling", 0xFFFFFF, 8, 1.8, 3.0, 0.1)
                .cross("block/short_grass", 0x5DB830, 30, 0.6, 1.0, 0.15)
                .weather(s -> {
                    s.ground(ParticleTypes.HAPPY_VILLAGER, 4, 0.6, 0.1);
                    s.ground(ParticleTypes.COMPOSTER, 3, 0.4, 0.1);
                    s.ground(ParticleTypes.SPORE_BLOSSOM_AIR, 3, 1.5, 0);
                    s.ground(new DustParticleOptions(new Vector3f(0.4f, 1f, 0.3f), 1.4f), 4, 0.2, 0.2);
                })
                .sounds(1.1f, SoundEvents.BONE_MEAL_USE, SoundEvents.CHORUS_FLOWER_GROW, SoundEvents.BONE_MEAL_USE));
        LOOKS.put(MOLTEN_DESTRUCTION, new Look("molten_destruction").ground("block/lava_still", 6, 0xFFFFFF)
                .ground("block/magma", 1, 0xFFFFFF).alpha(0xF0).waves(0.15).glow(0xFF6020)
                .box("block/basalt_side", "block/basalt_top", 0xFFFFFF, 10, 0.45, 1.0, 3.0)
                .box("block/blackstone", "block/magma", 0xFFFFFF, 8, 0.4, 0.3, 0.8)
                .cross("block/fire_0", 0xFFFFFF, 8, 0.5, 0.9, 0)
                .weather(s -> {
                    s.ground(ParticleTypes.LAVA, 4, 0.1, 0);
                    s.ground(ParticleTypes.FLAME, 5, 0.2, 0.05);
                    s.ground(ParticleTypes.SMOKE, 3, 0.4, 0.04);
                    s.air(ParticleTypes.ASH, 8, 3);
                })
                .sounds(0.8f, SoundEvents.LAVA_AMBIENT, SoundEvents.BLAZE_SHOOT, SoundEvents.LAVA_POP,
                        SoundEvents.FIRECHARGE_USE));
        LOOKS.put(NECROVALLEY, new Look("necrovalley").ground("block/sand", 4, 0xFFFFFF)
                .ground("block/sandstone_top", 1, 0xFFFFFF).alpha(0xF0).wind(0.4).glow(0x60E0FF)
                .box("block/cut_sandstone", "block/chiseled_sandstone", 0xFFFFFF, 8, 0.45, 2.4, 3.6)
                .box("block/sandstone", "block/sandstone_top", 0xFFFFFF, 6, 0.4, 0.3, 0.8)
                .cross("block/dead_bush", 0xFFFFFF, 10, 0.6, 1.0, 0.05)
                .cross("block/soul_fire_0", 0xFFFFFF, 8, 0.7, 1.2, 0)
                .weather(s -> {
                    s.ground(ParticleTypes.SOUL, 2, 0.3, 0.05);
                    s.ground(ParticleTypes.SOUL_FIRE_FLAME, 2, 0.2, 0.03);
                    s.wind(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), 5);
                })
                .sounds(0.7f, SoundEvents.SOUL_ESCAPE.value(), SoundEvents.SOUL_ESCAPE.value(), SoundEvents.SAND_BREAK));
        LOOKS.put(RISING_AIR_CURRENT, new Look("rising_air_current").ground("block/white_wool", 1, 0xE8F4FF)
                .alpha(0x90).wind(2.0).glow(0xC0F0FF)
                .weather(s -> {
                    s.ground(ParticleTypes.CLOUD, 5, 0.3, 0.25);
                    s.ground(ParticleTypes.GUST, 1, 0.8, 0);
                    s.ground(ParticleTypes.SMALL_GUST, 2, 1.2, 0);
                    s.wind(ParticleTypes.WHITE_ASH, 8);
                })
                .sounds(1.0f, SoundEvents.BREEZE_IDLE_AIR, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                        SoundEvents.ELYTRA_FLYING));
        LOOKS.put(LUMINOUS_SPARK, new Look("luminous_spark").ground("block/glowstone", 1, 0xFFFFFF)
                .alpha(0xA0).glow(0xFFE880)
                .box("block/sea_lantern", "block/sea_lantern", 0xFFFFFF, 8, 0.35, 1.4, 2.8)
                .weather(s -> {
                    s.ground(ParticleTypes.END_ROD, 5, 0.6, 0.1);
                    s.ground(ParticleTypes.ELECTRIC_SPARK, 6, 0.4, 0.4);
                    s.ground(ParticleTypes.WAX_ON, 3, 0.4, 0.2);
                })
                .sounds(1.3f, SoundEvents.AMETHYST_BLOCK_CHIME, SoundEvents.BEACON_ACTIVATE,
                        SoundEvents.AMETHYST_BLOCK_RESONATE));
        LOOKS.put(MYSTIC_PLASMA_ZONE, new Look("mystic_plasma_zone").ground("block/crying_obsidian", 6, 0xFFFFFF)
                .ground("block/amethyst_block", 1, 0xFFFFFF).alpha(0xE8).glow(0xC040FF)
                .box("block/amethyst_block", "block/amethyst_block", 0xFFFFFF, 8, 0.35, 1.2, 2.8)
                .cross("block/amethyst_cluster", 0xFFFFFF, 16, 0.7, 1.2, 0)
                .weather(s -> {
                    s.ground(ParticleTypes.PORTAL, 8, 0.4, 0.4);
                    s.ground(ParticleTypes.REVERSE_PORTAL, 4, 0.2, 0.1);
                    s.ground(ParticleTypes.WITCH, 3, 0.6, 0.1);
                })
                .sounds(0.8f, SoundEvents.PORTAL_AMBIENT, SoundEvents.AMETHYST_CLUSTER_PLACE,
                        SoundEvents.BEACON_POWER_SELECT));
    }

    /** Attribute colours for Field Spells without a look of their own. */
    private static final String[] ATTRIBUTES = {"WATER", "FIRE", "EARTH", "WIND", "LIGHT", "DARK", "DIVINE"};
    private static final int[] ATTRIBUTE_COLORS = {0x3A7BE0, 0xE8502A, 0xA07840, 0x58D878, 0xF0D860, 0x8030C0,
            0xF0C040};
    /** Monster types a Field Spell powers up, and the attribute they usually come with. */
    private static final String[][] TYPE_ATTRIBUTES = {{"Aqua", "WATER"}, {"Fish", "WATER"}, {"Sea Serpent", "WATER"},
            {"Pyro", "FIRE"}, {"Plant", "EARTH"}, {"Insect", "EARTH"}, {"Beast-Warrior", "EARTH"},
            {"Rock", "EARTH"}, {"Winged Beast", "WIND"}, {"Fairy", "LIGHT"}, {"Fiend", "DARK"}, {"Zombie", "DARK"},
            {"Spellcaster", "DARK"}, {"Thunder", "LIGHT"}, {"Machine", "EARTH"}, {"Dinosaur", "EARTH"}};
    private static final Map<Integer, Look> TINTS = new HashMap<>();

    static Look look(int code) {
        Look look = LOOKS.get(code);
        if (look != null) {
            return look;
        }
        CardInfo info = JadmData.cards().card(code);
        if (info != null && info.data().alias() != 0 && LOOKS.containsKey(info.data().alias())) {
            return LOOKS.get(info.data().alias());
        }
        return TINTS.computeIfAbsent(code, c -> tint(attributeColor(info)));
    }

    /** The colour of the attribute a Field Spell's text names most, or the holo blue when it names none. */
    private static int attributeColor(CardInfo info) {
        if (info == null || info.description() == null) {
            return 0x40C8FF;
        }
        String text = info.description();
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            int count = text.split("\\b" + ATTRIBUTES[i] + "\\b", -1).length - 1;
            if (count > bestCount) {
                best = i;
                bestCount = count;
            }
        }
        if (best < 0) {
            for (String[] pair : TYPE_ATTRIBUTES) {
                if (text.contains(pair[0])) {
                    best = List.of(ATTRIBUTES).indexOf(pair[1]);
                    break;
                }
            }
        }
        return best < 0 ? 0x40C8FF : ATTRIBUTE_COLORS[best];
    }

    private static Look tint(int rgb) {
        Vector3f color = new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        return new Look("tint").ground("block/white_concrete_powder", 1, rgb).alpha(0x90).glow(rgb)
                .weather(s -> {
                    s.ground(new DustParticleOptions(color, 1.5f), 8, 0.2, 0.25);
                    s.ground(ParticleTypes.END_ROD, 1, 0.3, 0.05);
                });
    }

    // ------------------------------------------------------------------ layers

    /** One Field Spell's ground on the field, from when it lands until it has drawn back. */
    private static final class Layer {
        final Look look;
        final int owner;
        final List<Prop> props;
        final long seed;
        boolean leaving;
        float progress;
        float lastProgress;
        int nextAmbient = 60;

        Layer(Look look, int owner, long seed) {
            this.look = look;
            this.owner = owner;
            this.seed = seed;
            this.props = props(look, owner, seed);
        }

        float progress(float partial) {
            return Mth.lerp(partial, lastProgress, progress);
        }

        /** Where the ground spreads from: the owner's Field Zone. */
        Slot origin() {
            return slot(owner, LOCATION_SZONE, 5);
        }
    }

    private record Prop(PropKind kind, double x, double z, double height, double width, float phase, int tier) {
    }

    /** Scatters a look's props over the ground around the mat, the same way every time for the same card. */
    private static List<Prop> props(Look look, int owner, long seed) {
        RandomSource random = RandomSource.create(seed);
        List<Prop> props = new ArrayList<>();
        for (PropKind kind : look.props) {
            for (int i = 0; i < kind.count(); i++) {
                double[] at = aroundMat(random, kind.shape() == Shape.CROSS ? 0.12 : 0.8);
                double height = Mth.lerp(random.nextDouble(), kind.minHeight(), kind.maxHeight());
                float phase = random.nextFloat() * Mth.TWO_PI;
                if (kind.shape() == Shape.PEAK) {
                    // Tiers that get narrower toward a snowy top.
                    for (int tier = 0; tier < 3; tier++) {
                        props.add(new Prop(kind, at[0], at[1], height * (tier + 1) / 3,
                                kind.width() * (1 - tier * 0.3), phase, tier));
                    }
                } else {
                    props.add(new Prop(kind, at[0], at[1], height, kind.width(), phase, 0));
                }
            }
        }
        return props;
    }

    /** A random point on the ground outside the mat, at least {@code clearance} from it. */
    private static double[] aroundMat(RandomSource random, double clearance) {
        double hx = HALF_WIDTH + 0.3;
        double hz = halfLength() + 0.3;
        while (true) {
            double x = (random.nextDouble() * 2 - 1) * (hx + MARGIN - 1);
            double z = (random.nextDouble() * 2 - 1) * (hz + MARGIN - 1);
            if (outside(x, z) >= clearance) {
                return new double[]{x, z};
            }
        }
    }

    /** How far a field-local point is outside the mat (0 on it). */
    private static double outside(double x, double z) {
        double dx = Math.max(0, Math.abs(x) - HALF_WIDTH - 0.3);
        double dz = Math.max(0, Math.abs(z) - halfLength() - 0.3);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Layer current(int owner) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            if (layer.owner == owner && !layer.leaving) {
                return layer;
            }
        }
        return null;
    }

    /**
     * How much of {@code layer} shows at a field-local point: where its spreading edge has got to, and only its
     * owner's half while the other duelist has a Field Spell too.
     */
    private static double cover(Layer layer, double x, double z, float partial) {
        float p = layer.progress(partial);
        if (p <= 0) {
            return 0;
        }
        Slot o = layer.origin();
        double reach = ease(p) * REACH;
        double d = Math.hypot(x - o.x(), z - o.z());
        double front = Mth.clamp((reach - d) / 2.5, 0, 1);
        Layer other = current(1 - layer.owner);
        if (other == null || other == layer) {
            return front;
        }
        double own = Mth.clamp(0.5 + (layer.owner == 0 ? -z : z) / 1.5, 0, 1);
        return front * Mth.lerp(other.progress(partial), 1, own);
    }

    private static double ease(float t) {
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ClientField.active() || !JadmClientConfig.FIELD_SPELL_ARENA.get()) {
            layers.clear();
            codes[0] = codes[1] = 0;
            return;
        }
        Board board = ClientDuel.view().board();
        for (int player = 0; player < 2; player++) {
            List<CardState> spells = board.side(player).spells();
            CardState card = spells.size() > 5 ? spells.get(5) : null;
            int code = card != null && (card.position() & POS_FACEUP) != 0 ? card.code() : 0;
            if (TEST_STEPS != null) {
                code = testCode(player);
            }
            if (code != codes[player]) {
                codes[player] = code;
                Layer old = current(player);
                if (old != null) {
                    old.leaving = true;
                }
                if (code != 0) {
                    Layer layer = new Layer(look(code), player, code * 31L + player);
                    layers.add(layer);
                    revealSound(layer);
                }
            }
        }
        for (Layer layer : layers) {
            layer.lastProgress = layer.progress;
            // The ground waits for the field to grow, then spreads; a leaving one draws back.
            if (layer.leaving) {
                layer.progress = Math.max(0, layer.progress - 1 / RETREAT_TICKS);
            } else if (ClientField.grown()) {
                layer.progress = Math.min(1, layer.progress + 1 / SPREAD_TICKS);
            }
        }
        layers.removeIf(l -> l.leaving && l.progress <= 0 && l.lastProgress <= 0);
        for (Layer layer : layers) {
            if (layer.progress > 0.05) {
                layer.look.weather.accept(new Spawn(layer));
                ambientSound(layer);
            }
        }
    }

    private static int testCode(int player) {
        long elapsed = ClientField.tick() - ClientField.revealAt() - 40;
        if (elapsed < 0) {
            return 0;
        }
        String[] pair = TEST_STEPS[(int) Math.min(elapsed / TEST_STEP, TEST_STEPS.length - 1)].split("/");
        String code = player < pair.length ? pair[player].trim() : "-";
        return code.equals("-") || code.isEmpty() ? 0 : Integer.parseInt(code);
    }

    private static void revealSound(Layer layer) {
        Slot o = layer.origin();
        Vec3 at = ClientField.toWorld(o.x(), o.z(), 1);
        float pitch = layer.look.pitch;
        for (SoundEvent sound : layer.look.reveal) {
            play(sound, at, 1.0f, pitch);
            pitch *= 0.85f;
        }
    }

    private static void ambientSound(Layer layer) {
        if (layer.look.ambient == null || --layer.nextAmbient > 0) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource random = level.getRandom();
        layer.nextAmbient = 50 + random.nextInt(80);
        double[] at = aroundMat(random, 0.5);
        play(layer.look.ambient, ClientField.toWorld(at[0], at[1], 0.5), 0.35f * layer.progress,
                layer.look.pitch * (0.85f + random.nextFloat() * 0.3f));
    }

    private static void play(SoundEvent sound, Vec3 at, float volume, float pitch) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
        }
    }

    /** Spawns a look's particles over the ground its layer covers. */
    static final class Spawn {
        private final Layer layer;
        private final ClientLevel level;
        private final RandomSource random;

        Spawn(Layer layer) {
            this.layer = layer;
            this.level = Minecraft.getInstance().level;
            this.random = level == null ? RandomSource.create() : level.getRandom();
        }

        /** Rounds {@code perTick} particles to whole ones, scaled by how far the ground has spread. */
        private int count(double perTick) {
            double n = perTick * layer.progress;
            int whole = (int) n;
            return whole + (random.nextDouble() < n - whole ? 1 : 0);
        }

        /** A covered point anywhere on the ground, or {@code null}. */
        private double[] point(boolean edgeOnly) {
            double hx = HALF_WIDTH + 0.3 + MARGIN * (edgeOnly ? 0.7 : 0.8);
            double hz = halfLength() + 0.3 + MARGIN * (edgeOnly ? 0.7 : 0.8);
            for (int tries = 0; tries < 4; tries++) {
                double x = (random.nextDouble() * 2 - 1) * hx;
                double z = (random.nextDouble() * 2 - 1) * hz;
                double out = outside(x, z);
                if (edgeOnly && (out < 0.5 || out > MARGIN * 0.7)) {
                    continue;
                }
                if (cover(layer, x, z, 1) > 0.5) {
                    return new double[]{x, z};
                }
            }
            return null;
        }

        private void add(ParticleOptions particle, double[] at, double up, double dx, double dy, double dz) {
            if (level == null || at == null) {
                return;
            }
            Vec3 p = ClientField.toWorld(at[0], at[1], up + groundY(layer, at[0], at[1], ClientField.tick(), 0));
            level.addParticle(particle, p.x, p.y, p.z, dx, dy, dz);
        }

        /** Particles that rise off the ground, up to {@code spread} high, with a little speed. */
        void ground(ParticleOptions particle, double perTick, double spread, double speed) {
            for (int i = count(perTick); i > 0; i--) {
                add(particle, point(false), random.nextDouble() * spread, (random.nextDouble() - 0.5) * speed,
                        random.nextDouble() * speed, (random.nextDouble() - 0.5) * speed);
            }
        }

        /** Particles along the outer ground, where the waves break. */
        void edge(ParticleOptions particle, double perTick, double up, double speed) {
            for (int i = count(perTick); i > 0; i--) {
                add(particle, point(true), up * random.nextDouble(), (random.nextDouble() - 0.5) * speed,
                        random.nextDouble() * speed, (random.nextDouble() - 0.5) * speed);
            }
        }

        /** Particles that drift down from up to {@code height} over the ground. */
        void air(ParticleOptions particle, double perTick, double height) {
            for (int i = count(perTick); i > 0; i--) {
                add(particle, point(false), 1 + random.nextDouble() * height, 0, -0.02, 0);
            }
        }

        /** Particles blown across the field with the wind. */
        void wind(ParticleOptions particle, double perTick) {
            Vec3 dir = ClientField.direction();
            Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(0.25 * Math.max(layer.look.wind, 0.5));
            for (int i = count(perTick); i > 0; i--) {
                add(particle, point(false), 0.2 + random.nextDouble() * 2, side.x, 0.01, side.z);
            }
        }

        /** Particles around the tops of mountain peaks. */
        void peak(ParticleOptions particle, double perTick) {
            for (int i = count(perTick); i > 0 && !layer.props.isEmpty(); i--) {
                Prop prop = layer.props.get(random.nextInt(layer.props.size()));
                if (prop.kind().shape() == Shape.PEAK && prop.tier() == 2) {
                    add(particle, new double[]{prop.x(), prop.z()}, prop.height() * 3 + 0.3,
                            (random.nextDouble() - 0.5) * 0.05, 0.02, (random.nextDouble() - 0.5) * 0.05);
                }
            }
        }
    }

    /** Height of a layer's ground over the mat at a field-local point: waves rising toward the outer edge. */
    private static double groundY(Layer layer, double x, double z, long now, float partial) {
        if (layer.look.waves <= 0) {
            return GROUND_Y;
        }
        double out = Math.min(1, outside(x, z) / 2.5);
        if (out <= 0) {
            return GROUND_Y;
        }
        double t = (now + partial) / 9.0;
        double wave = 0.55 + 0.45 * Math.sin(x * 0.7 + t) * Math.cos(z * 0.6 - t * 0.7);
        return GROUND_Y + out * layer.look.waves * wave;
    }

    // ------------------------------------------------------------------ rendering

    /** Draws the new ground, its props and glow; called by {@link FieldRenderer} before the mat. */
    static void render(FieldRenderer.Draw draw, long now, float partial) {
        if (layers.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = draw.buffers();
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer blocks = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        for (Layer layer : layers) {
            drawGround(draw, blocks, atlas, layer, now, partial);
        }
        for (Layer layer : layers) {
            drawProps(draw, blocks, atlas, layer, now, partial);
        }
        buffers.endBatch();
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        for (Layer layer : layers) {
            drawFront(draw, glow, layer, partial);
            drawWind(draw, glow, layer, now, partial);
        }
        buffers.endBatch();
    }

    private static void drawGround(FieldRenderer.Draw draw, VertexConsumer vc,
                                   java.util.function.Function<ResourceLocation, TextureAtlasSprite> atlas,
                                   Layer layer, long now, float partial) {
        Look look = layer.look;
        int total = look.ground.stream().mapToInt(Tile::weight).sum();
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[look.ground.size()];
        for (int i = 0; i < sprites.length; i++) {
            sprites[i] = atlas.apply(ResourceLocation.withDefaultNamespace(look.ground.get(i).sprite()));
        }
        double hx = HALF_WIDTH + 0.3 + MARGIN;
        double hz = halfLength() + 0.3 + MARGIN;
        int nx = (int) Math.ceil(hx);
        int nz = (int) Math.ceil(hz);
        for (int ix = -nx; ix < nx; ix++) {
            for (int iz = -nz; iz < nz; iz++) {
                double[][] corners = {{ix, iz}, {ix + 1, iz}, {ix + 1, iz + 1}, {ix, iz + 1}};
                int[] alphas = new int[4];
                boolean any = false;
                for (int c = 0; c < 4; c++) {
                    double x = corners[c][0];
                    double z = corners[c][1];
                    double fade = 1 - Mth.clamp(outside(x, z) / MARGIN, 0, 1);
                    double a = look.alpha * cover(layer, x, z, partial) * fade * fade;
                    alphas[c] = (int) a;
                    any |= alphas[c] > 26;
                }
                if (!any) {
                    continue;
                }
                int pick = Math.floorMod((ix * 73856093) ^ (iz * 19349663) ^ (int) layer.seed, total);
                int tile = 0;
                while (pick >= look.ground.get(tile).weight()) {
                    pick -= look.ground.get(tile).weight();
                    tile++;
                }
                TextureAtlasSprite sprite = sprites[tile];
                int tint = look.ground.get(tile).tint();
                float[][] uvs = {{sprite.getU0(), sprite.getV1()}, {sprite.getU1(), sprite.getV1()},
                        {sprite.getU1(), sprite.getV0()}, {sprite.getU0(), sprite.getV0()}};
                for (int c = 0; c < 4; c++) {
                    double x = corners[c][0];
                    double z = corners[c][1];
                    Vec3 p = ClientField.toWorld(x, z, groundY(layer, x, z, now, partial));
                    vertex(draw, vc, p, uvs[c][0], uvs[c][1], tint, alphas[c], 0, 1, 0);
                }
            }
        }
    }

    private static void drawProps(FieldRenderer.Draw draw, VertexConsumer vc,
                                  java.util.function.Function<ResourceLocation, TextureAtlasSprite> atlas,
                                  Layer layer, long now, float partial) {
        double ceiling = ClientField.ceiling() > 0 ? ClientField.ceiling() - 0.3 : Double.MAX_VALUE;
        for (Prop prop : layer.props) {
            double cover = cover(layer, prop.x(), prop.z(), partial);
            // Props sprout as the ground reaches them and sink back as it leaves.
            double grow = ease((float) Mth.clamp(cover * 1.4 - 0.2, 0, 1));
            if (grow <= 0.02) {
                continue;
            }
            double base = groundY(layer, prop.x(), prop.z(), now, partial) - 0.02;
            double height = Math.min(prop.height() * grow, ceiling - base);
            if (height <= 0.05) {
                continue;
            }
            PropKind kind = prop.kind();
            int alpha = (int) (255 * Math.min(1, grow * 1.5));
            if (kind.shape() == Shape.CROSS) {
                cross(draw, vc, atlas.apply(ResourceLocation.withDefaultNamespace(kind.sprite())), prop, base,
                        height, kind.tint(), alpha, layer.look.wind, now, partial);
            } else {
                TextureAtlasSprite side = atlas.apply(ResourceLocation.withDefaultNamespace(kind.sprite()));
                TextureAtlasSprite top = atlas.apply(ResourceLocation.withDefaultNamespace(
                        kind.shape() == Shape.PEAK && prop.tier() < 2 ? kind.sprite() : kind.top()));
                box(draw, vc, side, top, prop.x(), prop.z(), base, prop.width() * Math.min(1, grow * 1.2), height,
                        kind.tint(), alpha);
            }
        }
    }

    /** A plant: two crossed quads, their tops bending with the wind. */
    private static void cross(FieldRenderer.Draw draw, VertexConsumer vc, TextureAtlasSprite sprite, Prop prop,
                              double base, double height, int tint, int alpha, double wind, long now, float partial) {
        double half = height / 2;
        double t = (now + partial) / 10.0;
        double sway = prop.kind().sway() * (Math.sin(t * (1 + wind * 0.6) + prop.phase()) * 0.5
                + wind * 0.35 * (1 + Math.sin(t * 0.4 + prop.x() * 0.2)));
        double[][] axes = {{0.707, 0.707}, {0.707, -0.707}};
        for (double[] axis : axes) {
            double ax = axis[0] * half;
            double az = axis[1] * half;
            Vec3 b0 = ClientField.toWorld(prop.x() - ax, prop.z() - az, base);
            Vec3 b1 = ClientField.toWorld(prop.x() + ax, prop.z() + az, base);
            Vec3 t1 = ClientField.toWorld(prop.x() + ax + sway * height, prop.z() + az, base + height);
            Vec3 t0 = ClientField.toWorld(prop.x() - ax + sway * height, prop.z() - az, base + height);
            Vec3[] quad = {b0, b1, t1, t0};
            float[][] uvs = {{sprite.getU0(), sprite.getV1()}, {sprite.getU1(), sprite.getV1()},
                    {sprite.getU1(), sprite.getV0()}, {sprite.getU0(), sprite.getV0()}};
            for (int c = 0; c < 4; c++) {
                vertex(draw, vc, quad[c], uvs[c][0], uvs[c][1], tint, alpha, 0, 1, 0);
            }
            for (int c = 3; c >= 0; c--) {
                vertex(draw, vc, quad[c], uvs[c][0], uvs[c][1], tint, alpha, 0, 1, 0);
            }
        }
    }

    /** A block-textured box standing on the ground: a rock, a pillar, a tier of a peak. */
    private static void box(FieldRenderer.Draw draw, VertexConsumer vc, TextureAtlasSprite side,
                            TextureAtlasSprite top, double x, double z, double base, double width, double height,
                            int tint, int alpha) {
        double w = width / 2;
        double[][] ring = {{-w, -w}, {w, -w}, {w, w}, {-w, w}};
        float vTop = side.getV0();
        for (int i = 0; i < 4; i++) {
            double[] a = ring[i];
            double[] b = ring[(i + 1) % 4];
            Vec3 a0 = ClientField.toWorld(x + a[0], z + a[1], base);
            Vec3 b0 = ClientField.toWorld(x + b[0], z + b[1], base);
            Vec3 b1 = ClientField.toWorld(x + b[0], z + b[1], base + height);
            Vec3 a1 = ClientField.toWorld(x + a[0], z + a[1], base + height);
            // Sides darker than the top, as blocks are lit.
            int shade = shade(tint, i % 2 == 0 ? 0.8 : 0.6);
            vertex(draw, vc, a0, side.getU0(), side.getV1(), shade, alpha, 0, 0, 1);
            vertex(draw, vc, b0, side.getU1(), side.getV1(), shade, alpha, 0, 0, 1);
            vertex(draw, vc, b1, side.getU1(), vTop, shade, alpha, 0, 0, 1);
            vertex(draw, vc, a1, side.getU0(), vTop, shade, alpha, 0, 0, 1);
            vertex(draw, vc, a1, side.getU0(), vTop, shade, alpha, 0, 0, 1);
            vertex(draw, vc, b1, side.getU1(), vTop, shade, alpha, 0, 0, 1);
            vertex(draw, vc, b0, side.getU1(), side.getV1(), shade, alpha, 0, 0, 1);
            vertex(draw, vc, a0, side.getU0(), side.getV1(), shade, alpha, 0, 0, 1);
        }
        float[][] uvs = {{top.getU0(), top.getV1()}, {top.getU1(), top.getV1()}, {top.getU1(), top.getV0()},
                {top.getU0(), top.getV0()}};
        for (int i = 0; i < 4; i++) {
            Vec3 p = ClientField.toWorld(x + ring[i][0], z + ring[i][1], base + height);
            vertex(draw, vc, p, uvs[i][0], uvs[i][1], tint, alpha, 0, 1, 0);
        }
    }

    private static int shade(int rgb, double k) {
        int r = (int) (((rgb >> 16) & 0xFF) * k);
        int g = (int) (((rgb >> 8) & 0xFF) * k);
        int b = (int) ((rgb & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    /** The glowing edge of the ground as it spreads out of (or draws back into) the Field Zone. */
    private static void drawFront(FieldRenderer.Draw draw, VertexConsumer vc, Layer layer, float partial) {
        float p = layer.progress(partial);
        if (p <= 0 || p >= 1) {
            return;
        }
        Slot o = layer.origin();
        double reach = ease(p) * REACH - 1.2;
        if (reach <= 0) {
            return;
        }
        int alpha = (int) (170 * Math.sin(Math.PI * p));
        int color = (alpha << 24) | layer.look.glow;
        double hx = HALF_WIDTH + 0.3 + MARGIN * 0.8;
        double hz = halfLength() + 0.3 + MARGIN * 0.8;
        int segments = 72;
        for (int i = 0; i < segments; i++) {
            double a0 = Mth.TWO_PI * i / segments;
            double a1 = Mth.TWO_PI * (i + 1) / segments;
            double x0 = o.x() + Math.cos(a0) * reach;
            double z0 = o.z() + Math.sin(a0) * reach;
            double x1 = o.x() + Math.cos(a1) * reach;
            double z1 = o.z() + Math.sin(a1) * reach;
            if (Math.abs(x0) > hx || Math.abs(z0) > hz || Math.abs(x1) > hx || Math.abs(z1) > hz) {
                continue;
            }
            double y0 = groundY(layer, x0, z0, ClientField.tick(), partial);
            double y1 = groundY(layer, x1, z1, ClientField.tick(), partial);
            Vec3 b0 = ClientField.toWorld(x0, z0, y0);
            Vec3 b1 = ClientField.toWorld(x1, z1, y1);
            Vec3 t1 = ClientField.toWorld(x1, z1, y1 + 0.55);
            Vec3 t0 = ClientField.toWorld(x0, z0, y0 + 0.55);
            draw.colorQuad(vc, b0, b1, t1, t0, color);
            draw.colorQuad(vc, t0, t1, b1, b0, color);
        }
    }

    /** Streaks of wind blowing across the field. */
    private static void drawWind(FieldRenderer.Draw draw, VertexConsumer vc, Layer layer, long now, float partial) {
        double wind = layer.look.wind;
        if (wind <= 0) {
            return;
        }
        float p = layer.progress(partial);
        double hx = HALF_WIDTH + 0.3 + MARGIN * 0.8;
        double hz = halfLength() + 0.3 + MARGIN * 0.6;
        int streaks = (int) (10 + 10 * wind);
        double span = hx * 2 + 6;
        for (int i = 0; i < streaks; i++) {
            long h = (layer.seed + i * 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
            double lane = ((h >>> 11) % 1000) / 1000.0;
            double phase = ((h >>> 27) % 1000) / 1000.0;
            double height = 0.3 + ((h >>> 41) % 1000) / 1000.0 * 2.2;
            double length = 1.2 + wind * 1.2;
            double speed = 0.18 * (0.6 + wind);
            double x = -hx - 3 + ((now + partial) * speed + phase * span) % span;
            double z = (lane * 2 - 1) * hz;
            double inside = Math.sin(Math.PI * Mth.clamp((x + hx + 3) / span, 0, 1));
            double c = cover(layer, Mth.clamp(x, -hx, hx), z, partial);
            int alpha = (int) (90 * inside * c * p);
            if (alpha <= 4) {
                continue;
            }
            double bend = Math.sin((now + partial) / 7.0 + i) * 0.15;
            Vec3 a = ClientField.toWorld(x, z, height);
            Vec3 b = ClientField.toWorld(x + length, z + bend, height + bend * 0.5);
            Vec3 up = new Vec3(0, 0.035 * ClientField.size(), 0);
            int color = (alpha << 24) | 0xF4FAFF;
            draw.colorQuad(vc, a.subtract(up), b.subtract(up), b.add(up), a.add(up), color);
            draw.colorQuad(vc, a.add(up), b.add(up), b.subtract(up), a.subtract(up), color);
        }
    }

    private static void vertex(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 p, float u, float v, int rgb,
                               int alpha, float nx, float ny, float nz) {
        Vec3 cam = draw.cam();
        vc.addVertex(draw.pose(), (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                .setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Mth.clamp(alpha, 0, 255))
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(draw.pose(), nx, ny, nz);
    }
}
