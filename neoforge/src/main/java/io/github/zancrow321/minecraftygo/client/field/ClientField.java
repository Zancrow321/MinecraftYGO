package io.github.zancrow321.minecraftygo.client.field;

import io.github.zancrow321.minecraftygo.client.ClientDuel;
import io.github.zancrow321.minecraftygo.client.DuelScreen;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.engine.duel.FieldEvent;
import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import io.github.zancrow321.minecraftygo.engine.text.PromptView;
import io.github.zancrow321.minecraftygo.network.DuelFieldPayload;
import net.minecraft.client.Minecraft;
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

    private static Vec3 center;
    private static Vec3 forward;
    private static Vec3 right;
    private static long tick;
    private static long endsAt = -1;
    private static long queueFree;
    private static final List<FieldAnimation> animations = new ArrayList<>();
    private static Loc hovered;

    private ClientField() {
    }

    public static void receive(DuelFieldPayload payload) {
        if (!payload.active()) {
            clear();
            return;
        }
        center = new Vec3(payload.x(), payload.y(), payload.z());
        forward = Vec3.directionFromRotation(0, payload.yaw());
        right = new Vec3(-forward.z, 0, forward.x);
        endsAt = -1;
        animations.clear();
        queueFree = tick;
        FieldRenderer.reset();
    }

    public static void clear() {
        center = null;
        hovered = null;
        animations.clear();
        FieldRenderer.reset();
    }

    public static boolean active() {
        return center != null && ClientDuel.view() != null;
    }

    public static long tick() {
        return tick;
    }

    /** World position of a field-local point, {@code up} blocks above the mat. */
    public static Vec3 toWorld(double x, double z, double up) {
        return center.add(right.scale(x)).add(forward.scale(z)).add(0, up, 0);
    }

    /** Minecraft yaw (degrees) that faces from player {@code player}'s side toward the other side. */
    public static float facingYaw(int player) {
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        return player == 0 ? yaw : yaw + 180;
    }

    public static Vec3 forward() {
        return forward;
    }

    public static Vec3 right() {
        return right;
    }

    public static Loc hovered() {
        return hovered;
    }

    public static List<FieldAnimation> animations() {
        return animations;
    }

    /** Called for each new view: queues its events. */
    public static void onView(DuelView view) {
        long start = Math.max(queueFree, tick);
        for (FieldEvent event : view.events()) {
            animations.add(new FieldAnimation(event, start, FieldAnimation.duration(event.kind())));
            start += FieldAnimation.spacing(event.kind());
        }
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
        if (endsAt >= 0 && tick >= endsAt) {
            clear();
            return;
        }
        if (center == null) {
            return;
        }
        FieldRenderer.tickEffects();
        hovered = lookedAtZone();
    }

    /** The zone under the crosshair, if the player is looking at the mat. */
    private static Loc lookedAtZone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1);
        double planeY = center.y + 0.01;
        if (Math.abs(look.y) < 1e-4) {
            return null;
        }
        double t = (planeY - eye.y) / look.y;
        if (t <= 0 || t > REACH) {
            return null;
        }
        Vec3 hit = eye.add(look.scale(t)).subtract(center);
        return FieldLayout.zoneAt(hit.dot(right), hit.dot(forward));
    }

    /**
     * A right-click on the field: answers the prompt if exactly one choice is about the hovered zone, toggles it in
     * a multi-select, or opens the duel screen narrowed to that zone.
     *
     * @return whether the click was used (and the vanilla interaction should be cancelled)
     */
    public static boolean click() {
        if (!active() || hovered == null) {
            return false;
        }
        PromptView prompt = ClientDuel.prompt();
        if (prompt == null) {
            return true;
        }
        if (prompt.multi() != null) {
            PromptView.MultiSelect multi = prompt.multi();
            for (int i = 0; i < multi.locs().size(); i++) {
                if (FieldLayout.sameZone(multi.locs().get(i), hovered)) {
                    ClientDuel.toggle(i);
                    List<Integer> selected = ClientDuel.selected();
                    if (selected.size() == multi.max() && multi.canConfirm(selected)) {
                        ClientDuel.answer(multi.encode(List.copyOf(selected)));
                    }
                    return true;
                }
            }
            return true;
        }
        List<PromptView.Choice> matching = choicesAt(prompt, hovered);
        if (matching.size() == 1) {
            ClientDuel.answer(matching.getFirst().response());
        } else if (!matching.isEmpty()) {
            Minecraft.getInstance().setScreen(new DuelScreen(hovered));
        }
        return true;
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
