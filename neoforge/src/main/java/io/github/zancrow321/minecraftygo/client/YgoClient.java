package io.github.zancrow321.minecraftygo.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.client.render.MonsterRenderer;
import io.github.zancrow321.minecraftygo.entity.YgoEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_DUEL);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_DUEL.consumeClick()) {
            if (mc.screen == null && ClientDuel.view() != null) {
                mc.setScreen(new DuelScreen());
            }
        }
    }
}
