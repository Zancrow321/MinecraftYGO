package io.github.zancrow321.minecraftygo.client.render;

import io.github.zancrow321.minecraftygo.client.disk.DiskLayer;
import io.github.zancrow321.minecraftygo.entity.DuelistNpc;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** NPC duelists look like players, wearing one of the vanilla default skins, with their duel disk on the arm. */
public final class DuelistNpcRenderer extends HumanoidMobRenderer<DuelistNpc, PlayerModel<DuelistNpc>> {
    private static final List<ResourceLocation> SKINS = List.of("alex", "ari", "efe", "kai", "makena", "noor",
                    "steve", "sunny", "zuri").stream()
            .map(name -> ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + name + ".png"))
            .toList();

    public DuelistNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new DiskLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(DuelistNpc npc) {
        return SKINS.get(Math.floorMod(npc.skin(), SKINS.size()));
    }
}
