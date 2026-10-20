package io.github.zancrow321.jadm.client.field;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.CardArt;
import io.github.zancrow321.jadm.client.JadmClientConfig;
import io.github.zancrow321.jadm.engine.data.CardPool;
import io.github.zancrow321.jadm.engine.duel.DuelView;
import io.github.zancrow321.jadm.engine.duel.FieldEvent;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import io.github.zancrow321.jadm.network.VictoryFireworksPayload;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.zancrow321.jadm.client.field.FieldLayout.*;
import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * Destruction and the end of a duel: a destroyed card bursts into glowing shards of its own picture, every card
 * leaving the field flies visibly to where it goes, the blow that ends the duel plays in slow motion (with the
 * camera swinging round to whoever it hits, unless switched off), and the winner gets fireworks over their half.
 * Only touched on the client thread.
 */
public final class Finale {
    /** How many times slower the final blow plays. */
    private static final int SLOW = 3;
    /** How long a destroyed card trembles before it breaks, in ticks. */
    private static final int BREAK_AT = 6;
    /** How long shards fly and lie on the mat before they are gone. */
    private static final int SHARD_LIFE = 44;
    private static final int SHARD_COLUMNS = 3;
    private static final int SHARD_ROWS = 4;
    private static final double GRAVITY = 0.022;
    /** How long the camera takes to swing over to the final blow and back. */
    private static final int SWING_TICKS = 14;
    private static final int SHAKE_TICKS = 12;
    private static final int FLASH_TICKS = 10;
    /** How far from the camera, level, fireworks burst at least. */
    private static final double CLEAR_VIEW = 12;

    /** One piece of a broken card: a triangle cut from its picture, with where it flies. */
    private record Shard(Vec3 origin, Vec3 velocity, Vec3 axis, double spin, Vec3[] corners, float[][] uvs) {
    }

    /** A card that broke, from field tick {@code start} on. */
    private record Shatter(ResourceLocation texture, long start, int glow, double floor, List<Shard> shards) {
    }

    private static final List<Shatter> shatters = new ArrayList<>();
    /** Events that play as part of the final blow, and the field ticks it covers. */
    private static final Set<FieldAnimation> slowed = new HashSet<>();
    private static long finaleFrom = -1;
    private static long finaleTo = -1;
    private static long impactAt = -1;
    /** The final blow's attacker and target (Loc.NONE for a direct attack), or null for one without an attack. */
    private static Loc attacker;
    private static Loc target;
    private static int hitPlayer = -1;
    /** The latest attack whose battle hasn't dealt damage yet: it may be the one that ends the duel. */
    private static FieldEvent openAttack;
    /** Leaves whose card already broke, so it breaks once. */
    private static final Set<FieldAnimation> broken = new HashSet<>();

    private record Rocket(Vec3 from, Vec3 to, long launch, int flight, List<FireworkExplosion> burst) {
    }

    private static final List<Rocket> rockets = new ArrayList<>();
    /** Fireworks for this client's own field, waiting for the final blow to play out. */
    private static VictoryFireworksPayload waiting;
    /** Whether this client's own duel was won with fireworks, which hold the result screen back a little. */
    private static boolean celebrating;
    private static long rocketTick;

    private Finale() {
    }

    /** Forgets the previous duel's effects (fireworks keep going: they outlive the field). */
    static void reset() {
        shatters.clear();
        slowed.clear();
        broken.clear();
        finaleFrom = finaleTo = impactAt = -1;
        attacker = target = null;
        hitPlayer = -1;
        openAttack = null;
        celebrating = false;
    }

    // ------------------------------------------------------------------ timing

    /** How long a card leaving the field plays: a destroyed one trembles and breaks before its card flies. */
    static int leaveDuration(FieldEvent event) {
        return breaks(event) ? 36 : 22;
    }

    /** Cards leaving together (a Raigeki) break almost at once rather than one after another. */
    static int leaveSpacing(FieldEvent event) {
        return 10;
    }

    /** Whether {@code event} is a card that shatters: a destroyed monster, spell or trap. */
    static boolean breaks(FieldEvent event) {
        return event.destroyed() && (event.from().location() & LOCATION_ONFIELD) != 0;
    }

    /** One event to queue, and whether it plays slowed as part of the final blow. */
    record Step(FieldEvent event, boolean slow) {
    }

    /**
     * The events of a view to queue. In the view that ends the duel, the blow that took the last life points plays
     * slowed: the attack (shown again if it was declared in an earlier view) and what it did.
     */
    static List<Step> plan(DuelView view) {
        List<FieldEvent> events = view.events();
        List<Step> steps = new ArrayList<>();
        int last = -1;
        if (view.result() != null) {
            for (int i = events.size() - 1; i >= 0 && last < 0; i--) {
                FieldEvent e = events.get(i);
                if (e.kind() == FieldEvent.Kind.DAMAGE && e.player() >= 0 && e.player() < 2
                        && view.board().side(e.player()).lifePoints() <= 0) {
                    last = i;
                }
            }
        }
        int first = last;
        FieldEvent attack = null;
        if (last >= 0) {
            for (int i = last - 1; i >= 0; i--) {
                FieldEvent e = events.get(i);
                if (e.kind() == FieldEvent.Kind.DAMAGE || e.kind() == FieldEvent.Kind.TURN) {
                    break;
                }
                if (e.kind() == FieldEvent.Kind.ATTACK) {
                    first = i;
                    attack = e;
                    break;
                }
            }
            if (attack == null && openAttack != null && openAttack.player() != events.get(last).player()) {
                attack = openAttack;
            }
        }
        for (int i = 0; i < events.size(); i++) {
            FieldEvent e = events.get(i);
            if (i == first && attack != null && events.get(first) != attack) {
                // The attack was declared before the last response: it hits again, slowly, as it lands.
                steps.add(new Step(attack, true));
            }
            steps.add(new Step(e, last >= 0 && i >= first && i <= last));
            switch (e.kind()) {
                case ATTACK -> openAttack = e;
                case DAMAGE, TURN -> openAttack = null;
                default -> {
                }
            }
        }
        if (last >= 0) {
            attacker = attack == null ? null : attack.from();
            target = attack == null ? null : attack.to();
            hitPlayer = events.get(last).player();
        }
        return steps;
    }

    /** Called for each queued animation that is part of the final blow. */
    static void slowed(FieldAnimation a) {
        slowed.add(a);
        finaleFrom = finaleFrom < 0 ? a.start() : Math.min(finaleFrom, a.start());
        finaleTo = Math.max(finaleTo, a.start() + a.duration());
        if (a.event().kind() == FieldEvent.Kind.DAMAGE) {
            impactAt = a.start();
        }
    }

    static boolean slow(FieldAnimation a) {
        return slowed.contains(a);
    }

    /** How long a slowed event plays: the attack slowest; the damage only lingers a little after it lands. */
    static int slowDuration(FieldEvent event, int duration) {
        return event.kind() == FieldEvent.Kind.DAMAGE ? duration * 3 / 2 : duration * SLOW;
    }

    /** How long the queue waits after a slowed {@code a}: the damage lands as the attack strikes. */
    static int slowSpacing(FieldEvent event, int spacing) {
        return event.kind() == FieldEvent.Kind.ATTACK ? FieldAnimation.duration(event) * SLOW / 2 : spacing * SLOW;
    }

    /** Whether the final blow is playing now. */
    public static boolean finishing() {
        long now = ClientField.tick();
        return finaleFrom >= 0 && now >= finaleFrom && now < finaleTo + SWING_TICKS;
    }

    // ------------------------------------------------------------------ ticking

    /** When an effect starts: the impact of the final blow, and a breaking card. */
    static void onStart(FieldAnimation a) {
        FieldEvent e = a.event();
        if (slow(a) && e.kind() == FieldEvent.Kind.DAMAGE) {
            Vec3 at = hitPoint();
            FieldRenderer.sound(SoundEvents.GENERIC_EXPLODE.value(), at, 0.6f);
            FieldRenderer.sound(SoundEvents.WARDEN_SONIC_BOOM, at, 0.7f);
            FieldRenderer.burst(ParticleTypes.EXPLOSION, at, 6, 0.8, 0.1);
            FieldRenderer.burst(ParticleTypes.FLASH, at, 1, 0, 0);
            FieldRenderer.burst(ParticleTypes.END_ROD, at, 60, 1.2, 0.6);
            // Whoever stands there flinches.
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                for (net.minecraft.world.entity.LivingEntity hit : level.getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(at, at)
                                .inflate(2.5))) {
                    hit.animateHurt(0);
                }
            }
        }
    }

    /** Each client tick: breaks cards whose moment came and trails flying cards. */
    static void tick() {
        long now = ClientField.tick();
        shatters.removeIf(s -> now > s.start() + SHARD_LIFE);
        broken.removeIf(a -> !ClientField.animations().contains(a));
        for (FieldAnimation a : ClientField.animations()) {
            FieldEvent e = a.event();
            if (e.kind() != FieldEvent.Kind.LEAVE || !a.started(now)) {
                continue;
            }
            int t = (int) (now - a.start());
            if (breaks(e) && t >= breakAt(a) && broken.add(a)) {
                shatter(a);
            }
            Vec3 at = flight(a, now, 0);
            if (at != null && t % 2 == 0) {
                FieldRenderer.burst(ParticleTypes.END_ROD, at, 1, 0.05, 0.01);
            }
        }
    }

    private static int breakAt(FieldAnimation a) {
        return slow(a) ? BREAK_AT * 2 : BREAK_AT;
    }

    /** Whether the card of a leave has broken already, so its ghost is gone. */
    static boolean shattered(FieldAnimation a, long now) {
        return breaks(a.event()) && now - a.start() >= breakAt(a);
    }

    /** A breaking monster trembles. */
    static Vec3 tremble(FieldAnimation a, long now) {
        double k = 0.05 * ClientField.forward().length();
        return new Vec3(((now * 7) % 3 - 1) * k, 0, ((now * 5) % 3 - 1) * k);
    }

    /** The picture a breaking card shows: upright facing the camera, or lying on the mat. */
    private record Pane(Vec3 center, Vec3 u, Vec3 v, ResourceLocation texture, boolean standing,
                        boolean drawnElsewhere) {
    }

    private static Pane pane(FieldEvent e) {
        Loc from = e.from();
        Slot s = slot(from);
        if (s == null) {
            return null;
        }
        double k = ClientField.forward().length();
        int player = from.controller();
        boolean monster = (from.location() & LOCATION_MZONE) != 0;
        int code = e.code();
        CardPool.Model model = code == 0 ? null : JadmData.model(code);
        boolean hologram = monster && model == null && code != 0 && CardArt.hologram(code) != null;
        boolean defense = (from.position() & POS_DEFENSE) != 0;
        boolean standing = from.isFaceUp() && code != 0 && !(monster && defense && model == null && !hologram)
                && !(!monster && from.sequence() == 5);
        if (!standing) {
            // Lying on the mat: a set card (in its sleeve), a Field Spell or a monster card in defense.
            double mirror = player == 0 ? 1 : -1;
            ResourceLocation texture = from.isFaceUp() && code != 0 ? art(code, false)
                    : ClientField.sleeve(player, from.sequence());
            return new Pane(ClientField.toWorld(s.x(), s.z(), 0.06), ClientField.right().scale(mirror * CARD_WIDTH / 2),
                    ClientField.forward().scale(mirror * CARD_HEIGHT / 2), texture, false, false);
        }
        // Upright, facing the camera: the monster, its hologram or the standing spell card breaks like glass.
        double width;
        double height;
        ResourceLocation texture;
        if (monster && model != null) {
            double scale = FieldRenderer.modelScale(model) * ClientField.size();
            height = Mth.clamp(model.height() * scale, 1.4, 3.2);
            width = Mth.clamp(model.width() * scale, height * 0.6, height * 0.9);
            texture = art(code, true);
        } else if (hologram) {
            height = width = Holograms.SIZE;
            texture = art(code, true);
        } else {
            width = CARD_WIDTH;
            height = CARD_HEIGHT;
            texture = art(code, false);
        }
        Vec3 center = ClientField.toWorld(s.x(), s.z(), 0.2 + height / 2);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 look = new Vec3(center.x - cam.x, 0, center.z - cam.z);
        if (look.lengthSqr() < 1e-4) {
            look = ClientField.direction();
        }
        Vec3 u = look.normalize().cross(new Vec3(0, 1, 0)).normalize().scale(width / 2 * k);
        Vec3 v = new Vec3(0, height / 2 * k, 0);
        // A monster's model or hologram trembles on the field itself until it breaks.
        return new Pane(center, u, v, texture, true, monster && (model != null || hologram));
    }

    private static void shatter(FieldAnimation a) {
        Pane pane = pane(a.event());
        if (pane == null) {
            return;
        }
        double k = ClientField.forward().length();
        int code = a.event().code();
        boolean monster = (a.event().from().location() & LOCATION_MZONE) != 0;
        double floor = ClientField.toWorld(0, 0, FieldRenderer.MAT_Y + 0.02).y;
        int glow = monster ? 0x60D8FF : isTrap(code) ? 0xE060C0 : 0x40E8A0;
        Vec3 center = pane.center();
        shatters.add(new Shatter(pane.texture(), ClientField.tick(), glow, floor,
                cut(center, pane.u(), pane.v(), k, !pane.standing())));
        FieldRenderer.sound(SoundEvents.GLASS_BREAK, center, 0.7f);
        FieldRenderer.sound(SoundEvents.AMETHYST_CLUSTER_BREAK, center, 0.8f);
        FieldRenderer.sound(SoundEvents.GENERIC_EXPLODE.value(), center, 1.6f);
        FieldRenderer.burst(ParticleTypes.END_ROD, center, 24, 0.6 * k, 0.3);
        FieldRenderer.burst(ParticleTypes.ELECTRIC_SPARK, center, 30, 0.8 * k, 0.6);
        FieldRenderer.burst(ParticleTypes.FLASH, center, 1, 0, 0);
    }

    private static boolean isTrap(int code) {
        var info = JadmData.cards().card(code);
        return code != 0 && info != null && info.is(TYPE_TRAP);
    }

    private static ResourceLocation art(int code, boolean artworkOnly) {
        CardArt.Texture art = artworkOnly ? CardArt.hologram(code) : null;
        if (art == null) {
            art = CardArt.get(code);
        }
        return art != null ? art.location()
                : ResourceLocation.fromNamespaceAndPath(io.github.zancrow321.jadm.Jadm.MOD_ID,
                "textures/field/card_blank.png");
    }

    /** Cuts the picture spanned by {@code u} and {@code v} round {@code center} into jagged triangles. */
    private static List<Shard> cut(Vec3 center, Vec3 u, Vec3 v, double k, boolean flat) {
        RandomSource random = RandomSource.create();
        double[][][] points = new double[SHARD_COLUMNS + 1][SHARD_ROWS + 1][];
        for (int i = 0; i <= SHARD_COLUMNS; i++) {
            for (int j = 0; j <= SHARD_ROWS; j++) {
                double a = (double) i / SHARD_COLUMNS;
                double b = (double) j / SHARD_ROWS;
                if (i > 0 && i < SHARD_COLUMNS) {
                    a += (random.nextDouble() - 0.5) * 0.6 / SHARD_COLUMNS;
                }
                if (j > 0 && j < SHARD_ROWS) {
                    b += (random.nextDouble() - 0.5) * 0.6 / SHARD_ROWS;
                }
                points[i][j] = new double[]{a, b};
            }
        }
        Vec3 normal = u.cross(v).normalize();
        List<Shard> shards = new ArrayList<>();
        for (int i = 0; i < SHARD_COLUMNS; i++) {
            for (int j = 0; j < SHARD_ROWS; j++) {
                double[][][] triangles = {{points[i][j], points[i + 1][j], points[i + 1][j + 1]},
                        {points[i][j], points[i + 1][j + 1], points[i][j + 1]}};
                for (double[][] tri : triangles) {
                    Vec3[] at = new Vec3[3];
                    float[][] uvs = new float[3][];
                    Vec3 sum = Vec3.ZERO;
                    for (int c = 0; c < 3; c++) {
                        at[c] = center.add(u.scale(2 * tri[c][0] - 1)).add(v.scale(2 * tri[c][1] - 1));
                        uvs[c] = new float[]{(float) tri[c][0], (float) (1 - tri[c][1])};
                        sum = sum.add(at[c]);
                    }
                    Vec3 origin = sum.scale(1 / 3.0);
                    Vec3[] corners = new Vec3[3];
                    for (int c = 0; c < 3; c++) {
                        corners[c] = at[c].subtract(origin);
                    }
                    Vec3 out = origin.subtract(center);
                    out = out.lengthSqr() < 1e-6 ? normal : out.normalize();
                    double speed = (0.12 + random.nextDouble() * 0.16) * k;
                    Vec3 velocity = out.scale(speed)
                            .add(normal.scale((random.nextDouble() - 0.5) * 0.2 * k))
                            .add(0, (flat ? 0.22 : 0.1) * k + random.nextDouble() * 0.12 * k, 0);
                    Vec3 axis = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5,
                            random.nextDouble() - 0.5).normalize();
                    double spin = (0.15 + random.nextDouble() * 0.35) * (random.nextBoolean() ? 1 : -1);
                    shards.add(new Shard(origin, velocity, axis, spin, corners, uvs));
                }
            }
        }
        return shards;
    }

    // ------------------------------------------------------------------ drawing

    /** The flying shards and cards, after the field's other effects. */
    static void draw(FieldRenderer.Draw draw, long now, float partial) {
        for (Shatter s : shatters) {
            float t = now - s.start() + partial;
            if (t < 0) {
                continue;
            }
            float fade = Mth.clamp((SHARD_LIFE - t) / 14f, 0, 1);
            VertexConsumer vc = draw.buffers().getBuffer(RenderType.entityTranslucent(s.texture()));
            for (Shard shard : s.shards()) {
                Vec3[] p = place(shard, t, s.floor(), 1);
                triangle(draw, vc, p, shard.uvs(), (int) (255 * fade) << 24 | 0xFFFFFF);
            }
        }
        VertexConsumer glow = draw.buffers().getBuffer(RenderType.lightning());
        for (Shatter s : shatters) {
            float t = now - s.start() + partial;
            float bright = Mth.clamp(1 - t / 16f, 0, 1);
            if (t < 0 || bright <= 0) {
                continue;
            }
            int color = (int) (220 * bright) << 24 | s.glow();
            for (Shard shard : s.shards()) {
                Vec3[] p = place(shard, t, s.floor(), 1.25);
                draw.colorQuad(glow, p[0], p[1], p[2], p[2], color);
                draw.colorQuad(glow, p[2], p[2], p[1], p[0], color);
            }
        }
        for (FieldAnimation a : ClientField.animations()) {
            if (a.event().kind() != FieldEvent.Kind.LEAVE) {
                continue;
            }
            if (!a.started(now)) {
                // Still where it was until its turn in the queue comes.
                Pane pane = pane(a.event());
                if (pane != null && !pane.drawnElsewhere()) {
                    draw.texQuad(pane.texture(), pane.center(), pane.u(), pane.v(), 0xFFFFFFFF, pane.standing());
                }
                continue;
            }
            if (breaks(a.event()) && !shattered(a, now)) {
                // A card trembles and flashes where it was until it breaks.
                Pane pane = pane(a.event());
                if (pane != null && !pane.drawnElsewhere()) {
                    int tint = now % 2 == 0 ? 0xFFFF8080 : 0xFFFFFFFF;
                    draw.texQuad(pane.texture(), pane.center().add(tremble(a, now)), pane.u(), pane.v(), tint, true);
                }
            }
            drawFlight(draw, a, now, partial);
        }
    }

    /** Where a shard's corners are {@code t} ticks after it broke off: thrown out, spinning, then lying still. */
    private static Vec3[] place(Shard shard, float t, double floor, double grow) {
        Vec3 o = shard.origin();
        Vec3 vel = shard.velocity();
        // When it lands on the mat, it stops there.
        double drop = o.y - floor;
        double land = (vel.y + Math.sqrt(Math.max(0, vel.y * vel.y + 2 * GRAVITY * Math.max(drop, 0)))) / GRAVITY;
        double time = Math.min(t, land);
        Vec3 at = o.add(vel.scale(time)).add(0, -0.5 * GRAVITY * time * time, 0);
        double angle = shard.spin() * time;
        Vec3[] corners = new Vec3[3];
        for (int c = 0; c < 3; c++) {
            Vec3 r = rotate(shard.corners()[c].scale(grow), shard.axis(), angle);
            corners[c] = at.add(r.x, Math.max(r.y, floor - at.y + 0.005), r.z);
        }
        return corners;
    }

    private static Vec3 rotate(Vec3 p, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return p.scale(cos).add(axis.cross(p).scale(sin)).add(axis.scale(axis.dot(p) * (1 - cos)));
    }

    private static void triangle(FieldRenderer.Draw draw, VertexConsumer vc, Vec3[] p, float[][] uv, int argb) {
        Vec3 n = p[1].subtract(p[0]).cross(p[2].subtract(p[0]));
        n = n.lengthSqr() < 1e-9 ? new Vec3(0, 1, 0) : n.normalize();
        int[] order = {0, 1, 2, 2};
        for (int i : order) {
            vertex(draw, vc, p[i], uv[i], n, argb);
        }
        // The back face, so a spinning shard never vanishes edge-on.
        for (int i = order.length - 1; i >= 0; i--) {
            vertex(draw, vc, p[order[i]], uv[order[i]], n.scale(-1), argb);
        }
    }

    private static void vertex(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 p, float[] uv, Vec3 n, int argb) {
        Vec3 cam = draw.cam();
        vc.addVertex(draw.pose(), (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, argb >>> 24).setUv(uv[0], uv[1])
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .setNormal(draw.pose(), (float) n.x, (float) n.y, (float) n.z);
    }

    /** When the card of a leave is in the air: after it broke, or soon after it left. */
    private static float flightProgress(FieldAnimation a, long now, float partial) {
        int from = breaks(a.event()) ? breakAt(a) + 2 : 3;
        float t = (now - a.start() + partial - from) / (a.duration() - from);
        return t;
    }

    /** Where the flying card of a leave is now, or {@code null} while it isn't flying. */
    private static Vec3 flight(FieldAnimation a, long now, float partial) {
        float t = flightProgress(a, now, partial);
        if (t < 0 || t > 1) {
            return null;
        }
        Vec3[] ends = ends(a.event());
        if (ends == null) {
            return null;
        }
        double ease = t * t * (3 - 2 * t);
        double arc = Math.sin(Math.PI * t) * 2.2 * ClientField.forward().length();
        return ends[0].add(ends[1].subtract(ends[0]).scale(ease)).add(0, arc, 0);
    }

    /** Where a leaving card's flight starts and ends. */
    private static Vec3[] ends(FieldEvent e) {
        Slot from = slot(e.from());
        Loc to = e.to();
        if (from == null || to == null || to.isNone()) {
            return null;
        }
        Slot dest = slot(to.controller(), to.location(), (to.location() & LOCATION_ONFIELD) != 0 ? to.sequence() : 0);
        Vec3 end;
        if (dest == null) {
            // To the hand: off its owner's end of the mat.
            double side = to.controller() == 0 ? -1 : 1;
            end = ClientField.toWorld(from.x() * 0.5, side * (FieldLayout.halfLength() + 1.5), 1.2);
        } else {
            double pile = (to.location() & LOCATION_ONFIELD) != 0 ? 0.1 : 0.5;
            end = ClientField.toWorld(dest.x(), dest.z(), 0.04 + pile);
        }
        return new Vec3[]{ClientField.toWorld(from.x(), from.z(), 0.5), end};
    }

    private static void drawFlight(FieldRenderer.Draw draw, FieldAnimation a, long now, float partial) {
        float t = flightProgress(a, now, partial);
        Vec3 at = flight(a, now, partial);
        if (at == null) {
            return;
        }
        FieldEvent e = a.event();
        Loc to = e.to();
        boolean shown = e.code() != 0 && (to.location() == LOCATION_GRAVE || to.isFaceUp()
                || to.location() == LOCATION_HAND && e.player() == you());
        ResourceLocation texture = shown ? art(e.code(), false) : ClientField.sleeve(to.controller());
        double mirror = to.controller() == 0 ? 1 : -1;
        // Spins once round as it flies and tips up at the top of its arc, then lands flat in the pile.
        double yaw = t * Math.PI * 2;
        double tilt = Math.sin(Math.PI * t) * Math.toRadians(65);
        Vec3 r = ClientField.right().scale(mirror * CARD_WIDTH / 2);
        Vec3 f = ClientField.forward().scale(mirror * CARD_HEIGHT / 2);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 u = rotate(r, up, yaw);
        Vec3 v = rotate(f, up, yaw);
        v = v.scale(Math.cos(tilt)).add(0, v.length() * Math.sin(tilt), 0);
        draw.texQuad(texture, at, u, v, 0xF8FFFFFF, true);
    }

    private static int you() {
        DuelView view = io.github.zancrow321.jadm.client.ClientDuel.view();
        return view == null ? -1 : view.you();
    }

    // ------------------------------------------------------------------ the final blow: camera and screen

    /** Where the final blow lands: the hit duelist at their end of the field, or the monster attacked. */
    private static Vec3 hitPoint() {
        if (target != null && !target.isNone() && slot(target) != null) {
            Slot s = slot(target);
            return ClientField.toWorld(s.x(), s.z(), 1.2);
        }
        int player = hitPlayer < 0 ? 0 : hitPlayer;
        double side = player == 0 ? -1 : 1;
        return ClientField.toWorld(0, side * (FieldLayout.halfLength() + 1.2), 0).add(0, 1.5, 0);
    }

    /** How far the camera has swung over to the final blow, 0 to 1. */
    private static double swing() {
        if (finaleFrom < 0 || attacker == null || !JadmClientConfig.FINISHER_CAMERA.get()) {
            return 0;
        }
        float now = ClientField.tick() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double in = Mth.clamp((now - finaleFrom) / SWING_TICKS, 0, 1);
        double out = Mth.clamp((finaleTo + SWING_TICKS - now) / SWING_TICKS, 0, 1);
        double w = Math.min(in, out);
        return w * w * (3 - 2 * w);
    }

    /** The camera's place while the final blow plays: low behind the attacker, looking at whoever it hits. */
    public static Vec3 cameraPosition(Vec3 normal) {
        Vec3 shake = shake();
        double w = swing();
        if (w <= 0 || slot(attacker) == null) {
            return normal.add(shake);
        }
        double k = ClientField.size();
        Slot a = slot(attacker);
        Vec3 from = ClientField.toWorld(a.x(), a.z(), 0);
        Vec3 hit = hitPoint();
        Vec3 away = new Vec3(from.x - hit.x, 0, from.z - hit.z);
        away = away.lengthSqr() < 1e-4 ? ClientField.direction() : away.normalize();
        Vec3 side = away.cross(new Vec3(0, 1, 0)).normalize();
        // Rides in behind the attacker until the blow lands, close to whoever it hits, then holds there.
        float now = ClientField.tick() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        long land = impactAt >= 0 ? impactAt : finaleTo;
        double close = Mth.clamp((now - finaleFrom) / Math.max(1.0, land - finaleFrom), 0, 1);
        close = 1 - (1 - close) * (1 - close);
        double up = (3.2 - 1.2 * close) * k;
        if (ClientField.ceiling() > 0) {
            up = Math.min(up, ClientField.ceiling() - 0.6);
        }
        Vec3 start = from.add(away.scale(4.5 * k));
        Vec3 near = new Vec3(hit.x, from.y, hit.z).add(away.scale(6 * k));
        Vec3 track = start.add(near.subtract(start).scale(close * 0.85));
        Vec3 cine = track.add(side.scale(2.2 * k)).add(0, up, 0);
        return normal.add(cine.subtract(normal).scale(w)).add(shake);
    }

    /** What the camera looks at while the final blow plays. */
    public static Vec3 cameraTarget(Vec3 normal) {
        double w = swing();
        if (w <= 0 || slot(attacker) == null) {
            return normal;
        }
        Slot a = slot(attacker);
        Vec3 from = ClientField.toWorld(a.x(), a.z(), 1);
        Vec3 look = from.add(hitPoint().subtract(from).scale(0.75));
        return normal.add(look.subtract(normal).scale(w));
    }

    /** The camera shudders as the final blow lands. */
    private static Vec3 shake() {
        if (impactAt < 0) {
            return Vec3.ZERO;
        }
        long t = ClientField.tick() - impactAt;
        if (t < 0 || t > SHAKE_TICKS) {
            return Vec3.ZERO;
        }
        double k = 0.25 * (1 - (double) t / SHAKE_TICKS);
        return new Vec3(Math.sin(t * 2.7) * k, Math.cos(t * 3.1) * k, Math.sin(t * 1.9 + 1) * k);
    }

    /** Over the screen while the final blow plays: bars like a film, and a white flash as it lands. */
    public static void renderHud(GuiGraphics g, int w, int h) {
        if (finaleFrom < 0 || !ClientField.active()) {
            return;
        }
        float now = ClientField.tick() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double in = Mth.clamp((now - finaleFrom) / 8, 0, 1);
        double out = Mth.clamp((finaleTo + SWING_TICKS - now) / 8, 0, 1);
        double bars = Math.min(in, out);
        if (bars > 0) {
            int bar = (int) (h * 0.11 * bars);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
            // A dark edge pulls the eye to the middle.
            int edge = (int) (0x50 * bars);
            g.fillGradient(0, bar, w, bar + h / 6, edge << 24, 0);
            g.fillGradient(0, h - bar - h / 6, w, h - bar, 0, edge << 24);
        }
        if (impactAt >= 0) {
            float t = now - impactAt;
            if (t >= 0 && t < FLASH_TICKS) {
                int alpha = (int) (0xE0 * (1 - t / FLASH_TICKS));
                g.fill(0, 0, w, h, alpha << 24 | 0xFFFFFF);
            }
        }
    }

    // ------------------------------------------------------------------ fireworks

    /** The server says someone won a duel near here: fireworks, once this client's own final blow has played. */
    public static void fireworks(VictoryFireworksPayload payload) {
        Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
        if (ClientField.active() && ClientField.center() != null
                && ClientField.center().distanceToSqr(at) < 30 * 30) {
            waiting = payload;
            celebrating = true;
        } else {
            launch(payload);
        }
    }

    /** How much longer the result screen waits, so the fireworks over the field are seen first. */
    public static int resultHold() {
        return celebrating ? 100 : 0;
    }

    /** Every client tick, with or without a field: the fireworks' rockets. */
    public static void clientTick() {
        rocketTick++;
        if (waiting != null && (!ClientField.active() || ClientField.idle())) {
            launch(waiting);
            waiting = null;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            rockets.clear();
            return;
        }
        for (Rocket r : List.copyOf(rockets)) {
            long t = rocketTick - r.launch();
            if (t < 0) {
                continue;
            }
            if (t == 0) {
                level.playLocalSound(r.from().x, r.from().y, r.from().z, SoundEvents.FIREWORK_ROCKET_LAUNCH,
                        net.minecraft.sounds.SoundSource.PLAYERS, 2.5f, 1, false);
            }
            if (t >= r.flight()) {
                level.createFireworks(r.to().x, r.to().y, r.to().z, 0, 0, 0, r.burst());
                rockets.remove(r);
                continue;
            }
            double p = (double) t / r.flight();
            double eased = 1 - (1 - p) * (1 - p);
            Vec3 at = r.from().add(r.to().subtract(r.from()).scale(eased));
            level.addParticle(ParticleTypes.FIREWORK, at.x, at.y, at.z, (level.random.nextDouble() - 0.5) * 0.05,
                    -0.1, (level.random.nextDouble() - 0.5) * 0.05);
        }
    }

    private static void launch(VictoryFireworksPayload payload) {
        RandomSource random = RandomSource.create();
        Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
        // Over this client's own field they go up across the winner's half, where the duel camera sees them.
        boolean onField = ClientField.active() && ClientField.center() != null
                && ClientField.center().distanceToSqr(at) < 30 * 30;
        double winner = onField && at.subtract(ClientField.center()).dot(ClientField.direction()) < 0 ? -1 : 1;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        IntList colors = new IntArrayList(payload.colors().isEmpty() ? List.of(0xFFD040, 0xFFFFFF)
                : payload.colors());
        IntList fade = new IntArrayList(List.of(0xFFFFFF));
        double size = payload.spread();
        // Small bursts, so the field stays in sight; the closing volley is the big one.
        FireworkExplosion.Shape[] shapes = {FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.BURST,
                FireworkExplosion.Shape.SMALL_BALL};
        int count = 12;
        for (int i = 0; i <= count; i++) {
            boolean finale = i == count;
            for (int n = 0; n < (finale ? 4 : 1); n++) {
                Vec3 from;
                double height;
                if (onField) {
                    from = ClientField.toWorld((random.nextDouble() - 0.5) * 1.2 * HALF_WIDTH,
                            winner * (0.15 + random.nextDouble() * 0.6) * FieldLayout.halfLength(), 0.1);
                    height = (3.5 + random.nextDouble() * 3) * size;
                } else {
                    from = at.add((random.nextDouble() - 0.5) * 6 * size, 0.2, (random.nextDouble() - 0.5) * 6 * size);
                    height = (4.5 + random.nextDouble() * 3) * size;
                }
                if (payload.ceiling() > 0) {
                    height = Math.min(height, Math.max(3, payload.ceiling() - 1.5));
                }
                Vec3 to = from.add((random.nextDouble() - 0.5) * 1.5, height, (random.nextDouble() - 0.5) * 1.5);
                // Never right in front of someone's eyes, where a burst would only fill the screen.
                Vec3 away = new Vec3(to.x - camera.x, 0, to.z - camera.z);
                double distance = away.length();
                if (!onField && distance < CLEAR_VIEW) {
                    Vec3 push = (distance < 0.1 ? new Vec3(1, 0, 0) : away.normalize())
                            .scale(CLEAR_VIEW - distance);
                    from = from.add(push);
                    to = to.add(push);
                }
                FireworkExplosion.Shape shape = finale ? (onField ? FireworkExplosion.Shape.STAR
                        : FireworkExplosion.Shape.LARGE_BALL) : shapes[random.nextInt(shapes.length)];
                IntList own = new IntArrayList();
                own.add(colors.getInt(random.nextInt(colors.size())));
                if (colors.size() > 1 || finale) {
                    own.add(colors.getInt(random.nextInt(colors.size())));
                }
                List<FireworkExplosion> burst = List.of(new FireworkExplosion(shape, own, fade, true, finale
                        || random.nextBoolean()));
                rockets.add(new Rocket(from, to, rocketTick + (finale ? 66 + n * 2L : i * 5L + random.nextInt(4)),
                        16 + random.nextInt(6), burst));
            }
        }
    }
}
