package io.github.zancrow321.jadm.client.field;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.zancrow321.jadm.engine.duel.FieldEvent;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import static io.github.zancrow321.jadm.client.field.FieldLayout.*;
import static io.github.zancrow321.jadm.client.field.SignatureEffects.*;
import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * The second wave of signature moves, for the rest of the classic cards: the knights' and dragons' attacks, Exodia,
 * Kuriboh and Time Wizard, and the well-known spells and traps from Polymerization to Solemn Judgment. Built from
 * the same glow shapes as {@link SignatureEffects}, which hands every event on to this class.
 */
final class ClassicEffects {
    // Monsters
    static final int EXODIA = 33396948;
    static final int KURIBOH = 40640057;
    static final int GAIA = 6368038;
    static final int GAIA_DRAGON_CHAMPION = 66889139;
    static final int BLACK_LUSTER_SOLDIER = 5405694;
    static final int CELTIC_GUARDIAN = 91152256;
    static final int FLAME_SWORDSMAN = 45231177;
    static final int TIME_WIZARD = 71625222;
    static final int CURSE_OF_DRAGON = 28279543;
    static final int THOUSAND_DRAGON = 41462083;
    static final int BABY_DRAGON = 88819587;
    static final int BARREL_DRAGON = 81480460;
    static final int HARPIE_LADY = 76812113;
    static final int HARPIE_LADY_SISTERS = 12206212;

    // Spells and traps
    static final int POLYMERIZATION = 24094653;
    static final int CHANGE_OF_HEART = 4031928;
    static final int HARPIES_FEATHER_DUSTER = 18144506;
    static final int MYSTICAL_SPACE_TYPHOON = 5318639;
    static final int HEAVY_STORM = 19613556;
    static final int TRAP_HOLE = 4206964;
    static final int FISSURE = 66788016;
    static final int HINOTAMA = 46130346;
    static final int OOKAZI = 19523799;
    static final int SPARKS = 76103675;
    static final int DIAN_KETO = 84257639;
    static final int WABOKU = 12607053;
    static final int SOLEMN_JUDGMENT = 41420027;
    static final int HORN_OF_HEAVEN = 98069388;
    static final int MAGIC_JAMMER = 77414722;
    static final int SEVEN_TOOLS = 3819470;

    private ClassicEffects() {
    }

    static int activateDuration(int code) {
        return switch (code) {
            case HEAVY_STORM, HARPIES_FEATHER_DUSTER, TIME_WIZARD, POLYMERIZATION -> 42;
            case KURIBOH, CHANGE_OF_HEART, MYSTICAL_SPACE_TYPHOON, TRAP_HOLE, FISSURE, HINOTAMA, OOKAZI, SPARKS,
                 DIAN_KETO, WABOKU, SOLEMN_JUDGMENT, HORN_OF_HEAVEN, MAGIC_JAMMER, SEVEN_TOOLS -> 36;
            default -> FieldAnimation.duration(FieldEvent.Kind.ACTIVATE);
        };
    }

    // ------------------------------------------------------------------ sounds and particles

    static void tick(FieldAnimation a, int elapsed) {
        switch (a.event().kind()) {
            case SUMMON -> summonTick(a, elapsed);
            case ATTACK -> attackTick(a, elapsed);
            case ACTIVATE -> activateTick(a, elapsed);
            default -> {
            }
        }
    }

    private static void summonTick(FieldAnimation a, int elapsed) {
        Vec3 at = zone(a.event().from(), 0.5);
        if (at == null || elapsed != 0) {
            return;
        }
        switch (a.event().code()) {
            case EXODIA -> {
                sound(SoundEvents.WITHER_SPAWN, at, 1.4f);
                burst(ParticleTypes.TOTEM_OF_UNDYING, at, 60, 1.5, 0.6);
            }
            case BLACK_LUSTER_SOLDIER -> {
                sound(SoundEvents.BEACON_ACTIVATE, at, 0.8f);
                burst(ParticleTypes.ENCHANT, at, 60, 1.2, 0.8);
            }
            case KURIBOH -> sound(SoundEvents.SLIME_SQUISH_SMALL, at, 1.8f);
            default -> {
            }
        }
    }

    private static void attackTick(FieldAnimation a, int elapsed) {
        Vec3 from = mouth(a);
        Vec3 to = target(a);
        if (from == null || to == null) {
            return;
        }
        float t = fraction(a, elapsed);
        switch (a.actor()) {
            case GAIA, GAIA_DRAGON_CHAMPION -> {
                if (at(a, elapsed, 0.25f)) {
                    sound(SoundEvents.TRIDENT_RIPTIDE_1.value(), from, 1.0f);
                }
                if (at(a, elapsed, 0.6f)) {
                    burst(ParticleTypes.CLOUD, to, 30, 0.8, 0.3);
                    burst(ParticleTypes.SWEEP_ATTACK, to, 3, 0.4, 0);
                }
            }
            case BLACK_LUSTER_SOLDIER, CELTIC_GUARDIAN, FLAME_SWORDSMAN, HARPIE_LADY -> {
                if (at(a, elapsed, 0.4f)) {
                    sound(SoundEvents.PLAYER_ATTACK_STRONG, to, a.actor() == HARPIE_LADY ? 1.5f : 0.8f);
                    burst(ParticleTypes.SWEEP_ATTACK, to, 2, 0.3, 0);
                    burst(a.actor() == FLAME_SWORDSMAN ? ParticleTypes.FLAME : ParticleTypes.CRIT, to, 30, 0.7, 0.4);
                }
            }
            case CURSE_OF_DRAGON, BABY_DRAGON, THOUSAND_DRAGON -> {
                if (at(a, elapsed, 0.3f)) {
                    sound(a.actor() == THOUSAND_DRAGON ? SoundEvents.ENDER_DRAGON_GROWL : SoundEvents.BLAZE_SHOOT,
                            from, a.actor() == BABY_DRAGON ? 1.6f : 0.9f);
                }
                if (t >= 0.3f && t < 0.75f) {
                    Vec3 p = lerp(from, to, (t - 0.3f) / 0.45f);
                    burst(a.actor() == THOUSAND_DRAGON ? ParticleTypes.SNEEZE : ParticleTypes.FLAME, p, 5, 0.3, 0.05);
                }
            }
            case BARREL_DRAGON -> {
                for (float shot : new float[]{0.3f, 0.42f, 0.54f}) {
                    if (at(a, elapsed, shot)) {
                        sound(SoundEvents.FIREWORK_ROCKET_BLAST, from, 0.7f);
                    }
                    if (at(a, elapsed, shot + 0.12f)) {
                        burst(ParticleTypes.EXPLOSION, to, 1, 0.4, 0);
                        sound(SoundEvents.GENERIC_EXPLODE.value(), to, 1.6f);
                    }
                }
            }
            case HARPIE_LADY_SISTERS -> {
                if (at(a, elapsed, 0.3f)) {
                    sound(SoundEvents.LIGHTNING_BOLT_THUNDER, to, 2.0f);
                }
                if (at(a, elapsed, 0.5f)) {
                    burst(ParticleTypes.ELECTRIC_SPARK, to, 50, 0.8, 0.5);
                }
            }
            case EXODIA -> {
                if (at(a, elapsed, 0.3f)) {
                    sound(SoundEvents.WITHER_SHOOT, from, 0.6f);
                }
                if (at(a, elapsed, 0.45f)) {
                    burst(ParticleTypes.EXPLOSION_EMITTER, to, 1, 0, 0);
                    sound(SoundEvents.GENERIC_EXPLODE.value(), to, 0.8f);
                }
            }
            default -> {
            }
        }
    }

    private static void activateTick(FieldAnimation a, int elapsed) {
        FieldEvent e = a.event();
        int me = e.player();
        int opp = 1 - me;
        Vec3 at = activation(e);
        float t = fraction(a, elapsed);
        switch (e.code()) {
            case KURIBOH -> {
                if (elapsed % 3 == 0 && t < 0.5f) {
                    sound(SoundEvents.SLIME_SQUISH_SMALL, at, 1.4f + elapsed * 0.02f);
                    burst(ParticleTypes.POOF, at.add(0, 1, 0), 8, 1.5, 0.1);
                }
            }
            case TIME_WIZARD -> {
                // The roulette clicks slower and slower as it winds down.
                if (t < 0.7f && elapsed % Math.max(1, (int) (1 + t * 6)) == 0) {
                    sound(SoundEvents.NOTE_BLOCK_HAT.value(), at, 1.6f);
                }
                if (at(a, elapsed, 0.75f)) {
                    sound(SoundEvents.NOTE_BLOCK_CHIME.value(), at, 1.2f);
                }
            }
            case POLYMERIZATION -> {
                if (elapsed == 0) {
                    sound(SoundEvents.BREWING_STAND_BREW, at, 1.0f);
                }
                if (at(a, elapsed, 0.6f)) {
                    sound(SoundEvents.BEACON_POWER_SELECT, at, 0.8f);
                    burst(ParticleTypes.END_ROD, at.add(0, 2, 0), 40, 0.6, 0.4);
                }
            }
            case CHANGE_OF_HEART -> {
                if (elapsed == 0) {
                    sound(SoundEvents.AMETHYST_BLOCK_CHIME, sideCenter(opp), 1.6f);
                }
                if (elapsed % 4 == 0 && t < 0.8f) {
                    burst(ParticleTypes.HEART, ClientField.toWorld(0, side(opp) * MONSTER_ROW, 2.5), 2, 1.2, 0.1);
                }
            }
            case HARPIES_FEATHER_DUSTER -> {
                if (elapsed == 0) {
                    sound(SoundEvents.PHANTOM_FLAP, sideCenter(opp), 1.2f);
                }
                if (elapsed % 2 == 0 && t < 0.7f) {
                    burst(ParticleTypes.CLOUD, ClientField.toWorld((t * 2 - 0.7) * HALF_WIDTH, side(opp) * SPELL_ROW,
                            1), 6, 1.0, 0.2);
                }
            }
            case MYSTICAL_SPACE_TYPHOON, HEAVY_STORM -> {
                if (elapsed == 0) {
                    sound(SoundEvents.PHANTOM_SWOOP, sideCenter(opp), 0.6f);
                }
                if (elapsed % 2 == 0 && t < 0.85f) {
                    for (Vec3 c : storms(e)) {
                        burst(ParticleTypes.CLOUD, c.add(0, 1.5, 0), 4, 1.0, 0.3);
                    }
                }
            }
            case TRAP_HOLE -> {
                Vec3 pit = trapHole(a);
                if (elapsed == 0) {
                    sound(SoundEvents.GRAVEL_BREAK, pit, 0.6f);
                }
                if (elapsed % 3 == 0 && t < 0.7f) {
                    burst(ParticleTypes.LARGE_SMOKE, pit, 4, 0.8, 0.05);
                }
            }
            case FISSURE -> {
                if (elapsed == 0) {
                    sound(SoundEvents.GENERIC_EXPLODE.value(), sideCenter(opp), 0.5f);
                }
                if (elapsed % 3 == 0 && t < 0.7f) {
                    burst(ParticleTypes.LAVA, ClientField.toWorld(0, side(opp) * MONSTER_ROW, 0.2), 3, 3.0, 0.3);
                    burst(ParticleTypes.CAMPFIRE_COSY_SMOKE, ClientField.toWorld(0, side(opp) * MONSTER_ROW, 0.2), 1,
                            3.0, 0.02);
                }
            }
            case HINOTAMA, OOKAZI, SPARKS -> {
                Vec3 ground = ClientField.toWorld(0, side(opp) * HALF_LENGTH * 0.7, 0.5);
                if (elapsed == 0) {
                    sound(SoundEvents.BLAZE_SHOOT, ground, e.code() == SPARKS ? 1.6f : 0.7f);
                }
                if (at(a, elapsed, 0.45f)) {
                    sound(SoundEvents.GENERIC_EXPLODE.value(), ground, e.code() == OOKAZI ? 0.6f : 1.3f);
                    burst(ParticleTypes.FLAME, ground, e.code() == SPARKS ? 20 : 50, 1.0, 0.4);
                    burst(ParticleTypes.EXPLOSION, ground, e.code() == OOKAZI ? 3 : 1, 0.6, 0);
                }
            }
            case DIAN_KETO -> {
                Vec3 c = ClientField.toWorld(0, side(me) * MONSTER_ROW, 1);
                if (elapsed == 0) {
                    sound(SoundEvents.AMETHYST_BLOCK_CHIME, c, 1.5f);
                    sound(SoundEvents.PLAYER_LEVELUP, c, 1.8f);
                }
                if (elapsed % 3 == 0 && t < 0.8f) {
                    burst(ParticleTypes.HEART, c.add(0, 1, 0), 2, 1.0, 0.2);
                    burst(ParticleTypes.HAPPY_VILLAGER, c, 6, 1.5, 0.3);
                }
            }
            case WABOKU -> {
                if (elapsed == 0) {
                    sound(SoundEvents.BEACON_POWER_SELECT, sideCenter(me), 1.2f);
                }
            }
            case SOLEMN_JUDGMENT, HORN_OF_HEAVEN, MAGIC_JAMMER, SEVEN_TOOLS -> {
                if (elapsed == 0) {
                    switch (e.code()) {
                        case SOLEMN_JUDGMENT -> sound(SoundEvents.BELL_BLOCK, at, 0.8f);
                        case HORN_OF_HEAVEN -> sound(SoundEvents.BELL_RESONATE, at, 1.4f);
                        case MAGIC_JAMMER -> sound(SoundEvents.ILLUSIONER_MIRROR_MOVE, at, 1.0f);
                        default -> sound(SoundEvents.ANVIL_USE, at, 1.2f);
                    }
                    burst(ParticleTypes.END_ROD, at.add(0, 1.5, 0), 40, 0.6, 0.6);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ glow geometry

    static void draw(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, float p, long now, float partial) {
        switch (a.event().kind()) {
            case SUMMON -> drawSummon(draw, vc, a, p, now, partial);
            case ATTACK -> drawAttack(draw, vc, a, p, now, partial);
            case ACTIVATE -> drawActivation(draw, vc, a, p, now, partial);
            default -> {
            }
        }
    }

    private static void drawSummon(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, float p, long now,
                                   float partial) {
        Vec3 at = zone(a.event().from(), 0.07);
        if (at == null) {
            return;
        }
        float alpha = (float) Math.sin(Math.PI * p);
        double spin = (now + partial) * 0.08;
        switch (a.event().code()) {
            case EXODIA -> {
                magicCircle(draw, vc, at, 3.2, spin, glow(220 * alpha, 0xFFC830));
                tube(draw, vc, at, at.add(0, 12, 0), 1.2 * alpha, glow(90 * alpha, 0xFFD860));
            }
            case BLACK_LUSTER_SOLDIER -> magicCircle(draw, vc, at, 1.8, -spin, glow(220 * alpha, 0x5080FF));
            default -> {
            }
        }
    }

    private static void drawAttack(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, float p, long now,
                                   float partial) {
        Vec3 from = mouth(a);
        Vec3 to = target(a);
        if (from == null || to == null) {
            return;
        }
        Vec3 right = ClientField.right().normalize();
        switch (a.actor()) {
            case GAIA, GAIA_DRAGON_CHAMPION -> {
                if (p >= 0.25f && p < 0.7f) {
                    float k = (p - 0.25f) / 0.45f;
                    float fade = k > 0.7f ? (1 - k) / 0.3f : 1;
                    Vec3 end = lerp(from, to, Math.min(1, k * 1.6));
                    double phase = (now + partial) * 0.6;
                    int strands = a.actor() == GAIA_DRAGON_CHAMPION ? 4 : 3;
                    for (int i = 0; i < strands; i++) {
                        helix(draw, vc, from, end, 2.5, 0.45, phase + i * Math.PI * 2 / strands,
                                glow(200 * fade, i % 2 == 0 ? 0xE0F8FF : 0x60D0FF));
                    }
                    tube(draw, vc, from, end, 0.08, glow(220 * fade, 0xFFFFFF));
                }
            }
            case BLACK_LUSTER_SOLDIER, CELTIC_GUARDIAN, FLAME_SWORDSMAN -> {
                if (p >= 0.35f && p < 0.75f) {
                    float k = (p - 0.35f) / 0.4f;
                    int colour = switch (a.actor()) {
                        case BLACK_LUSTER_SOLDIER -> 0x80B0FF;
                        case FLAME_SWORDSMAN -> 0xFF7020;
                        default -> 0xC0FFC0;
                    };
                    double size = a.actor() == BLACK_LUSTER_SOLDIER ? 2.2 : 1.5;
                    slash(draw, vc, to, right, size, k, 0.35, glow(230 * (1 - k * 0.7f), colour));
                    if (a.actor() == BLACK_LUSTER_SOLDIER) {
                        slash(draw, vc, to, right.scale(-1), size * 0.9, k, 0.25, glow(200 * (1 - k * 0.7f), 0xFFE070));
                    }
                }
            }
            case HARPIE_LADY -> {
                if (p >= 0.35f && p < 0.7f) {
                    float k = (p - 0.35f) / 0.35f;
                    for (int i = -1; i <= 1; i++) {
                        slash(draw, vc, to.add(right.scale(i * 0.25)), right, 1.0, k, 0.08,
                                glow(230 * (1 - k * 0.7f), 0xFF80C0));
                    }
                }
            }
            case CURSE_OF_DRAGON, BABY_DRAGON, THOUSAND_DRAGON -> {
                if (p >= 0.3f && p < 0.8f) {
                    float k = (p - 0.3f) / 0.5f;
                    int colour = a.actor() == THOUSAND_DRAGON ? 0xA0C070 : 0xFF7A20;
                    double size = a.actor() == BABY_DRAGON ? 0.35 : 0.6;
                    for (int i = 0; i < 6; i++) {
                        double s = k * 1.6 - i * 0.12;
                        if (s < 0 || s > 1) {
                            continue;
                        }
                        star(draw, vc, lerp(from, to, s), size * (0.5 + s), glow(170 * (1 - k * 0.5f), colour));
                    }
                }
            }
            case BARREL_DRAGON -> {
                for (float shot : new float[]{0.3f, 0.42f, 0.54f}) {
                    if (p >= shot && p < shot + 0.12f) {
                        star(draw, vc, lerp(from, to, (p - shot) / 0.12f), 0.22, glow(240, 0xFFF080));
                    } else if (p >= shot + 0.12f && p < shot + 0.25f) {
                        star(draw, vc, to, 0.9 * (1 - (p - shot - 0.12f) / 0.13f), glow(200, 0xFF9020));
                    }
                }
            }
            case HARPIE_LADY_SISTERS -> {
                if (p >= 0.3f && p < 0.75f) {
                    float fade = p > 0.6f ? (0.75f - p) / 0.15f : 1;
                    Vec3 c = to.add(0, 3.5, 0);
                    Vec3[] corners = new Vec3[3];
                    for (int i = 0; i < 3; i++) {
                        double angle = i * Math.PI * 2 / 3 + (now + partial) * 0.1;
                        corners[i] = c.add(right.scale(Math.cos(angle) * 1.8)).add(0, Math.sin(angle) * 1.8, 0);
                    }
                    long seed = now / 2 * 13;
                    for (int i = 0; i < 3; i++) {
                        bolt(draw, vc, corners[i], corners[(i + 1) % 3], seed + i, glow(220 * fade, 0xFFB0F0));
                    }
                    bolt(draw, vc, c, to, seed + 7, glow(240 * fade, 0xFFE0FF));
                }
            }
            case EXODIA -> {
                if (p >= 0.3f && p < 0.85f) {
                    float k = (p - 0.3f) / 0.55f;
                    float fade = k > 0.7f ? (1 - k) / 0.3f : 1;
                    Vec3 end = lerp(from, to, Math.min(1, k * 3));
                    tube(draw, vc, from, end, 0.35, glow(230 * fade, 0xFFF0B0));
                    tube(draw, vc, from, end, 1.0, glow(120 * fade, 0xFFB020));
                    star(draw, vc, from, 1.2 * fade, glow(200 * fade, 0xFFD040));
                    if (k * 3 >= 1) {
                        star(draw, vc, to, 2.0 * fade, glow(220 * fade, 0xFFC040));
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void drawActivation(FieldRenderer.Draw draw, VertexConsumer vc, FieldAnimation a, float p,
                                       long now, float partial) {
        FieldEvent e = a.event();
        int me = e.player();
        int opp = 1 - me;
        Vec3 at = activation(e);
        Vec3 right = ClientField.right().normalize();
        double time = now + partial;
        float fade = p > 0.8f ? (1 - p) / 0.2f : 1;
        switch (e.code()) {
            case KURIBOH -> {
                // Kuriboh multiplies: fluffballs pop out in a widening spiral.
                for (int i = 0; i < 14; i++) {
                    double s = p * 2.2 - i * 0.08;
                    if (s <= 0) {
                        continue;
                    }
                    double angle = i * 2.4;
                    double r = Math.min(1, s) * (1.2 + i * 0.25);
                    Vec3 c = at.add(Math.cos(angle) * r, 0.6 + Math.min(1, s) * (0.5 + (i % 3) * 0.4),
                            Math.sin(angle) * r);
                    star(draw, vc, c, 0.35, glow(170 * fade, 0xB06A30));
                    star(draw, vc, c, 0.18, glow(200 * fade, 0xFFD090));
                }
            }
            case TIME_WIZARD -> {
                // A roulette that spins fast, slows down and settles.
                double spin = 10 * (1 - Math.pow(1 - Math.min(1, p / 0.75), 3));
                Vec3 c = at.add(0, 2.6, 0);
                for (int i = 0; i < 6; i++) {
                    double a0 = spin + i * Math.PI / 3;
                    double a1 = a0 + Math.PI / 3 * 0.85;
                    int colour = i % 2 == 0 ? 0xFFD040 : 0xFF4040;
                    for (int j = 0; j < 4; j++) {
                        double b0 = a0 + (a1 - a0) * j / 4;
                        double b1 = a0 + (a1 - a0) * (j + 1) / 4;
                        tube(draw, vc, onWheel(c, right, b0, 1.4), onWheel(c, right, b1, 1.4), 0.12,
                                glow(220 * fade, colour));
                    }
                }
                tube(draw, vc, c.add(0, 1.4, 0).add(0, 0.5, 0), c.add(0, 1.4, 0), 0.08, glow(240 * fade, 0xFFFFFF));
                star(draw, vc, c, 0.25, glow(220 * fade, 0xFFFFFF));
            }
            case POLYMERIZATION -> {
                Vec3 top = at.add(0, 3, 0);
                float k = Math.min(1, p / 0.6f);
                Vec3 end = lerp(at, top, k);
                double phase = time * 0.4;
                helix(draw, vc, at, end, 1.5, 0.9 * (1 - k * 0.8), phase, glow(210 * fade, 0xFF5050));
                helix(draw, vc, at, end, 1.5, 0.9 * (1 - k * 0.8), phase + Math.PI, glow(210 * fade, 0x5070FF));
                if (p >= 0.55f) {
                    star(draw, vc, top, 1.4 * Math.sin(Math.PI * Math.min(1, (p - 0.55) / 0.45)),
                            glow(220 * fade, 0xF0D0FF));
                }
            }
            case CHANGE_OF_HEART -> {
                Vec3 c = ClientField.toWorld(0, side(opp) * MONSTER_ROW, 2.6);
                double pulse = 1 + 0.08 * Math.sin(time * 0.5);
                heart(draw, vc, c, right, 1.4 * pulse * Math.min(1, p * 4), glow(230 * fade, 0xFF60A0));
            }
            case HARPIES_FEATHER_DUSTER -> {
                // A gust of feathers sweeps the opponent's spell and trap row.
                for (int i = 0; i < 18; i++) {
                    double s = p * 1.6 - i * 0.03;
                    if (s < 0 || s > 1) {
                        continue;
                    }
                    double x = (s * 2.4 - 1.2) * HALF_WIDTH;
                    double lane = ((i * 7) % 5 - 2) * 0.6;
                    Vec3 c = ClientField.toWorld(x, side(opp) * SPELL_ROW + lane, 0.8 + (i % 4) * 0.5);
                    tube(draw, vc, c.subtract(right.scale(0.5)), c.add(right.scale(0.5)), 0.12,
                            glow(200 * fade, 0xFFFFFF));
                }
            }
            case MYSTICAL_SPACE_TYPHOON, HEAVY_STORM -> {
                for (Vec3 c : storms(e)) {
                    tornado(draw, vc, c, 4.5, 1.8 * Math.min(1, p * 4), time * 0.5, glow(150 * fade, 0xC8F0E0));
                }
            }
            case TRAP_HOLE -> {
                Vec3 pit = trapHole(a);
                double open = Math.min(1, p * 3);
                ring(draw, vc, pit.add(0, 0.06, 0), 1.3 * open, 0.25, time * 0.1, glow(220 * fade, 0xFF5020));
                ring(draw, vc, pit.add(0, 0.05, 0), 0.9 * open, 0.9 * open, -time * 0.05, glow(120 * fade, 0x601010));
            }
            case FISSURE -> {
                long seed = 11;
                java.util.Random random = new java.util.Random(seed);
                double reach = Math.min(1, p * 2.5);
                Vec3 previous = ClientField.toWorld(-HALF_WIDTH * 0.7, side(opp) * MONSTER_ROW, 0.06);
                for (int i = 1; i <= 12 && i / 12.0 <= reach; i++) {
                    Vec3 point = ClientField.toWorld(-HALF_WIDTH * 0.7 + HALF_WIDTH * 1.4 * i / 12,
                            side(opp) * MONSTER_ROW + (random.nextDouble() - 0.5) * 1.2, 0.06);
                    tube(draw, vc, previous, point, 0.15, glow(230 * fade, 0xFF6010));
                    tube(draw, vc, previous, point, 0.4, glow(90 * fade, 0xFFB040));
                    previous = point;
                }
            }
            case HINOTAMA, OOKAZI, SPARKS -> {
                Vec3 ground = ClientField.toWorld(0, side(opp) * HALF_LENGTH * 0.7, 0.5);
                double size = switch (e.code()) {
                    case OOKAZI -> 1.3;
                    case SPARKS -> 0.4;
                    default -> 0.8;
                };
                if (p < 0.45f) {
                    Vec3 ball = lerp(ground.add(-2, 10, 0), ground, p / 0.45f);
                    star(draw, vc, ball, size, glow(220, 0xFF6010));
                    star(draw, vc, ball, size * 0.55, glow(240, 0xFFE070));
                } else if (p < 0.8f) {
                    float k = (p - 0.45f) / 0.35f;
                    star(draw, vc, ground, size * (1 + 2 * k), glow(220 * (1 - k), 0xFF5010));
                }
            }
            case DIAN_KETO -> {
                Vec3 base = ClientField.toWorld(0, side(me) * MONSTER_ROW, 0);
                tube(draw, vc, base, base.add(0, 7, 0), 1.6 * Math.sin(Math.PI * p), glow(110, 0x60FF90));
                ring(draw, vc, base.add(0, 0.07, 0), 2.6, 0.3, time * 0.06, glow(200 * fade, 0x80FFA0));
            }
            case WABOKU -> {
                Vec3 c = ClientField.toWorld(0, side(me) * MONSTER_ROW, 0.05);
                double r = HALF_WIDTH * 0.6 * Math.min(1, p * 4);
                for (int i = 0; i < 6; i++) {
                    double h = i / 6.0;
                    ring(draw, vc, c.add(0, Math.sin(h * Math.PI / 2) * r * 0.5, 0), Math.cos(h * Math.PI / 2) * r,
                            0.15, time * 0.03 * (i % 2 == 0 ? 1 : -1), glow(140 * fade, 0x70A0FF));
                }
            }
            case SOLEMN_JUDGMENT, HORN_OF_HEAVEN, MAGIC_JAMMER, SEVEN_TOOLS -> {
                int colour = switch (e.code()) {
                    case SOLEMN_JUDGMENT -> 0xFFD040;
                    case HORN_OF_HEAVEN -> 0xFFFFFF;
                    case MAGIC_JAMMER -> 0x60A0FF;
                    default -> 0xC0C0C0;
                };
                rays(draw, vc, at.add(0, 2, 0), right, 12, 3.2 * Math.min(1, p * 3), time * 0.02,
                        glow(220 * fade, colour));
                if (e.code() == HORN_OF_HEAVEN) {
                    tube(draw, vc, at, at.add(0, 12, 0), 0.9 * Math.sin(Math.PI * p), glow(120, 0xFFFFF0));
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ shapes

    /** A strand spiralling around the line from {@code a} to {@code b}. */
    private static void helix(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 a, Vec3 b, double turns,
                              double radius, double phase, int argb) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1e-6) {
            return;
        }
        Vec3 n = dir.normalize();
        Vec3 u = n.cross(Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = n.cross(u).normalize();
        int segments = (int) (turns * 12) + 2;
        Vec3 previous = null;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double angle = phase + t * turns * Math.PI * 2;
            Vec3 point = a.add(dir.scale(t)).add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
            if (previous != null) {
                tube(draw, vc, previous, point, 0.07, argb);
            }
            previous = point;
        }
    }

    /** A sword slash: a crescent sweeping across {@code c}, drawn up to {@code k} (0 to 1) of its swing. */
    private static void slash(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 c, Vec3 across, double size, float k,
                              double width, int argb) {
        Vec3 up = new Vec3(0, 1, 0);
        double start = Math.PI * 0.8;
        double sweep = Math.PI * 0.9 * Math.min(1, k * 2.5);
        int segments = 10;
        for (int i = 0; i < segments; i++) {
            double a0 = start - sweep * i / segments;
            double a1 = start - sweep * (i + 1) / segments;
            double w = width * Math.sin(Math.PI * (i + 0.5) / segments);
            Vec3 o0 = across.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            Vec3 o1 = across.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            quad(draw, vc, c.add(o0.scale(size - w)), c.add(o1.scale(size - w)), c.add(o1.scale(size + w)),
                    c.add(o0.scale(size + w)), argb);
        }
    }

    /** A spinning funnel of rings, wide at the top. */
    private static void tornado(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 base, double height, double radius,
                                double spin, int argb) {
        for (int i = 0; i < 8; i++) {
            double h = i / 7.0;
            ring(draw, vc, base.add(Math.sin(spin + i) * 0.2, h * height, Math.cos(spin + i) * 0.2),
                    0.3 + radius * h, 0.18, spin * (1.5 - h), argb);
        }
    }

    /** A heart outline standing up, facing across the field. */
    private static void heart(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 c, Vec3 right, double size, int argb) {
        int segments = 32;
        Vec3 previous = null;
        for (int i = 0; i <= segments; i++) {
            double t = i * Math.PI * 2 / segments;
            double x = 16 * Math.pow(Math.sin(t), 3) / 16;
            double y = (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 16;
            Vec3 point = c.add(right.scale(x * size)).add(0, y * size, 0);
            if (previous != null) {
                tube(draw, vc, previous, point, 0.1, argb);
            }
            previous = point;
        }
    }

    /** A summoning circle: a ring with a five-pointed star, lying on the field. */
    private static void magicCircle(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 c, double radius, double spin,
                                    int argb) {
        ring(draw, vc, c, radius, 0.12, spin, argb);
        ring(draw, vc, c.add(0, 0.005, 0), radius * 0.78, 0.08, -spin * 1.3, argb);
        for (int i = 0; i < 5; i++) {
            double a0 = spin + i * Math.PI * 2 / 5;
            double a1 = spin + (i + 2) * Math.PI * 2 / 5;
            tube(draw, vc, c.add(Math.cos(a0) * radius * 0.78, 0, Math.sin(a0) * radius * 0.78),
                    c.add(Math.cos(a1) * radius * 0.78, 0, Math.sin(a1) * radius * 0.78), 0.06, argb);
        }
    }

    /** Rays bursting out from {@code c} in the plane facing across the field. */
    private static void rays(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 c, Vec3 right, int count, double length,
                             double spin, int argb) {
        for (int i = 0; i < count; i++) {
            double angle = spin + i * Math.PI * 2 / count;
            Vec3 dir = right.scale(Math.cos(angle)).add(0, Math.sin(angle), 0);
            tube(draw, vc, c.add(dir.scale(0.4)), c.add(dir.scale(length * (i % 2 == 0 ? 1 : 0.65))), 0.07, argb);
        }
        star(draw, vc, c, 0.5, argb);
    }

    private static Vec3 onWheel(Vec3 c, Vec3 right, double angle, double radius) {
        return c.add(right.scale(Math.cos(angle) * radius)).add(0, Math.sin(angle) * radius, 0);
    }

    // ------------------------------------------------------------------ positions

    /** Where the typhoons touch down: the opponent's spell and trap row, and both rows for Heavy Storm. */
    private static java.util.List<Vec3> storms(FieldEvent e) {
        int opp = 1 - e.player();
        Vec3 theirs = ClientField.toWorld(0, side(opp) * SPELL_ROW, 0.05);
        if (e.code() != HEAVY_STORM) {
            return java.util.List.of(theirs);
        }
        return java.util.List.of(theirs, ClientField.toWorld(0, side(e.player()) * SPELL_ROW, 0.05));
    }

    /** Trap Hole opens under the monster the opponent summoned last, else in the middle of their monster row. */
    private static Vec3 trapHole(FieldAnimation a) {
        int opp = 1 - a.event().player();
        FieldAnimation latest = null;
        for (FieldAnimation other : ClientField.animations()) {
            if (other.event().kind() == FieldEvent.Kind.SUMMON && other.event().player() == opp
                    && other.start() <= a.start() && (latest == null || other.start() > latest.start())) {
                latest = other;
            }
        }
        Vec3 at = latest == null ? null : zone(latest.event().from(), 0);
        return at != null ? at : zone(new Loc(opp, LOCATION_MZONE, 2, 0), 0);
    }
}
