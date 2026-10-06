package io.github.zancrow321.jadm.client.disk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.cosmetics.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The duel disk model, converted from its .bbmodel by {@code tools/disk/convert_disk.py}: quads in Blockbench's
 * absolute coordinates (pixels, y up), grouped into bones with a pivot and rest rotation, plus keyframe animations.
 * Bones are transformed exactly as Blockbench displays them, so the disk looks the same in game.
 */
public final class DiskModel {
    public static final ResourceLocation LOCATION =
            ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "disk/duel_disk.json");

    private static DiskModel loaded;
    private static boolean failed;

    private final Vector3f pivot;
    private final Vector3f boundsMin;
    private final Vector3f boundsMax;
    private final List<Layer> layers;
    private final List<Bone> bones;
    private final Map<String, Animation> animations;

    /** A texture of the model; skins swap it for a recolored copy. */
    private record Layer(String id, boolean emissive) {
    }

    private record Quad(int layer, float[] vertices, Vector3f normal) {
    }

    private record Bone(int parent, Vector3f origin, Vector3f rotation, List<Quad> quads) {
    }

    /** One keyframe channel; values are what Blockbench shows in the keyframe panel. */
    private record Track(float[] times, float[][] values, String[] interpolation) {
        float[] sample(float t) {
            int n = times.length;
            if (t <= times[0]) {
                return values[0];
            }
            if (t >= times[n - 1]) {
                return values[n - 1];
            }
            int i = 0;
            while (times[i + 1] < t) {
                i++;
            }
            float f = (t - times[i]) / Math.max(1e-6f, times[i + 1] - times[i]);
            if ("step".equals(interpolation[i])) {
                return values[i];
            }
            float[] out = new float[3];
            boolean smooth = "catmullrom".equals(interpolation[i]) || "catmullrom".equals(interpolation[i + 1]);
            for (int axis = 0; axis < 3; axis++) {
                float p1 = values[i][axis], p2 = values[i + 1][axis];
                if (smooth) {
                    float p0 = values[Math.max(0, i - 1)][axis], p3 = values[Math.min(n - 1, i + 2)][axis];
                    out[axis] = 0.5f * (2 * p1 + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f * f
                            + (-p0 + 3 * p1 - 3 * p2 + p3) * f * f * f);
                } else {
                    out[axis] = Mth.lerp(f, p1, p2);
                }
            }
            return out;
        }
    }

    public record Animation(float length, Map<Integer, Map<String, Track>> bones) {
    }

    /** A point in an animation, or the rest pose when {@code animation} is {@code null}. */
    public record Pose(Animation animation, float seconds) {
        public static final Pose REST = new Pose(null, 0);
    }

    private DiskModel(JsonObject json) {
        pivot = vec(json.getAsJsonArray("pivot"));
        JsonArray bounds = json.getAsJsonArray("bounds");
        boundsMin = vec(bounds.get(0).getAsJsonArray());
        boundsMax = vec(bounds.get(1).getAsJsonArray());
        layers = new ArrayList<>();
        for (JsonElement t : json.getAsJsonArray("textures")) {
            String id = t.getAsJsonObject().get("id").getAsString();
            boolean emissive = t.getAsJsonObject().get("emissive").getAsBoolean();
            layers.add(new Layer(id, emissive));
        }
        bones = new ArrayList<>();
        for (JsonElement b : json.getAsJsonArray("bones")) {
            JsonObject bone = b.getAsJsonObject();
            List<Quad> quads = new ArrayList<>();
            for (JsonElement q : bone.getAsJsonArray("quads")) {
                JsonObject quad = q.getAsJsonObject();
                float[] vertices = new float[20];
                JsonArray vs = quad.getAsJsonArray("v");
                for (int i = 0; i < 4; i++) {
                    JsonArray v = vs.get(i).getAsJsonArray();
                    for (int k = 0; k < 5; k++) {
                        vertices[i * 5 + k] = v.get(k).getAsFloat();
                    }
                }
                quads.add(new Quad(quad.get("t").getAsInt(), vertices, vec(quad.getAsJsonArray("n"))));
            }
            bones.add(new Bone(bone.get("parent").getAsInt(), vec(bone.getAsJsonArray("origin")),
                    vec(bone.getAsJsonArray("rotation")), quads));
        }
        animations = new HashMap<>();
        for (Map.Entry<String, JsonElement> a : json.getAsJsonObject("animations").entrySet()) {
            JsonObject anim = a.getValue().getAsJsonObject();
            Map<Integer, Map<String, Track>> tracks = new HashMap<>();
            for (Map.Entry<String, JsonElement> bone : anim.getAsJsonObject("bones").entrySet()) {
                Map<String, Track> channels = new HashMap<>();
                for (Map.Entry<String, JsonElement> channel : bone.getValue().getAsJsonObject().entrySet()) {
                    JsonArray frames = channel.getValue().getAsJsonArray();
                    float[] times = new float[frames.size()];
                    float[][] values = new float[frames.size()][3];
                    String[] interpolation = new String[frames.size()];
                    for (int i = 0; i < frames.size(); i++) {
                        JsonArray f = frames.get(i).getAsJsonArray();
                        times[i] = f.get(0).getAsFloat();
                        for (int k = 0; k < 3; k++) {
                            values[i][k] = f.get(1 + k).getAsFloat();
                        }
                        interpolation[i] = f.get(4).getAsString();
                    }
                    channels.put(channel.getKey(), new Track(times, values, interpolation));
                }
                tracks.put(Integer.parseInt(bone.getKey()), channels);
            }
            animations.put(a.getKey(), new Animation(anim.get("length").getAsFloat(), tracks));
        }
    }

    /** @return the model, or {@code null} if it is missing or broken (logged once) */
    public static DiskModel get() {
        if (loaded == null && !failed) {
            try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(LOCATION)) {
                loaded = new DiskModel(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception e) {
                failed = true;
                Jadm.LOGGER.error("Could not load the duel disk model {}", LOCATION, e);
            }
        }
        return loaded;
    }

    /** Resource packs changed: load the model again on next use. */
    public static void reload() {
        loaded = null;
        failed = false;
    }

    public Animation animation(String name) {
        return animations.get(name);
    }

    /**
     * Draws the disk on a player's left arm. {@code poses} must be in the arm's space, as after
     * {@code leftArm.translateAndRotate}.
     */
    public void renderOnArm(PoseStack poses, MultiBufferSource buffers, int light, int overlay, Pose pose,
                            String skin) {
        poses.pushPose();
        // Figura's Blockbench space is entity model space with y pointing up instead of down (the left arm is on
        // +x and the player faces -z in both), in pixels instead of blocks.
        poses.scale(1 / 16f, -1 / 16f, 1 / 16f);
        poses.translate(-pivot.x, -pivot.y, -pivot.z);
        render(poses, buffers, light, overlay, pose, skin);
        poses.popPose();
    }

    /** Draws the disk centred in a 1×1×1 item box, its face toward the viewer. */
    public void renderAsItem(PoseStack poses, MultiBufferSource buffers, int light, int overlay, Pose pose,
                             String skin) {
        Vector3f size = new Vector3f(boundsMax).sub(boundsMin);
        float scale = 1 / Math.max(size.x, Math.max(size.y, size.z));
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        // The side facing away from the left arm (+x in Figura) faces the viewer (+z).
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90));
        poses.scale(scale, scale, scale);
        poses.translate(-(boundsMin.x + boundsMax.x) / 2, -(boundsMin.y + boundsMax.y) / 2,
                -(boundsMin.z + boundsMax.z) / 2);
        render(poses, buffers, light, overlay, pose, skin);
        poses.popPose();
    }

    private void render(PoseStack poses, MultiBufferSource buffers, int light, int overlay, Pose pose, String skin) {
        Matrix4f[] transforms = boneTransforms(pose);
        for (int layer = 0; layer < layers.size(); layer++) {
            Layer l = layers.get(layer);
            draw(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(Cosmetics.diskTexture(l.id(), skin, false))),
                    transforms, layer, light, overlay);
            if (l.emissive()) {
                draw(poses, buffers.getBuffer(RenderType.eyes(Cosmetics.diskTexture(l.id(), skin, true))), transforms,
                        layer,
                        0xF000F0, overlay);
            }
        }
    }

    private void draw(PoseStack poses, VertexConsumer consumer, Matrix4f[] transforms, int layer, int light,
                      int overlay) {
        PoseStack.Pose last = poses.last();
        Vector3f normal = new Vector3f();
        for (int b = 0; b < bones.size(); b++) {
            Matrix4f matrix = new Matrix4f(last.pose()).mul(transforms[b]);
            Matrix3f normals = new Matrix3f(last.normal()).mul(new Matrix3f(transforms[b]));
            for (Quad quad : bones.get(b).quads()) {
                if (quad.layer() != layer) {
                    continue;
                }
                normals.transform(normal.set(quad.normal())).normalize();
                float[] v = quad.vertices();
                for (int i = 0; i < 4; i++) {
                    int o = i * 5;
                    consumer.addVertex(matrix, v[o], v[o + 1], v[o + 2])
                            .setColor(255, 255, 255, 255)
                            .setUv(v[o + 3], v[o + 4])
                            .setOverlay(overlay)
                            .setLight(light)
                            .setNormal(normal.x, normal.y, normal.z);
                }
            }
        }
    }

    /**
     * Each bone's transform in model space, applied like Blockbench: translate to the pivot (plus animated
     * position), rotate Z·Y·X, scale, translate back. Blockbench shows a keyframe rotation of (x, y, z) as
     * (-x, -y, z) on top of the rest rotation, and a position of (x, y, z) as (-x, y, z).
     */
    private Matrix4f[] boneTransforms(Pose pose) {
        Matrix4f[] out = new Matrix4f[bones.size()];
        for (int i = 0; i < bones.size(); i++) {
            Bone bone = bones.get(i);
            float[] rot = {0, 0, 0}, pos = {0, 0, 0}, scale = {1, 1, 1};
            Map<String, Track> channels = pose.animation() == null ? null : pose.animation().bones().get(i);
            if (channels != null) {
                float t = pose.seconds();
                if (channels.containsKey("rotation")) {
                    rot = channels.get("rotation").sample(t);
                }
                if (channels.containsKey("position")) {
                    pos = channels.get("position").sample(t);
                }
                if (channels.containsKey("scale")) {
                    scale = channels.get("scale").sample(t);
                }
            }
            Matrix4f m = bone.parent() < 0 ? new Matrix4f() : new Matrix4f(out[bone.parent()]);
            Vector3f o = bone.origin();
            m.translate(o.x - pos[0], o.y + pos[1], o.z + pos[2])
                    .rotateZ((float) Math.toRadians(bone.rotation().z + rot[2]))
                    .rotateY((float) Math.toRadians(bone.rotation().y - rot[1]))
                    .rotateX((float) Math.toRadians(bone.rotation().x - rot[0]))
                    .scale(scale[0], scale[1], scale[2])
                    .translate(-o.x, -o.y, -o.z);
            out[i] = m;
        }
        return out;
    }


    private static Vector3f vec(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }
}
