package io.github.zancrow321.minecraftygo.client.disk;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.zancrow321.minecraftygo.duel.DuelDisks;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Draws the worn duel disk on the player's left arm, like Figura's LeftArm part.
 */
public final class DiskLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public DiskLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        if (player.isInvisible() || DuelDisks.worn(player).isEmpty()) {
            return;
        }
        DiskModel model = DiskModel.get();
        if (model == null) {
            return;
        }
        poses.pushPose();
        getParentModel().leftArm.translateAndRotate(poses);
        model.renderOnArm(poses, buffers, light, LivingEntityRenderer.getOverlayCoords(player, 0),
                DiskClient.pose(player, partialTick));
        poses.popPose();
    }
}
