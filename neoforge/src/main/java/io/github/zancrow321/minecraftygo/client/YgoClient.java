package io.github.zancrow321.minecraftygo.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.client.field.ClientField;
import io.github.zancrow321.minecraftygo.client.field.DuelHud;
import io.github.zancrow321.minecraftygo.client.field.FieldRenderer;
import io.github.zancrow321.minecraftygo.client.render.MonsterRenderer;
import io.github.zancrow321.minecraftygo.entity.YgoEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = MinecraftYgo.MOD_ID, value = Dist.CLIENT)
public final class YgoClient {
    public static final KeyMapping OPEN_DUEL = new KeyMapping("key.minecraftygo.open_duel",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.minecraftygo");

    private YgoClient() {
    }

    @EventBusSubscriber(modid = MinecraftYgo.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(YgoEntities.MONSTER.get(), MonsterRenderer::new);
        }

        @SubscribeEvent
        public static void registerHud(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR,
                    ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID, "duel_hud"), DuelHud::render);
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_DUEL);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        FieldRenderer.render(event);
    }

    /** Right-clicking the projected field answers prompts instead of using the held item. */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && ClientField.click()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientField.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientField.clientTick();
        ClientDuel.autoplayTick();
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_DUEL.consumeClick()) {
            if (mc.screen == null && ClientDuel.view() != null) {
                mc.setScreen(new DuelScreen());
            }
        }
    }
}
