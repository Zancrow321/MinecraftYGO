package io.github.zancrow321.minecraftygo.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.zancrow321.minecraftygo.MinecraftYgo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Card artwork, downloaded on first use and cached in {@code <game dir>/minecraftygo/card_art/}. Nothing is bundled
 * with the mod. Callers draw a placeholder until {@link #get} returns a texture.
 */
public final class CardArt {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    /** Textures by passcode; an unfinished future is still loading. */
    private static final Map<Integer, CompletableFuture<Texture>> ART = new ConcurrentHashMap<>();

    /** A loaded artwork texture and its size in pixels (the size depends on the image source). */
    public record Texture(ResourceLocation location, int width, int height) {
    }

    private CardArt() {
    }

    /** @return the card's art texture if it's ready, otherwise {@code null} (and starts loading it) */
    public static Texture get(int code) {
        if (code <= 0) {
            return null;
        }
        CompletableFuture<Texture> art = ART.computeIfAbsent(code, CardArt::load);
        return art.isDone() && !art.isCompletedExceptionally() ? art.join() : null;
    }

    public static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("minecraftygo").resolve("card_art");
    }

    private static CompletableFuture<Texture> load(int code) {
        Path file = cacheDir().resolve(code + ".jpg");
        CompletableFuture<byte[]> bytes;
        if (Files.isRegularFile(file)) {
            bytes = CompletableFuture.supplyAsync(() -> read(file));
        } else if (YgoClientConfig.DOWNLOAD_CARD_ART.get()) {
            URI uri = URI.create(YgoClientConfig.CARD_ART_URL.get().replace("{code}", Integer.toString(code)));
            bytes = HTTP.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                            .header("User-Agent", "MinecraftYGO").build(), HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new IllegalStateException("HTTP " + response.statusCode() + " for " + uri);
                        }
                        write(file, response.body());
                        return response.body();
                    });
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException("card art downloads are off"));
        }
        return bytes.thenApply(CardArt::decode)
                .thenCompose(image -> CompletableFuture.supplyAsync(() -> {
                    ResourceLocation location = ResourceLocation.fromNamespaceAndPath(MinecraftYgo.MOD_ID,
                            "card_art/" + code);
                    Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
                    return new Texture(location, image.getWidth(), image.getHeight());
                }, Minecraft.getInstance()))
                .whenComplete((location, error) -> {
                    if (error != null) {
                        MinecraftYgo.LOGGER.warn("No art for card {}: {}", code, error.toString());
                    }
                });
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Path file, byte[] data) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".part");
            Files.write(tmp, data);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            MinecraftYgo.LOGGER.warn("Could not cache card art {}: {}", file, e.toString());
        }
    }

    /** Decodes JPEG or PNG; Minecraft's own loader only reads PNG. */
    private static NativeImage decode(byte[] data) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (source == null) {
            throw new IllegalStateException("not an image");
        }
        NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), false);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                // NativeImage stores ABGR.
                int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                image.setPixelRGBA(x, y, abgr);
            }
        }
        return image;
    }
}
