package io.github.zancrow321.jadm.client.duel;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.client.ClientDuel;
import io.github.zancrow321.jadm.client.field.AceCamera;
import io.github.zancrow321.jadm.client.field.ClientField;
import io.github.zancrow321.jadm.client.field.FieldLayout;
import io.github.zancrow321.jadm.client.field.Finale;
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
@EventBusSubscriber(modid = Jadm.MOD_ID, value = Dist.CLIENT)
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
    /** Whether the first-person view is detached for a moment so an ace shot can fly the camera around. */
    private static boolean shotCamera;

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
        shotCamera = false;
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
        boolean dueling = ClientField.active() && !ClientField.nearby() && mc.player != null;
        if (dueling && !active) {
            enter(mc);
        } else if (!dueling && active) {
            leave(mc);
        }
        if (active && mc.screen == null) {
            mc.setScreen(new DuelModeScreen());
        }
        if (active && view == View.FIRST_PERSON && AceCamera.playing() != shotCamera) {
            // An ace shot needs a detached camera to fly; the duelist's eyes come back once it is over.
            shotCamera = !shotCamera;
            mc.options.setCameraType(shotCamera ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
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
        shotCamera = false;
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

    /** Whether duel mode places the camera itself: top-down, or flying an ace shot from the duelist's eyes. */
    private static boolean placed() {
        return active && (view == View.TOP_DOWN || shotCamera) && ClientField.active();
    }

    /** Where the camera is and what it looks at this frame, with an ace shot flying it around a monster. */
    private static Vec3[] camera() {
        if (view == View.TOP_DOWN) {
            // The final blow's camera swing and shake start from the shot that fits the field between the panels.
            Shot shot = topDownShot();
            return AceCamera.camera(Finale.cameraPosition(shot.position()), Finale.cameraTarget(shot.target()));
        }
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(partial);
        return AceCamera.camera(eye, eye.add(mc.player.getViewVector(partial).scale(8)));
    }

    /** Where the top-down camera is: in place behind your end, or still sweeping in over the field at the start. */
    private static Vec3 topDownPosition() {
        double sweep = DuelStaging.sweep();
        double turn = (1 - sweep) * Math.PI;
        // A longer field (Extra Monster Zones in the middle) needs the camera further back and higher.
        double longer = FieldLayout.halfLength() - FieldLayout.HALF_LENGTH;
        // A bigger or smaller field (on a player-built arena) moves the camera with it.
        double size = ClientField.size();
        Vec3 back = towardOpponent().scale(-(CAMERA_BACK + longer) * size);
        Vec3 turned = new Vec3(back.x * Math.cos(turn) - back.z * Math.sin(turn), 0,
                back.x * Math.sin(turn) + back.z * Math.cos(turn));
        double up = (CAMERA_HEIGHT + longer * 1.6) * size + (1 - sweep) * 5;
        return underRoof(ClientField.center().add(turned).add(0, up, 0));
    }

    /** Under a roof the camera stays inside the arena. */
    private static Vec3 underRoof(Vec3 position) {
        if (ClientField.ceiling() <= 0) {
            return position;
        }
        return new Vec3(position.x, Math.min(position.y, ClientField.center().y + ClientField.ceiling() - 0.4),
                position.z);
    }

    /** What the top-down camera looks at: the middle of the field while it sweeps in, then nearer your end. */
    private static Vec3 topDownTarget() {
        return ClientField.center().subtract(towardOpponent().scale(CAMERA_AIM * ClientField.size()
                * DuelStaging.sweep()));
    }

    /** Where the top-down camera ends up and what it looks at. */
    private record Shot(Vec3 position, Vec3 target) {
    }

    /** Room left between the field and the panels at the sides, in GUI pixels. */
    private static final int FIT_MARGIN = 4;
    /** The camera moves back at most this much further to fit the field between the panels. */
    private static final double MAX_ZOOM = 1.6;

    /**
     * The top-down camera, moved so the field lies between the panels on the left and the phase buttons on the
     * right instead of under them: sideways to centre it there, and back as well on a screen too narrow for it (so
     * the Field Zone, nearest the card panel, is never hidden behind it).
     */
    private static Shot topDownShot() {
        Vec3 position = topDownPosition();
        Vec3 target = topDownTarget();
        double sweep = DuelStaging.sweep();
        Minecraft mc = Minecraft.getInstance();
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        double left = -1 + 2.0 * (DuelUi.LEFT_COLUMN + FIT_MARGIN) / guiWidth;
        double right = 1 - 2.0 * (DuelUi.RIGHT_COLUMN + FIT_MARGIN) / guiWidth;
        if (sweep <= 0 || right - left < 0.2) {
            return new Shot(position, target);
        }
        double[] span = span(position, target);
        double zoom = Math.min(MAX_ZOOM, (span[2] - span[0]) / (right - left));
        if (zoom > 1) {
            position = underRoof(target.add(position.subtract(target).scale(1 + (zoom - 1) * sweep)));
            span = span(position, target);
        }
        // Moving the camera sideways moves each point across the screen by less the further away it is, so the
        // move is the one that leaves the same room at both ends.
        double shift = (span[0] + span[2] - left - right) / (1 / span[1] + 1 / span[3]) * sweep;
        Vec3 side = screenRight(position, target).scale(shift);
        return new Shot(position.add(side), target.add(side));
    }

    private static Vec3 screenRight(Vec3 position, Vec3 target) {
        return target.subtract(position).cross(new Vec3(0, 1, 0)).normalize();
    }

    /**
     * How far the mat's zones reach across the screen from this camera: the leftmost and rightmost corner, each as
     * {screen x from -1 to 1, its distance scaled so that moving the camera sideways by d moves it by d / that}.
     */
    private static double[] span(Vec3 position, Vec3 target) {
        Minecraft mc = Minecraft.getInstance();
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double perDepth = Math.tan(Math.toRadians(FOV) / 2) * aspect;
        Vec3 look = target.subtract(position).normalize();
        Vec3 right = screenRight(position, target);
        double[] span = {Double.MAX_VALUE, 1, -Double.MAX_VALUE, 1};
        double halfWidth = FieldLayout.ZONE_WIDTH / 2;
        double halfDepth = FieldLayout.ZONE_DEPTH / 2;
        for (int[] zone : FieldLayout.ZONES) {
            FieldLayout.Slot slot = FieldLayout.slot(zone[0], zone[1], zone[2]);
            for (int corner = 0; corner < 4; corner++) {
                Vec3 to = ClientField.toWorld(slot.x() + (corner % 2 == 0 ? -halfWidth : halfWidth),
                        slot.z() + (corner < 2 ? -halfDepth : halfDepth), 0).subtract(position);
                double depth = to.dot(look);
                if (depth < 0.1) {
                    continue;
                }
                double scaled = depth * perDepth;
                double x = to.dot(right) / scaled;
                if (x < span[0]) {
                    span[0] = x;
                    span[1] = scaled;
                }
                if (x > span[2]) {
                    span[2] = x;
                    span[3] = scaled;
                }
            }
        }
        return span[0] > span[2] ? new double[]{0, 1, 0, 1} : span;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!placed()) {
            return;
        }
        Vec3[] camera = camera();
        Vec3 look = camera[1].subtract(camera[0]);
        event.setYaw((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
        event.setPitch((float) Math.toDegrees(Math.atan2(-look.y, look.horizontalDistance())));
        event.setRoll(0);
    }

    /** Fired while the camera is set up, after it took the duelist's position: moves it above the field instead. */
    @SubscribeEvent
    public static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (!placed()) {
            return;
        }
        try {
            SET_CAMERA_POSITION.invoke(event.getCamera(), camera()[0]);
            event.setDistance(0);
        } catch (ReflectiveOperationException e) {
            Jadm.LOGGER.error("Could not place the duel camera", e);
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
