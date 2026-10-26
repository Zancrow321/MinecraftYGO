package io.github.zancrow321.jadm.client.field;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.zancrow321.jadm.client.field.FieldLayout.*;
import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * Field Spells transform the ground of the duel field while they are face-up: Umi floods it with rolling water,
 * Yami turns it into creeping shadow, Sogen grows grass that bends in the wind, and so on. The new ground spreads out
 * from the Field Zone when the card lands and draws back into it when the card leaves. Each half shows its owner's
 * Field Spell; a single one covers the whole field. Field Spells without a look of their own tint the field in the
 * colour of the attribute their text is about.
 *
 * <p>Only the field and the ground around it change, never sky or fog. Each look is a JSON file in a resource pack,
 * {@code assets/<namespace>/jadm/field_spells/<name>.json}, naming its cards, ground, props, particles and sounds;
 * the mod's own looks are such files too, so packs can add looks and replace the built-in ones. The format is in the
 * Duelist's Handbook and in {@code docs/field-spell-looks.md}.
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

    /** Where the looks live, in every namespace of every resource pack: {@code assets/<ns>/jadm/field_spells/*.json}. */
    static final String FOLDER = "jadm/field_spells";

    /** One block texture of a ground, how often it is picked, and its tint. */
    private record Tile(ResourceLocation sprite, int weight, int tint) {
    }

    private enum Shape { CROSS, BOX, PEAK }

    /** What grows out of the ground around the mat. */
    private record PropKind(Shape shape, ResourceLocation sprite, ResourceLocation top, int tint, int count,
                            double minHeight, double maxHeight, double width, double sway) {
    }

    /** Where a look's particles appear (see {@link Spawn}). */
    private enum Where { GROUND, EDGE, AIR, WIND, PEAK }

    /** Particles a look sends up every tick, scaled by how far its ground has spread. */
    private record Particles(ParticleOptions particle, Where where, double perTick, double spread, double speed) {
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
        final List<Particles> particles = new ArrayList<>();
        List<SoundEvent> reveal = List.of(SoundEvents.AMETHYST_BLOCK_RESONATE);
        float pitch = 1;
        SoundEvent ambient;

        Look(String name) {
            this.name = name;
        }

        void weather(Spawn s) {
            for (Particles p : particles) {
                switch (p.where()) {
                    case GROUND -> s.ground(p.particle(), p.perTick(), p.spread(), p.speed());
                    case EDGE -> s.edge(p.particle(), p.perTick(), p.spread(), p.speed());
                    case AIR -> s.air(p.particle(), p.perTick(), p.spread());
                    case WIND -> s.wind(p.particle(), p.perTick());
                    case PEAK -> s.peak(p.particle(), p.perTick());
                }
            }
        }
    }

    /** Looks by card passcode, from the resource packs; replaced as a whole on every resource reload. */
    private static volatile Map<Integer, Look> LOOKS = Map.of();

    /**
     * Reads every look from the resource packs. The mod's own files come first, so a file of the same name higher in
     * the pack list replaces one of them, and a look in another namespace that names the same card wins over the
     * mod's.
     */
    static void reload(ResourceManager manager) {
        Map<Integer, Look> looks = new HashMap<>();
        var files = new java.util.TreeMap<ResourceLocation, Resource>((a, b) -> {
            boolean ownA = a.getNamespace().equals(Jadm.MOD_ID);
            boolean ownB = b.getNamespace().equals(Jadm.MOD_ID);
            return ownA != ownB ? (ownA ? -1 : 1) : a.compareTo(b);
        });
        files.putAll(manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")));
        for (var file : files.entrySet()) {
            String path = file.getKey().getPath();
            String name = file.getKey().getNamespace() + ":" + path.substring(FOLDER.length() + 1, path.length() - 5);
            try (Reader reader = file.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Look look = parse(name, json);
                for (JsonElement card : json.getAsJsonArray("cards")) {
                    looks.put(card.getAsInt(), look);
                }
            } catch (Exception e) {
                Jadm.LOGGER.warn("Skipping Field Spell look {}: {}", file.getKey(), e.toString());
            }
        }
        LOOKS = Map.copyOf(looks);
        TINTS.clear();
        Jadm.LOGGER.info("Loaded {} Field Spell looks for {} cards", files.size(), looks.size());
    }

    private static Look parse(String name, JsonObject json) {
        Look look = new Look(name);
        if (!json.has("cards") || json.getAsJsonArray("cards").isEmpty()) {
            throw new IllegalArgumentException("no \"cards\"");
        }
        for (JsonElement e : json.getAsJsonArray("ground")) {
            JsonObject tile = e.getAsJsonObject();
            look.ground.add(new Tile(ResourceLocation.parse(GsonHelper.getAsString(tile, "sprite")),
                    Math.max(1, GsonHelper.getAsInt(tile, "weight", 1)), color(tile, "tint", 0xFFFFFF)));
        }
        if (look.ground.isEmpty()) {
            throw new IllegalArgumentException("no \"ground\"");
        }
        look.alpha = (int) (255 * Mth.clamp(GsonHelper.getAsFloat(json, "opacity", 0.88f), 0, 1));
        look.glow = color(json, "glow", 0x40C8FF);
        look.waves = GsonHelper.getAsDouble(json, "waves", 0);
        look.wind = GsonHelper.getAsDouble(json, "wind", 0);
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "props", new JsonArray())) {
            JsonObject prop = e.getAsJsonObject();
            Shape shape = Shape.valueOf(GsonHelper.getAsString(prop, "shape", "cross").toUpperCase(Locale.ROOT));
            ResourceLocation sprite = ResourceLocation.parse(GsonHelper.getAsString(prop, "sprite"));
            ResourceLocation top = prop.has("top") ? ResourceLocation.parse(GsonHelper.getAsString(prop, "top")) : sprite;
            double[] height = range(prop, "height", 1);
            look.props.add(new PropKind(shape, sprite, top, color(prop, "tint", 0xFFFFFF),
                    Mth.clamp(GsonHelper.getAsInt(prop, "count", 8), 0, 400), height[0], height[1],
                    GsonHelper.getAsDouble(prop, "width", 0.4), GsonHelper.getAsDouble(prop, "sway", 0)));
        }
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "particles", new JsonArray())) {
            JsonObject p = e.getAsJsonObject();
            look.particles.add(new Particles(particle(p),
                    Where.valueOf(GsonHelper.getAsString(p, "where", "ground").toUpperCase(Locale.ROOT)),
                    Mth.clamp(GsonHelper.getAsDouble(p, "perTick", 2), 0, 40), GsonHelper.getAsDouble(p, "spread", 0.3),
                    GsonHelper.getAsDouble(p, "speed", 0)));
        }
        JsonObject sounds = GsonHelper.getAsJsonObject(json, "sounds", new JsonObject());
        look.pitch = GsonHelper.getAsFloat(sounds, "pitch", 1);
        look.ambient = sounds.has("ambient") ? sound(GsonHelper.getAsString(sounds, "ambient")) : null;
        if (sounds.has("reveal")) {
            List<SoundEvent> reveal = new ArrayList<>();
            sounds.getAsJsonArray("reveal").forEach(s -> reveal.add(sound(s.getAsString())));
            look.reveal = reveal;
        }
        return look;
    }

    /** A colour written as "#RRGGBB" (or a plain number). */
    private static int color(JsonObject json, String key, int fallback) {
        if (!json.has(key)) {
            return fallback;
        }
        JsonElement e = json.get(key);
        if (e.getAsJsonPrimitive().isNumber()) {
            return e.getAsInt() & 0xFFFFFF;
        }
        String s = e.getAsString().trim();
        return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF;
    }

    /** A height written as one number or as [lowest, highest]. */
    private static double[] range(JsonObject json, String key, double fallback) {
        if (!json.has(key)) {
            return new double[]{fallback, fallback};
        }
        JsonElement e = json.get(key);
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            return new double[]{a.get(0).getAsDouble(), a.get(a.size() > 1 ? 1 : 0).getAsDouble()};
        }
        return new double[]{e.getAsDouble(), e.getAsDouble()};
    }

    /**
     * A particle by id. Particles that need more than their id take it from the entry: {@code block} for block and
     * falling-dust particles, {@code color} and {@code size} for dust.
     */
    @SuppressWarnings("unchecked")
    private static ParticleOptions particle(JsonObject json) {
        ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(json, "type"));
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown particle " + id));
        if (type instanceof SimpleParticleType simple) {
            return simple;
        }
        if (type == ParticleTypes.DUST) {
            int rgb = color(json, "color", 0xFFFFFF);
            return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f,
                    (rgb & 0xFF) / 255f), GsonHelper.getAsFloat(json, "size", 1));
        }
        if (type == ParticleTypes.BLOCK || type == ParticleTypes.FALLING_DUST || type == ParticleTypes.DUST_PILLAR) {
            ResourceLocation block = ResourceLocation.parse(GsonHelper.getAsString(json, "block"));
            Block b = BuiltInRegistries.BLOCK.getOptional(block)
                    .orElseThrow(() -> new IllegalArgumentException("unknown block " + block));
            return new BlockParticleOption((ParticleType<BlockParticleOption>) type, b.defaultBlockState());
        }
        throw new IllegalArgumentException("particle " + id + " needs options this file can't give");
    }

    /** A sound by id: a vanilla or mod sound, or one a resource pack adds in its own sounds.json. */
    private static SoundEvent sound(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        return BuiltInRegistries.SOUND_EVENT.getOptional(location)
                .orElseGet(() -> SoundEvent.createVariableRangeEvent(location));
    }

    /** Loads the looks on every resource reload (start-up, F3+T, changing resource packs). */
    @EventBusSubscriber(modid = Jadm.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Loader {
        private Loader() {
        }

        @SubscribeEvent
        public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) FieldSpellArena::reload);
        }
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
        Look look = new Look("tint");
        look.ground.add(new Tile(ResourceLocation.withDefaultNamespace("block/white_concrete_powder"), 1, rgb));
        look.alpha = 0x90;
        look.glow = rgb;
        look.particles.add(new Particles(new DustParticleOptions(color, 1.5f), Where.GROUND, 8, 0.2, 0.25));
        look.particles.add(new Particles(ParticleTypes.END_ROD, Where.GROUND, 1, 0.3, 0.05));
        return look;
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
                layer.look.weather(new Spawn(layer));
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
            sprites[i] = atlas.apply(look.ground.get(i).sprite());
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
                cross(draw, vc, atlas.apply(kind.sprite()), prop, base,
                        height, kind.tint(), alpha, layer.look.wind, now, partial);
            } else {
                TextureAtlasSprite side = atlas.apply(kind.sprite());
                TextureAtlasSprite top = atlas.apply(
                        kind.shape() == Shape.PEAK && prop.tier() < 2 ? kind.sprite() : kind.top());
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
