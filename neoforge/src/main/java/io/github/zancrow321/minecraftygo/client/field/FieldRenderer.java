package io.github.zancrow321.minecraftygo.client.field;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.client.CardArt;
import io.github.zancrow321.minecraftygo.client.ClientDuel;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.engine.duel.Board;
import io.github.zancrow321.minecraftygo.engine.duel.DuelTable;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.engine.duel.FieldEvent;
import io.github.zancrow321.minecraftygo.engine.protocol.CardState;
import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import io.github.zancrow321.minecraftygo.entity.MonsterEntity;
import io.github.zancrow321.minecraftygo.entity.YgoEntities;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.zancrow321.minecraftygo.client.field.FieldLayout.*;
import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;

/**
 * Draws the projected duel field: the holo mat, monsters standing on their zones (GeckoLib models), hologram
 * spell/trap cards, face-down cards and piles, ATK/DEF readouts, and the effects queued in {@link ClientField}.
 */
public final class FieldRenderer {
    private static final ResourceLocation CARD_BLANK = texture("card_blank");
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    private static final double MAT_Y = 0.02;
    private static final double CARD_Y = 0.04;
    /** Largest footprint and height a monster model is scaled down to fit. */
    private static final double MODEL_MAX_WIDTH = 2.6;
    private static final double MODEL_MAX_HEIGHT = 3.4;

    /** Stand-in entities that only exist to be drawn, by zone. */
    private static final Map<Integer, MonsterEntity> proxies = new HashMap<>();
    /** Animations whose start sounds/particles already played. */
    private static final Set<FieldAnimation> started = new HashSet<>();

    private FieldRenderer() {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "textures/field/" + name + ".png");
    }

    static void reset() {
        proxies.clear();
        started.clear();
    }

    // ------------------------------------------------------------------ ticking: proxies, particles, sounds

    static void tickEffects() {
        proxies.values().forEach(p -> p.tickCount++);
        long now = ClientField.tick();
        started.removeIf(a -> !ClientField.animations().contains(a));
        for (FieldAnimation a : ClientField.animations()) {
            if (a.started(now) && started.add(a)) {
                onStart(a);
            }
        }
    }

    private static void onStart(FieldAnimation a) {
        FieldEvent e = a.event();
        Slot slot = e.from() != null && !e.from().isNone() ? slot(e.from()) : null;
        Vec3 at = slot != null ? ClientField.toWorld(slot.x(), slot.z(), 0.5) : null;
        switch (e.kind()) {
            case SUMMON -> {
                if (at != null) {
                    burst(ParticleTypes.END_ROD, at, 30, 0.6, 0.15);
                    sound(SoundEvents.BEACON_POWER_SELECT, at, 1.4f);
                }
            }
            case ACTIVATE -> {
                Vec3 p = at != null ? at : activationPoint(e);
                burst(ParticleTypes.ENCHANT, p.add(0, 1, 0), 40, 0.8, 0.5);
                sound(SoundEvents.AMETHYST_BLOCK_RESONATE, p, 1.2f);
            }
            case ATTACK -> {
                if (at != null) {
                    sound(SoundEvents.PLAYER_ATTACK_SWEEP, at, 0.8f);
                }
            }
            case LEAVE -> {
                if (at != null) {
                    burst(new BlockParticleOption(ParticleTypes.BLOCK,
                            Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()), at.add(0, 0.5, 0), 60, 0.7, 0.2);
                    sound(SoundEvents.GLASS_BREAK, at, 1.0f);
                }
            }
            case DAMAGE -> sound(SoundEvents.PLAYER_HURT, sideCenter(e.player()), 0.9f);
            case RECOVER -> sound(SoundEvents.AMETHYST_BLOCK_CHIME, sideCenter(e.player()), 1.3f);
            default -> {
            }
        }
    }

    private static Vec3 activationPoint(FieldEvent e) {
        return ClientField.toWorld(0, (e.player() == 0 ? -1 : 1) * MONSTER_ROW, 0.5);
    }

    private static Vec3 sideCenter(int player) {
        return ClientField.toWorld(0, (player == 0 ? -1 : 1) * HALF_LENGTH, 1);
    }

    private static void burst(ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var random = level.getRandom();
        for (int i = 0; i < count; i++) {
            level.addParticle(particle, at.x + (random.nextDouble() - 0.5) * spread * 2,
                    at.y + random.nextDouble() * spread * 2, at.z + (random.nextDouble() - 0.5) * spread * 2,
                    (random.nextDouble() - 0.5) * speed, random.nextDouble() * speed, (random.nextDouble() - 0.5) * speed);
        }
    }

    private static void sound(SoundEvent sound, Vec3 at, float pitch) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, 0.8f, pitch, false);
        }
    }

    // ------------------------------------------------------------------ rendering

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !ClientField.active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        DuelView view = ClientDuel.view();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack poses = event.getPoseStack();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ClientField.updateScale(partial);
        long now = ClientField.tick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Draw draw = new Draw(poses.last(), cam, buffers);

        drawMat(draw, now, partial);
        buffers.endBatch(RenderType.debugQuads());

        Board board = view.board();
        for (int player = 0; player < 2; player++) {
            Board.Side side = board.side(player);
            for (int seq = 0; seq < side.spells().size(); seq++) {
                CardState card = side.spells().get(seq);
                if (card != null) {
                    drawSpell(draw, player, seq, card, now, partial);
                }
            }
            for (int seq = 0; seq < side.monsters().size(); seq++) {
                CardState card = side.monsters().get(seq);
                if (card != null && !faceUp(card) || card != null && model(card) == null) {
                    drawFlatOrStanding(draw, player, seq, card, now, partial);
                }
            }
            drawPiles(draw, player, side);
        }
        drawEffects(draw, now, partial);
        buffers.endBatch();

        drawMonsters(draw, poses, board, now, partial);
        buffers.endBatch();
        drawLabels(draw, poses, board);
        buffers.endBatch();
    }

    /** Camera-relative quad drawing in the view-rotated pose. */
    private record Draw(PoseStack.Pose pose, Vec3 cam, MultiBufferSource.BufferSource buffers) {
        void colorQuad(VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
            int alpha = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
            for (Vec3 v : new Vec3[]{a, b, c, d}) {
                vc.addVertex(pose, (float) (v.x - cam.x), (float) (v.y - cam.y), (float) (v.z - cam.z))
                        .setColor(r, g, bl, alpha);
            }
        }

        void texQuad(ResourceLocation texture, Vec3 center, Vec3 u, Vec3 v, int argb, boolean bothSides) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(texture));
            int alpha = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
            Vec3 normal = u.cross(v).normalize();
            Vec3[] corners = {center.subtract(u).subtract(v), center.add(u).subtract(v), center.add(u).add(v),
                    center.subtract(u).add(v)};
            float[][] uvs = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
            for (int i = 0; i < 4; i++) {
                vertex(vc, corners[i], uvs[i], normal, r, g, bl, alpha);
            }
            if (bothSides) {
                // The back face shows the same image mirrored back to readable.
                for (int i = 3; i >= 0; i--) {
                    vertex(vc, corners[i], new float[]{1 - uvs[i][0], uvs[i][1]}, normal.scale(-1), r, g, bl, alpha);
                }
            }
        }

        private void vertex(VertexConsumer vc, Vec3 p, float[] uv, Vec3 n, int r, int g, int b, int a) {
            vc.addVertex(pose, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                    .setColor(r, g, b, a).setUv(uv[0], uv[1]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        }
    }

    private static void drawMat(Draw draw, long now, float partial) {
        VertexConsumer vc = draw.buffers().getBuffer(RenderType.debugQuads());
        Vec3 r = ClientField.right();
        Vec3 f = ClientField.forward();
        float pulse = 0.5f + 0.5f * Mth.sin((now + partial) / 4f);
        int you = ClientDuel.view().you();
        // A faint plate under the whole mat.
        Vec3 c = ClientField.toWorld(0, 0, MAT_Y - 0.005);
        Vec3 hu = r.scale(HALF_WIDTH + 0.3);
        Vec3 hv = f.scale(HALF_LENGTH + 0.3);
        draw.colorQuad(vc, c.subtract(hu).subtract(hv), c.add(hu).subtract(hv), c.add(hu).add(hv),
                c.subtract(hu).add(hv), 0x300A2A4A);
        for (int[] zone : ZONES) {
            Loc loc = new Loc(zone[0], zone[1], zone[2], 0);
            Slot s = slot(loc);
            boolean own = zone[0] == you;
            int fill = own ? 0x5018506E : 0x50401C40;
            int edge = own ? 0xC038E0FF : 0xC0E040A0;
            if (ClientField.split() && (zone[1] == LOCATION_MZONE || zone[1] == LOCATION_SZONE) && zone[2] < 5
                    && DuelTable.zoneOwner(zone[2]) == 1) {
                // The second partner's half: green for your team, orange for theirs.
                fill = own ? 0x50186E40 : 0x506E4018;
                edge = own ? 0xC040FFA0 : 0xC0FFA040;
            }
            if (ClientField.selected(loc)) {
                fill = 0x8030C060;
                edge = 0xFF60FF90;
            } else if (ClientField.selectable(loc)) {
                int a = (int) (0x60 + 0x80 * pulse);
                fill = (a << 24) | 0x806010;
                edge = 0xFFFFD040;
            }
            if (loc.equals(ClientField.hovered() == null ? null : ClientField.hovered().place())) {
                edge = 0xFFFFFFFF;
            }
            Vec3 center = ClientField.toWorld(s.x(), s.z(), MAT_Y);
            Vec3 u = r.scale(ZONE_WIDTH / 2 - 0.08);
            Vec3 v = f.scale(ZONE_DEPTH / 2 - 0.08);
            draw.colorQuad(vc, center.subtract(u).subtract(v), center.add(u).subtract(v), center.add(u).add(v),
                    center.subtract(u).add(v), fill);
            border(draw, vc, center.add(0, 0.002, 0), u, v, 0.06, edge);
        }
    }

    private static void border(Draw draw, VertexConsumer vc, Vec3 c, Vec3 u, Vec3 v, double t, int argb) {
        Vec3 un = u.normalize().scale(t);
        Vec3 vn = v.normalize().scale(t);
        // four thin strips
        draw.colorQuad(vc, c.subtract(u).subtract(v), c.add(u).subtract(v), c.add(u).subtract(v).add(vn),
                c.subtract(u).subtract(v).add(vn), argb);
        draw.colorQuad(vc, c.subtract(u).add(v).subtract(vn), c.add(u).add(v).subtract(vn), c.add(u).add(v),
                c.subtract(u).add(v), argb);
        draw.colorQuad(vc, c.subtract(u).subtract(v), c.subtract(u).add(un).subtract(v), c.subtract(u).add(un).add(v),
                c.subtract(u).add(v), argb);
        draw.colorQuad(vc, c.add(u).subtract(un).subtract(v), c.add(u).subtract(v), c.add(u).add(v),
                c.add(u).subtract(un).add(v), argb);
    }

    private static boolean faceUp(CardState card) {
        return (card.position() & POS_FACEUP) != 0;
    }

    private static boolean defense(CardState card) {
        return (card.position() & POS_DEFENSE) != 0;
    }

    private static CardPool.Model model(CardState card) {
        return card.code() == 0 ? null : YgoData.pool().model(card.code());
    }

    private static ResourceLocation front(int code) {
        CardArt.Texture art = CardArt.get(code);
        return art != null ? art.location() : CARD_BLANK;
    }

    /** A spell or trap: a standing hologram when face-up, a flat card back when set. */
    private static void drawSpell(Draw draw, int player, int seq, CardState card, long now, float partial) {
        Slot s = slot(player, LOCATION_SZONE, seq);
        if (!faceUp(card)) {
            flatCard(draw, player, s, ClientField.sleeve(player, seq), false, 0, 0xFFFFFFFF);
            return;
        }
        if (seq == 5) { // Field Spells lie flat, face-up.
            flatCard(draw, player, s, front(card.code()), false, 0, 0xFFFFFFFF);
            return;
        }
        FieldAnimation activation = ClientField.pending(FieldEvent.Kind.ACTIVATE, new Loc(player, LOCATION_SZONE,
                seq, 0));
        double rise = activation != null && activation.started(now)
                ? Math.sin(Math.PI * activation.progress(now, partial)) * 0.6 : 0;
        standingCard(draw, s, player, card.code(), 0.15 + rise, 0xE8FFFFFF);
    }

    /** Monsters without a model, and face-down monsters. */
    private static void drawFlatOrStanding(Draw draw, int player, int seq, CardState card, long now, float partial) {
        Slot s = slot(player, LOCATION_MZONE, seq);
        if (!faceUp(card)) {
            flatCard(draw, player, s, ClientField.sleeve(player, seq), defense(card), 0, 0xFFFFFFFF);
            return;
        }
        if (defense(card)) {
            flatCard(draw, player, s, front(card.code()), true, 0, 0xFFFFFFFF);
        } else {
            standingCard(draw, s, player, card.code(), 0.15 + bob(seq, player, now, partial), 0xF0FFFFFF);
        }
    }

    private static double bob(int seq, int player, long now, float partial) {
        return 0.08 + 0.06 * Math.sin((now + partial + seq * 7 + player * 13) / 12.0);
    }

    /** A card lying on the mat, readable from its owner's end (top of the card away from them). */
    private static void flatCard(Draw draw, int player, Slot s, ResourceLocation tex, boolean sideways, double up,
                                 int argb) {
        Vec3 c = ClientField.toWorld(s.x(), s.z(), CARD_Y + up);
        double mirror = player == 0 ? 1 : -1;
        Vec3 u = ClientField.right().scale(mirror * CARD_WIDTH / 2);
        Vec3 v = ClientField.forward().scale(mirror * CARD_HEIGHT / 2);
        if (sideways) {
            // Turned a quarter to the owner's left, as a card in defense position.
            Vec3 t = u.normalize().scale(CARD_HEIGHT / 2);
            u = v.normalize().scale(-CARD_WIDTH / 2);
            v = t;
        }
        draw.texQuad(tex, c, u, v, argb, false);
    }

    private static void standingCard(Draw draw, Slot s, int player, int code, double lift, int argb) {
        double back = (player == 0 ? -1 : 1) * 0.3;
        Vec3 c = ClientField.toWorld(s.x(), s.z() + back, lift + CARD_HEIGHT / 2);
        Vec3 u = ClientField.right().scale(CARD_WIDTH / 2 * (player == 0 ? 1 : -1));
        Vec3 v = new Vec3(0, CARD_HEIGHT / 2, 0);
        // Front faces the opponent; both sides show the art so everyone can read it.
        draw.texQuad(front(code), c, u.scale(-1), v, argb, true);
    }

    private static void drawPiles(Draw draw, int player, Board.Side side) {
        pile(draw, player, slot(player, LOCATION_DECK, 0), ClientField.sleeve(player), side.deckCount());
        pile(draw, player, slot(player, LOCATION_EXTRA, 0), ClientField.sleeve(player), side.extra().size());
        if (!side.graveyard().isEmpty()) {
            pile(draw, player, slot(player, LOCATION_GRAVE, 0), front(side.graveyard().getLast().code()),
                    side.graveyard().size());
        }
        if (!side.banished().isEmpty()) {
            CardState top = side.banished().getLast();
            pile(draw, player, slot(player, LOCATION_REMOVED, 0), top.code() == 0 ? ClientField.sleeve(player) : front(top.code()),
                    side.banished().size());
        }
    }

    private static void pile(Draw draw, int player, Slot s, ResourceLocation top, int count) {
        if (count <= 0) {
            return;
        }
        double height = Math.min(count, 60) * 0.008;
        flatCard(draw, player, s, top, false, height, 0xFFFFFFFF);
        // A glowing side so the stack reads as a pile.
        VertexConsumer vc = draw.buffers().getBuffer(RenderType.lightning());
        Vec3 u = ClientField.right().scale(CARD_WIDTH / 2);
        Vec3 toward = ClientField.forward().scale((player == 0 ? -1 : 1) * CARD_HEIGHT / 2);
        Vec3 base = ClientField.toWorld(s.x(), s.z(), CARD_Y).add(toward);
        Vec3 up = new Vec3(0, height, 0);
        draw.colorQuad(vc, base.subtract(u), base.add(u), base.add(u).add(up), base.subtract(u).add(up), 0x6038E0FF);
        draw.colorQuad(vc, base.subtract(u).add(up), base.add(u).add(up), base.add(u), base.subtract(u), 0x6038E0FF);
    }

    /** Summon beams and activation pillars (additive glow). */
    private static void drawEffects(Draw draw, long now, float partial) {
        VertexConsumer vc = draw.buffers().getBuffer(RenderType.lightning());
        for (FieldAnimation a : ClientField.animations()) {
            if (!a.started(now)) {
                continue;
            }
            float p = a.progress(now, partial);
            FieldEvent e = a.event();
            Slot s = e.from() == null || e.from().isNone() ? null : slot(e.from());
            switch (e.kind()) {
                case SUMMON -> {
                    if (s != null) {
                        beam(draw, vc, s, 7 * Math.min(1, p * 3), 0.9 * (1 - p), (int) (200 * (1 - p)));
                    }
                }
                case ACTIVATE -> {
                    if (s != null) {
                        beam(draw, vc, s, 3.5, 0.5 + 0.4 * p, (int) (150 * Math.sin(Math.PI * p)));
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void beam(Draw draw, VertexConsumer vc, Slot s, double height, double radius, int alpha) {
        if (alpha <= 0) {
            return;
        }
        Vec3 base = ClientField.toWorld(s.x(), s.z(), MAT_Y);
        int color = (Math.min(255, alpha) << 24) | 0x40C8FF;
        Vec3 r = ClientField.right().scale(radius);
        Vec3 f = ClientField.forward().scale(radius);
        Vec3 up = new Vec3(0, height, 0);
        // Two crossed planes, each wound both ways, read as a column from every side.
        draw.colorQuad(vc, base.subtract(r), base.add(r), base.add(r).add(up), base.subtract(r).add(up), color);
        draw.colorQuad(vc, base.subtract(r).add(up), base.add(r).add(up), base.add(r), base.subtract(r), color);
        draw.colorQuad(vc, base.subtract(f), base.add(f), base.add(f).add(up), base.subtract(f).add(up), color);
        draw.colorQuad(vc, base.subtract(f).add(up), base.add(f).add(up), base.add(f), base.subtract(f), color);
    }

    /** Face-up monsters with models, plus fading ghosts of monsters that just left the field. */
    private static void drawMonsters(Draw draw, PoseStack poses, Board board, long now, float partial) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            for (int player = 0; player < 2; player++) {
                List<CardState> monsters = board.side(player).monsters();
                for (int seq = 0; seq < monsters.size(); seq++) {
                    CardState card = monsters.get(seq);
                    CardPool.Model model = card == null || !faceUp(card) ? null : model(card);
                    if (model == null) {
                        continue;
                    }
                    Loc loc = new Loc(player, LOCATION_MZONE, seq, 0);
                    FieldAnimation summon = ClientField.pending(FieldEvent.Kind.SUMMON, loc);
                    float grow = 1;
                    if (summon != null) {
                        if (!summon.started(now)) {
                            continue; // appears when its summon plays
                        }
                        float p = summon.progress(now, partial);
                        grow = Mth.clamp((p - 0.15f) / 0.5f, 0, 1);
                    }
                    MonsterEntity proxy = proxy(player * 16 + seq, card.code());
                    proxy.alpha = summon != null ? 0.4f + 0.6f * grow : 1;
                    proxy.tint = flash(loc, now, partial) ? 0xFF5050 : 0xFFFFFF;
                    Vec3 offset = lunge(loc, now, partial);
                    drawModel(draw, poses, dispatcher, proxy, model, player, seq, defense(card), grow, offset, now,
                            partial);
                }
            }
            for (FieldAnimation a : ClientField.animations()) {
                FieldEvent e = a.event();
                if (e.kind() != FieldEvent.Kind.LEAVE || !a.started(now) || e.code() == 0
                        || (e.from().location() & LOCATION_MZONE) == 0) {
                    continue;
                }
                CardPool.Model model = YgoData.pool().model(e.code());
                if (model == null) {
                    continue;
                }
                float p = a.progress(now, partial);
                MonsterEntity ghost = proxy(1000 + e.from().controller() * 16 + e.from().sequence(), e.code());
                ghost.alpha = 1 - p;
                ghost.tint = 0xA0E8FF;
                drawModel(draw, poses, dispatcher, ghost, model, e.from().controller(), e.from().sequence(), false,
                        1 - p * 0.5f, new Vec3(0, p * 1.5, 0), now, partial);
            }
        } finally {
            dispatcher.setRenderShadow(true);
        }
    }

    private static void drawModel(Draw draw, PoseStack poses, EntityRenderDispatcher dispatcher, MonsterEntity proxy,
                                  CardPool.Model model, int player, int seq, boolean defense, float grow, Vec3 offset,
                                  long now, float partial) {
        Slot s = slot(player, LOCATION_MZONE, seq);
        double scale = modelScale(model) * grow;
        if (scale <= 0.001) {
            return;
        }
        Vec3 at = ClientField.toWorld(s.x(), s.z(), MAT_Y + bob(seq, player, now, partial)).add(offset);
        float yaw = ClientField.facingYaw(player) + (defense ? 90 : 0);
        proxy.setYRot(yaw);
        proxy.yRotO = yaw;
        proxy.setPos(at);
        poses.pushPose();
        poses.translate(at.x - draw.cam().x, at.y - draw.cam().y, at.z - draw.cam().z);
        poses.scale((float) scale, (float) scale, (float) scale);
        dispatcher.render(proxy, 0, 0, 0, yaw, partial, poses, draw.buffers(), FULL_BRIGHT);
        poses.popPose();
    }

    static double modelScale(CardPool.Model model) {
        double scale = 1;
        if (model.width() > MODEL_MAX_WIDTH) {
            scale = MODEL_MAX_WIDTH / model.width();
        }
        if (model.height() * scale > MODEL_MAX_HEIGHT) {
            scale = MODEL_MAX_HEIGHT / model.height();
        }
        return Math.max(scale, 0.12);
    }

    private static MonsterEntity proxy(int key, int code) {
        MonsterEntity proxy = proxies.get(key);
        ClientLevel level = Minecraft.getInstance().level;
        if (proxy == null || proxy.code() != code || proxy.level() != level) {
            proxy = YgoEntities.MONSTER.get().create(level);
            if (proxy == null) {
                throw new IllegalStateException("could not create a monster proxy");
            }
            proxy.setCode(code);
            proxies.put(key, proxy);
        }
        return proxy;
    }

    /** Where an attacking monster is pushed toward its target: out and back over the attack. */
    private static Vec3 lunge(Loc loc, long now, float partial) {
        FieldAnimation attack = ClientField.pending(FieldEvent.Kind.ATTACK, loc);
        if (attack == null || !attack.started(now)) {
            return Vec3.ZERO;
        }
        Slot from = slot(loc);
        Loc target = attack.event().to();
        Slot to = target == null || target.isNone() ? new Slot(from.x(), (loc.controller() == 0 ? 1 : -1) * HALF_LENGTH)
                : slot(target);
        double reach = Math.sin(Math.PI * attack.progress(now, partial)) * 0.7;
        return ClientField.toWorld(to.x(), to.z(), 0).subtract(ClientField.toWorld(from.x(), from.z(), 0)).scale(reach);
    }

    /** Whether the monster at {@code loc} is being hit right now. */
    private static boolean flash(Loc loc, long now, float partial) {
        for (FieldAnimation a : ClientField.animations()) {
            if (a.event().kind() == FieldEvent.Kind.ATTACK && a.started(now)
                    && FieldLayout.sameZone(a.event().to(), loc)) {
                float p = a.progress(now, partial);
                return p > 0.4f && p < 0.8f && ((now / 2) % 2 == 0);
            }
        }
        return false;
    }

    /** ATK/DEF over each face-up monster, and the hovered card's name. */
    private static void drawLabels(Draw draw, PoseStack poses, Board board) {
        Font font = Minecraft.getInstance().font;
        for (int player = 0; player < 2; player++) {
            List<CardState> monsters = board.side(player).monsters();
            for (int seq = 0; seq < monsters.size(); seq++) {
                CardState card = monsters.get(seq);
                if (card == null || !faceUp(card)) {
                    continue;
                }
                CardPool.Model model = model(card);
                double height = model != null ? model.height() * modelScale(model) : CARD_HEIGHT + 0.15;
                Slot s = slot(player, LOCATION_MZONE, seq);
                String text = card.attack() + " / " + card.defense();
                label(draw, poses, font, ClientField.toWorld(s.x(), s.z(), height + 0.5), text,
                        defense(card) ? 0xFF80C8FF : 0xFFFFE070);
            }
        }
        Loc hovered = ClientField.hovered();
        if (hovered != null) {
            CardState card = cardAt(board, hovered);
            if (card != null && card.code() != 0) {
                Slot s = slot(hovered);
                label(draw, poses, font, ClientField.toWorld(s.x(), s.z(), 0.35), YgoData.text().cardName(card.code()),
                        0xFFFFFFFF);
            }
        }
    }

    static CardState cardAt(Board board, Loc loc) {
        Board.Side side = board.side(loc.controller());
        List<CardState> zone = switch (loc.location()) {
            case LOCATION_MZONE -> side.monsters();
            case LOCATION_SZONE -> side.spells();
            case LOCATION_GRAVE -> side.graveyard().isEmpty() ? null : List.of(side.graveyard().getLast());
            case LOCATION_REMOVED -> side.banished().isEmpty() ? null : List.of(side.banished().getLast());
            default -> null;
        };
        if (zone == null) {
            return null;
        }
        int index = loc.location() == LOCATION_MZONE || loc.location() == LOCATION_SZONE ? loc.sequence() : 0;
        return index < zone.size() ? zone.get(index) : null;
    }

    private static void label(Draw draw, PoseStack poses, Font font, Vec3 at, String text, int color) {
        poses.pushPose();
        poses.translate(at.x - draw.cam().x, at.y - draw.cam().y, at.z - draw.cam().z);
        poses.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poses.scale(0.03f, -0.03f, 0.03f);
        Matrix4f matrix = poses.last().pose();
        float x = -font.width(text) / 2f;
        font.drawInBatch(text, x, 0, color, false, matrix, draw.buffers(), Font.DisplayMode.SEE_THROUGH, 0x60000000,
                FULL_BRIGHT);
        poses.popPose();
    }
}
