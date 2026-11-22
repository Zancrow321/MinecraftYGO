package io.github.zancrow321.jadm.custom;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.engine.data.CustomProducts;
import io.github.zancrow321.jadm.engine.data.Products;
import io.github.zancrow321.jadm.network.CustomProductsPayload;
import io.github.zancrow321.jadm.progression.Progress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads a server's own booster packs, structure decks and tins from its datapacks
 * ({@code data/<namespace>/jadm/products/*.json}) when they load and on {@code /reload}, and sends them to every
 * client, which needs them for names, tooltips and pictures.
 */
public final class CustomProductLoader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "jadm/products";

    /** What was wrong with the files last read, for {@code /jadm products}. */
    private static volatile List<String> problems = List.of();

    private CustomProductLoader() {
        super(new Gson(), DIRECTORY);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) ->
                event.addListener(new CustomProductLoader()));
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) ->
                event.getRelevantPlayers().forEach(CustomProductLoader::sync));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        List<String> found = new ArrayList<>();
        Map<String, Products.Product> byId = new LinkedHashMap<>();
        CustomProducts.Names names = new CustomProducts.Names(JadmData.cards());
        // In a stable order, so that of two files with one id the same one wins every time.
        for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
            ResourceLocation location = file.getKey();
            String id = location.getPath().replace('/', '_').toLowerCase(Locale.ROOT);
            String source = location.toString();
            if (!file.getValue().isJsonObject()) {
                found.add(source + ": not a JSON object");
                continue;
            }
            try {
                Products.Product product = CustomProducts.parse(id, source, file.getValue().getAsJsonObject(), names,
                        found::add);
                if (product != null) {
                    Products.Product before = byId.put(id, product);
                    if (before != null) {
                        found.add(source + ": has the same id \"" + id + "\" as " + before.custom().source()
                                + ", which it replaces");
                    }
                }
            } catch (RuntimeException e) {
                found.add(source + ": " + e.getMessage());
            }
        }
        problems = List.copyOf(found);
        found.forEach(line -> Jadm.LOGGER.warn("Custom product {}", line));
        List<Products.Product> products = List.copyOf(byId.values());
        if (!products.isEmpty()) {
            Jadm.LOGGER.info("Loaded {} custom products from datapacks", products.size());
        }
        JadmData.customProducts(products);
    }

    /** What was wrong with the datapack products last read. */
    public static List<String> problems() {
        return problems;
    }

    /** Sends a player the server's own products, and where they stand, since steps may have moved. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CustomProductsPayload(
                CustomProducts.toJson(JadmData.customProducts())));
        if (JadmServerConfig.SPEC.isLoaded()) {
            Progress.sync(player);
        }
    }

    /** On a client: the products the server sent, unless this game runs that server. */
    public static void received(CustomProductsPayload payload) {
        if (Progress.running()) {
            return;
        }
        try {
            JadmData.customProducts(CustomProducts.parseAll(payload.json(), JadmData.cards(),
                    line -> Jadm.LOGGER.warn("Custom product from the server {}", line)));
        } catch (RuntimeException e) {
            Jadm.LOGGER.warn("The server sent custom products that couldn't be read", e);
        }
    }
}
