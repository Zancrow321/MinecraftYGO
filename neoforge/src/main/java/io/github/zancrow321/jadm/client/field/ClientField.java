package io.github.zancrow321.jadm.client.field;

import io.github.zancrow321.jadm.cosmetics.Cosmetics;
import io.github.zancrow321.jadm.engine.duel.DuelTable;
import net.minecraft.resources.ResourceLocation;
import io.github.zancrow321.jadm.client.ClientDuel;
import io.github.zancrow321.jadm.client.disk.DiskClient;
import io.github.zancrow321.jadm.client.duel.DuelMode;
import io.github.zancrow321.jadm.engine.duel.Board;
import io.github.zancrow321.jadm.engine.duel.DuelView;
import io.github.zancrow321.jadm.engine.duel.FieldEvent;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.duel.DuelStaging;
import io.github.zancrow321.jadm.client.duel.DuelUi;
import io.github.zancrow321.jadm.engine.protocol.CardState;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import io.github.zancrow321.jadm.engine.text.PromptView;
import io.github.zancrow321.jadm.network.DuelFieldPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The duel field projected into the world on this client: where it is, which zone the player is looking at, the
 * queue of effects to play, and what a right-click on a zone does. Only touched on the client thread.
 */
public final class ClientField {
    /** How long the field stays up after the duel ends, in ticks. */
    private static final int LINGER_TICKS = 200;
    private static final double REACH = 40;
    /** How long the field takes to grow out of its centre once the disk has unfolded, in ticks. */
    private static final int GROW_TICKS = 16;

    private static Vec3 center;
    private static Vec3 forward;
    private static Vec3 right;
    private static long tick;
    private static long endsAt = -1;
    private static long queueFree;
    /** When the field starts growing, and its current size (0 to 1) for this frame. */
    private static long revealAt;
    private static double scale = 1;
    /** How far the field has grown (0 to 1), and its full size: 1 normally, other sizes on a player-built arena. */
    private static double grow = 1;
    private static double size = 1;
    /** Blocks of room over the mat (0 for open sky), and whether only the zone frames are drawn. */
    private static double ceiling;
    private static boolean outline;
    private static final List<FieldAnimation> animations = new ArrayList<>();
    /** The board as of the previous view, to find attackers that the new view no longer shows. */
    private static Board lastBoard;
    private static Loc hovered;
    /** Card backs per team, or per duelist (team * 2 + partner) on a split field. */
    private static final List<ResourceLocation> sleeves = new ArrayList<>();
    private static boolean split;
    private static boolean watching;

    private ClientField() {
    }

    public static void receive(DuelFieldPayload payload) {
        if (!payload.active()) {
            clear();
            return;
        }
        center = new Vec3(payload.x(), payload.y(), payload.z());
        ClientDuel.forget(); // a new duel: its first view is on its way
        split = payload.layout().split();
        watching = payload.layout().watching();
        size = payload.layout().size() > 0 ? payload.layout().size() : 1;
        ceiling = payload.layout().ceiling();
        outline = payload.layout().outline();
        sleeves.clear();
        payload.layout().sleeves().forEach(s -> sleeves.add(Cosmetics.sleeveTexture(s)));
        forward = Vec3.directionFromRotation(0, payload.yaw());
        right = new Vec3(-forward.z, 0, forward.x);
        endsAt = -1;
        animations.clear();
        lastBoard = null;
        // Wait for the duel disk to unfold, then grow the field; queued effects start once it is full size.
        revealAt = tick + DiskClient.deployTicks();
        // Spectators join a running duel: no start show for them.
        queueFree = revealAt + (watching ? GROW_TICKS : Math.max(GROW_TICKS, DuelStaging.INTRO_TICKS));
        scale = 0;
        grow = 0;
        FieldRenderer.reset();
    }

    public static void clear() {
        center = null;
        hovered = null;
        animations.clear();
        lastBoard = null;
        FieldRenderer.reset();
    }

    public static boolean active() {
        return center != null && ClientDuel.view() != null;
    }

    /** Whether each partner of a team plays on their own half of the zones (a Battle City duel). */
    public static boolean split() {
        return split;
    }

    /** The back of team {@code team}'s face-down cards, in their sleeve. */
    public static ResourceLocation sleeve(int team) {
        return sleeve(team, -1);
    }

    /**
     * The back of a face-down card in zone column {@code seq} (-1 for piles): on a split field, the sleeve of the
     * partner whose half it is on.
     */
    public static ResourceLocation sleeve(int team, int seq) {
        int index = team & 1;
        if (split) {
            int owner = seq < 0 ? -1 : DuelTable.zoneOwner(seq);
            index = index * 2 + Math.max(owner, 0);
        }
        return index < sleeves.size() ? sleeves.get(index) : Cosmetics.sleeveTexture(null);
    }

    public static long tick() {
        return tick;
    }

    /** Updates how far the field has grown, once per frame before it is drawn. */
    public static void updateScale(float partialTick) {
        double t = Mth.clamp((tick - revealAt + partialTick) / GROW_TICKS, 0, 1);
        grow = t * t * (3 - 2 * t);
        scale = grow * size;
    }

    /** The field's full size: 1 normally, more or less on a player-built arena that fits it to its room. */
    public static double size() {
        return size;
    }

    /** Blocks of room over the mat, or 0 under open sky. */
    public static double ceiling() {
        return ceiling;
    }

    /** Whether the mat shows only its zone frames, so the arena's floor shows through. */
    public static boolean outline() {
        return outline;
    }

    /** Whether this client is only watching the duel. */
    public static boolean watching() {
        return watching;
    }

    /** When the field starts growing (and the duel's start plays), as a {@link #tick()}. */
    public static long revealAt() {
        return revealAt;
    }

    /** Whether every queued effect has played. */
    public static boolean idle() {
        return animations.isEmpty() && tick >= queueFree;
    }

    /** Whether the field has finished growing. */
    public static boolean grown() {
        return grow >= 1;
    }

    /** World position of a field-local point, {@code up} blocks above the mat. */
    public static Vec3 toWorld(double x, double z, double up) {
        return center.add(right.scale(x * scale)).add(forward.scale(z * scale)).add(0, up * scale, 0);
    }

    /** Minecraft yaw (degrees) that faces from player {@code player}'s side toward the other side. */
    public static float facingYaw(int player) {
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        return player == 0 ? yaw : yaw + 180;
    }

    /** The middle of the mat, at its full size. */
    public static Vec3 center() {
        return center;
    }

    /** Toward player 1, level and one block long, whatever the field's size. */
    public static Vec3 direction() {
        return forward;
    }

    /** Toward player 1, as long as the field is grown (so sizes built from it grow with the field). */
    public static Vec3 forward() {
        return forward.scale(scale);
    }

    public static Vec3 right() {
        return right.scale(scale);
    }

    public static Loc hovered() {
        return hovered;
    }

    public static List<FieldAnimation> animations() {
        return animations;
    }

    /**
     * The card behind an event. Attacks carry no code, so the attacker is looked up on the board from before the
     * view (it may not survive the battle), then on the new one.
     */
    public static int actor(FieldEvent event, DuelView view) {
        if (event.kind() != FieldEvent.Kind.ATTACK) {
            return event.code();
        }
        for (Board board : new Board[]{lastBoard, view.board()}) {
            CardState card = board == null ? null : FieldRenderer.cardAt(board, event.from());
            if (card != null && card.code() != 0) {
                return card.code();
            }
        }
        return 0;
    }

    /** Called for each new view: queues its events. */
    public static void onView(DuelView view) {
        long start = Math.max(queueFree, tick);
        for (FieldEvent event : view.events()) {
            int actor = actor(event, view);
            if (actor != 0 && event.player() != view.you()
                    && (event.kind() == FieldEvent.Kind.ACTIVATE || event.kind() == FieldEvent.Kind.SUMMON)) {
                // The opponent's card is shown big first, then its effect plays.
                DuelStaging.banner(actor, view.names().get(event.player()) + (event.kind() == FieldEvent.Kind.ACTIVATE
                        ? " activates " : " summons ") + JadmData.text().cardName(actor), start);
                start += DuelStaging.BANNER_TICKS;
            }
            animations.add(new FieldAnimation(event, start, FieldAnimation.duration(event), actor));
            start += FieldAnimation.spacing(event);
        }
        lastBoard = view.board();
        queueFree = start;
        if (view.result() != null) {
            endsAt = start + LINGER_TICKS;
        }
    }

    /** @return an animation of {@code kind} at {@code loc} that is queued or playing, or {@code null} */
    public static FieldAnimation pending(FieldEvent.Kind kind, Loc loc) {
        for (FieldAnimation a : animations) {
            if (a.event().kind() == kind && FieldLayout.sameZone(a.event().from(), loc)) {
                return a;
            }
        }
        return null;
    }

    public static void clientTick() {
        tick++;
        animations.removeIf(a -> a.finished(tick));
        if (endsAt >= 0 && tick >= endsAt && !DuelStaging.holdsField()) {
            clear();
            return;
        }
        if (center == null) {
            return;
        }
        FieldRenderer.tickEffects();
        if (!DuelMode.active()) {
            hovered = lookedAtZone();
        }
    }

    /** In duel mode: the zone under the mouse cursor, from a ray through the camera. */
    public static void hoverRay(Vec3 origin, Vec3 dir) {
        hovered = center == null ? null : zoneOnRay(origin, dir);
    }

    /** The zone under the crosshair, if the player is looking at the mat. */
    private static Loc lookedAtZone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        return zoneOnRay(mc.player.getEyePosition(), mc.player.getViewVector(1));
    }

    /** The zone where a ray meets the mat. */
    private static Loc zoneOnRay(Vec3 eye, Vec3 look) {
        double planeY = center.y + 0.01;
        if (Math.abs(look.y) < 1e-4) {
            return null;
        }
        double t = (planeY - eye.y) / look.y;
        if (t <= 0 || t > REACH) {
            return null;
        }
        if (!grown()) {
            return null;
        }
        Vec3 hit = eye.add(look.scale(t)).subtract(center);
        return FieldLayout.zoneAt(hit.dot(right) / size, hit.dot(forward) / size);
    }

    /**
     * A right-click on the field: answers the prompt if exactly one choice is about the hovered zone, toggles it in
     * a multi-select, or offers the choices about it in a menu.
     *
     * @return whether the click was used (and the vanilla interaction should be cancelled)
     */
    public static boolean click() {
        if (!active() || hovered == null) {
            return false;
        }
        clickAt(hovered);
        return true;
    }

    /**
     * Picks {@code zone} for the current prompt: answers if exactly one choice is about it, toggles it in a
     * multi-select, or offers the choices about it in a menu.
     */
    public static void clickAt(Loc zone) {
        PromptView prompt = ClientDuel.prompt();
        if (prompt == null) {
            return;
        }
        if (prompt.multi() != null) {
            PromptView.MultiSelect multi = prompt.multi();
            for (int i = 0; i < multi.locs().size(); i++) {
                if (FieldLayout.sameZone(multi.locs().get(i), zone)) {
                    ClientDuel.toggle(i);
                    List<Integer> selected = ClientDuel.selected();
                    if (selected.size() == multi.max() && multi.canConfirm(selected)) {
                        ClientDuel.answer(multi.encode(List.copyOf(selected)));
                    }
                    return;
                }
            }
            return;
        }
        List<PromptView.Choice> matching = choicesAt(prompt, zone);
        if (matching.size() == 1) {
            ClientDuel.answer(matching.getFirst().response());
        } else if (!matching.isEmpty()) {
            DuelUi.offer(matching);
        }
    }

    public static List<PromptView.Choice> choicesAt(PromptView prompt, Loc zone) {
        List<PromptView.Choice> out = new ArrayList<>();
        for (PromptView.Choice choice : prompt.choices()) {
            if (FieldLayout.sameZone(choice.at(), zone)) {
                out.add(choice);
            }
        }
        return out;
    }

    /** Whether the current prompt offers anything for {@code zone}, so it should glow. */
    public static boolean selectable(Loc zone) {
        PromptView prompt = ClientDuel.prompt();
        if (prompt == null) {
            return false;
        }
        if (prompt.multi() != null) {
            return prompt.multi().locs().stream().anyMatch(l -> FieldLayout.sameZone(l, zone));
        }
        return prompt.choices().stream().anyMatch(c -> FieldLayout.sameZone(c.at(), zone));
    }

    /** Whether {@code zone} is ticked in the current multi-select. */
    public static boolean selected(Loc zone) {
        PromptView prompt = ClientDuel.prompt();
        if (prompt == null || prompt.multi() == null) {
            return false;
        }
        for (int i : ClientDuel.selected()) {
            if (FieldLayout.sameZone(prompt.multi().locs().get(i), zone)) {
                return true;
            }
        }
        return false;
    }
}
