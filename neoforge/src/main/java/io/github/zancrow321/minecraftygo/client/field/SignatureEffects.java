package io.github.zancrow321.minecraftygo.client.field;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.client.ClientDuel;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.engine.duel.Board;
import io.github.zancrow321.minecraftygo.engine.duel.FieldEvent;
import io.github.zancrow321.minecraftygo.engine.protocol.CardState;
import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static io.github.zancrow321.minecraftygo.client.field.FieldLayout.*;
import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * Signature moves for fan favourites, on top of the effects every card gets: Blue-Eyes' White Lightning, Red-Eyes'
 * Inferno Fire Blast, Dark Magic Attack, Summoned Skull's Lightning Strike, and the big spells and traps (Raigeki,
 * Dark Hole, Mirror Force, Monster Reborn, Pot of Greed, Swords of Revealing Light).
 *
 * <p>Everything is drawn as additive glow geometry plus vanilla particles and sounds, so no textures are needed.
 */
final class SignatureEffects {
    static final int BLUE_EYES = 89631139;
    static final int BLUE_EYES_ULTIMATE = 23995346;
    static final int RED_EYES = 74677422;
    static final int DARK_MAGICIAN = 46986414;
    static final int DARK_MAGICIAN_ALT = 36996508;
    static final int SUMMONED_SKULL = 70781052;

    static final int RAIGEKI = 12580477;
    static final int DARK_HOLE = 53129443;
    static final int MIRROR_FORCE = 44095762;
    static final int MONSTER_REBORN = 83764718;
    static final int POT_OF_GREED = 55144522;
    static final int SWORDS_OF_REVEALING_LIGHT = 72302403;

    private SignatureEffects() {
    }

    /** How long an activation of this card plays, in ticks: the big spells get time to land. */
    static int activateDuration(int code) {
        return switch (code) {
            case RAIGEKI, DARK_HOLE, MIRROR_FORCE, SWORDS_OF_REVEALING_LIGHT -> 46;
            case MONSTER_REBORN, POT_OF_GREED -> 36;
            default -> ClassicEffects.activateDuration(code);
        };
    }

    private static boolean dark(int code) {
        return code == DARK_MAGICIAN || code == DARK_MAGICIAN_ALT;
    }

    // ------------------------------------------------------------------ sounds and particles, once per tick

    /** Called every tick while {@code a} plays, with the ticks since it started. */
    static void tick(FieldAnimation a, int elapsed) {
        FieldEvent e = a.event();
        switch (e.kind()) {
            case SUMMON -> {
                if (elapsed == 0) {
                    summon(e);
                }
            }
            case ATTACK -> attackTick(a, elapsed);
            case ACTIVATE -> activateTick(a, elapsed);
            default -> {
            }
        }
        ClassicEffects.tick(a, elapsed);
    }

    private static void summon(FieldEvent e) {
        Vec3 at = zone(e.from(), 1);
        if (at == null) {
            return;
        }
        int code = e.code();
        if (code == BLUE_EYES || code == BLUE_EYES_ULTIMATE || code == RED_EYES) {
            sound(SoundEvents.ENDER_DRAGON_GROWL, at, code == RED_EYES ? 0.9f : 1.3f);
        } else if (dark(code)) {
            sound(SoundEvents.EVOKER_PREPARE_SUMMON, at, 1.1f);
            burst(ParticleTypes.WITCH, at, 40, 0.8, 0.2);
        } else if (code == SUMMONED_SKULL) {
            sound(SoundEvents.LIGHTNING_BOLT_THUNDER, at, 1.8f);
            burst(ParticleTypes.ELECTRIC_SPARK, at, 40, 0.8, 0.4);
        }
    }

    private static void attackTick(FieldAnimation a, int elapsed) {
        int code = a.actor();
        Vec3 from = mouth(a);
        Vec3 to = target(a);
        if (from == null || to == null) {
            return;
        }
        if (code == BLUE_EYES || code == BLUE_EYES_ULTIMATE) {
            if (at(a, elapsed, 0.3f)) {
                sound(SoundEvents.ENDER_DRAGON_GROWL, from, 1.5f);
            }
            if (at(a, elapsed, 0.45f)) {
                sound(SoundEvents.BEACON_DEACTIVATE, to, 1.6f);
                burst(ParticleTypes.END_ROD, to, 50, 0.8, 0.5);
            }
        } else if (code == RED_EYES) {
            if (at(a, elapsed, 0.3f)) {
                sound(SoundEvents.BLAZE_SHOOT, from, 0.8f);
            }
            float t = fraction(a, elapsed);
            if (t >= 0.3f && t < 0.6f) {
                burst(ParticleTypes.FLAME, lerp(from, to, (t - 0.3f) / 0.3f), 6, 0.2, 0.05);
            }
            if (at(a, elapsed, 0.6f)) {
                sound(SoundEvents.GENERIC_EXPLODE.value(), to, 1.4f);
                burst(ParticleTypes.FLAME, to, 40, 0.8, 0.3);
                burst(ParticleTypes.LAVA, to, 12, 0.6, 0.3);
                burst(ParticleTypes.EXPLOSION, to, 2, 0.3, 0);
            }
        } else if (dark(code)) {
            if (at(a, elapsed, 0.2f)) {
                sound(SoundEvents.EVOKER_CAST_SPELL, from, 1.0f);
            }
            float t = fraction(a, elapsed);
            if (t >= 0.25f && t < 0.6f) {
                burst(ParticleTypes.WITCH, lerp(from, to, (t - 0.25f) / 0.35f), 4, 0.25, 0.02);
            }
            if (at(a, elapsed, 0.6f)) {
                sound(SoundEvents.ILLUSIONER_CAST_SPELL, to, 0.8f);
                burst(ParticleTypes.PORTAL, to, 60, 0.6, 0.8);
            }
        } else if (code == SUMMONED_SKULL) {
            if (at(a, elapsed, 0.3f)) {
                sound(SoundEvents.LIGHTNING_BOLT_THUNDER, to, 1.6f);
            }
            if (at(a, elapsed, 0.35f)) {
                burst(ParticleTypes.ELECTRIC_SPARK, to, 60, 1.0, 0.6);
            }
        }
    }

    private static void activateTick(FieldAnimation a, int elapsed) {
        FieldEvent e = a.event();
        int me = e.player();
        int opp = 1 - me;
        switch (e.code()) {
            case RAIGEKI -> {
                if (elapsed == 0) {
                    sound(SoundEvents.LIGHTNING_BOLT_THUNDER, sideCenter(opp), 1.0f);
                }
                for (int i = 0; i < 5; i++) {
                    if (at(a, elapsed, 0.1f + i * 0.1f)) {
                        Vec3 zone = zone(new Loc(opp, LOCATION_MZONE, i, 0), 0.2);
                        burst(ParticleTypes.ELECTRIC_SPARK, zone, 30, 0.8, 0.5);
                        sound(SoundEvents.LIGHTNING_BOLT_IMPACT, zone, 1.2f);
                    }
                }
            }
            case DARK_HOLE -> {
                Vec3 c = ClientField.toWorld(0, 0, 0.3);
                if (elapsed == 0) {
                    sound(SoundEvents.ENDERMAN_TELEPORT, c, 0.5f);
                    sound(SoundEvents.BEACON_DEACTIVATE, c, 0.5f);
                }
                burst(ParticleTypes.PORTAL, c, 12, 3.0, 1.5);
                burst(ParticleTypes.REVERSE_PORTAL, c, 4, 0.5, 0.1);
            }
            case MIRROR_FORCE -> {
                if (elapsed == 0) {
                    sound(SoundEvents.AMETHYST_BLOCK_CHIME, sideCenter(me), 0.7f);
                    sound(SoundEvents.BEACON_ACTIVATE, sideCenter(me), 1.4f);
                }
                if (at(a, elapsed, 0.35f)) {
                    sound(SoundEvents.GLASS_BREAK, sideCenter(opp), 0.7f);
                    for (Vec3 target : monsters(opp)) {
                        burst(ParticleTypes.END_ROD, target, 25, 0.6, 0.4);
                    }
                }
            }
            case MONSTER_REBORN -> {
                Vec3 c = ClientField.toWorld(0, side(me) * MONSTER_ROW, 1.5);
                if (elapsed == 0) {
                    sound(SoundEvents.TOTEM_USE, c, 1.0f);
                }
                if (elapsed % 3 == 0 && fraction(a, elapsed) < 0.7f) {
                    burst(ParticleTypes.TOTEM_OF_UNDYING, c, 8, 0.8, 0.4);
                }
            }
            case POT_OF_GREED -> {
                Vec3 c = activation(e).add(0, 1.2, 0);
                if (elapsed == 0) {
                    sound(SoundEvents.PLAYER_LEVELUP, c, 1.5f);
                }
                if (elapsed % 2 == 0 && fraction(a, elapsed) < 0.6f) {
                    burst(ParticleTypes.HAPPY_VILLAGER, c, 6, 0.8, 0.3);
                }
            }
            case SWORDS_OF_REVEALING_LIGHT -> {
                if (at(a, elapsed, 0.35f)) {
                    sound(SoundEvents.ANVIL_LAND, sideCenter(opp), 1.8f);
                    for (int i = -1; i <= 1; i++) {
                        burst(ParticleTypes.END_ROD, ClientField.toWorld(i * ZONE_WIDTH, side(opp) * MONSTER_ROW,
                                0.2), 20, 0.5, 0.3);
                    }
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ glow geometry, once per frame

    static void draw(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, long now, float partial) {
        float p = a.progress(now, partial);
        switch (a.event().kind()) {
            case ATTACK -> drawAttack(draw, vc, a, p, now);
            case ACTIVATE -> drawActivation(draw, vc, a, p, now, partial);
            default -> {
            }
        }
        ClassicEffects.draw(draw, vc, a, p, now, partial);
    }

    private static void drawAttack(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, float p, long now) {
        int code = a.actor();
        Vec3 from = mouth(a);
        Vec3 to = target(a);
        if (from == null || to == null) {
            return;
        }
        if (code == BLUE_EYES || code == BLUE_EYES_ULTIMATE) {
            if (p < 0.3f || p > 0.85f) {
                return;
            }
            float k = (p - 0.3f) / 0.55f;
            double grow = Math.min(1, k * 4);
            float fade = k > 0.75f ? (1 - k) / 0.25f : 1;
            double scale = code == BLUE_EYES_ULTIMATE ? 1.4 : 1;
            Vec3[] origins = code == BLUE_EYES_ULTIMATE
                    ? new Vec3[]{from.add(across(a, -0.9)), from, from.add(across(a, 0.9))} : new Vec3[]{from};
            for (Vec3 origin : origins) {
                Vec3 end = lerp(origin, to, Math.min(1, k * 3));
                tube(draw, vc, origin, end, 0.16 * grow * scale, glow(230 * fade, 0xFFFFFF));
                tube(draw, vc, origin, end, 0.5 * grow * scale, glow(110 * fade, 0x4AA8FF));
            }
            if (k * 3 >= 1) {
                star(draw, vc, to, 1.0 * scale * fade + 0.1 * Math.sin(now), glow(200 * fade, 0x9AD8FF));
            }
        } else if (code == RED_EYES) {
            if (p >= 0.3f && p < 0.6f) {
                Vec3 ball = lerp(from, to, (p - 0.3f) / 0.3f);
                star(draw, vc, ball, 0.8, glow(220, 0xFF6A10));
                star(draw, vc, ball, 0.45, glow(240, 0xFFE070));
            } else if (p >= 0.6f && p < 0.9f) {
                float k = (p - 0.6f) / 0.3f;
                star(draw, vc, to, 0.6 + 1.4 * k, glow(220 * (1 - k), 0xFF5A10));
            }
        } else if (dark(code)) {
            if (p >= 0.25f && p < 0.6f) {
                Vec3 orb = lerp(from, to, (p - 0.25f) / 0.35f);
                star(draw, vc, orb, 0.75, glow(200, 0xA040FF));
                star(draw, vc, orb, 0.4, glow(240, 0xF0C0FF));
            } else if (p >= 0.6f && p < 0.95f) {
                float k = (p - 0.6f) / 0.35f;
                ring(draw, vc, to.add(0, -0.6, 0), 0.4 + 1.6 * k, 0.25, now * 0.3, glow(200 * (1 - k), 0xB050FF));
            }
        } else if (code == SUMMONED_SKULL) {
            if (p < 0.3f || p > 0.75f) {
                return;
            }
            float fade = p > 0.6f ? (0.75f - p) / 0.15f : 1;
            for (int i = 0; i < 3; i++) {
                Vec3 top = to.add(across(a, (i - 1) * 1.2)).add(0, 7, 0);
                bolt(draw, vc, top, to, now / 2 * 31 + i, glow(230 * fade, 0xF0D8FF));
            }
            star(draw, vc, to, 0.8 * fade, glow(180 * fade, 0xD8B0FF));
        }
    }

    private static void drawActivation(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, float p, long now,
                                       float partial) {
        FieldEvent e = a.event();
        int me = e.player();
        int opp = 1 - me;
        switch (e.code()) {
            case RAIGEKI -> {
                for (int i = 0; i < 5; i++) {
                    float start = 0.1f + i * 0.1f;
                    if (p < start || p > start + 0.3f) {
                        continue;
                    }
                    float fade = 1 - (p - start) / 0.3f;
                    Vec3 bottom = zone(new Loc(opp, LOCATION_MZONE, i, 0), 0);
                    bolt(draw, vc, bottom.add(0, 9, 0), bottom, now / 2 * 17 + i, glow(240 * fade, 0xFFF4A0));
                    star(draw, vc, bottom.add(0, 0.3, 0), 0.9 * fade, glow(200 * fade, 0xFFE060));
                }
            }
            case DARK_HOLE -> {
                Vec3 c = ClientField.toWorld(0, 0, 0.06);
                double size = 4.0 * Math.sin(Math.PI * p);
                double spin = (now + partial) * 0.25;
                for (int i = 0; i < 4; i++) {
                    ring(draw, vc, c.add(0, i * 0.01, 0), size * (1 - i * 0.22), 0.35, spin * (1 + i * 0.4),
                            glow(150, i % 2 == 0 ? 0x6A1FA0 : 0x2A0850));
                }
                star(draw, vc, c.add(0, 0.4, 0), 0.6 * Math.sin(Math.PI * p), glow(200, 0x9A40FF));
            }
            case MIRROR_FORCE -> {
                double z = side(me) * 0.45;
                float alpha = p < 0.12f ? p / 0.12f : p > 0.8f ? (1 - p) / 0.2f : 1;
                Vec3 base = ClientField.toWorld(0, z, 0);
                Vec3 u = ClientField.right().scale(HALF_WIDTH * 0.62);
                Vec3 up = new Vec3(0, 3.4 * ClientField.toWorld(0, 0, 1).subtract(ClientField.toWorld(0, 0, 0)).y, 0);
                quad(draw, vc, base.subtract(u), base.add(u), base.add(u).add(up), base.subtract(u).add(up),
                        glow(70 * alpha, 0x80E8FF));
                for (int i = 0; i < 5; i++) {
                    double h = ((p * 2.5 + i / 5.0) % 1.0);
                    Vec3 y0 = up.scale(h);
                    Vec3 y1 = up.scale(Math.min(1, h + 0.04));
                    quad(draw, vc, base.subtract(u).add(y0), base.add(u).add(y0), base.add(u).add(y1),
                            base.subtract(u).add(y1), glow(170 * alpha, 0xC8FFFF));
                }
                if (p >= 0.3f && p < 0.65f) {
                    float k = (p - 0.3f) / 0.35f;
                    for (Vec3 target : monsters(opp)) {
                        Vec3 origin = ClientField.toWorld(0, z, 1.2).add(target.subtract(ClientField.toWorld(0, z, 1.2))
                                .multiply(1, 0, 1).normalize().scale(0.1));
                        Vec3 end = lerp(origin, target, Math.min(1, k * 2.5));
                        tube(draw, vc, origin, end, 0.14, glow(220 * (1 - k * 0.6f), 0xD0FFFF));
                    }
                }
            }
            case MONSTER_REBORN -> {
                double rise = smooth(Math.min(1, p / 0.4f));
                float alpha = p > 0.8f ? (1 - p) / 0.2f : 1;
                Vec3 c = ClientField.toWorld(0, side(me) * MONSTER_ROW, 0.5 + 1.6 * rise);
                ankh(draw, vc, c, a, glow(230 * alpha, 0xFFD040));
            }
            case POT_OF_GREED -> {
                float alpha = Mth.sin((float) Math.PI * p);
                star(draw, vc, activation(e).add(0, 1.4, 0), 0.9 * alpha + 0.15 * Math.sin(now * 0.8),
                        glow(200 * alpha, 0x50FF60));
            }
            case SWORDS_OF_REVEALING_LIGHT -> {
                double drop = smooth(Math.min(1, p / 0.35f));
                float alpha = p > 0.85f ? (1 - p) / 0.15f : 1;
                for (int i = -1; i <= 1; i++) {
                    Vec3 tip = ClientField.toWorld(i * ZONE_WIDTH, side(opp) * MONSTER_ROW, 8 * (1 - drop) - 0.1);
                    sword(draw, vc, tip, glow(230 * alpha, 0xFFF4B0));
                }
                if (drop >= 1) {
                    ring(draw, vc, ClientField.toWorld(0, side(opp) * MONSTER_ROW, 0.08), HALF_WIDTH * 0.55, 0.25,
                            now * 0.05, glow(160 * alpha, 0xFFE070));
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ shapes

    static int glow(double alpha, int rgb) {
        return ((int) Mth.clamp(alpha, 0, 255) << 24) | rgb;
    }

    /** A quad seen from both sides. */
    static void quad(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        draw.colorQuad(vc, a, b, c, d, argb);
        draw.colorQuad(vc, d, c, b, a, argb);
    }

    /** A beam from {@code a} to {@code b}: two crossed strips, which read as a solid glow from any side. */
    static void tube(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 a, Vec3 b, double radius, int argb) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1e-6 || radius <= 0) {
            return;
        }
        Vec3 n = dir.normalize();
        Vec3 p = n.cross(new Vec3(0, 1, 0));
        if (p.lengthSqr() < 1e-4) {
            p = n.cross(new Vec3(1, 0, 0));
        }
        p = p.normalize().scale(radius);
        Vec3 q = n.cross(p).normalize().scale(radius);
        quad(draw, vc, a.subtract(p), b.subtract(p), b.add(p), a.add(p), argb);
        quad(draw, vc, a.subtract(q), b.subtract(q), b.add(q), a.add(q), argb);
    }

    /** A glowing ball: three crossed squares. */
    static void star(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 c, double size, int argb) {
        if (size <= 0) {
            return;
        }
        Vec3 x = new Vec3(size, 0, 0);
        Vec3 y = new Vec3(0, size, 0);
        Vec3 z = new Vec3(0, 0, size);
        quad(draw, vc, c.subtract(x).subtract(y), c.add(x).subtract(y), c.add(x).add(y), c.subtract(x).add(y), argb);
        quad(draw, vc, c.subtract(z).subtract(y), c.add(z).subtract(y), c.add(z).add(y), c.subtract(z).add(y), argb);
        quad(draw, vc, c.subtract(x).subtract(z), c.add(x).subtract(z), c.add(x).add(z), c.subtract(x).add(z), argb);
    }

    /** A lightning bolt that re-forks whenever {@code seed} changes. */
    static void bolt(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 top, Vec3 bottom, long seed, int argb) {
        RandomSource random = RandomSource.create(seed);
        int segments = 9;
        Vec3 previous = top;
        for (int i = 1; i <= segments; i++) {
            Vec3 point = lerp(top, bottom, i / (float) segments);
            if (i < segments) {
                point = point.add((random.nextDouble() - 0.5) * 0.9, 0, (random.nextDouble() - 0.5) * 0.9);
            }
            tube(draw, vc, previous, point, 0.06, argb);
            tube(draw, vc, previous, point, 0.2, (argb & 0xFFFFFF) | (((argb >>> 24) / 3) << 24));
            previous = point;
        }
    }

    /** A flat ring of broken arcs lying on the field, turned by {@code spin} radians. */
    static void ring(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 c, double radius, double width,
                             double spin, int argb) {
        if (radius <= 0) {
            return;
        }
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            if (i % 4 == 3) {
                continue; // gaps make the turning visible
            }
            double a0 = spin + i * Math.PI * 2 / segments;
            double a1 = spin + (i + 1) * Math.PI * 2 / segments;
            double inner = Math.max(0, radius - width);
            quad(draw, vc, c.add(Math.cos(a0) * inner, 0, Math.sin(a0) * inner),
                    c.add(Math.cos(a1) * inner, 0, Math.sin(a1) * inner),
                    c.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius),
                    c.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), argb);
        }
    }

    /** The ankh of Monster Reborn, facing across the field. */
    private static void ankh(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 c, FieldAnimation a, int argb) {
        Vec3 right = ClientField.right().normalize();
        double r = 0.09;
        tube(draw, vc, c.add(0, -1.3, 0), c, r, argb);
        tube(draw, vc, c.subtract(right.scale(0.6)), c.add(right.scale(0.6)), r, argb);
        Vec3 loop = c.add(0, 0.5, 0);
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments;
            double a1 = (i + 1) * Math.PI * 2 / segments;
            tube(draw, vc, loop.add(right.scale(Math.sin(a0) * 0.32)).add(0, -Math.cos(a0) * 0.45, 0),
                    loop.add(right.scale(Math.sin(a1) * 0.32)).add(0, -Math.cos(a1) * 0.45, 0), r, argb);
        }
        star(draw, vc, c.add(0, 0.2, 0), 1.1, (argb & 0xFFFFFF) | (((argb >>> 24) / 4) << 24));
    }

    /** A sword of light, point down at {@code tip}. */
    private static void sword(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 tip, int argb) {
        Vec3 right = ClientField.right().normalize();
        Vec3 guard = tip.add(0, 2.4, 0);
        tube(draw, vc, tip, guard, 0.1, argb);
        tube(draw, vc, tip, guard, 0.28, (argb & 0xFFFFFF) | (((argb >>> 24) / 3) << 24));
        tube(draw, vc, guard.subtract(right.scale(0.45)), guard.add(right.scale(0.45)), 0.08, argb);
        tube(draw, vc, guard, guard.add(0, 0.55, 0), 0.07, argb);
    }

    // ------------------------------------------------------------------ positions

    static double side(int player) {
        return player == 0 ? -1 : 1;
    }

    static Vec3 sideCenter(int player) {
        return ClientField.toWorld(0, side(player) * HALF_LENGTH * 0.5, 1);
    }

    static Vec3 zone(Loc loc, double up) {
        Slot s = loc == null || loc.isNone() ? null : slot(loc);
        return s == null ? null : ClientField.toWorld(s.x(), s.z(), up);
    }

    static Vec3 activation(FieldEvent e) {
        Vec3 at = zone(e.from(), 0);
        return at != null ? at : ClientField.toWorld(0, side(e.player()) * MONSTER_ROW, 0);
    }

    /** Where an attacker's breath or spell comes from: near the top of its model, a little toward the target. */
    static Vec3 mouth(FieldAnimation a) {
        Loc from = a.event().from();
        CardPool.Model model = a.actor() == 0 ? null : YgoData.modeled().model(a.actor());
        double height = model != null ? model.height() * FieldRenderer.modelScale(model) * 0.8 : 1.2;
        Vec3 at = zone(from, height);
        if (at == null) {
            return null;
        }
        Vec3 target = target(a);
        return target == null ? at : at.add(target.subtract(at).multiply(1, 0, 1).normalize().scale(0.6));
    }

    static Vec3 target(FieldAnimation a) {
        FieldEvent e = a.event();
        Vec3 to = zone(e.to(), 1.0);
        if (to != null) {
            return to;
        }
        Slot from = e.from() == null || e.from().isNone() ? null : slot(e.from());
        return from == null ? null : ClientField.toWorld(from.x(), -side(e.player()) * HALF_LENGTH, 1.5);
    }

    /** A sideways offset, across the line of the attack. */
    static Vec3 across(FieldAnimation a, double amount) {
        return ClientField.right().normalize().scale(amount);
    }

    /** Face-up monsters on {@code player}'s side, at chest height. */
    static List<Vec3> monsters(int player) {
        Board board = ClientDuel.view() == null ? null : ClientDuel.view().board();
        if (board == null) {
            return List.of();
        }
        List<CardState> monsters = board.side(player).monsters();
        java.util.ArrayList<Vec3> out = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(5, monsters.size()); i++) {
            if (monsters.get(i) != null) {
                out.add(zone(new Loc(player, LOCATION_MZONE, i, 0), 1.0));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(Mth.clamp(t, 0, 1)));
    }

    static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    static float fraction(FieldAnimation a, int elapsed) {
        return elapsed / (float) a.duration();
    }

    /** Whether this tick is the one {@code fraction} of the way through {@code a}. */
    static boolean at(FieldAnimation a, int elapsed, float fraction) {
        return elapsed == Math.round(fraction * a.duration());
    }

    static void burst(ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
        if (at != null) {
            FieldRenderer.burst(particle, at, count, spread, speed);
        }
    }

    static void sound(SoundEvent sound, Vec3 at, float pitch) {
        if (at != null) {
            FieldRenderer.sound(sound, at, pitch);
        }
    }
}
