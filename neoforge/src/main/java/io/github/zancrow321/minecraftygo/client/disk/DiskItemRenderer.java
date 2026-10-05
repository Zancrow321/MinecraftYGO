package io.github.zancrow321.minecraftygo.client.disk;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.zancrow321.minecraftygo.cosmetics.PlayerCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The disk as an item: in inventories, on the ground, in item frames and in the hand.
 */
public final class DiskItemRenderer extends BlockEntityWithoutLevelRenderer {
    public DiskItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers,
                             int light, int overlay) {
        // Held in the left arm's hand it is worn, and DiskLayer draws it on the arm instead.
        if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return;
        }
        DiskModel model = DiskModel.get();
        if (model == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        DiskModel.Pose pose = firstPerson && mc.player != null
                ? DiskClient.pose(mc.player, mc.getTimer().getGameTimeDeltaPartialTick(false))
                : DiskModel.Pose.REST;
        model.renderAsItem(poses, buffers, light, overlay, pose, PlayerCosmetics.skin(stack));
    }
}
