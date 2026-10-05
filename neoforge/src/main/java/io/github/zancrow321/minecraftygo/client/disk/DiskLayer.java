package io.github.zancrow321.minecraftygo.client.disk;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.zancrow321.minecraftygo.compat.figura.FiguraCompat;
import io.github.zancrow321.minecraftygo.cosmetics.PlayerCosmetics;
import io.github.zancrow321.minecraftygo.duel.DuelDisks;
import io.github.zancrow321.minecraftygo.entity.DuelistNpc;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Draws the duel disk on the left arm, like Figura's LeftArm part: on players who wear one, and on NPC duelists,
 * who always carry theirs.
 */
public final class DiskLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    public DiskLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        String skin;
        if (entity instanceof DuelistNpc npc) {
            skin = npc.diskSkin();
        } else if (entity instanceof Player player && !DuelDisks.worn(player).isEmpty()) {
            skin = PlayerCosmetics.skin(DuelDisks.worn(player));
        } else {
            return;
        }
        if (entity.isInvisible() || FiguraCompat.diskHidden(entity)) {
            return;
        }
        DiskModel model = DiskModel.get();
        if (model == null) {
            return;
        }
        poses.pushPose();
        getParentModel().leftArm.translateAndRotate(poses);
        model.renderOnArm(poses, buffers, light, LivingEntityRenderer.getOverlayCoords(entity, 0),
                DiskClient.pose(entity, partialTick), skin);
        poses.popPose();
    }
}
