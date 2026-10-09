package io.github.zancrow321.jadm.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.client.disk.DiskClient;
import io.github.zancrow321.jadm.client.duel.DuelMode;
import io.github.zancrow321.jadm.client.collection.CardItemRenderer;
import io.github.zancrow321.jadm.client.collection.CardPreview;
import io.github.zancrow321.jadm.client.collection.ProductItemRenderer;
import io.github.zancrow321.jadm.client.disk.DiskItemRenderer;
import io.github.zancrow321.jadm.client.disk.DiskLayer;
import io.github.zancrow321.jadm.client.disk.DiskModel;
import io.github.zancrow321.jadm.client.field.ClientField;
import io.github.zancrow321.jadm.client.field.DuelHud;
import io.github.zancrow321.jadm.client.field.FieldRenderer;
import io.github.zancrow321.jadm.client.render.DuelistNpcRenderer;
import io.github.zancrow321.jadm.compat.figura.FiguraCompat;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import io.github.zancrow321.jadm.client.render.MonsterRenderer;
import io.github.zancrow321.jadm.arena.DuelArena;
import io.github.zancrow321.jadm.client.arena.ArenaRenderer;
import io.github.zancrow321.jadm.entity.JadmEntities;
import io.github.zancrow321.jadm.item.BoosterPackItem;
import io.github.zancrow321.jadm.item.JadmItems;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
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
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Jadm.MOD_ID, value = Dist.CLIENT)
public final class JadmClient {
    /** Switches between the top-down and first-person view in duel mode (read by the duel mode screen). */
    public static final KeyMapping DUEL_CAMERA = new KeyMapping("key.jadm.duel_camera",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.jadm");
    /** Opens and closes the duel log in duel mode. */
    public static final KeyMapping DUEL_LOG = new KeyMapping("key.jadm.duel_log",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.jadm");
    /** Switches between being asked to respond and passing every response in duel mode. */
    public static final KeyMapping DUEL_RESPONSES = new KeyMapping("key.jadm.duel_responses",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.jadm");
    public static final KeyMapping COSMETICS = new KeyMapping("key.jadm.cosmetics",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.jadm");
    /** Opens the ranking window. */
    public static final KeyMapping RANKING = new KeyMapping("key.jadm.ranking",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.jadm");
    /** Opens the admin menu, for operators. */
    public static final KeyMapping ADMIN = new KeyMapping("key.jadm.admin",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.jadm");

    /**
     * For headless testing: {@code -Djadm.camera=THIRD_PERSON_FRONT:90} keeps the camera there and turns
     * the body that many degrees from the head (to see the duel disk from the side).
     */
    private static final String TEST_CAMERA = System.getProperty("jadm.camera");

    /**
     * For headless testing: {@code -Djadm.useItem=80,200} closes any screen and right-clicks with the
     * main-hand item (on the block or entity in the crosshair, if any) when the player has been in the world that many ticks,
     * to open packs, binders and deck boxes or place things without a mouse.
     */
    private static final java.util.Set<Integer> TEST_USE_ITEM = java.util.Arrays.stream(
                    System.getProperty("jadm.useItem", "").split(","))
            .filter(t -> !t.isBlank()).map(String::trim).map(Integer::valueOf)
            .collect(java.util.stream.Collectors.toSet());

    private JadmClient() {
    }

    @EventBusSubscriber(modid = Jadm.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(JadmEntities.MONSTER.get(), MonsterRenderer::new);
            event.registerEntityRenderer(JadmEntities.DUELIST.get(), DuelistNpcRenderer::new);
            event.registerBlockEntityRenderer(DuelArena.ARENA_ENTITY.get(), ArenaRenderer::new);
            event.registerBlockEntityRenderer(io.github.zancrow321.jadm.ranking.RankingBoard.ENTITY.get(),
                    io.github.zancrow321.jadm.client.ranking.RankingBoardRenderer::new);
            event.registerBlockEntityRenderer(io.github.zancrow321.jadm.arena.DuelDome.CORE_ENTITY.get(),
                    io.github.zancrow321.jadm.client.arena.ArenaCoreRenderer::new);
        }

        @SubscribeEvent
        public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            event.register(io.github.zancrow321.jadm.village.PlayerShops.STAND_MENU.get(),
                    io.github.zancrow321.jadm.client.shop.ShopStandScreen::new);
            event.register(io.github.zancrow321.jadm.points.Points.SHOP_MENU.get(),
                    io.github.zancrow321.jadm.client.shop.PointShopScreen::new);
            event.register(io.github.zancrow321.jadm.trade.Trades.MENU.get(),
                    io.github.zancrow321.jadm.client.trade.TradeScreen::new);
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof LivingEntityRenderer<?, ?> renderer) {
                    var playerRenderer = (LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>)
                            renderer;
                    playerRenderer.addLayer(new DiskLayer<>(playerRenderer));
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
            }, JadmItems.DUEL_DISK.get());
            event.registerItem(new IClientItemExtensions() {
                private CardItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = new CardItemRenderer();
                    }
                    return renderer;
                }
            }, JadmItems.CARD.get());
            productRenderer(event, JadmItems.BOOSTER_PACK.get(), "booster_pack", 1, 1);
            productRenderer(event, JadmItems.STRUCTURE_DECK.get(), "structure_deck", 3, 0.8f);
            productRenderer(event, JadmItems.TIN.get(), "tin", 2.5f, 0.85f);
        }

        /**
         * Real product pictures for packs, decks and tins; {@code thickness} in sixteenths, {@code size} outside slots.
         */
        private static void productRenderer(RegisterClientExtensionsEvent event, Item item,
                                            String name, float thickness, float size) {
            event.registerItem(new IClientItemExtensions() {
                private ProductItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = new ProductItemRenderer(name, thickness, size);
                    }
                    return renderer;
                }
            }, item);
        }

        /** The packs', decks' and tins' own icons, drawn when there is no product picture. */
        @SubscribeEvent
        public static void registerModels(ModelEvent.RegisterAdditional event) {
            for (String item : new String[]{"booster_pack", "structure_deck", "tin"}) {
                event.register(ProductItemRenderer.iconModel(item));
            }
        }

        @SubscribeEvent
        public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
            event.register((stack, layer) -> layer == 0 ? packColor(stack) : -1, JadmItems.BOOSTER_PACK.get());
            event.register((stack, layer) -> layer == 0 ? productColor(stack) : -1, JadmItems.STRUCTURE_DECK.get(),
                    JadmItems.TIN.get());
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

        /** Decks and tins get a color of their own per product. */
        private static int productColor(ItemStack stack) {
            var product = io.github.zancrow321.jadm.item.SealedProductItem.product(stack);
            int rgb = product == null ? 0x8090A0 : 0x808080 ^ (product.id().hashCode() & 0x7F7F7F);
            return 0xFF000000 | rgb;
        }

        @SubscribeEvent
        public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> DiskModel.reload());
            event.registerReloadListener((ResourceManagerReloadListener)
                    io.github.zancrow321.jadm.client.render.PackModels::reload);
        }

        @SubscribeEvent
        public static void registerHud(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR,
                    ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "duel_hud"), DuelHud::render);
            // Under the chat, so new messages show over a held card's preview.
            event.registerBelow(VanillaGuiLayers.CHAT,
                    ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "card_preview"), CardPreview::renderHud);
            event.registerAbove(VanillaGuiLayers.HOTBAR,
                    ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "star_chips"),
                    io.github.zancrow321.jadm.client.starchips.StarChipHud::render);
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(FiguraCompat::init);
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(DUEL_CAMERA);
            event.register(DUEL_LOG);
            event.register(DUEL_RESPONSES);
            event.register(COSMETICS);
            event.register(ADMIN);
            event.register(RANKING);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        FieldRenderer.render(event);
    }

    @SubscribeEvent
    public static void beforeScreen(ScreenEvent.Render.Pre event) {
        CardPreview.beforeRender(event);
    }

    @SubscribeEvent
    public static void afterScreen(ScreenEvent.Render.Post event) {
        CardPreview.afterRender(event);
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
        DuelMode.clientTick();
        Minecraft mc = Minecraft.getInstance();
        if (TEST_CAMERA != null && mc.player != null) {
            String[] camera = TEST_CAMERA.split(":");
            mc.options.setCameraType(CameraType.valueOf(camera[0]));
            float body = mc.player.getYRot() + (camera.length > 1 ? Float.parseFloat(camera[1]) : 0);
            mc.player.setYBodyRot(body);
            mc.player.yBodyRotO = body;
        }
        if (mc.player != null && mc.gameMode != null && TEST_USE_ITEM.contains(mc.player.tickCount)) {
            Jadm.LOGGER.info("Test hook: using the item at tick {}", mc.player.tickCount);
            if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
                mc.player.closeContainer(); // tells the server too, so a villager stops trading
            } else {
                mc.setScreen(null);
            }
            if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            } else if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit) {
                mc.gameMode.interact(mc.player, hit.getEntity(), net.minecraft.world.InteractionHand.MAIN_HAND);
            } else {
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }
        while (COSMETICS.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.player.connection.sendCommand("jadm cosmetics");
            }
        }
        while (RANKING.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.player.connection.sendCommand(Jadm.COMMAND + " rank");
            }
        }
        while (ADMIN.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                if (mc.player.hasPermissions(io.github.zancrow321.jadm.admin.AdminMenu.LEVEL)) {
                    mc.player.connection.sendCommand(Jadm.COMMAND + " admin");
                } else {
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.jadm.admin.not_op"), true);
                }
            }
        }
    }
}
