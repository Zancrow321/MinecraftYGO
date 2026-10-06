package io.github.zancrow321.jadm.client;

import io.github.zancrow321.jadm.Jadm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pictures of the real products (booster packs, structure decks, tins), downloaded by set code on first use and
 * cached in {@code <game dir>/jadm/product_art/}. Nothing is bundled with the mod. The white background of a
 * product photo is cut away and the picture cropped to the product, so it can stand as the item itself.
 */
public final class ProductArt {
    /** Longest side of the inventory icon, which is drawn far smaller than the picture. */
    private static final int ICON_SIZE = 64;

    private static final Map<String, CompletableFuture<Art>> ART = new ConcurrentHashMap<>();

    /**
     * A product picture: {@code full} for the item in a hand or the world, {@code icon} (scaled down smoothly) for
     * slots, and the picture's width divided by its height.
     */
    public record Art(ResourceLocation full, ResourceLocation icon, float aspect) {
    }

    private ProductArt() {
    }

    /**
     * @return the product's picture if real product pictures are on and it's ready, otherwise {@code null} (and
     *         starts loading it); stays {@code null} if there is no picture for the code
     */
    public static Art get(String code) {
        if (code == null || code.isEmpty() || !JadmClientConfig.PRODUCT_IMAGES.get()) {
            return null;
        }
        CompletableFuture<Art> art = ART.computeIfAbsent(code, ProductArt::load);
        return art.isDone() && !art.isCompletedExceptionally() ? art.join() : null;
    }

    private static CompletableFuture<Art> load(String code) {
        String safe = code.replaceAll("[^A-Za-z0-9_-]", "_");
        Path file = CardArt.cacheDir().resolveSibling("product_art").resolve(safe + ".jpg");
        CompletableFuture<byte[]> bytes;
        if (Files.isRegularFile(file)) {
            bytes = CompletableFuture.supplyAsync(() -> CardArt.read(file));
        } else {
            URI uri = URI.create(JadmClientConfig.PRODUCT_IMAGE_URL.get().replace("{code}", code));
            bytes = CardArt.HTTP.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                            .header("User-Agent", "Just Another Dueling Mod").build(), HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new IllegalStateException("HTTP " + response.statusCode() + " for " + uri);
                        }
                        CardArt.write(file, response.body());
                        return response.body();
                    });
        }
        return bytes.thenApply(data -> crop(cutBackground(CardArt.pixels(data))))
                .thenCompose(full -> CompletableFuture.supplyAsync(() -> {
                    String path = "product_art/" + safe.toLowerCase(Locale.ROOT);
                    ResourceLocation fullLocation = register(path, full);
                    ResourceLocation iconLocation = register(path + "_icon", shrink(full, ICON_SIZE));
                    return new Art(fullLocation, iconLocation, full.width() / (float) full.height());
                }, Minecraft.getInstance()))
                .whenComplete((art, error) -> {
                    if (error != null) {
                        Jadm.LOGGER.warn("No product picture for {}: {}", code, error.toString());
                    }
                });
    }

    private static ResourceLocation register(String path, CardArt.Pixels pixels) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, path);
        DynamicTexture texture = new DynamicTexture(CardArt.toNative(pixels));
        Minecraft.getInstance().getTextureManager().register(location, texture);
        texture.setFilter(true, false);
        return location;
    }

    /** Whether a pixel is part of the near-white backdrop of a product photo. */
    private static boolean backdrop(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int min = Math.min(r, Math.min(g, b));
        int max = Math.max(r, Math.max(g, b));
        return min >= 228 && max - min <= 24;
    }

    /**
     * Makes the near-white backdrop that touches the picture's edge transparent, and gives the cleared pixels next
     * to the product the product's colour, so smooth scaling doesn't draw a white or black fringe around it.
     */
    static CardArt.Pixels cutBackground(CardArt.Pixels pixels) {
        int w = pixels.width();
        int h = pixels.height();
        int[] argb = pixels.argb().clone();
        boolean[] cleared = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            queue.add(x);
            queue.add((h - 1) * w + x);
        }
        for (int y = 0; y < h; y++) {
            queue.add(y * w);
            queue.add(y * w + w - 1);
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            if (cleared[i] || !backdrop(argb[i])) {
                continue;
            }
            cleared[i] = true;
            for (int n : neighbours(i, w, h)) {
                if (n >= 0) {
                    queue.add(n);
                }
            }
        }
        int[] out = argb.clone();
        for (int i = 0; i < out.length; i++) {
            if (!cleared[i]) {
                continue;
            }
            int color = 0;
            for (int n : neighbours(i, w, h)) {
                if (n >= 0 && !cleared[n]) {
                    color = argb[n] & 0xFFFFFF;
                    break;
                }
            }
            out[i] = color;
        }
        return new CardArt.Pixels(w, h, out);
    }

    /** The pixels left, right, above and below pixel {@code i}, or -1 past an edge. */
    private static int[] neighbours(int i, int w, int h) {
        int x = i % w;
        int y = i / w;
        return new int[]{x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
    }

    /** Crops a picture to its non-transparent pixels. */
    static CardArt.Pixels crop(CardArt.Pixels pixels) {
        int w = pixels.width();
        int h = pixels.height();
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((pixels.argb()[y * w + x] >>> 24) != 0) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 < 0) {
            throw new IllegalStateException("the picture is blank");
        }
        int cw = x1 - x0 + 1;
        int ch = y1 - y0 + 1;
        int[] out = new int[cw * ch];
        for (int y = 0; y < ch; y++) {
            System.arraycopy(pixels.argb(), (y0 + y) * w + x0, out, y * cw, cw);
        }
        return new CardArt.Pixels(cw, ch, out);
    }

    /** Scales a picture down so its longer side is at most {@code size}, averaging the pixels each one covers. */
    static CardArt.Pixels shrink(CardArt.Pixels pixels, int size) {
        int w = pixels.width();
        int h = pixels.height();
        double scale = Math.min(1, size / (double) Math.max(w, h));
        int sw = Math.max(1, (int) Math.round(w * scale));
        int sh = Math.max(1, (int) Math.round(h * scale));
        int[] out = new int[sw * sh];
        for (int y = 0; y < sh; y++) {
            int ya = y * h / sh;
            int yb = Math.max(ya + 1, (y + 1) * h / sh);
            for (int x = 0; x < sw; x++) {
                int xa = x * w / sw;
                int xb = Math.max(xa + 1, (x + 1) * w / sw);
                long a = 0, r = 0, g = 0, b = 0, plainR = 0, plainG = 0, plainB = 0;
                int n = 0;
                for (int sy = ya; sy < yb; sy++) {
                    for (int sx = xa; sx < xb; sx++) {
                        int c = pixels.argb()[sy * w + sx];
                        int alpha = c >>> 24;
                        // Weight colour by coverage so transparent pixels don't darken the edge.
                        a += alpha;
                        r += (long) ((c >> 16) & 0xFF) * alpha;
                        g += (long) ((c >> 8) & 0xFF) * alpha;
                        b += (long) (c & 0xFF) * alpha;
                        plainR += (c >> 16) & 0xFF;
                        plainG += (c >> 8) & 0xFF;
                        plainB += c & 0xFF;
                        n++;
                    }
                }
                // A clear pixel keeps the colour it was given, so smooth scaling doesn't fringe the edge.
                out[y * sw + x] = a == 0
                        ? (int) (plainR / n) << 16 | (int) (plainG / n) << 8 | (int) (plainB / n)
                        : (int) (a / n) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
            }
        }
        return new CardArt.Pixels(sw, sh, out);
    }
}
