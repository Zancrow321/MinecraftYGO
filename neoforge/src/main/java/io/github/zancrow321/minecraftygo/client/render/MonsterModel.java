package io.github.zancrow321.minecraftygo.client.render;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.entity.MonsterEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks the geometry, texture and animations for a monster from its card, and poses its attack.
 *
 * <p>The imported models only come with an idle loop, so the attack is posed in code from the bone names the models
 * share: the monster rears back, then strikes with its head, jaw, arms and wings while the field pushes it toward
 * its target. That way every monster attacks, including models added later.
 */
public final class MonsterModel extends GeoModel<MonsterEntity> {
    /** Used for a card that has no model of its own. */
    private static final String FALLBACK = "kuriboh";

    private final Map<String, Rig> rigs = new HashMap<>();
    /**
     * The turns added for the attack this frame. Bones are shared by every monster with the same model, and GeckoLib
     * snapshots them before the next frame, so the pose is taken back off after drawing ({@link #undoPose}).
     */
    private final Map<GeoBone, float[]> posed = new HashMap<>();

    @Override
    public ResourceLocation getModelResource(MonsterEntity monster) {
        return location("geo/monster/", ".geo.json", monster);
    }

    @Override
    public ResourceLocation getTextureResource(MonsterEntity monster) {
        return location("textures/monster/", ".png", monster);
    }

    @Override
    public ResourceLocation getAnimationResource(MonsterEntity monster) {
        return location("animations/monster/", ".animation.json", monster);
    }

    private static ResourceLocation location(String folder, String extension, MonsterEntity monster) {
        String id = id(monster);
        int colon = id.indexOf(':');
        // Models from resource packs name their namespace: "mypack:dragon".
        return colon < 0 ? ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, folder + id + extension)
                : ResourceLocation.fromNamespaceAndPath(id.substring(0, colon), folder + id.substring(colon + 1)
                + extension);
    }

    private static String id(MonsterEntity monster) {
        CardPool.Model model = monster.model();
        return model != null ? model.id() : FALLBACK;
    }

    @Override
    public void setCustomAnimations(MonsterEntity monster, long instanceId, AnimationState<MonsterEntity> state) {
        float t = monster.attackProgress(state.getPartialTick());
        if (t < 0) {
            return;
        }
        Rig rig = rig(monster);
        // Rear back over the first third, strike, hold the blow, then settle.
        float windup = t < 0.35f ? smooth(t / 0.35f) : t < 0.5f ? 1 - smooth((t - 0.35f) / 0.15f) : 0;
        float strike = t < 0.35f ? 0 : t < 0.5f ? smooth((t - 0.35f) / 0.15f) : t < 0.75f ? 1
                : 1 - smooth((t - 0.75f) / 0.25f);
        float nod = rig.forward * (-30 * windup + 28 * strike); // degrees, positive tips toward the target
        for (String bone : rig.neck) {
            pitch(bone, nod / Math.max(1, rig.neck.size()));
        }
        for (String bone : rig.head) {
            pitch(bone, rig.forward * (-15 * windup + 18 * strike));
        }
        for (String bone : rig.jaw) {
            pitch(bone, rig.forward * (8 * windup + 35 * strike));
        }
        for (String bone : rig.body) {
            pitch(bone, rig.forward * (-10 * windup + 10 * strike));
        }
        for (String bone : rig.arms) {
            // Raise the arm up in front, then bring it down to point at the target.
            pitch(bone, rig.forward * (150 * windup + 85 * strike));
        }
        for (String bone : rig.wings) {
            GeoBone b = getAnimationProcessor().getBone(bone);
            if (b != null) {
                float side = Math.signum(b.getPivotX()) == 0 ? 1 : Math.signum(b.getPivotX());
                turn(b, 0, side * Mth.DEG_TO_RAD * (40 * windup - 25 * strike));
            }
        }
    }

    /** 1 if the monster's model faces north (-z) like most do, -1 if it was built facing south. */
    float forward(MonsterEntity monster) {
        return rig(monster).forward;
    }

    private Rig rig(MonsterEntity monster) {
        return rigs.computeIfAbsent(id(monster), id -> {
            List<GeoBone> bones = new ArrayList<>();
            getBakedModel(getModelResource(monster)).topLevelBones().forEach(bone -> collect(bone, bones));
            return Rig.of(bones);
        });
    }

    private static void collect(GeoBone bone, List<GeoBone> into) {
        into.add(bone);
        bone.getChildBones().forEach(child -> collect(child, into));
    }

    private void pitch(String name, float degrees) {
        turn(getAnimationProcessor().getBone(name), PITCH * degrees * Mth.DEG_TO_RAD, 0);
    }

    private void turn(GeoBone bone, float x, float z) {
        if (bone == null) {
            return;
        }
        bone.setRotX(bone.getRotX() + x);
        bone.setRotZ(bone.getRotZ() + z);
        float[] total = posed.computeIfAbsent(bone, b -> new float[2]);
        total[0] += x;
        total[1] += z;
    }

    /** Takes this frame's attack pose back off the shared bones. */
    void undoPose() {
        posed.forEach((bone, total) -> {
            bone.setRotX(bone.getRotX() - total[0]);
            bone.setRotZ(bone.getRotZ() - total[1]);
        });
        posed.clear();
    }

    /** The sign that tips a bone's top toward a model facing north (-z). */
    private static final float PITCH = -1;

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /**
     * The bones an attack moves, found by the names the imported models use, and which way the model faces: most
     * face north, but some were built facing south.
     */
    private record Rig(float forward, List<String> neck, List<String> head, List<String> jaw, List<String> body,
                       List<String> arms, List<String> wings) {
        static Rig of(Collection<GeoBone> bones) {
            List<String> neck = new ArrayList<>(), head = new ArrayList<>(), jaw = new ArrayList<>(),
                    body = new ArrayList<>(), arms = new ArrayList<>(), wings = new ArrayList<>();
            GeoBone headBone = null, jawBone = null, tailBone = null, root = null;
            for (GeoBone bone : bones) {
                String name = bone.getName().toLowerCase(Locale.ROOT);
                String parent = bone.getParent() == null ? "" : bone.getParent().getName().toLowerCase(Locale.ROOT);
                if (bone.getParent() == null && root == null) {
                    root = bone;
                }
                if (name.startsWith("neck")) {
                    neck.add(bone.getName());
                } else if (name.startsWith("head") && !parent.startsWith("head")) {
                    head.add(bone.getName());
                    headBone = headBone == null ? bone : headBone;
                } else if (name.startsWith("jaw")) {
                    jaw.add(bone.getName());
                    jawBone = jawBone == null ? bone : jawBone;
                } else if (name.equals("upper_body") || name.equals("shoulders") || name.equals("torso")) {
                    body.add(bone.getName());
                } else if (name.startsWith("wing") && !parent.startsWith("wing")) {
                    wings.add(bone.getName());
                } else if ((name.contains("shoulder") || name.startsWith("arm") || name.endsWith("_arm"))
                        && !name.contains("lower") && !parent.contains("arm") && !parent.contains("shoulder")) {
                    arms.add(bone.getName());
                } else if (name.startsWith("tail") && !parent.startsWith("tail")) {
                    tailBone = bone;
                }
            }
            float forward = 1;
            if (headBone != null && jawBone != null && Math.abs(jawBone.getPivotZ() - headBone.getPivotZ()) > 0.5f) {
                forward = jawBone.getPivotZ() < headBone.getPivotZ() ? 1 : -1;
            } else if (tailBone != null && root != null && Math.abs(tailBone.getPivotZ() - root.getPivotZ()) > 0.5f) {
                forward = tailBone.getPivotZ() > root.getPivotZ() ? 1 : -1;
            }
            return new Rig(forward, neck, head, jaw, body, arms, wings);
        }
    }
}
