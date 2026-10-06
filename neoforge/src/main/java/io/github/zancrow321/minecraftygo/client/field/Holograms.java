package io.github.zancrow321.minecraftygo.client.field;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.client.CardArt;
import io.github.zancrow321.minecraftygo.engine.data.CardInfo;
import io.github.zancrow321.minecraftygo.engine.duel.FieldEvent;
import io.github.zancrow321.minecraftygo.engine.protocol.CardState;
import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static io.github.zancrow321.minecraftygo.client.field.FieldLayout.Slot;
import static io.github.zancrow321.minecraftygo.client.field.FieldLayout.slot;
import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * The artwork hologram for a face-up monster that has no model: its artwork alone, projected above the zone from a
 * ring of light in the colour of its attribute, with a frame in the same colour, its name above and its level and
 * stats below. It flickers in when summoned, bobs, lunges when it attacks, tips back in defense (with a blue
 * frame) and rises and fades when it leaves the field. Until the artwork is downloaded, or if it can't be, the
 * monster stands as its card instead.
 */
final class Holograms {
    /** The artwork's side, in field blocks: with its frame it fits its zone, so neighbours don't overlap. */
    static final double SIZE = 1.5;
    /** The artwork's lower edge above the mat. */
    private static final double LIFT = 0.3;
    /** How far a monster in defense position tips back, in radians. */
    private static final double TILT = Math.toRadians(50);
    /** How much of the way up to the viewer's eye the artwork leans, so it reads from a high camera too. */
    private static final double LEAN = 0.3;
    private static final double RING_RADIUS = 0.95;
    private static final int RING_SEGMENTS = 28;
    private static final int DEFENSE_COLOR = 0x50A8FF;
    /** Smaller than other field labels: a full board puts many of these close together. */
    private static final float LABEL_SCALE = 0.024f;

    private Holograms() {
    }

    /** Whether this monster is drawn as a hologram right now (face-up, no model, artwork ready). */
    static boolean shows(CardState card) {
        return card != null && card.code() != 0 && (card.position() & POS_FACEUP) != 0
                && YgoData.model(card.code()) == null && CardArt.hologram(card.code()) != null;
    }

    /** The colour of an attribute (a single attribute bit), as RGB. */
    static int attributeColor(int attribute) {
        return switch (attribute) {
            case 0x01 -> 0xB07A40; // EARTH
            case 0x02 -> 0x3C8CFF; // WATER
            case 0x04 -> 0xFF4A30; // FIRE
            case 0x08 -> 0x40E070; // WIND
            case 0x10 -> 0xFFD040; // LIGHT
            case 0x20 -> 0xB050FF; // DARK
            case 0x40 -> 0xFFFFFF; // DIVINE
            default -> 0x40C8FF;
        };
    }

    /** The colour of a card's frame for the monster kinds that have their own, or -1. */
    static int typeColor(CardInfo info) {
        if (info == null) {
            return -1;
        }
        if (info.is(TYPE_LINK)) {
            return 0x1E5AC8;
        }
        if (info.is(TYPE_XYZ)) {
            return 0x26262E;
        }
        if (info.is(TYPE_PENDULUM)) {
            return 0x30B890;
        }
        if (info.is(TYPE_SYNCHRO)) {
            return 0xEEEEEE;
        }
        if (info.is(TYPE_FUSION)) {
            return 0x9050C0;
        }
        if (info.is(TYPE_RITUAL)) {
            return 0x5A8CE0;
        }
        return -1;
    }

    private static int frameColor(CardState card) {
        if ((card.position() & POS_DEFENSE) != 0) {
            return DEFENSE_COLOR;
        }
        CardInfo info = YgoData.cards().card(card.code());
        return attributeColor(info == null ? 0 : info.data().attribute());
    }

    /** Draws a monster on the field as a hologram. */
    static void draw(FieldRenderer.Draw draw, int player, int seq, CardState card, long now, float partial) {
        Loc loc = new Loc(player, LOCATION_MZONE, seq, 0);
        FieldAnimation summon = ClientField.pending(FieldEvent.Kind.SUMMON, loc);
        float grow = 1;
        float alpha = 1;
        if (summon != null) {
            if (!summon.started(now)) {
                return; // appears when its summon plays
            }
            float p = summon.progress(now, partial);
            grow = Mth.clamp((p - 0.12f) / 0.45f, 0, 1);
            // Flickers on like a projector warming up.
            alpha = p < 0.6f && (now + seq) % 3 == 0 ? 0.25f : 0.5f + 0.5f * Mth.clamp(p / 0.6f, 0, 1);
        }
        int tint = FieldRenderer.flash(loc, now, partial) ? 0xFF6060 : 0xFFFFFF;
        projection(draw, player, seq, card.code(), (card.position() & POS_DEFENSE) != 0, frameColor(card), grow,
                alpha, tint, FieldRenderer.lunge(loc, now, partial), FieldRenderer.bob(seq, player, now, partial),
                true);
    }

    /** A hologram of a monster that just left the field, rising and fading out. */
    static void drawGhost(FieldRenderer.Draw draw, FieldEvent e, float p, long now) {
        Loc from = e.from();
        if (CardArt.hologram(e.code()) == null) {
            return;
        }
        // Breaks up: flickers harder as it fades.
        float alpha = (1 - p) * ((now % 2 == 0 && p > 0.3f) ? 0.4f : 1);
        CardInfo info = YgoData.cards().card(e.code());
        projection(draw, from.controller(), from.sequence(), e.code(), false,
                attributeColor(info == null ? 0 : info.data().attribute()), 1 - p * 0.4f, alpha, 0xA0E8FF,
                new Vec3(0, p * 1.5, 0), 0.1, false);
    }

    private static void projection(FieldRenderer.Draw draw, int player, int seq, int code, boolean defense, int color,
                                   float grow, float alpha, int tint, Vec3 offset, double bob, boolean ring) {
        CardArt.Texture art = CardArt.hologram(code);
        if (art == null || grow <= 0.001f || alpha <= 0.01f) {
            return;
        }
        Slot s = slot(player, LOCATION_MZONE, seq);
        double k = ClientField.forward().length(); // the field's current size
        Vec3 bottom = ClientField.toWorld(s.x(), s.z(), FieldRenderer.MAT_Y + LIFT + bob).add(offset);
        Vec3 h = towardEye(draw, bottom, player);
        Vec3 rise = rise(draw, bottom, h, defense);
        Vec3 v = rise.scale(SIZE / 2 * grow * k);
        Vec3 u = new Vec3(0, 1, 0).cross(h).scale(SIZE / 2 * k);
        Vec3 center = bottom.add(v);
        int a = (int) (255 * alpha);
        draw.texQuad(art.location(), center, u, v, (a << 24) | tint, true);

        int type = typeColor(YgoData.cards().card(code));
        if (type != -1) {
            // An inner band in the colour of the card's frame: Fusion, Ritual, Synchro, Xyz, Pendulum or Link.
            VertexConsumer solid = draw.buffers().getBuffer(RenderType.debugQuads());
            frame(draw, solid, center, u, v, rise.normalize().scale(0.07 * k),
                    u.normalize().scale(0.07 * k), ((int) (220 * alpha) << 24) | type);
            u = u.add(u.normalize().scale(0.07 * k));
            v = v.add(v.normalize().scale(0.07 * k));
        }
        VertexConsumer glow = draw.buffers().getBuffer(RenderType.lightning());
        int frame = ((int) (230 * alpha) << 24) | color;
        frame(draw, glow, center, u, v, rise.normalize().scale(0.08 * k), u.normalize().scale(0.08 * k), frame);
        if (ring) {
            Vec3 base = ClientField.toWorld(s.x(), s.z(), FieldRenderer.MAT_Y + 0.01);
            ring(draw, glow, base, k, ((int) (200 * alpha) << 24) | color);
            cone(draw, glow, base, bottom, u, k, color, alpha);
        }
    }

    /**
     * Level and toward the viewer, the same for every hologram: they all face the way the viewer looks at the field
     * as a whole, so a row of them stands side by side instead of fanning out into each other.
     */
    private static Vec3 towardEye(FieldRenderer.Draw draw, Vec3 at, int player) {
        Vec3 toEye = draw.cam().subtract(ClientField.center());
        Vec3 h = new Vec3(toEye.x, 0, toEye.z);
        return h.lengthSqr() < 1e-6 ? ClientField.forward().normalize().scale(player == 0 ? -1 : 1) : h.normalize();
    }

    /**
     * Up the artwork, from its lower edge: it faces whoever looks at it, leaning back partway toward their eye so it
     * reads from a high camera too, and further back in defense position.
     */
    private static Vec3 rise(FieldRenderer.Draw draw, Vec3 bottom, Vec3 h, boolean defense) {
        Vec3 toEye = draw.cam().subtract(bottom);
        double tilt = Math.atan2(toEye.y, Math.sqrt(toEye.x * toEye.x + toEye.z * toEye.z)) * LEAN
                + (defense ? TILT : 0);
        tilt = Mth.clamp(tilt, 0, Math.toRadians(80));
        return new Vec3(0, Math.cos(tilt), 0).subtract(h.scale(Math.sin(tilt)));
    }

    /** A glowing frame just outside the artwork, seen from both sides. */
    private static void frame(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 c, Vec3 u, Vec3 v, Vec3 tv, Vec3 tu,
                              int argb) {
        Vec3 bl = c.subtract(u).subtract(v);
        Vec3 br = c.add(u).subtract(v);
        Vec3 tl = c.subtract(u).add(v);
        Vec3 tr = c.add(u).add(v);
        Vec3 ou = u.normalize().scale(tu.length());
        // bottom, top, left, right
        both(draw, vc, bl.subtract(ou).subtract(tv), br.add(ou).subtract(tv), br.add(ou), bl.subtract(ou), argb);
        both(draw, vc, tl.subtract(ou), tr.add(ou), tr.add(ou).add(tv), tl.subtract(ou).add(tv), argb);
        both(draw, vc, bl.subtract(ou), bl, tl, tl.subtract(ou), argb);
        both(draw, vc, br, br.add(ou), tr.add(ou), tr, argb);
    }

    /** The ring of light on the zone the hologram is projected from. */
    private static void ring(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 base, double k, int argb) {
        Vec3 r = ClientField.right().normalize();
        Vec3 f = ClientField.forward().normalize();
        double outer = RING_RADIUS * k;
        double inner = (RING_RADIUS - 0.1) * k;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = 2 * Math.PI * i / RING_SEGMENTS;
            double a1 = 2 * Math.PI * (i + 1) / RING_SEGMENTS;
            Vec3 d0 = r.scale(Math.cos(a0)).add(f.scale(Math.sin(a0)));
            Vec3 d1 = r.scale(Math.cos(a1)).add(f.scale(Math.sin(a1)));
            both(draw, vc, base.add(d0.scale(inner)), base.add(d0.scale(outer)), base.add(d1.scale(outer)),
                    base.add(d1.scale(inner)), argb);
        }
    }

    /** Faint light rising from the ring to the artwork. */
    private static void cone(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 base, Vec3 top, Vec3 u, double k,
                             int color, float alpha) {
        Vec3 r = u.normalize().scale(RING_RADIUS * k);
        Vec3 f = u.normalize().cross(new Vec3(0, 1, 0)).scale(RING_RADIUS * k);
        int low = ((int) (90 * alpha) << 24) | color;
        int high = color; // fades to nothing at the top
        for (Vec3 side : new Vec3[]{r, f}) {
            Vec3 wide = side.normalize().scale(u.length() * 0.9);
            Vec3[] q = {base.subtract(side), base.add(side), top.add(wide), top.subtract(wide)};
            int[] colors = {low, low, high, high};
            quad(draw, vc, q, colors);
            quad(draw, vc, new Vec3[]{q[3], q[2], q[1], q[0]}, new int[]{high, high, low, low});
        }
    }

    private static void both(FieldRenderer.Draw draw, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        draw.colorQuad(vc, a, b, c, d, argb);
        draw.colorQuad(vc, d, c, b, a, argb);
    }

    private static void quad(FieldRenderer.Draw draw, VertexConsumer vc, Vec3[] corners, int[] colors) {
        for (int i = 0; i < 4; i++) {
            Vec3 p = corners[i];
            int argb = colors[i];
            vc.addVertex(draw.pose(), (float) (p.x - draw.cam().x), (float) (p.y - draw.cam().y),
                    (float) (p.z - draw.cam().z)).setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF,
                    argb >>> 24);
        }
    }

    /** The name over a hologram and its level and stats under it. */
    static void labels(FieldRenderer.Draw draw, PoseStack poses, Font font, int player, int seq, CardState card,
                       long now, float partial) {
        FieldAnimation summon = ClientField.pending(FieldEvent.Kind.SUMMON, new Loc(player, LOCATION_MZONE, seq, 0));
        if (summon != null && summon.progress(now, partial) < 0.5f) {
            return; // not there yet
        }
        Slot s = slot(player, LOCATION_MZONE, seq);
        boolean defense = (card.position() & POS_DEFENSE) != 0;
        double k = ClientField.forward().length();
        Vec3 bottom = ClientField.toWorld(s.x(), s.z(), FieldRenderer.MAT_Y + LIFT
                + FieldRenderer.bob(seq, player, now, partial))
                .add(FieldRenderer.lunge(new Loc(player, LOCATION_MZONE, seq, 0), now, partial));
        Vec3 top = bottom.add(rise(draw, bottom, towardEye(draw, bottom, player), defense).scale(SIZE * k));
        CardInfo info = YgoData.cards().card(card.code());
        // Both lines stay within the zone's width, so the labels of neighbouring monsters don't run into each other.
        int room = (int) (FieldLayout.ZONE_WIDTH * k * 0.95 / LABEL_SCALE);
        FieldRenderer.label(draw, poses, font, top.add(0, 0.3 * k, 0),
                fit(font, YgoData.text().cardName(card.code()), room), 0xFF000000 | frameColor(card), LABEL_SCALE);
        String stats = stats(info, card, true);
        FieldRenderer.label(draw, poses, font, bottom.subtract(0, 0.08 * k, 0),
                font.width(stats) <= room ? stats : fit(font, stats(info, card, false), room),
                defense ? 0xFF80C8FF : 0xFFFFE070, LABEL_SCALE);
    }

    /**
     * "LIGHT ★7  2500 / 2000", with rank or link rating where they apply; without the attribute (the ring shows its
     * colour) when {@code attribute} is false.
     */
    private static String stats(CardInfo info, CardState card, boolean attribute) {
        if (info == null) {
            return card.attack() + " / " + card.defense();
        }
        String prefix = attribute ? YgoData.text().system(1010 + Integer.numberOfTrailingZeros(
                Math.max(1, info.data().attribute()))) + "  " : "";
        int level = info.data().level();
        if (info.is(TYPE_LINK)) {
            return prefix + "LINK-" + level + "  " + card.attack();
        }
        String stars = info.is(TYPE_XYZ) ? "Rank " + level : "★" + level;
        return prefix + stars + "  " + card.attack() + "/" + card.defense();
    }

    /** {@code text}, cut with an ellipsis to {@code room} pixels. */
    private static String fit(Font font, String text, int room) {
        if (font.width(text) <= room) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, room - font.width("…"))) + "…";
    }
}
