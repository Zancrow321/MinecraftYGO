package io.github.zancrow321.jadm.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
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
 * Card artwork, downloaded on first use and cached in {@code <game dir>/jadm/card_art/} (whole cards) and
 * {@code card_art_cropped/} (the artwork alone, for holograms). Nothing is bundled with the mod. Callers draw a
 * placeholder until {@link #get} or {@link #hologram} returns a texture.
 */
public final class CardArt {
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    /** Textures by passcode; an unfinished future is still loading. */
    private static final Map<Integer, CompletableFuture<Texture>> ART = new ConcurrentHashMap<>();
    private static final Map<Integer, CompletableFuture<Texture>> HOLOGRAMS = new ConcurrentHashMap<>();
    private static final Map<Integer, CompletableFuture<Texture>> GHOSTS = new ConcurrentHashMap<>();
    private static final Map<Integer, CompletableFuture<Texture>> LETTERS = new ConcurrentHashMap<>();
    /** The part of a card that {@link #letters} looks at: its name, as fractions of the card image. */
    private static volatile float[] lettersBox = {0, 0, 1, 1};

    /**
     * The kinds of art: the whole card, its artwork as a hologram, the whole card as a Ghost Rare shows it, and the
     * lettering of its name.
     */
    private enum Kind {
        CARD, HOLOGRAM, GHOST, LETTERS
    }

    /** A loaded artwork texture and its size in pixels (the size depends on the image source). */
    public record Texture(ResourceLocation location, int width, int height) {
    }

    private CardArt() {
    }

    /** @return the card's art texture if it's ready, otherwise {@code null} (and starts loading it) */
    public static Texture get(int code) {
        return ready(code, ART, Kind.CARD);
    }

    /**
     * @return the whole card with its colors gone, a pale silver image, as a Ghost Rare's artwork looks; or
     *         {@code null} until it's ready (and starts loading it)
     */
    public static Texture ghost(int code) {
        return ready(code, GHOSTS, Kind.GHOST);
    }

    /**
     * @return where the card's name is lettered, as a mask over the box {@code left, top, right, bottom} (fractions of
     *         the card image): white and opaque on the letters, clear elsewhere, its colors premultiplied by its alpha
     *         so foil can color the letters alone; or {@code null} until it's ready (and starts loading it). The box
     *         is the same for every card; the first call's box is used.
     */
    public static Texture letters(int code, float left, float top, float right, float bottom) {
        if (LETTERS.isEmpty()) {
            lettersBox = new float[]{left, top, right, bottom};
        }
        return ready(code, LETTERS, Kind.LETTERS);
    }

    /**
     * @return the card's artwork alone, made up as a hologram (scan lines, a blue cast, soft edges), if it's ready;
     *         otherwise {@code null} (and starts loading it). Stays {@code null} if it can't be had.
     */
    public static Texture hologram(int code) {
        return ready(code, HOLOGRAMS, Kind.HOLOGRAM);
    }

    /** Whether the hologram art failed for good (downloads off, or no image), so a fallback should be drawn. */
    public static boolean hologramMissing(int code) {
        CompletableFuture<Texture> art = HOLOGRAMS.get(code);
        if (art == null || !art.isCompletedExceptionally()) {
            return false;
        }
        int original = JadmData.original(code);
        return original == code || hologramMissing(original);
    }

    private static Texture ready(int code, Map<Integer, CompletableFuture<Texture>> cache, Kind kind) {
        if (code <= 0) {
            return null;
        }
        CompletableFuture<Texture> art = cache.computeIfAbsent(code, c -> load(c, kind));
        if (art.isCompletedExceptionally()) {
            // An alternate artwork without a picture of its own shows the original's.
            int original = JadmData.original(code);
            return original == code ? null : ready(original, cache, kind);
        }
        return art.isDone() ? art.join() : null;
    }

    public static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("jadm").resolve("card_art");
    }

    private static CompletableFuture<Texture> load(int code, Kind kind) {
        boolean cropped = kind == Kind.HOLOGRAM;
        Path dir = cropped ? cacheDir().resolveSibling("card_art_cropped") : cacheDir();
        Path file = dir.resolve(code + ".jpg");
        CompletableFuture<byte[]> bytes;
        if (kind == Kind.GHOST || kind == Kind.LETTERS) {
            // Made from the card's own image once that is cached, so the two never download it at once.
            bytes = ART.computeIfAbsent(code, c -> load(c, Kind.CARD)).thenApplyAsync(texture -> read(file));
        } else if (Files.isRegularFile(file)) {
            bytes = CompletableFuture.supplyAsync(() -> read(file));
        } else if (JadmClientConfig.DOWNLOAD_CARD_ART.get()) {
            String url = (cropped ? JadmClientConfig.CARD_ART_CROPPED_URL : JadmClientConfig.CARD_ART_URL).get();
            URI uri = URI.create(url.replace("{code}", Integer.toString(code)));
            bytes = HTTP.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                            .header("User-Agent", "Just Another Dueling Mod").build(), HttpResponse.BodyHandlers.ofByteArray())
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
        return bytes.thenApply(data -> switch (kind) {
                    case CARD -> decode(data);
                    case HOLOGRAM -> holographic(decode(data));
                    case GHOST -> ghostly(decode(data));
                    case LETTERS -> letters(decode(data), lettersBox);
                })
                .thenCompose(image -> CompletableFuture.supplyAsync(() -> {
                    ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, switch (kind) {
                        case CARD -> "card_art/";
                        case HOLOGRAM -> "card_hologram/";
                        case GHOST -> "card_ghost/";
                        case LETTERS -> "card_letters/";
                    } + code);
                    Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
                    return new Texture(location, image.getWidth(), image.getHeight());
                }, Minecraft.getInstance()))
                .whenComplete((location, error) -> {
                    if (error != null) {
                        Jadm.LOGGER.warn("No {} for card {}: {}", switch (kind) {
                            case CARD -> "art";
                            case HOLOGRAM -> "hologram art";
                            case GHOST -> "ghost art";
                            case LETTERS -> "name lettering";
                        }, code, error.toString());
                    }
                });
    }

    /**
     * Makes artwork look projected: a cool blue cast, darker scan lines every few rows and edges that fade out.
     * Works on the ABGR pixels of a {@link NativeImage} in place.
     */
    private static NativeImage holographic(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        double fade = Math.max(4, Math.min(w, h) * 0.06);
        for (int y = 0; y < h; y++) {
            boolean line = (y * 96 / h) % 3 == 0;
            for (int x = 0; x < w; x++) {
                int abgr = image.getPixelRGBA(x, y);
                int r = abgr & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                // Lift the blues and lower the reds a little.
                r = (int) (r * 0.82);
                g = (int) Math.min(255, g * 0.95 + 10);
                b = (int) Math.min(255, b * 0.9 + 40);
                if (line) {
                    r = r * 3 / 5;
                    g = g * 3 / 5;
                    b = b * 3 / 4;
                }
                double edge = Math.min(Math.min(x, w - 1 - x), Math.min(y, h - 1 - y)) / fade;
                int a = (int) (235 * Math.min(1, edge));
                image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return image;
    }

    /**
     * Makes a card look as a Ghost Rare's artwork does: the colors gone, a pale silver image with a cool cast, its
     * dark lines softened. Works on the ABGR pixels of a {@link NativeImage} in place.
     */
    private static NativeImage ghostly(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                double lum = ((abgr & 0xFF) * 0.299 + ((abgr >> 8) & 0xFF) * 0.587 + ((abgr >> 16) & 0xFF) * 0.114)
                        / 255;
                double v = 0.16 + 0.84 * Math.pow(lum, 0.95);
                int r = (int) Math.min(255, v * 0.93 * 255);
                int g = (int) Math.min(255, v * 0.97 * 255);
                int b = (int) Math.min(255, v * 1.04 * 255);
                image.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
            }
        }
        return image;
    }

    /**
     * Finds the lettering in a box of a card: pixels much brighter or darker than the box's middle brightness, so dark
     * names on light frames and white names on dark ones alike. Returns the mask as a new image the size of the box.
     */
    private static NativeImage letters(NativeImage card, float[] box) {
        int x0 = Math.round(box[0] * card.getWidth());
        int y0 = Math.round(box[1] * card.getHeight());
        int w = Math.max(1, Math.round(box[2] * card.getWidth()) - x0);
        int h = Math.max(1, Math.round(box[3] * card.getHeight()) - y0);
        int[] lum = new int[w * h];
        int[] histogram = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = card.getPixelRGBA(x0 + x, y0 + y);
                int l = Math.min(255, (int) ((abgr & 0xFF) * 0.299 + ((abgr >> 8) & 0xFF) * 0.587
                        + ((abgr >> 16) & 0xFF) * 0.114));
                lum[y * w + x] = l;
                histogram[l]++;
            }
        }
        int median = 0;
        for (int seen = 0; seen + histogram[median] <= lum.length / 2 && median < 255; median++) {
            seen += histogram[median];
        }
        NativeImage mask = new NativeImage(w, h, false);
        for (int i = 0; i < lum.length; i++) {
            double m = Math.clamp((Math.abs(lum[i] - median) / 255.0 - 0.15) / 0.2, 0, 1);
            int v = (int) Math.round(m * 255);
            mask.setPixelRGBA(i % w, i / w, v << 24 | v << 16 | v << 8 | v);
        }
        card.close();
        return mask;
    }

    static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void write(Path file, byte[] data) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".part");
            Files.write(tmp, data);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Jadm.LOGGER.warn("Could not cache image {}: {}", file, e.toString());
        }
    }

    static NativeImage decode(byte[] data) {
        return toNative(pixels(data));
    }

    /** An image as ARGB pixels, row by row. */
    record Pixels(int width, int height, int[] argb) {
    }

    /** Decodes JPEG or PNG into ARGB pixels; Minecraft's own loader only reads PNG. */
    static Pixels pixels(byte[] data) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (source == null) {
            throw new IllegalStateException("not an image");
        }
        int w = source.getWidth();
        int h = source.getHeight();
        return new Pixels(w, h, source.getRGB(0, 0, w, h, null, 0, w));
    }

    static NativeImage toNative(Pixels pixels) {
        NativeImage image = new NativeImage(pixels.width(), pixels.height(), false);
        for (int y = 0; y < pixels.height(); y++) {
            for (int x = 0; x < pixels.width(); x++) {
                int argb = pixels.argb()[y * pixels.width() + x];
                // NativeImage stores ABGR.
                int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                image.setPixelRGBA(x, y, abgr);
            }
        }
        return image;
    }
}
