package io.github.zancrow321.minecraftygo.client.duel;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.client.ClientDuel;
import io.github.zancrow321.minecraftygo.client.field.ClientField;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Duel mode: while this client sits at a duel, the duelist stands still, the camera looks down on the field (or out
 * of the duelist's eyes, toggled with V) and the mouse cursor is free to click cards and zones. A transparent
 * {@link DuelModeScreen} takes the mouse and keys while the world keeps running behind it. Only touched on the
 * client thread.
 */
@EventBusSubscriber(modid = MinecraftYgo.MOD_ID, value = Dist.CLIENT)
public final class DuelMode {
    /** The field of view in duel mode, whatever the player's own setting, so the field always fits the screen. */
    public static final double FOV = 70;
    /** Where the top-down camera sits, in blocks behind your end of the field and above it. */
    private static final double CAMERA_BACK = 6.5;
    private static final double CAMERA_HEIGHT = 11;
    /** The camera looks at a point this far from the centre toward you, so your half gets more of the screen. */
    private static final double CAMERA_AIM = -2.0;
    /** The duelist's own view starts looking this far down at the field. */
    private static final float FIRST_PERSON_PITCH = 35;
    /** Vanilla HUD parts that have no place in a duel. */
    private static final Set<ResourceLocation> HIDDEN_LAYERS = Set.of(VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.CROSSHAIR, VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH,
            VanillaGuiLayers.JUMP_METER, VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.SELECTED_ITEM_NAME, VanillaGuiLayers.EFFECTS);
    private static final Method SET_CAMERA_POSITION =
            ObfuscationReflectionHelper.findMethod(Camera.class, "setPosition", Vec3.class);

    /** How the duel is seen. */
    public enum View {
        TOP_DOWN, FIRST_PERSON
    }

    private static boolean active;
    private static View view = View.TOP_DOWN;
    /** The player's own camera setting, put back when the duel is over. */
    private static CameraType previousCamera;

    private DuelMode() {
    }

    /** Whether this client is in duel mode right now. */
    public static boolean active() {
        return active;
    }

    public static View view() {
        return view;
    }

    public static void toggleView() {
        setView(view == View.TOP_DOWN ? View.FIRST_PERSON : View.TOP_DOWN);
    }

    public static void setView(View next) {
        view = next;
        Minecraft mc = Minecraft.getInstance();
        if (active) {
            // The top-down camera is a detached camera moved into place, so the duelist is drawn too.
            mc.options.setCameraType(view == View.TOP_DOWN ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
            if (view == View.FIRST_PERSON) {
                faceTheField();
            }
        }
    }

    /** Whether the screen in front of the world belongs to duel mode or is the cursor layer itself. */
    public static boolean showsHud(Screen screen) {
        return screen == null || screen instanceof DuelModeScreen;
    }

    /** Enters and leaves duel mode with the field, and keeps the cursor layer open in between. */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        boolean dueling = ClientField.active() && mc.player != null;
        if (dueling && !active) {
            enter(mc);
        } else if (!dueling && active) {
            leave(mc);
        }
        if (active && mc.screen == null) {
            mc.setScreen(new DuelModeScreen());
        }
    }

    private static void enter(Minecraft mc) {
        active = true;
        previousCamera = mc.options.getCameraType();
        faceTheField();
        setView(View.TOP_DOWN);
    }

    private static void leave(Minecraft mc) {
        active = false;
        if (previousCamera != null) {
            mc.options.setCameraType(previousCamera);
            previousCamera = null;
        }
        if (mc.screen instanceof DuelModeScreen || mc.screen instanceof DuelMenuScreen) {
            mc.setScreen(null);
        }
    }

    /** Turns the duelist toward the opponent, looking down at the field. */
    private static void faceTheField() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientDuel.view() == null) {
            return;
        }
        float yaw = ClientField.facingYaw(ClientDuel.view().you());
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setYHeadRot(yaw);
        mc.player.setYBodyRot(yaw);
        mc.player.setXRot(FIRST_PERSON_PITCH);
        mc.player.xRotO = FIRST_PERSON_PITCH;
    }

    /** Turns the duelist's head by a mouse drag, in screen pixels (first person only). */
    public static void look(double dx, double dy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && view == View.FIRST_PERSON) {
            double speed = 0.6 + mc.options.sensitivity().get() * 2;
            mc.player.turn(dx * speed * 4, dy * speed * 4);
        }
    }

    /** Toward the opponent's end of the field from yours, level. */
    private static Vec3 towardOpponent() {
        Vec3 forward = ClientField.direction();
        return ClientDuel.view() == null || ClientDuel.view().you() == 0 ? forward : forward.reverse();
    }

    private static boolean topDown() {
        return active && view == View.TOP_DOWN && ClientField.active();
    }

    /** Where the top-down camera is: in place behind your end, or still sweeping in over the field at the start. */
    private static Vec3 topDownPosition() {
        double sweep = DuelStaging.sweep();
        double turn = (1 - sweep) * Math.PI;
        // A longer field (Extra Monster Zones in the middle) needs the camera further back and higher.
        double longer = io.github.zancrow321.minecraftygo.client.field.FieldLayout.halfLength()
                - io.github.zancrow321.minecraftygo.client.field.FieldLayout.HALF_LENGTH;
        Vec3 back = towardOpponent().scale(-(CAMERA_BACK + longer));
        Vec3 turned = new Vec3(back.x * Math.cos(turn) - back.z * Math.sin(turn), 0,
                back.x * Math.sin(turn) + back.z * Math.cos(turn));
        return ClientField.center().add(turned).add(0, CAMERA_HEIGHT + longer * 1.6 + (1 - sweep) * 5, 0);
    }

    /** What the top-down camera looks at: the middle of the field while it sweeps in, then nearer your end. */
    private static Vec3 topDownTarget() {
        return ClientField.center().subtract(towardOpponent().scale(CAMERA_AIM * DuelStaging.sweep()));
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!topDown()) {
            return;
        }
        Vec3 look = topDownTarget().subtract(topDownPosition());
        event.setYaw((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
        event.setPitch((float) Math.toDegrees(Math.atan2(-look.y, look.horizontalDistance())));
        event.setRoll(0);
    }

    /** Fired while the camera is set up, after it took the duelist's position: moves it above the field instead. */
    @SubscribeEvent
    public static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (!topDown()) {
            return;
        }
        try {
            SET_CAMERA_POSITION.invoke(event.getCamera(), topDownPosition());
            event.setDistance(0);
        } catch (ReflectiveOperationException e) {
            MinecraftYgo.LOGGER.error("Could not place the duel camera", e);
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (active && event.usedConfiguredFov()) {
            event.setFOV(FOV);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (active) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        if (active) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (active && HIDDEN_LAYERS.contains(event.getName())) {
            event.setCanceled(true);
        }
    }

    /**
     * The ray from the camera through a point on the screen.
     *
     * @param fx the point's distance from the left edge, as a fraction of the screen width
     * @param fy the point's distance from the top edge, as a fraction of the screen height
     * @return the ray's origin and direction (not normalised)
     */
    public static Vec3[] rayThrough(double fx, double fy) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        double tanHalf = Math.tan(Math.toRadians(FOV) / 2);
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double sx = (2 * fx - 1) * tanHalf * aspect;
        double sy = (1 - 2 * fy) * tanHalf;
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        Vec3 dir = new Vec3(look.x() + up.x() * sy - left.x() * sx, look.y() + up.y() * sy - left.y() * sx,
                look.z() + up.z() * sy - left.z() * sx);
        return new Vec3[]{camera.getPosition(), dir};
    }
}
