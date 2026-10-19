package io.github.zancrow321.jadm.client.field;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.ClientDuel;
import io.github.zancrow321.jadm.client.JadmClientConfig;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.engine.duel.Board;
import io.github.zancrow321.jadm.engine.duel.FieldEvent;
import io.github.zancrow321.jadm.engine.duel.SummonStyle;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.zancrow321.jadm.client.field.FieldLayout.*;
import static io.github.zancrow321.jadm.client.field.SignatureEffects.*;
import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * Each kind of summon gets its own show, for every card: Fusion = a swirl of colours the materials melt into, Synchro =
 * green rings the stars fly through, Xyz = a galaxy the materials dive into, Ritual = a burning summoning circle,
 * Link = the Link Arrows lighting up one after another, Pendulum = a pendulum swinging over the scales. The monster
 * appears at {@link #REVEAL} of the way through.
 *
 * <p>The materials' own leave events (tagged by the server) feed the show: where they left from is remembered, and
 * the show pulls them in from there. Materials from the hand have no place on the field, so they come in from the
 * summoner's end.
 */
final class SummonShow {
    /** How far through the show the monster starts to appear. */
    static final float REVEAL = 0.55f;
    private static final int LONG_TICKS = 50;
    private static final int SHORT_TICKS = 30;
    /** How long a material's place is kept for the summon it went to, in ticks. */
    private static final int MATERIAL_TICKS = 400;

    private static final int FUSION_MAGENTA = 0xFF40C8;
    private static final int FUSION_CYAN = 0x40D0FF;
    private static final int FUSION_GOLD = 0xFFB020;
    private static final int SYNCHRO_GREEN = 0x50FF70;
    private static final int XYZ_BLUE = 0x3050FF;
    private static final int XYZ_VIOLET = 0xA040FF;
    private static final int RITUAL_RED = 0xFF4818;
    private static final int RITUAL_GOLD = 0xFFC040;
    private static final int LINK_CYAN = 0x30E0FF;
    private static final int LINK_RED = 0xFF3030;
    private static final int SCALE_BLUE = 0x4080FF;
    private static final int SCALE_RED = 0xFF4040;

    /** Link Arrow bits in the order they light up, and where each points (owner's right, up). */
    private static final int[] MARKERS = {0x1, 0x2, 0x4, 0x20, 0x100, 0x80, 0x40, 0x8};
    private static final int[][] MARKER_DIRS = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};

    /** A material that just left the field: where from, for the summon of {@code style} it went to. */
    private record Material(int player, int style, Vec3 at, int code, long tick) {
    }

    private static final List<Material> recent = new ArrayList<>();
    /** The materials each playing show pulls in. */
    private static final Map<FieldAnimation, List<Material>> pulled = new HashMap<>();

    private SummonShow() {
    }

    static void reset() {
        recent.clear();
        pulled.clear();
    }

    /** Whether {@code e} is a summon that plays a show, or a later monster of a Pendulum Summon. */
    static boolean plays(FieldEvent e) {
        return e.kind() == FieldEvent.Kind.SUMMON && e.amount() >= SummonStyle.FUSION
                && e.amount() <= SummonStyle.PENDULUM_MORE;
    }

    private static boolean material(FieldEvent e) {
        return e.kind() == FieldEvent.Kind.LEAVE && e.amount() >= SummonStyle.FUSION
                && e.amount() <= SummonStyle.LINK;
    }

    /** How long a show plays: long by default, short with {@code longSummonShows} off. */
    static int duration() {
        return JadmClientConfig.LONG_SUMMON_SHOWS.get() ? LONG_TICKS : SHORT_TICKS;
    }

    /**
     * When a later monster of a Pendulum Summon plays: with the first one's show while that is still on, so they all
     * come down together. {@code -1} plays it in the queue as usual (as a show of its own).
     */
    static long joins(FieldEvent e, long tick) {
        if (e.kind() != FieldEvent.Kind.SUMMON || e.amount() != SummonStyle.PENDULUM_MORE) {
            return -1;
        }
        for (FieldAnimation a : ClientField.animations()) {
            FieldEvent other = a.event();
            if (other.kind() == FieldEvent.Kind.SUMMON && other.player() == e.player()
                    && (other.amount() == SummonStyle.PENDULUM || other.amount() == SummonStyle.PENDULUM_MORE)
                    && !a.finished(tick) && a.start() + a.duration() * REVEAL + 4 > tick) {
                return a.start();
            }
        }
        return -1;
    }

    /** The style a show plays as: a Pendulum monster that came too late for the first one's show swings its own. */
    private static int style(FieldAnimation a) {
        int style = a.event().amount();
        if (style == SummonStyle.PENDULUM_MORE) {
            for (FieldAnimation other : ClientField.animations()) {
                if (other != a && other.start() == a.start() && other.event().kind() == FieldEvent.Kind.SUMMON
                        && other.event().player() == a.event().player()
                        && other.event().amount() == SummonStyle.PENDULUM) {
                    return SummonStyle.PENDULUM_MORE;
                }
            }
            return SummonStyle.PENDULUM;
        }
        return style;
    }

    /**
     * How far {@code summon} has come for the monster it brings, 0 to 1 like {@link FieldAnimation#progress}: the
     * monster waits for {@link #REVEAL} in a show, so its usual grow-in plays after that.
     */
    static float appearance(FieldAnimation summon, long now, float partial) {
        float p = summon.progress(now, partial);
        return plays(summon.event()) ? Mth.clamp((p - REVEAL) / (1 - REVEAL) * 1.6f, 0, 1) : p;
    }

    // ------------------------------------------------------------------ sounds and particles

    /** The first tick of {@code a}. @return whether the show took it over from the usual summon or leave effect */
    static boolean start(FieldAnimation a) {
        FieldEvent e = a.event();
        if (material(e)) {
            Vec3 at = zone(e.from(), 1.0);
            if (at != null) {
                recent.removeIf(m -> ClientField.tick() - m.tick() > MATERIAL_TICKS);
                recent.add(new Material(e.player(), e.amount(), at, e.code(), ClientField.tick()));
                burst(ParticleTypes.END_ROD, at, 20, 0.5, 0.2);
                burst(materialParticle(e.amount()), at, 25, 0.6, 0.3);
                sound(SoundEvents.AMETHYST_BLOCK_CHIME, at, 1.6f);
            }
            return true;
        }
        if (!plays(e)) {
            return false;
        }
        List<Material> mine = new ArrayList<>();
        int style = style(a);
        recent.removeIf(m -> {
            boolean match = m.player() == e.player() && m.style() == style;
            if (match) {
                mine.add(m);
            }
            return match || ClientField.tick() - m.tick() > MATERIAL_TICKS;
        });
        pulled.keySet().removeIf(x -> !ClientField.animations().contains(x));
        pulled.put(a, mine);
        Vec3 at = zone(e.from(), 0.5);
        if (at == null) {
            return true;
        }
        switch (style) {
            case SummonStyle.FUSION -> {
                sound(SoundEvents.EVOKER_PREPARE_SUMMON, at, 1.3f);
                sound(SoundEvents.BEACON_ACTIVATE, at, 0.7f);
            }
            case SummonStyle.SYNCHRO -> sound(SoundEvents.AMETHYST_BLOCK_RESONATE, at, 1.4f);
            case SummonStyle.XYZ -> {
                sound(SoundEvents.ENDERMAN_TELEPORT, at, 0.5f);
                sound(SoundEvents.AMETHYST_BLOCK_RESONATE, at, 0.6f);
            }
            case SummonStyle.RITUAL -> {
                sound(SoundEvents.FIRECHARGE_USE, at, 0.7f);
                sound(SoundEvents.EVOKER_PREPARE_SUMMON, at, 0.8f);
            }
            case SummonStyle.LINK -> sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, at, 1.2f);
            case SummonStyle.PENDULUM -> sound(SoundEvents.BELL_BLOCK, at, 1.3f);
            default -> {
            }
        }
        return true;
    }

    /** Called every tick while {@code a} plays. */
    static void tick(FieldAnimation a, int elapsed) {
        FieldEvent e = a.event();
        if (!plays(e)) {
            return;
        }
        Vec3 c = zone(e.from(), 0);
        if (c == null) {
            return;
        }
        int style = style(a);
        double k = scale();
        float t = fraction(a, elapsed);
        boolean reveal = at(a, elapsed, REVEAL);
        switch (style) {
            case SummonStyle.FUSION -> {
                Vec3 v = fusionCenter(c);
                if (t < REVEAL) {
                    burst(ParticleTypes.WITCH, v, 3, 1.2 * k, 0.1);
                    burst(ParticleTypes.PORTAL, v, 6, 1.6 * k, 0.6);
                }
                if (at(a, elapsed, 0.45f)) {
                    sound(SoundEvents.ILLUSIONER_CAST_SPELL, v, 1.2f);
                }
                if (reveal) {
                    sound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, c, 0.9f);
                    sound(SoundEvents.TOTEM_USE, c, 1.6f);
                    burst(ParticleTypes.FIREWORK, c.add(0, k, 0), 50, 0.9 * k, 0.4);
                    burst(ParticleTypes.END_ROD, c.add(0, k, 0), 30, 0.8 * k, 0.3);
                }
            }
            case SummonStyle.SYNCHRO -> {
                int stars = stars(e.code());
                for (int j = 0; j < stars; j++) {
                    if (at(a, elapsed, starStart(j, stars))) {
                        sound(SoundEvents.NOTE_BLOCK_CHIME.value(), c.add(0, 1.5 * k, 0), 0.9f + 0.9f * j / stars);
                    }
                }
                if (t < REVEAL && elapsed % 2 == 0) {
                    burst(ParticleTypes.HAPPY_VILLAGER, c.add(0, 1.5 * k, 0), 3, 1.0 * k, 0.2);
                }
                if (reveal) {
                    sound(SoundEvents.BEACON_ACTIVATE, c, 1.6f);
                    sound(SoundEvents.FIREWORK_ROCKET_TWINKLE, c, 1.2f);
                    burst(ParticleTypes.END_ROD, c.add(0, 2 * k, 0), 50, 0.6 * k, 0.5);
                    burst(ParticleTypes.HAPPY_VILLAGER, c.add(0, k, 0), 30, 1.2 * k, 0.3);
                }
            }
            case SummonStyle.XYZ -> {
                List<Material> mats = materials(a);
                for (int i = 0; i < Math.max(2, mats.size()); i++) {
                    if (at(a, elapsed, 0.2f + 0.06f * i)) {
                        sound(SoundEvents.ENDER_EYE_LAUNCH, c.add(0, 2 * k, 0), 0.8f + 0.2f * i);
                    }
                }
                if (t < REVEAL) {
                    burst(ParticleTypes.REVERSE_PORTAL, c.add(0, 0.2 * k, 0), 5, 2.0 * k, 0.05);
                }
                if (reveal) {
                    sound(SoundEvents.GENERIC_EXPLODE.value(), c, 1.5f);
                    sound(SoundEvents.BEACON_ACTIVATE, c, 1.1f);
                    burst(ParticleTypes.END_ROD, c.add(0, 0.5 * k, 0), 60, 0.7 * k, 0.8);
                    burst(ParticleTypes.PORTAL, c.add(0, 0.5 * k, 0), 60, 1.5 * k, 1.0);
                }
            }
            case SummonStyle.RITUAL -> {
                double r = 2.3 * k;
                if (t < 0.9f) {
                    for (int i = 0; i < 3; i++) {
                        double angle = (elapsed * 0.7 + i * 2.1);
                        Vec3 rim = c.add(Math.cos(angle) * r, 0.1 * k, Math.sin(angle) * r);
                        burst(ParticleTypes.FLAME, rim, 2, 0.1 * k, 0.08);
                    }
                    if (elapsed % 3 == 0) {
                        burst(ParticleTypes.SOUL_FIRE_FLAME, c.add(0, 0.1 * k, 0), 2, r * 0.6, 0.05);
                    }
                }
                if (at(a, elapsed, 0.25f)) {
                    sound(SoundEvents.BLAZE_BURN, c, 0.7f);
                }
                if (reveal) {
                    sound(SoundEvents.BLAZE_SHOOT, c, 0.6f);
                    sound(SoundEvents.EVOKER_CAST_SPELL, c, 0.9f);
                    burst(ParticleTypes.FLAME, c.add(0, 0.5 * k, 0), 70, 0.7 * k, 0.6);
                    burst(ParticleTypes.LAVA, c.add(0, 0.5 * k, 0), 12, 0.6 * k, 0.3);
                }
            }
            case SummonStyle.LINK -> {
                int[] lit = litMarkers(e.code());
                for (int j = 0; j < lit.length; j++) {
                    if (at(a, elapsed, markerStart(j, lit.length))) {
                        Vec3 portal = linkCenter(c);
                        sound(SoundEvents.NOTE_BLOCK_BIT.value(), portal, 1.0f + 0.12f * j);
                        sound(SoundEvents.BEACON_POWER_SELECT, portal, 1.8f);
                    }
                }
                if (reveal) {
                    sound(SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, c, 1.2f);
                    sound(SoundEvents.BEACON_ACTIVATE, c, 1.4f);
                    burst(ParticleTypes.ELECTRIC_SPARK, c.add(0, k, 0), 50, 0.8 * k, 0.5);
                }
            }
            case SummonStyle.PENDULUM -> {
                Pendulum pend = pendulum(e.player(), c);
                // A chime at each end of the swing, blue on the left and red on the right.
                for (int i = 1; i <= 4; i++) {
                    if (at(a, elapsed, swingStart() + (swingEnd() - swingStart()) * (i - 0.5f) / 4)) {
                        sound(SoundEvents.AMETHYST_BLOCK_CHIME, i % 2 == 1 ? pend.right() : pend.left(),
                                i % 2 == 1 ? 1.5f : 1.0f);
                    }
                }
                if (reveal) {
                    sound(SoundEvents.BEACON_ACTIVATE, c, 1.3f);
                    sound(SoundEvents.FIREWORK_ROCKET_TWINKLE, c, 1.0f);
                }
                if (reveal || (t > REVEAL && t < REVEAL + 0.1f)) {
                    burst(ParticleTypes.END_ROD, c.add(0, k, 0), 10, 0.5 * k, 0.4);
                }
            }
            case SummonStyle.PENDULUM_MORE -> {
                if (reveal) {
                    burst(ParticleTypes.END_ROD, c.add(0, k, 0), 30, 0.5 * k, 0.4);
                }
            }
            default -> {
            }
        }
    }

    private static ParticleOptions materialParticle(int style) {
        return switch (style) {
            case SummonStyle.FUSION -> ParticleTypes.WITCH;
            case SummonStyle.SYNCHRO -> ParticleTypes.HAPPY_VILLAGER;
            case SummonStyle.XYZ -> ParticleTypes.REVERSE_PORTAL;
            case SummonStyle.RITUAL -> ParticleTypes.FLAME;
            default -> ParticleTypes.ELECTRIC_SPARK;
        };
    }

    // ------------------------------------------------------------------ glow geometry

    /** Draws {@code a} if it is a show. @return whether it was (so the usual summon beam is left out) */
    static boolean draw(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, long now, float partial) {
        FieldEvent e = a.event();
        if (!plays(e)) {
            return false;
        }
        Vec3 c = zone(e.from(), 0.06);
        if (c == null) {
            return true;
        }
        float p = a.progress(now, partial);
        double time = now + partial;
        switch (style(a)) {
            case SummonStyle.FUSION -> fusion(draw, vc, a, c, p, time);
            case SummonStyle.SYNCHRO -> synchro(draw, vc, a, c, p, time);
            case SummonStyle.XYZ -> xyz(draw, vc, a, c, p, time);
            case SummonStyle.RITUAL -> ritual(draw, vc, a, c, p, time);
            case SummonStyle.LINK -> link(draw, vc, a, c, p, time);
            case SummonStyle.PENDULUM -> pendulumShow(draw, vc, a, c, p, time, true);
            case SummonStyle.PENDULUM_MORE -> pendulumShow(draw, vc, a, c, p, time, false);
            default -> {
            }
        }
        return true;
    }

    /** A swirl of three colours over the zone, with a funnel down to it; the materials spiral in and melt. */
    private static void fusion(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, Vec3 c, float p,
                               double time) {
        double k = scale();
        Vec3 v = fusionCenter(c);
        double open = smooth(p / 0.15) * (1 - smooth((p - 0.6) / 0.15));
        double spin = time * 0.22;
        int[] colors = {FUSION_MAGENTA, FUSION_CYAN, FUSION_GOLD};
        if (open > 0) {
            double radius = 2.3 * k * open;
            for (int arm = 0; arm < 3; arm++) {
                Vec3 previous = v;
                for (int j = 1; j <= 14; j++) {
                    double f = j / 14.0;
                    double angle = spin + arm * Math.PI * 2 / 3 + f * 3.2;
                    Vec3 point = v.add(Math.cos(angle) * radius * f, -0.15 * k * f, Math.sin(angle) * radius * f);
                    tube(draw, vc, previous, point, (0.16 - 0.08 * f) * k, glow(220 * (1 - f * 0.6) * open,
                            colors[arm]));
                    previous = point;
                }
            }
            // The funnel down to the zone, its rings taking turns in each colour.
            double drop = v.y - c.y;
            for (int i = 0; i < 6; i++) {
                double h = i / 6.0;
                ring(draw, vc, c.add(0, drop * h, 0), (0.4 + 1.6 * h) * k * open, 0.12 * k,
                        spin * (2 - h) + i, glow(150 * open, colors[i % 3]));
            }
            star(draw, vc, v, (0.5 + 0.1 * Math.sin(time * 0.9)) * k * open, glow(170 * open, 0xFFE0FF));
        }
        // The materials come in on a spiral and melt into the centre.
        List<Vec3> from = materialPoints(a, c);
        double in = smooth((p - 0.05) / 0.4);
        for (int i = 0; i < from.size(); i++) {
            if (in >= 1) {
                break;
            }
            Vec3 start = from.get(i);
            double angle = spin * 1.5 + i * Math.PI * 2 / from.size();
            double r = (1 - in) * 1.2 * k;
            Vec3 at = lerp(start, v, in).add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
            int color = colors[i % 3];
            cardOutline(draw, vc, at, angle, 1 - in * 0.6, glow(230 * (1 - in * 0.5), color));
            star(draw, vc, at, 0.25 * k, glow(160, color));
        }
        // They melt together in a white flash.
        if (p >= 0.42f && p < 0.62f) {
            float f = (p - 0.42f) / 0.2f;
            star(draw, vc, v, (0.6 + 1.6 * f) * k, glow(240 * (1 - f), 0xFFFFFF));
        }
        beamDown(draw, vc, v, c, p, 0.55f, 0.8f, 0.7 * k, 0xF0A0FF);
        shockwave(draw, vc, c, p, FUSION_MAGENTA);
    }

    /** Green rings stacked over the zone; the stars of the monster's level fly up through them, then light falls. */
    private static void synchro(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, Vec3 c, float p,
                                double time) {
        double k = scale();
        int level = level(a.event().code());
        int rings = Mth.clamp((level + 1) / 2, 2, 5);
        double top = cap(5.4);
        double bottom = 1.0;
        double fade = 1 - smooth((p - 0.62) / 0.2);
        double burst = smooth((p - REVEAL) / 0.15);
        for (int i = 0; i < rings; i++) {
            double grow = smooth((p - i * 0.04) / 0.1);
            if (grow <= 0) {
                continue;
            }
            double h = bottom + (top - bottom) * i / Math.max(1, rings - 1);
            double radius = (1.25 + 0.6 * burst) * k * grow;
            int alpha = (int) (220 * fade * grow);
            ring(draw, vc, c.add(0, h * k, 0), radius, 0.16 * k, time * 0.12 * (i % 2 == 0 ? 1 : -1),
                    glow(alpha, SYNCHRO_GREEN));
            ring(draw, vc, c.add(0, h * k + 0.01, 0), radius * 0.9, 0.06 * k, -time * 0.2, glow(alpha * 0.6, 0xC8FFD0));
        }
        // The materials come together under the rings and break into stars.
        List<Vec3> from = materialPoints(a, c);
        double in = smooth(p / 0.14);
        for (Vec3 start : from) {
            if (in < 1) {
                Vec3 at = lerp(start, c.add(0, 0.6 * k, 0), in);
                star(draw, vc, at, 0.35 * k, glow(200 * (1 - in), SYNCHRO_GREEN));
            }
        }
        int stars = stars(a.event().code());
        for (int j = 0; j < stars; j++) {
            double s = starStart(j, stars);
            double f = (p - s) / 0.2;
            if (f <= 0 || f >= 1.1) {
                continue;
            }
            double angle = j * Math.PI * 2 / stars + time * 0.05;
            double h = 0.5 + (top + 0.6 - 0.5) * Math.min(1, f);
            Vec3 at = c.add(Math.cos(angle) * 0.45 * k, h * k, Math.sin(angle) * 0.45 * k);
            double alpha = f > 1 ? (1.1 - f) / 0.1 : 1;
            star(draw, vc, at, 0.2 * k, glow(240 * alpha, 0xF4FFF0));
            star(draw, vc, at, 0.42 * k, glow(120 * alpha, SYNCHRO_GREEN));
            tube(draw, vc, at.add(0, -0.7 * k, 0), at, 0.07 * k, glow(110 * alpha, SYNCHRO_GREEN));
        }
        beamDown(draw, vc, c.add(0, (top + 0.8) * k, 0), c, p, REVEAL - 0.03f, 0.8f, 0.85 * k, 0xC0FFC8);
        shockwave(draw, vc, c, p, SYNCHRO_GREEN);
    }

    /** A spinning galaxy on the zone; the materials rise as lights and dive into it, and it bursts upward. */
    private static void xyz(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, Vec3 c, float p,
                            double time) {
        double k = scale();
        double open = smooth(p / 0.15) * (1 - smooth((p - 0.62) / 0.18));
        double spin = -time * 0.18;
        Vec3 g = c.add(0, 0.12 * k, 0);
        if (open > 0) {
            double radius = 2.7 * k * open;
            for (int arm = 0; arm < 2; arm++) {
                Vec3 previous = g;
                for (int j = 1; j <= 18; j++) {
                    double f = j / 18.0;
                    double angle = spin + arm * Math.PI + f * 4.2;
                    Vec3 point = g.add(Math.cos(angle) * radius * f, 0, Math.sin(angle) * radius * f);
                    int color = f < 0.5 ? XYZ_VIOLET : XYZ_BLUE;
                    tube(draw, vc, previous, point, (0.3 - 0.18 * f) * k, glow(200 * (1 - f * 0.7) * open, color));
                    previous = point;
                }
            }
            RandomSource random = RandomSource.create(a.event().code() * 31L + a.start());
            for (int i = 0; i < 40; i++) {
                double f = Math.sqrt(random.nextDouble());
                double angle = random.nextDouble() * Math.PI * 2 + spin * (1.4 - f);
                Vec3 dot = g.add(Math.cos(angle) * radius * f, 0.05 * k, Math.sin(angle) * radius * f);
                double twinkle = 0.5 + 0.5 * Math.sin(time * 0.6 + i);
                star(draw, vc, dot, (0.04 + 0.05 * twinkle) * k, glow(230 * open, 0xFFFFFF));
            }
            ring(draw, vc, g, radius, 0.1 * k, spin * 0.5, glow(120 * open, XYZ_VIOLET));
            star(draw, vc, g.add(0, 0.1 * k, 0), (0.5 + 0.15 * Math.sin(time)) * k * open, glow(220 * open, 0xE8D0FF));
        }
        // The materials rise as lights in their attribute's colour, then dive into the centre.
        List<Material> mats = materials(a);
        List<Vec3> from = materialPoints(a, c);
        for (int i = 0; i < from.size(); i++) {
            double s = 0.2 + 0.06 * i;
            int color = i < mats.size() ? Holograms.attributeColor(attribute(mats.get(i).code())) : 0xFFD040;
            Vec3 high = from.get(i).add(0, 2.2 * k, 0);
            Vec3 at;
            if (p < s) {
                at = lerp(from.get(i), high, smooth(p / s));
            } else if (p < s + 0.18) {
                double f = (p - s) / 0.18;
                at = lerp(high, g, f * f).add(0, Math.sin(Math.PI * f) * 0.8 * k, 0);
                tube(draw, vc, lerp(high, g, Math.max(0, f - 0.25) * Math.max(0, f - 0.25)), at, 0.12 * k,
                        glow(150, color));
            } else {
                continue;
            }
            star(draw, vc, at, 0.38 * k, glow(230, color));
            star(draw, vc, at, 0.2 * k, glow(240, 0xFFFFFF));
        }
        // The burst: a column of light out of the galaxy.
        if (p >= REVEAL - 0.08f && p < 0.85f) {
            float f = (p - (REVEAL - 0.08f)) / (0.85f - (REVEAL - 0.08f));
            double height = cap(7) * k * Math.min(1, f * 3);
            float alpha = 1 - f;
            tube(draw, vc, g, g.add(0, height, 0), (0.25 + 1.1 * f) * k, glow(170 * alpha, XYZ_VIOLET));
            tube(draw, vc, g, g.add(0, height, 0), 0.25 * k, glow(240 * alpha, 0xFFFFFF));
        }
        shockwave(draw, vc, c, p, XYZ_VIOLET);
    }

    /** A summoning circle drawn in light, flames at its points, and a pillar of fire the monster rises from. */
    private static void ritual(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, Vec3 c, float p,
                               double time) {
        double k = scale();
        double drawn = smooth(p / 0.22);
        double fade = 1 - smooth((p - 0.8) / 0.2);
        double spin = time * 0.03;
        double r = 2.3 * k;
        int red = glow(220 * fade, RITUAL_RED);
        int gold = glow(230 * fade, RITUAL_GOLD);
        arc(draw, vc, c, r, 0.14 * k, spin, drawn, red);
        arc(draw, vc, c.add(0, 0.004, 0), r * 0.86, 0.07 * k, -spin, drawn, gold);
        arc(draw, vc, c.add(0, 0.006, 0), r * 0.32, 0.08 * k, spin * 2, drawn, red);
        // The hexagram, its lines drawing themselves in.
        for (int tri = 0; tri < 2; tri++) {
            for (int i = 0; i < 3; i++) {
                double a0 = -spin + tri * Math.PI / 3 + i * Math.PI * 2 / 3;
                double a1 = -spin + tri * Math.PI / 3 + (i + 1) * Math.PI * 2 / 3;
                Vec3 p0 = c.add(Math.cos(a0) * r * 0.86, 0.01, Math.sin(a0) * r * 0.86);
                Vec3 p1 = c.add(Math.cos(a1) * r * 0.86, 0.01, Math.sin(a1) * r * 0.86);
                double f = Mth.clamp(drawn * 6 - (tri * 3 + i), 0, 1);
                if (f > 0) {
                    tube(draw, vc, p0, lerp(p0, p1, f), 0.05 * k, gold);
                }
            }
        }
        // Rune marks between the two rings.
        for (int i = 0; i < 24 && drawn >= 1; i++) {
            double angle = spin + i * Math.PI * 2 / 24;
            Vec3 in = c.add(Math.cos(angle) * r * 0.9, 0.01, Math.sin(angle) * r * 0.9);
            Vec3 out = c.add(Math.cos(angle) * r * 0.97, 0.01, Math.sin(angle) * r * 0.97);
            tube(draw, vc, in, out, 0.03 * k, glow(200 * fade, i % 3 == 0 ? 0xFFFFFF : RITUAL_GOLD));
        }
        // Flames at the six points.
        for (int i = 0; i < 6; i++) {
            double appear = smooth((p - 0.12 - i * 0.025) / 0.08);
            if (appear <= 0) {
                continue;
            }
            double angle = -spin + i * Math.PI / 3;
            Vec3 base = c.add(Math.cos(angle) * r * 0.86, 0, Math.sin(angle) * r * 0.86);
            double flicker = 0.75 + 0.25 * Math.sin(time * 1.3 + i * 1.7);
            double height = (1.0 + 0.6 * flicker) * k * appear;
            tube(draw, vc, base, base.add(0, height, 0), 0.3 * k * flicker, glow(140 * fade, RITUAL_RED));
            tube(draw, vc, base, base.add(0, height * 0.7, 0), 0.12 * k, glow(230 * fade, 0xFFE890));
            star(draw, vc, base.add(0, height * 0.35, 0), 0.32 * k * flicker, glow(160 * fade, 0xFF8020));
        }
        // Tributes burn up and fall into the centre.
        List<Vec3> from = materialPoints(a, c);
        double in = smooth((p - 0.1) / 0.3);
        for (Vec3 start : from) {
            if (in < 1) {
                Vec3 at = lerp(start, c.add(0, 0.3 * k, 0), in).add(0, Math.sin(Math.PI * in) * 1.2 * k, 0);
                star(draw, vc, at, 0.4 * k, glow(220 * (1 - in * 0.5), 0xFF7020));
                star(draw, vc, at, 0.2 * k, glow(240, 0xFFE070));
            }
        }
        // The pillar of fire.
        if (p >= REVEAL - 0.05f && p < 0.9f) {
            float f = (p - (REVEAL - 0.05f)) / (0.9f - (REVEAL - 0.05f));
            double height = cap(6) * k * Math.min(1, f * 4);
            float alpha = 1 - f;
            tube(draw, vc, c, c.add(0, height, 0), (0.6 + 0.7 * f) * k, glow(150 * alpha, RITUAL_RED));
            tube(draw, vc, c, c.add(0, height, 0), 0.3 * k, glow(230 * alpha, 0xFFE070));
        }
        shockwave(draw, vc, c, p, RITUAL_RED);
    }

    /** The Link circuit over the zone; the card's Link Arrows light one after another, then it opens. */
    private static void link(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, Vec3 c, float p,
                             double time) {
        double k = scale();
        Vec3 portal = linkCenter(c);
        Vec3 right = ownerRight(a.event().player()).scale(k);
        Vec3 up = new Vec3(0, k, 0);
        double open = smooth(p / 0.1) * (1 - smooth((p - 0.72) / 0.15));
        if (open <= 0) {
            return;
        }
        double half = 0.95 * open;
        int frame = glow(220 * open, LINK_CYAN);
        Vec3 bl = portal.add(right.scale(-half)).add(up.scale(-half));
        Vec3 br = portal.add(right.scale(half)).add(up.scale(-half));
        Vec3 tr = portal.add(right.scale(half)).add(up.scale(half));
        Vec3 tl = portal.add(right.scale(-half)).add(up.scale(half));
        tube(draw, vc, bl, br, 0.07 * k, frame);
        tube(draw, vc, br, tr, 0.07 * k, frame);
        tube(draw, vc, tr, tl, 0.07 * k, frame);
        tube(draw, vc, tl, bl, 0.07 * k, frame);
        quad(draw, vc, bl, br, tr, tl, glow(40 * open, LINK_CYAN));
        // The circuit's inner lines turn like a dial.
        double spin = time * 0.15;
        for (int i = 0; i < 4; i++) {
            double angle = spin + i * Math.PI / 2;
            Vec3 dir = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            tube(draw, vc, portal.add(dir.scale(0.25 * open)), portal.add(dir.scale(0.7 * open)), 0.04 * k,
                    glow(160 * open, LINK_CYAN));
        }
        int markers = linkMarkers(a.event().code());
        int[] lit = litMarkers(a.event().code());
        for (int m = 0; m < MARKERS.length; m++) {
            boolean has = (markers & MARKERS[m]) != 0;
            int order = indexOf(lit, MARKERS[m]);
            double on = has ? smooth((p - markerStart(order, lit.length)) / 0.04) : 0;
            Vec3 dir = right.scale(MARKER_DIRS[m][0]).add(up.scale(MARKER_DIRS[m][1]));
            Vec3 tip = portal.add(dir.scale((MARKER_DIRS[m][0] != 0 && MARKER_DIRS[m][1] != 0 ? 1.45 : 1.55)
                    * open));
            arrow(draw, vc, tip, dir.normalize().scale(k), right, up, 0.42 * open,
                    glow(open * (70 + 170 * on), on > 0 ? LINK_RED : 0x802020));
            if (on > 0) {
                arrow(draw, vc, tip.subtract(dir.normalize().scale(0.08 * k)), dir.normalize().scale(k), right, up,
                        0.22 * open, glow(240 * on * open, 0xFFFFFF));
                // Light shoots out of each arrow as it comes on.
                double shoot = Math.min(1, (p - markerStart(order, lit.length)) / 0.1);
                if (shoot < 1) {
                    Vec3 out = tip.add(dir.normalize().scale(k * 2.5 * shoot));
                    tube(draw, vc, tip, out, 0.08 * k, glow(200 * (1 - shoot), LINK_RED));
                }
            }
        }
        // The materials are pulled into the circuit.
        List<Vec3> from = materialPoints(a, c);
        double in = smooth(p / 0.3);
        for (Vec3 start : from) {
            if (in < 1) {
                Vec3 at = lerp(start, portal, in);
                tube(draw, vc, lerp(start, portal, Math.max(0, in - 0.3)), at, 0.06 * k, glow(180, LINK_CYAN));
                star(draw, vc, at, 0.3 * k, glow(220 * (1 - in * 0.4), LINK_CYAN));
            }
        }
        if (p >= REVEAL - 0.08f && p < REVEAL + 0.15f) {
            float f = (p - (REVEAL - 0.08f)) / 0.23f;
            star(draw, vc, portal, (0.4 + 1.4 * f) * k, glow(240 * (1 - f), 0xFFFFFF));
        }
        beamDown(draw, vc, portal, c, p, REVEAL - 0.02f, 0.78f, 0.6 * k, 0xFF8080);
        shockwave(draw, vc, c, p, LINK_RED);
    }

    /**
     * A pendulum swinging over the Pendulum Zones, leaving its arc in the air, which opens and lets the monsters
     * down. Only the first monster of a Pendulum Summon draws the pendulum; every one draws its own light.
     */
    private static void pendulumShow(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, Vec3 c, float p,
                                     double time, boolean lead) {
        double k = scale();
        Pendulum pend = pendulum(a.event().player(), c);
        double open = smooth(p / 0.08) * (1 - smooth((p - 0.72) / 0.15));
        Vec3 across = pend.right().subtract(pend.left());
        Vec3 dir = across.lengthSqr() < 1e-6 ? ClientField.right().normalize() : across.normalize();
        Vec3 mid = lerp(pend.left(), pend.right(), 0.5);
        double height = cap(6.2) * k;
        Vec3 pivot = mid.add(0, height, 0);
        double length = height - 1.5 * k;
        double reach = Math.max(1, across.length() / 2 * 0.9);
        double amplitude = Math.asin(Mth.clamp(reach / length, 0.2, 0.95));
        Vec3 arcCenter = mid.add(0, height - length, 0);
        if (lead && open > 0) {
            // The scales: a pillar of light over each Pendulum Zone, blue on the left, red on the right.
            double pulse = 0.8 + 0.2 * Math.sin(time * 0.5);
            tube(draw, vc, pend.left(), pend.left().add(0, 4 * k, 0), 0.35 * k * pulse,
                    glow(140 * open, SCALE_BLUE));
            tube(draw, vc, pend.right(), pend.right().add(0, 4 * k, 0), 0.35 * k * pulse,
                    glow(140 * open, SCALE_RED));
            // The swing: two full periods, from the left end.
            double s = Mth.clamp((p - swingStart()) / (swingEnd() - swingStart()), 0, 1);
            double angle = -amplitude * Math.cos(s * Math.PI * 4);
            if (p > swingEnd()) {
                angle = -amplitude * Math.cos(Math.PI * 4) * (1 - smooth((p - swingEnd()) / 0.1));
            }
            Vec3 bob = pivot.add(dir.scale(Math.sin(angle) * length)).add(0, -Math.cos(angle) * length, 0);
            // The arc it leaves in the air, brightening as the swing goes on.
            double trail = Math.min(1, s * 1.5);
            int segments = 24;
            Vec3 previous = null;
            for (int i = 0; i <= segments; i++) {
                double t = -amplitude + 2 * amplitude * i / segments;
                Vec3 point = pivot.add(dir.scale(Math.sin(t) * length)).add(0, -Math.cos(t) * length, 0);
                if (previous != null) {
                    int color = blend(SCALE_BLUE, SCALE_RED, i / (float) segments);
                    double portal = smooth((p - (REVEAL - 0.08)) / 0.08);
                    tube(draw, vc, previous, point, (0.1 + 0.25 * portal) * k,
                            glow((60 + 120 * trail + 60 * portal) * open, portal > 0.5 ? 0xFFFFFF : color));
                    tube(draw, vc, previous, point, (0.3 + 0.4 * portal) * k, glow(70 * trail * open, color));
                }
                previous = point;
            }
            if (p < REVEAL + 0.05f) {
                tube(draw, vc, pivot, bob, 0.05 * k, glow(220 * open, 0xFFE8A0));
                int gem = blend(SCALE_BLUE, SCALE_RED, (float) ((Math.sin(angle) / Math.sin(amplitude) + 1) / 2));
                star(draw, vc, bob, 0.55 * k, glow(220 * open, gem));
                star(draw, vc, bob, 0.28 * k, glow(240 * open, 0xFFFFFF));
                star(draw, vc, pivot, 0.18 * k, glow(200 * open, 0xFFE8A0));
            }
        }
        // Each monster comes down in its own light, out of the middle of the arc.
        beamDown(draw, vc, arcCenter, c, p, REVEAL - 0.03f, 0.8f, 0.55 * k, 0xFFD0F0);
        shockwave(draw, vc, c, p, lead ? SCALE_RED : SCALE_BLUE);
    }

    // ------------------------------------------------------------------ shapes

    /** A beam from {@code top} down to the zone, falling between {@code from} and {@code to}. */
    private static void beamDown(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 top, Vec3 c, float p, float from,
                                 float to, double radius, int rgb) {
        if (p < from || p >= to) {
            return;
        }
        float f = (p - from) / (to - from);
        Vec3 end = lerp(top, c, Math.min(1, f * 3));
        float alpha = f < 0.5f ? 1 : (1 - f) / 0.5f;
        tube(draw, vc, top, end, radius * (1 - f * 0.5), glow(150 * alpha, rgb));
        tube(draw, vc, top, end, radius * 0.35, glow(240 * alpha, 0xFFFFFF));
    }

    /** A ring of light running out over the mat as the monster lands. */
    private static void shockwave(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 c, float p, int rgb) {
        if (p < REVEAL || p >= 0.9f) {
            return;
        }
        float f = (p - REVEAL) / (0.9f - REVEAL);
        double k = scale();
        ring(draw, vc, c.add(0, 0.02, 0), (0.6 + 3.2 * f) * k, 0.3 * k * (1 - f), f * 2, glow(230 * (1 - f), rgb));
        ring(draw, vc, c.add(0, 0.03, 0), (0.4 + 2.2 * f) * k, 0.12 * k, -f * 3, glow(200 * (1 - f), 0xFFFFFF));
    }

    /** A flat ring lying on the field, drawn round to {@code drawn} (0 to 1) of the way. */
    private static void arc(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 c, double radius, double width,
                            double spin, double drawn, int argb) {
        int segments = 40;
        int count = (int) Math.ceil(segments * Mth.clamp(drawn, 0, 1));
        double inner = Math.max(0, radius - width);
        for (int i = 0; i < count; i++) {
            double a0 = spin + i * Math.PI * 2 / segments;
            double a1 = spin + (i + 1) * Math.PI * 2 / segments;
            quad(draw, vc, c.add(Math.cos(a0) * inner, 0, Math.sin(a0) * inner),
                    c.add(Math.cos(a1) * inner, 0, Math.sin(a1) * inner),
                    c.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius),
                    c.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), argb);
        }
    }

    /** A standing card outline of light, turned by {@code angle} about the vertical. */
    private static void cardOutline(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 c, double angle, double size,
                                    int argb) {
        double k = scale() * size;
        Vec3 u = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(CARD_WIDTH / 2 * 0.7 * k);
        Vec3 v = new Vec3(0, CARD_HEIGHT / 2 * 0.7 * k, 0);
        Vec3 a = c.subtract(u).subtract(v);
        Vec3 b = c.add(u).subtract(v);
        Vec3 d = c.add(u).add(v);
        Vec3 e = c.subtract(u).add(v);
        quad(draw, vc, a, b, d, e, (argb & 0xFFFFFF) | (((argb >>> 24) / 4) << 24));
        tube(draw, vc, a, b, 0.04 * k, argb);
        tube(draw, vc, b, d, 0.04 * k, argb);
        tube(draw, vc, d, e, 0.04 * k, argb);
        tube(draw, vc, e, a, 0.04 * k, argb);
    }

    /** A Link Arrow: a triangle pointing along {@code dir} with its tip at {@code tip}, in the circuit's plane. */
    private static void arrow(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 tip, Vec3 dir, Vec3 right, Vec3 up,
                              double size, int argb) {
        // The direction across the arrow, in the plane of the circuit.
        Vec3 normal = right.cross(up);
        Vec3 side = dir.cross(normal).normalize().scale(size * scale());
        Vec3 back = tip.subtract(dir.normalize().scale(size * scale()));
        quad(draw, vc, back.subtract(side), back.add(side), tip, tip, argb);
    }

    // ------------------------------------------------------------------ positions and timings

    private record Pendulum(Vec3 left, Vec3 right) {
    }

    /** The owner's left and right Pendulum Zones, raised a little; two zones either side of {@code c} without them. */
    private static Pendulum pendulum(int player, Vec3 c) {
        Board board = ClientDuel.view() == null ? null : ClientDuel.view().board();
        boolean separate = board != null && board.separatePendulumZones();
        Vec3 left = zone(new Loc(player, LOCATION_SZONE, separate ? 6 : 0, 0), 0.1);
        Vec3 right = zone(new Loc(player, LOCATION_SZONE, separate ? 7 : 4, 0), 0.1);
        if (left == null || right == null) {
            Vec3 across = ownerRight(player).scale(2 * ZONE_WIDTH);
            return new Pendulum(c.subtract(across), c.add(across));
        }
        return new Pendulum(left, right);
    }

    /** Where the materials of {@code a} come from: their zones, or the summoner's end of the field for the hand. */
    private static List<Vec3> materialPoints(FieldAnimation a, Vec3 c) {
        List<Material> mats = materials(a);
        List<Vec3> out = new ArrayList<>();
        for (Material m : mats) {
            out.add(m.at());
        }
        if (out.isEmpty() && a.event().amount() != SummonStyle.LINK && a.event().amount() < SummonStyle.PENDULUM) {
            int player = a.event().player();
            Vec3 hand = ClientField.toWorld(0, side(player) * (FieldLayout.halfLength() + 1), 1.5);
            Vec3 across = ownerRight(player).scale(1.5);
            out.add(hand.subtract(across));
            out.add(hand.add(across));
        }
        return out;
    }

    private static List<Material> materials(FieldAnimation a) {
        List<Material> mats = pulled.get(a);
        return mats == null ? List.of() : mats;
    }

    /** The owner's right across the field: player 1 sees the field mirrored. */
    private static Vec3 ownerRight(int player) {
        Vec3 right = ClientField.right().normalize();
        return player == 0 ? right : right.scale(-1);
    }

    private static Vec3 fusionCenter(Vec3 c) {
        return c.add(0, cap(3.4) * scale(), 0);
    }

    private static Vec3 linkCenter(Vec3 c) {
        return c.add(0, cap(3.6) * scale(), 0);
    }

    /** {@code height} in field blocks, kept under a roof over the field. */
    private static double cap(double height) {
        double ceiling = ClientField.ceiling();
        return ceiling > 0 ? Math.min(height, Math.max(1.2, (ceiling - 0.5) / scale())) : height;
    }

    /** How big the field is drawn (1 normally). */
    private static double scale() {
        return ClientField.forward().length();
    }

    private static float swingStart() {
        return 0.06f;
    }

    private static float swingEnd() {
        return REVEAL - 0.08f;
    }

    private static int stars(int code) {
        return Mth.clamp(level(code), 3, 12);
    }

    /** When star {@code j} of {@code stars} starts to rise. */
    private static float starStart(int j, int stars) {
        return 0.12f + 0.3f * j / stars;
    }

    /** When the {@code order}th lit Link Arrow of {@code count} comes on. */
    private static float markerStart(int order, int count) {
        return 0.1f + 0.34f * order / Math.max(1, count);
    }

    /** The card's Link Arrows in the order they light up, going round from the bottom left. */
    private static int[] litMarkers(int code) {
        int markers = linkMarkers(code);
        List<Integer> out = new ArrayList<>();
        for (int m : MARKERS) {
            if ((markers & m) != 0) {
                out.add(m);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int linkMarkers(int code) {
        CardInfo info = code == 0 ? null : JadmData.cards().card(code);
        int markers = info == null ? 0 : info.data().linkMarker();
        return markers == 0 ? 0x2 | 0x80 : markers; // unknown card: up and down
    }

    private static int level(int code) {
        CardInfo info = code == 0 ? null : JadmData.cards().card(code);
        return info == null ? 6 : info.data().level() & 0xFF;
    }

    private static int attribute(int code) {
        CardInfo info = code == 0 ? null : JadmData.cards().card(code);
        return info == null ? 0 : info.data().attribute();
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return 0;
    }

    private static int blend(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }
}
