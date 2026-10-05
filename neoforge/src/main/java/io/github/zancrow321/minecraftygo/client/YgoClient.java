package io.github.zancrow321.minecraftygo.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.client.disk.DiskClient;
import io.github.zancrow321.minecraftygo.client.collection.CardItemRenderer;
import io.github.zancrow321.minecraftygo.client.disk.DiskItemRenderer;
import io.github.zancrow321.minecraftygo.client.disk.DiskLayer;
import io.github.zancrow321.minecraftygo.client.disk.DiskModel;
import io.github.zancrow321.minecraftygo.client.field.ClientField;
import io.github.zancrow321.minecraftygo.client.field.DuelHud;
import io.github.zancrow321.minecraftygo.client.field.FieldRenderer;
import io.github.zancrow321.minecraftygo.client.render.MonsterRenderer;
import io.github.zancrow321.minecraftygo.entity.YgoEntities;
import io.github.zancrow321.minecraftygo.item.BoosterPackItem;
import io.github.zancrow321.minecraftygo.item.YgoItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
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

    /**
     * For headless testing: {@code -Dminecraftygo.camera=THIRD_PERSON_FRONT:90} keeps the camera there and turns
     * the body that many degrees from the head (to see the duel disk from the side).
     */
    private static final String TEST_CAMERA = System.getProperty("minecraftygo.camera");

    /**
     * For headless testing: {@code -Dminecraftygo.useItem=80,200} closes any screen and uses the main-hand item when
     * the player has been in the world that many ticks (to open packs, binders and deck boxes without a mouse).
     */
    private static final java.util.Set<Integer> TEST_USE_ITEM = java.util.Arrays.stream(
                    System.getProperty("minecraftygo.useItem", "").split(","))
            .filter(t -> !t.isBlank()).map(String::trim).map(Integer::valueOf)
            .collect(java.util.stream.Collectors.toSet());

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
        @SuppressWarnings("unchecked")
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof LivingEntityRenderer<?, ?> renderer) {
                    var playerRenderer = (LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>)
                            renderer;
                    playerRenderer.addLayer(new DiskLayer(playerRenderer));
                }
            }
        }

        @SubscribeEvent
        public static void registerItemRenderers(RegisterClientExtensionsEvent event) {
            event.registerItem(new IClientItemExtensions() {
                private DiskItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = new DiskItemRenderer();
                    }
                    return renderer;
                }
            }, YgoItems.DUEL_DISK.get());
            event.registerItem(new IClientItemExtensions() {
                private CardItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = new CardItemRenderer();
                    }
                    return renderer;
                }
            }, YgoItems.CARD.get());
        }

        @SubscribeEvent
        public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
            event.register((stack, layer) -> layer == 0 ? packColor(stack) : -1, YgoItems.BOOSTER_PACK.get());
        }

        /** Each set's pack has its own wrapper color; a random pack is grey-green. */
        private static int packColor(ItemStack stack) {
            var set = BoosterPackItem.set(stack);
            int rgb = set == null ? 0x6A8F6A : switch (set.code()) {
                case "LOB" -> 0x3D6FD8;
                case "MRD" -> 0x8A4FB8;
                case "MRL" -> 0xC8463C;
                default -> 0x40A0A0 ^ (set.code().hashCode() & 0x3F3F3F);
            };
            return 0xFF000000 | rgb;
        }

        @SubscribeEvent
        public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> DiskModel.reload());
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
        DiskClient.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientField.clientTick();
        DiskClient.clientTick();
        ClientDuel.autoplayTick();
        Minecraft mc = Minecraft.getInstance();
        if (TEST_CAMERA != null && mc.player != null) {
            String[] camera = TEST_CAMERA.split(":");
            mc.options.setCameraType(CameraType.valueOf(camera[0]));
            float body = mc.player.getYRot() + (camera.length > 1 ? Float.parseFloat(camera[1]) : 0);
            mc.player.setYBodyRot(body);
            mc.player.yBodyRotO = body;
        }
        if (mc.player != null && mc.gameMode != null && TEST_USE_ITEM.contains(mc.player.tickCount)) {
            mc.setScreen(null);
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        while (OPEN_DUEL.consumeClick()) {
            if (mc.screen == null && ClientDuel.view() != null) {
                mc.setScreen(new DuelScreen());
            }
        }
    }
}
