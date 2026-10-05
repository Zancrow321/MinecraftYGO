package io.github.zancrow321.minecraftygo.client.disk;

import io.github.zancrow321.minecraftygo.duel.DuelDisks;
import io.github.zancrow321.minecraftygo.network.DuelistStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Which players this client knows to be dueling, and since when, to play the disk's deploy and fold animations.
 * Only touched on the client thread.
 */
public final class DiskClient {
    private static final Map<Integer, State> states = new HashMap<>();
    private static long tick;

    private record State(boolean dueling, long since) {
    }

    private DiskClient() {
    }

    public static void receive(DuelistStatePayload payload) {
        states.put(payload.entityId(), new State(payload.dueling(), tick));
    }

    public static void clientTick() {
        tick++;
    }

    public static void clear() {
        states.clear();
    }

    /** The disk's pose for this duelist: rest, unfolding or unfolded while dueling, folding back afterwards. */
    public static DiskModel.Pose pose(Entity duelist, float partialTick) {
        State state = states.get(duelist.getId());
        DiskModel model = DiskModel.get();
        if (state == null || model == null) {
            return DiskModel.Pose.REST;
        }
        DiskModel.Animation animation = model.animation(state.dueling() ? "deploy" : "fold");
        if (animation == null) {
            return DiskModel.Pose.REST;
        }
        float seconds = (tick - state.since() + partialTick) / 20f;
        return new DiskModel.Pose(animation, Math.min(seconds, animation.length()));
    }

    /** Whether this client last heard that the duelist is dueling. */
    public static boolean dueling(Entity duelist) {
        State state = states.get(duelist.getId());
        return state != null && state.dueling();
    }

    /** How long the local player's disk takes to unfold, in ticks; 0 if they don't wear one. */
    public static int deployTicks() {
        Player player = Minecraft.getInstance().player;
        DiskModel model = DiskModel.get();
        if (player == null || model == null || DuelDisks.worn(player).isEmpty()) {
            return 0;
        }
        DiskModel.Animation deploy = model.animation("deploy");
        return deploy == null ? 0 : (int) Math.ceil(deploy.length() * 20);
    }
}
