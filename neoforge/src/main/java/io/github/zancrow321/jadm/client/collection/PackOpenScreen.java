package io.github.zancrow321.jadm.client.collection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.CardArt;
import io.github.zancrow321.jadm.client.JadmClientConfig;
import io.github.zancrow321.jadm.client.ProductArt;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Opening a booster pack as a show: the pack flies in, tears open in 3D at a click, and its cards fan out face down.
 * Rare ones announce themselves face down: they glow in the color of their rarity, the better ones throw sparks, and a
 * Secret Rare or better stands in a beam of light and twitches. Click a card to turn it over; the rarer, the bigger
 * the reveal (sparks, a shock wave, a flash, the screen shaking, confetti, its rarity in big letters). "Reveal all"
 * turns the rest over one after the other, Space or a right click skips to all of them face up. Once all are up they
 * line up in rows to look at. With the client option {@code packOpeningShow} off, the cards just turn over in rows.
 */
public final class PackOpenScreen extends Screen {
    private static final ResourceLocation PACK_ICON =
            ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "textures/item/booster_pack.png");
    /** Size of a card as drawn before scaling, that of the card back texture. */
    private static final int CARD_W = 68;
    private static final int CARD_H = 100;
    private static final float CARD_RATIO = 391f / 268f;

    /** The pack flying in */
    private static final int FLY_IN = 18;
    /** From the click to the top of the pack tearing off */
    private static final int RIP = 6;
    /** From the click to the first card flying out */
    private static final int TEAR = 12;
    /** Between two cards flying out of the pack, and how long one flies */
    private static final int DEAL_GAP = 3;
    private static final int DEAL = 12;
    private static final int FLIP = 10;
    /** From the last card turned to the cards lining up, and how long that takes */
    private static final int LINE_UP_PAUSE = 24;
    private static final int LINE_UP = 14;
    /** Between two cards that "Reveal all" turns */
    private static final int REVEAL_ALL_GAP = 5;
    /** Where the top of the pack tears off, as a fraction of its height */
    private static final float TEAR_LINE = 0.17f;

    private enum Stage {
        /** The pack, waiting to be torn open */
        PACK,
        TEAR,
        /** The cards in a fan, to be turned over one by one */
        FAN,
        /** All cards face up in rows */
        ROWS
    }

    /** One card of the pack. */
    private static final class Slot {
        final int code;
        final Rarity rarity;
        /** Tick its turn over starts, -1 if not yet clicked; before that it charges up */
        int flipAt = -1;
        int clickedAt;
        boolean revealed;
        boolean announced;
        /** Tick it was turned face up (half way through the turn) */
        int revealedAt;

        Slot(JadmComponents.CardStack card) {
            this.code = card.code();
            Rarity r;
            try {
                r = Rarity.parse(card.rarity());
            } catch (IllegalArgumentException e) {
                r = Rarity.COMMON;
            }
            this.rarity = r;
        }

        /** 0 for a common up to 4 for the Secret Rare slot */
        int tier() {
            return Rarity.SLOTS.indexOf(rarity.slot());
        }
    }

    /** Where and how a card is drawn: its middle, turn in the screen, width, and how far it is turned over (0-180). */
    private record CardPose(float x, float y, float angle, float width, float flip) {
    }

    /** A big word over the cards, e.g. "SECRET RARE!" */
    private record Banner(Component text, Slot slot, int at) {
    }

    private final Slot[] slots;
    private final String productCode;
    /** The screen to go on to once this one is closed, or {@code null} */
    private final Screen next;
    private final ShowFx.Particles particles = new ShowFx.Particles();

    private Stage stage;
    private int ticks;
    private int tearAt;
    /** Whether the pack was torn open in this screen (not so without the show) */
    private boolean torn;
    private int dealAt;
    private int rowsAt;
    private int lastRevealAt;
    private float shake;
    private float flash;
    private int flashColor = 0xFFFFFFFF;
    private Banner banner;
    private Button button;
    private int hovered = -1;
    /** Whether the cards went straight into the player's binder. */
    private boolean intoBinder;

    public PackOpenScreen(String setName, List<JadmComponents.CardStack> cards) {
        this(setName, "", cards, null);
    }

    /** A pack the server just opened; {@code intoBinder} when its cards went straight into the player's binder. */
    public PackOpenScreen(String setName, String productCode, List<JadmComponents.CardStack> cards,
                          boolean intoBinder) {
        this(setName, productCode, cards, null);
        this.intoBinder = intoBinder;
    }

    /**
     * @param productCode the set code the picture of the pack is looked up by, or empty for the mod's own pack
     * @param next        the screen to go on to afterwards, e.g. the next pack of a Sealed tournament, or {@code null}
     */
    public PackOpenScreen(String setName, String productCode, List<JadmComponents.CardStack> cards, Screen next) {
        super(Component.literal(setName));
        this.slots = cards.stream().map(Slot::new).toArray(Slot[]::new);
        this.productCode = productCode == null ? "" : productCode;
        this.next = next;
        if (JadmClientConfig.PACK_OPENING_SHOW.get()) {
            stage = Stage.PACK;
        } else {
            // No show: the cards lie face down in rows and turn over one after the other.
            stage = Stage.ROWS;
            rowsAt = -LINE_UP;
            for (int i = 0; i < slots.length; i++) {
                slots[i].announced = true;
                slots[i].flipAt = 2 + i * 3;
            }
        }
    }

    @Override
    protected void init() {
        ShowFx.ensureTextures();
        button = addRenderableWidget(Button.builder(Component.empty(), b -> pressButton())
                .bounds(width / 2 - 70, height - 26, 140, 20).build());
        updateButton();
    }

    private boolean allRevealed() {
        for (Slot s : slots) {
            if (!s.revealed) {
                return false;
            }
        }
        return true;
    }

    /** Whether everything has happened and the screen only waits to be closed. */
    private boolean done() {
        return stage == Stage.ROWS && allRevealed() && ticks >= lastRevealAt + FLIP / 2;
    }

    private void updateButton() {
        if (button == null) {
            return;
        }
        if (allRevealed()) {
            button.setMessage(Component.translatable(next instanceof PackOpenScreen
                    ? "screen.jadm.pack.next" : "screen.jadm.pack.done"));
        } else {
            button.setMessage(Component.translatable("screen.jadm.pack.reveal_all"));
        }
        button.active = !allRevealed() || stage == Stage.ROWS || ticks >= lastRevealAt + FLIP;
    }

    private void pressButton() {
        if (allRevealed()) {
            onClose();
        } else {
            revealAll();
        }
    }

    @Override
    public void onClose() {
        if (next != null && minecraft != null) {
            minecraft.setScreen(next);
        } else {
            super.onClose();
        }
    }

    // --- What happens ---------------------------------------------------------------------------------------------

    private void tear() {
        if (stage != Stage.PACK) {
            return;
        }
        stage = Stage.TEAR;
        tearAt = ticks;
        torn = true;
        sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.4f, 0.8f);
    }

    /** The top of the pack tears off. */
    private void rip() {
        sound(SoundEvents.BUNDLE_DROP_CONTENTS, 0.8f, 1);
        sound(SoundEvents.SHEEP_SHEAR, 1.3f, 0.6f);
        float[] pack = packBox();
        float y = pack[1] - pack[3] + pack[3] * 2 * TEAR_LINE;
        for (int i = 0; i < 26; i++) {
            float x = pack[0] + (particles.random.nextFloat() * 2 - 1) * pack[2];
            ShowFx.Particle p = particles.add(i % 3 == 0 ? ShowFx.Kind.STAR : ShowFx.Kind.SPARK, x, y,
                    (particles.random.nextFloat() * 2 - 1) * 3, -1 - particles.random.nextFloat() * 5,
                    2 + particles.random.nextFloat() * 2, i % 2 == 0 ? 0xFFFFE9A0 : 0xFFFFFFFF, 16 + i % 10);
            p.gravity = 0.2f;
        }
        shake = Math.max(shake, 4);
        flash = 0.35f;
        flashColor = 0xFFFFF4D0;
    }

    /** Starts turning a card over; better rarities charge up first. */
    private void flip(int i, int delay) {
        Slot s = slots[i];
        if (s.flipAt >= 0) {
            return;
        }
        int charge = switch (s.tier()) {
            case 4 -> 16;
            case 3 -> 8;
            default -> 0;
        };
        s.clickedAt = ticks + delay;
        s.flipAt = ticks + delay + charge;
        if (delay == 0) {
            startCharge(s);
        }
    }

    private void startCharge(Slot s) {
        if (s.tier() >= 4) {
            sound(SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.9f);
        } else if (s.tier() == 3) {
            sound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.4f, 1);
        }
    }

    /** Turns over every card not turned yet, one after the other. */
    private void revealAll() {
        if (stage == Stage.PACK) {
            tear();
        }
        int at = Math.max(0, dealAt(slots.length - 1) + DEAL - ticks);
        if (stage.ordinal() < Stage.FAN.ordinal()) {
            at = TEAR + (slots.length - 1) * DEAL_GAP + DEAL;
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].flipAt < 0) {
                flip(i, at);
                at += REVEAL_ALL_GAP;
            }
        }
    }

    /** Skips the show: all cards face up in rows at once. */
    private void skip() {
        Slot best = null;
        for (Slot s : slots) {
            if (!s.revealed) {
                s.revealed = true;
                s.announced = true;
                s.revealedAt = ticks - 100;
                s.flipAt = ticks - 100;
                if (best == null || s.tier() > best.tier()) {
                    best = s;
                }
            }
        }
        stage = Stage.ROWS;
        rowsAt = ticks - LINE_UP;
        lastRevealAt = ticks - FLIP;
        particles.clear();
        if (best != null) {
            revealSound(best);
        }
        updateButton();
    }

    /** The card turns face up, half way through turning over. */
    private void reveal(int i) {
        Slot s = slots[i];
        s.revealed = true;
        s.revealedAt = ticks;
        lastRevealAt = ticks;
        revealSound(s);
        CardPose pose = pose(i, ticks);
        int[] colors = colors(s);
        int tier = s.tier();
        if (!JadmClientConfig.PACK_OPENING_SHOW.get()) {
            return;
        }
        if (tier == 0) {
            particles.burst(pose.x(), pose.y(), 5, 3, new int[]{0xFFFFFFFF});
            return;
        }
        particles.burst(pose.x(), pose.y(), new int[]{0, 14, 26, 40, 70}[tier], 4 + tier * 1.5f, colors);
        if (tier >= 2) {
            particles.ring(pose.x(), pose.y(), pose.width() * 0.5f, 3 + tier * 1.5f, colors[0], 12 + tier * 2);
        }
        if (tier >= 3) {
            particles.ring(pose.x(), pose.y(), pose.width() * 0.3f, 2 + tier, 0xFFFFFFFF, 16);
            banner = new Banner(Component.literal(CardItem.rarityName(s.rarity).toUpperCase(java.util.Locale.ROOT)
                    + "!"), s, ticks);
        }
        if (tier >= 4) {
            particles.confetti(width / 2f, -10, jackpot(s) ? 140 : 80, width / 2f, colors(s, true));
        }
        shake = Math.max(shake, new float[]{0, 1, 2.5f, 5, 9}[tier] * (jackpot(s) ? 1.4f : 1));
        flash = Math.max(flash, new float[]{0, 0.15f, 0.3f, 0.5f, 0.85f}[tier]);
        flashColor = tier >= 4 && !jackpot(s) ? colors[0] : 0xFFFFF4E0;
    }

    private void revealSound(Slot s) {
        float pitch = 0.9f + particles.random.nextFloat() * 0.3f;
        switch (s.tier()) {
            case 0 -> sound(SoundEvents.BOOK_PAGE_TURN, pitch, 1);
            case 1 -> {
                sound(SoundEvents.BOOK_PAGE_TURN, pitch, 1);
                sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.5f);
            }
            case 2 -> {
                sound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4f, 1);
                sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f, 0.8f);
            }
            case 3 -> {
                sound(SoundEvents.PLAYER_LEVELUP, 1, 0.8f);
                sound(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.2f, 0.8f);
            }
            default -> {
                sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1, 0.9f);
                sound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.9f, 1);
                if (jackpot(s)) {
                    sound(SoundEvents.TOTEM_USE, 1.2f, 0.6f);
                }
            }
        }
    }

    /** A rare card landing face down in the fan announces itself. */
    private void announce(int i) {
        Slot s = slots[i];
        s.announced = true;
        switch (s.tier()) {
            case 0 -> sound(SoundEvents.BOOK_PUT, 1.2f + i * 0.04f, 0.5f);
            case 1 -> sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.2f);
            case 2 -> sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f, 0.7f);
            case 3 -> sound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 0.8f);
            default -> {
                sound(SoundEvents.BEACON_ACTIVATE, 1.3f, 1);
                shake = Math.max(shake, 3);
            }
        }
        if (s.tier() > 0) {
            CardPose pose = pose(i, ticks);
            particles.burst(pose.x(), pose.y(), 6 + s.tier() * 4, 2 + s.tier(), colors(s));
        }
    }

    private void sound(SoundEvent sound, float pitch, float volume) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }

    @Override
    public void tick() {
        ticks++;
        particles.tick();
        shake *= 0.8f;
        flash *= 0.82f;
        if (stage == Stage.PACK && ticks == FLY_IN) {
            sound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8f, 1);
            shake = 2;
        }
        if (stage == Stage.PACK && ticks > FLY_IN && ticks % 3 == 0) {
            float[] pack = packBox();
            float a = particles.random.nextFloat() * Mth.TWO_PI;
            float r = Math.max(pack[2], pack[3]) * (1.05f + particles.random.nextFloat() * 0.3f);
            particles.add(ShowFx.Kind.STAR, pack[0] + Mth.cos(a) * r, pack[1] + Mth.sin(a) * r * 0.9f, 0, -0.3f,
                    2 + particles.random.nextFloat() * 2, 0xFFFFE070, 20);
        }
        if (stage == Stage.TEAR) {
            if (ticks == tearAt + RIP) {
                rip();
            }
            if (ticks >= tearAt + TEAR) {
                stage = Stage.FAN;
                dealAt = ticks;
            }
        }
        for (int i = 0; i < slots.length; i++) {
            Slot s = slots[i];
            if (stage == Stage.FAN && ticks == dealAt(i)) {
                sound(SoundEvents.BOOK_PAGE_TURN, 1.4f + particles.random.nextFloat() * 0.3f, 0.5f);
            }
            if (stage == Stage.FAN && !s.announced && ticks >= dealAt(i) + DEAL) {
                announce(i);
            }
            if (s.flipAt >= 0 && s.clickedAt == ticks && s.flipAt > ticks) {
                startCharge(s);
            }
            if (s.flipAt >= 0 && s.flipAt == ticks) {
                sound(SoundEvents.BOOK_PAGE_TURN, 1.1f, 0.7f);
            }
            if (s.flipAt >= 0 && !s.revealed && ticks >= s.flipAt + FLIP / 2) {
                reveal(i);
            }
            if (s.announced && !s.revealed && stage != Stage.PACK) {
                ambient(i, s);
            }
        }
        if (stage == Stage.FAN && allRevealed() && ticks >= lastRevealAt + LINE_UP_PAUSE) {
            stage = Stage.ROWS;
            rowsAt = ticks;
        }
        updateButton();
    }

    /** Sparks around a rare card waiting face down; the better, the more. */
    private void ambient(int i, Slot s) {
        int tier = s.tier();
        if (tier == 0 || !JadmClientConfig.PACK_OPENING_SHOW.get()) {
            return;
        }
        int every = new int[]{0, 8, 4, 2, 1}[tier];
        boolean charging = s.flipAt > ticks;
        if (ticks % every != 0 && !charging) {
            return;
        }
        CardPose pose = pose(i, ticks);
        float hw = pose.width() / 2;
        float hh = hw * CARD_RATIO;
        int[] colors = colors(s);
        int color = colors[particles.random.nextInt(colors.length)];
        if (charging) {
            // Sparks drawn into the card while it charges up.
            float a = particles.random.nextFloat() * Mth.TWO_PI;
            float r = hh * 1.6f;
            particles.add(ShowFx.Kind.SPARK, pose.x() + Mth.cos(a) * r, pose.y() + Mth.sin(a) * r,
                    -Mth.cos(a) * r / 9, -Mth.sin(a) * r / 9, 3, color, 9).drag = 1;
            shake = Math.max(shake, tier >= 4 ? 2.5f : 1);
            return;
        }
        float x = pose.x() + (particles.random.nextFloat() * 2 - 1) * hw * 1.2f;
        float y = pose.y() + (particles.random.nextFloat() * 2 - 1) * hh * 1.1f;
        particles.add(particles.random.nextBoolean() ? ShowFx.Kind.STAR : ShowFx.Kind.SPARK, x, y, 0,
                -0.6f - tier * 0.3f, 1.5f + tier * 0.5f, color, 18).drag = 1;
        if (tier >= 4) {
            // Light running up the beam.
            particles.add(ShowFx.Kind.SPARK, pose.x() + (particles.random.nextFloat() * 2 - 1) * hw * 0.5f,
                    pose.y() - hh, 0, -4 - particles.random.nextFloat() * 4, 2.5f, color, 30).drag = 1;
        }
    }

    // --- Where things are -----------------------------------------------------------------------------------------

    private int dealAt(int i) {
        return dealAt + i * DEAL_GAP;
    }

    /** The pack's middle and half its width and height: {@code x, y, halfWidth, halfHeight}. */
    private float[] packBox() {
        float aspect = packAspect();
        float hh = Math.min(height * 0.32f, 110);
        return new float[]{width / 2f, height * 0.5f, hh * aspect, hh};
    }

    private float packAspect() {
        ProductArt.Art art = ProductArt.get(productCode);
        return art == null ? 1 : art.aspect();
    }

    private float fanWidth() {
        return Math.min(height * 0.36f, 130) / CARD_RATIO;
    }

    /** The middle and turn of card {@code i} in the fan. */
    private float[] fanSlot(int i) {
        int n = slots.length;
        float w = fanWidth();
        float h = w * CARD_RATIO;
        float radius = h * 6;
        float chord = n > 1 ? Math.min(w * 1.08f, (width - 70 - w) / (n - 1f)) : 0;
        float step = 2 * (float) Math.asin(Math.min(1, chord / (2 * radius)));
        float a = (i - (n - 1) / 2f) * step;
        float cy = height * 0.45f;
        return new float[]{width / 2f + radius * Mth.sin(a), cy + radius * (1 - Mth.cos(a)), a * Mth.RAD_TO_DEG};
    }

    /** Rows for all cards face up: {@code width, columns, top}. */
    private float[] rows() {
        int n = slots.length;
        float best = 0;
        int bestColumns = n;
        int bestRows = 1;
        float labels = 22;
        float available = height - 34 - 34;
        for (int rows = 1; rows <= 3; rows++) {
            int columns = Mth.ceil(n / (float) rows);
            float byWidth = (width - 30f) / columns - 10;
            float byHeight = (available / rows - labels - 14) / CARD_RATIO;
            float w = Math.min(Math.min(byWidth, byHeight), 110);
            if (w > best + 2) {
                best = w;
                bestColumns = columns;
                bestRows = rows;
            }
        }
        float h = best * CARD_RATIO;
        float top = 34 + 12 + (available - bestRows * (h + labels + 14)) / 2;
        return new float[]{best, bestColumns, top};
    }

    private float[] rowSlot(int i) {
        float[] rows = rows();
        float w = rows[0];
        int columns = (int) rows[1];
        float h = w * CARD_RATIO;
        int row = i / columns;
        int column = i % columns;
        int inRow = Math.min(columns, slots.length - row * columns);
        float rowWidth = inRow * (w + 10) - 10;
        float x = (width - rowWidth) / 2 + column * (w + 10) + w / 2;
        float y = rows[2] + row * (h + 22 + 14) + h / 2;
        return new float[]{x, y, w};
    }

    private static float easeOutBack(float t) {
        float c = 1.6f;
        float u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    private static float easeOutCubic(float t) {
        float u = 1 - t;
        return 1 - u * u * u;
    }

    private static float easeInOut(float t) {
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    private CardPose pose(int i, float time) {
        Slot s = slots[i];
        float flip = 0;
        float lift = 0;
        if (s.flipAt >= 0 && time >= s.flipAt) {
            float f = Mth.clamp((time - s.flipAt) / FLIP, 0, 1);
            flip = 180 * easeInOut(f);
            lift = Mth.sin(Mth.PI * f) * (0.2f + s.tier() * 0.08f);
        }
        if (s.revealed && time >= s.flipAt + FLIP) {
            flip = 180;
        }
        float x;
        float y;
        float angle;
        float w;
        if (stage == Stage.ROWS) {
            float[] row = rowSlot(i);
            float q = easeInOut(Mth.clamp((time - rowsAt) / LINE_UP, 0, 1));
            float[] fan = fanSlot(i);
            x = Mth.lerp(q, fan[0], row[0]);
            y = Mth.lerp(q, fan[1], row[1]);
            angle = Mth.lerp(q, fan[2], 0);
            w = Mth.lerp(q, fanWidth(), row[2]);
        } else {
            float[] fan = fanSlot(i);
            float[] pack = packBox();
            float startW = Math.min(pack[2] * 1.6f, fanWidth());
            float p = stage == Stage.FAN ? Mth.clamp((time - dealAt(i)) / DEAL, 0, 1) : 0;
            float e = easeOutBack(p);
            // Out of the torn top of the pack, spinning into place.
            float startY = pack[1] - pack[3] * 0.25f - Math.min(1, (time - tearAt - RIP) / 8f) * pack[3] * 0.25f;
            x = Mth.lerp(e, pack[0], fan[0]);
            y = Mth.lerp(e, startY, fan[1]) - Mth.sin(Mth.PI * p) * 30;
            angle = fan[2] + (1 - easeOutCubic(p)) * 360 * (i % 2 == 0 ? 1 : -1);
            w = Mth.lerp(easeOutCubic(p), startW, fanWidth());
        }
        if (i == hovered && !s.revealed && s.flipAt < 0 && stage == Stage.FAN) {
            w *= 1.08f;
            x += Mth.sin(angle * Mth.DEG_TO_RAD) * 8;
            y -= Mth.cos(angle * Mth.DEG_TO_RAD) * 8;
        }
        if (s.announced && !s.revealed && s.tier() >= 4 && stage != Stage.PACK) {
            // A Secret Rare twitches now and then, and all the time while charging.
            boolean twitch = (int) time % 30 < 6 || (s.flipAt >= 0 && time < s.flipAt);
            if (twitch) {
                angle += Mth.sin(time * 2.7f) * 4;
                x += Mth.sin(time * 3.3f) * 1.5f;
            }
        } else if (s.announced && !s.revealed && s.tier() == 3 && (int) time % 40 < 4) {
            angle += Mth.sin(time * 2.7f) * 2;
        }
        w *= 1 + lift;
        y -= lift * 50;
        return new CardPose(x, y, angle, w, flip);
    }

    /** The colors of a rarity's sparks: its own, or the rainbow for the best. */
    private int[] colors(Slot s) {
        return colors(s, false);
    }

    private int[] colors(Slot s, boolean party) {
        if (party || jackpot(s)) {
            return new int[]{0xFFFF5050, 0xFFFFB040, 0xFFFFF060, 0xFF60FF80, 0xFF50C0FF, 0xFFB070FF, 0xFFFFFFFF};
        }
        int c = 0xFF000000 | rarityColor(s.rarity);
        return new int[]{c, c, 0xFFFFFFFF};
    }

    /** Starlight, Ghost, Collector's, Quarter Century and the like: the rarest of the rare. */
    private static boolean jackpot(Slot s) {
        return s.tier() >= 4 && (s.rarity.weight() < 1 || s.rarity == Rarity.PRISMATIC_SECRET);
    }

    private static int rarityColor(Rarity rarity) {
        Integer color = CardItem.color(rarity).getColor();
        return color == null ? 0xFFFFFF : color;
    }

    /** The glow color of a card now; the rarest cycle through the rainbow. */
    private int glowColor(Slot s, float time) {
        return jackpot(s) ? ShowFx.rainbow(time, 40, s.code % 7 / 7f) : 0xFF000000 | rarityColor(s.rarity);
    }

    // --- Drawing --------------------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        float time = ticks + partialTick;
        g.fillGradient(0, 0, width, height, 0xB0050510, 0xE0000004);
        Matrix4f m = g.pose().last().pose();
        g.flush();
        // Slow light behind everything; warmer while the pack is still shut.
        float strength = stage == Stage.PACK || stage == Stage.TEAR ? 1 : 0.55f;
        ShowFx.rays(m, width / 2f, height * 0.5f, Math.max(width, height) * 0.75f, 18, time * 0.006f,
                ShowFx.fade(0xFFFFD890, 0.22f * strength));
        ShowFx.glow(m, width / 2f, height * 0.5f, width * 0.55f, height * 0.6f, 0,
                ShowFx.fade(0xFF4060C0, 0.35f));
        drawScene(g, mouseX, mouseY, partialTick);
    }

    private void drawScene(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float time = ticks + partialTick;
        PoseStack poses = g.pose();
        poses.pushPose();
        if (shake > 0.05f) {
            poses.translate(Mth.sin(time * 9.1f) * shake, Mth.cos(time * 7.3f) * shake, 0);
        }
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        hovered = stage == Stage.PACK || stage == Stage.TEAR ? -1 : cardAt(mouseX, mouseY, time);
        boolean packGoing = torn && time - tearAt - RIP < 30;
        if (stage == Stage.PACK || stage == Stage.TEAR || packGoing) {
            drawPack(g, time);
        }
        if (stage != Stage.PACK && (stage != Stage.TEAR || ticks >= tearAt + RIP)) {
            drawCards(g, time);
        }
        if (stage == Stage.TEAR || packGoing) {
            drawPackFront(g, time);
        }
        g.flush();
        particles.render(poses.last().pose(), partialTick);
        poses.popPose();
        drawBanner(g, time);
        drawHints(g, time);
        if (flash > 0.02f) {
            g.flush();
            ShowFx.glow(poses.last().pose(), width / 2f, height / 2f, width * 1.2f, height * 1.4f, 0,
                    ShowFx.fade(flashColor, flash));
        }
        // The button comes after this in the screen's own rendering, a little above the cards.
        poses.translate(0, 0, 300);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.pose().pushPose();
        super.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
        // Card texts only once all lie in rows, so they don't cover the fan while turning cards over.
        if (hovered >= 0 && stage == Stage.ROWS && slots[hovered].revealed && slots[hovered].flipAt + FLIP <= ticks) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.renderComponentTooltip(font, CardGrid.tooltip(slots[hovered].code, null), mouseX, mouseY);
            g.pose().popPose();
        }
    }

    private void drawHints(GuiGraphics g, float time) {
        Component hint = null;
        if (stage == Stage.PACK && ticks >= FLY_IN) {
            hint = Component.translatable("screen.jadm.pack.tear");
        } else if (stage == Stage.FAN && !allRevealed() && ticks >= dealAt(slots.length - 1) + DEAL) {
            hint = Component.translatable("screen.jadm.pack.flip");
        } else if (done()) {
            hint = Component.translatable("screen.jadm.pack.close");
        }
        if (hint != null) {
            int alpha = (int) (170 + 85 * Mth.sin(time * 0.15f));
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            if (intoBinder && done()) {
                g.drawCenteredString(font, Component.translatable("screen.jadm.pack.binder"), width / 2, height - 52,
                        0xFF55FF55);
            }
            g.drawCenteredString(font, hint, width / 2, height - 40, (alpha << 24) | 0xE0E0E0);
            g.pose().popPose();
        }
    }

    private void drawBanner(GuiGraphics g, float time) {
        if (banner == null) {
            return;
        }
        float age = time - banner.at();
        if (age > 50) {
            banner = null;
            return;
        }
        float in = Mth.clamp(age / 8, 0, 1);
        float scale = Mth.lerp(easeOutBack(in), 5, 2.4f) * Math.min(1, width / 360f);
        float alpha = Math.min(in * 2, Mth.clamp((50 - age) / 10, 0, 1));
        int color = glowColor(banner.slot(), time);
        PoseStack poses = g.pose();
        poses.pushPose();
        poses.translate(width / 2f, height * 0.16f, 260);
        g.flush();
        ShowFx.glow(poses.last().pose(), 0, 0, font.width(banner.text()) * scale * 0.8f, 12 * scale, 0,
                ShowFx.fade(color, alpha * 0.6f));
        poses.scale(scale, scale, 1);
        int a = Math.max(4, Math.round(alpha * 255));
        g.drawCenteredString(font, banner.text(), 0, -4, (a << 24) | (color & 0xFFFFFF));
        poses.popPose();
    }

    /** The card under the mouse, the topmost if they overlap; -1 for none. */
    private int cardAt(double mouseX, double mouseY, float time) {
        for (int i = slots.length - 1; i >= 0; i--) {
            if (stage == Stage.FAN && ticks < dealAt(i) + DEAL) {
                continue;
            }
            CardPose p = pose(i, time);
            float a = -p.angle() * Mth.DEG_TO_RAD;
            float dx = (float) mouseX - p.x();
            float dy = (float) mouseY - p.y();
            float lx = dx * Mth.cos(a) - dy * Mth.sin(a);
            float ly = dx * Mth.sin(a) + dy * Mth.cos(a);
            if (Math.abs(lx) <= p.width() / 2 && Math.abs(ly) <= p.width() * CARD_RATIO / 2) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A matrix that makes what is drawn after it turned about the Y axis look turned in 3D: farther parts smaller.
     * The depth is flattened so turned cards don't cut into each other or the screen's depth range.
     */
    private static Matrix4f perspective(float distance) {
        Matrix4f m = new Matrix4f();
        m.m23(-1 / distance);
        m.m22(0);
        return m;
    }

    private void drawCards(GuiGraphics g, float time) {
        int top = hovered;
        for (int i = 0; i < slots.length; i++) {
            if (i != top) {
                drawCard(g, i, time, i);
            }
        }
        if (top >= 0) {
            drawCard(g, top, time, slots.length);
        }
    }

    private void drawCard(GuiGraphics g, int i, float time, int layer) {
        Slot s = slots[i];
        CardPose p = pose(i, time);
        float scale = p.width() / CARD_W;
        PoseStack poses = g.pose();
        boolean showFx = JadmClientConfig.PACK_OPENING_SHOW.get();
        int color = glowColor(s, time);
        if (s.tier() >= 4 && s.announced && showFx && stage != Stage.ROWS) {
            drawBeam(g, s, p, time, color);
        }
        poses.pushPose();
        poses.translate(p.x(), p.y(), 10 + layer * 8);
        poses.mulPose(Axis.ZP.rotationDegrees(p.angle()));
        poses.scale(scale, scale, 1);
        // Behind the card: its glow, face down to announce it, face up to show it off.
        if (s.tier() > 0 && s.announced) {
            float pulse = 0.75f + 0.25f * Mth.sin(time * 0.25f + i);
            float strength = (s.revealed && stage != Stage.ROWS ? 0.45f : 0.8f) * (0.6f + s.tier() * 0.1f) * pulse;
            if (s.flipAt >= 0 && time < s.flipAt) {
                strength = Math.min(1, strength + (time - s.clickedAt) / Math.max(1, s.flipAt - s.clickedAt));
            }
            if (!showFx && !s.revealed) {
                strength = 0;
            }
            float size = 1 + s.tier() * 0.12f;
            g.flush();
            Matrix4f m = poses.last().pose();
            ShowFx.glow(m, 0, 0, CARD_W * 0.85f * size, CARD_H * 0.75f * size, 0, ShowFx.fade(color, strength));
            if (s.tier() >= 3 && !s.revealed) {
                ShowFx.rays(m, 0, 0, CARD_H * (0.9f + s.tier() * 0.1f), 12, time * 0.03f,
                        ShowFx.fade(color, strength * 0.5f));
            }
        }
        float flip = p.flip();
        if (flip > 0.01f && flip < 179.9f) {
            // Only while turning: the tags on a card lying flat are drawn a little above it, which the perspective
            // would push aside.
            poses.last().pose().mul(perspective(CARD_H * 2.5f));
        }
        boolean face = flip > 90;
        poses.mulPose(Axis.YP.rotationDegrees(face ? flip - 180 : flip));
        poses.translate(-CARD_W / 2f, -CARD_H / 2f, 0);
        if (face) {
            if (flip >= 179.9f) {
                CardGrid.drawCard(g, font, s.code, s.rarity, 0, 0, CARD_W, CARD_H);
            } else {
                CardArt.Texture art = CardArt.get(s.code);
                if (art != null) {
                    g.blit(art.location(), 0, 0, CARD_W, CARD_H, 0, 0, art.width(), art.height(), art.width(),
                            art.height());
                    FoilEffect.drawGui(g, s.code, s.rarity, 0, 0, CARD_W, CARD_H);
                } else {
                    CardGrid.drawCard(g, font, s.code, s.rarity, 0, 0, CARD_W, CARD_H);
                }
            }
            // The reveal lights the card up for a moment.
            float since = time - s.revealedAt;
            if (s.tier() > 0 && since < 12 && showFx) {
                g.flush();
                ShowFx.glow(poses.last().pose(), CARD_W / 2f, CARD_H / 2f, CARD_W * 0.8f, CARD_H * 0.8f, 0,
                        ShowFx.fade(0xFFFFFFFF, (1 - since / 12) * 0.8f));
            }
        } else {
            g.blit(CardGrid.CARD_BACK, 0, 0, CARD_W, CARD_H, 0, 0, 68, 100, 68, 100);
            if (i == hovered && stage == Stage.FAN && s.flipAt < 0) {
                g.renderOutline(-1, -1, CARD_W + 2, CARD_H + 2, 0xFFFFE070);
            }
        }
        poses.popPose();
        if (face && flip >= 179.9f && stage == Stage.ROWS) {
            drawLabels(g, s, p, time);
        }
    }

    /** The rarity under a face-up card, and "Alt Art!" over it. */
    private void drawLabels(GuiGraphics g, Slot s, CardPose p, float time) {
        float q = Mth.clamp((time - rowsAt - LINE_UP) / 6, 0, 1);
        if (q <= 0) {
            return;
        }
        int alpha = Math.max(4, Math.round(q * 255)) << 24;
        int w = Math.round(p.width());
        int x = Math.round(p.x());
        int bottom = Math.round(p.y() + p.width() * CARD_RATIO / 2);
        int top = Math.round(p.y() - p.width() * CARD_RATIO / 2);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (JadmData.isAltArt(s.code)) {
            g.drawCenteredString(font, Component.translatable("screen.jadm.pack.alt_art"), x, top - 10,
                    alpha | 0xFFC040);
        }
        if (s.rarity != Rarity.COMMON) {
            List<FormattedCharSequence> lines = font.split(Component.literal(CardItem.rarityName(s.rarity)), w + 8);
            int color = glowColor(s, time) & 0xFFFFFF;
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                g.drawCenteredString(font, lines.get(l), x, bottom + 4 + l * 10, alpha | color);
            }
        }
        g.pose().popPose();
    }

    /** A beam of light from the top of the screen onto a Secret Rare or better. */
    private void drawBeam(GuiGraphics g, Slot s, CardPose p, float time, int color) {
        float strength;
        if (!s.revealed) {
            strength = 0.55f + 0.2f * Mth.sin(time * 0.3f);
            if (s.flipAt >= 0) {
                strength = 1;
            }
        } else {
            strength = Mth.clamp(1 - (time - s.revealedAt) / 30, 0, 1);
        }
        if (strength <= 0) {
            return;
        }
        float w = p.width();
        float length = p.y() + 40;
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 5);
        Matrix4f m = g.pose().last().pose();
        ShowFx.glow(m, p.x(), p.y() - length / 2 + 20, w * 0.75f, length * 0.65f, 0, ShowFx.fade(color, strength * 0.7f));
        ShowFx.glow(m, p.x(), p.y() - length / 2 + 20, w * 0.22f, length * 0.6f, 0,
                ShowFx.fade(0xFFFFFFFF, strength * 0.6f));
        ShowFx.glow(m, p.x(), p.y() + w * CARD_RATIO * 0.45f, w * 1.6f, w * 0.35f, 0,
                ShowFx.fade(color, strength * 0.6f));
        g.pose().popPose();
    }

    /**
     * Where the pack is and how it is turned, at {@code time}: {@code x, y, yaw, pitch, scale}. It flies in spinning,
     * hovers, and shakes when torn.
     */
    private float[] packPose(float time) {
        float[] box = packBox();
        float x = box[0];
        float y = box[1];
        float yaw;
        float pitch;
        float scale = 1;
        if (stage == Stage.PACK && time < FLY_IN) {
            float p = Mth.clamp(time / FLY_IN, 0, 1);
            float e = easeOutCubic(p);
            y += (1 - e) * height;
            yaw = (1 - e) * 720;
            scale = 0.4f + 0.6f * easeOutBack(p);
            pitch = (1 - e) * 30;
        } else {
            yaw = Mth.sin(time * 0.06f) * 20;
            pitch = Mth.sin(time * 0.045f) * 7;
            y += Mth.sin(time * 0.12f) * 3;
        }
        if (stage != Stage.PACK) {
            float t = time - tearAt;
            float settle = Mth.clamp(t / RIP, 0, 1);
            yaw *= 1 - settle;
            pitch *= 1 - settle;
            if (t < RIP) {
                // Tugging at the top before it gives.
                x += Mth.sin(t * 6) * t * 0.8f;
                scale *= 1 + 0.05f * t / RIP;
            }
        }
        return new float[]{x, y, yaw, pitch, scale};
    }

    private void drawPack(GuiGraphics g, float time) {
        float[] box = packBox();
        float[] pose = packPose(time);
        float hw = box[2];
        float hh = box[3];
        PoseStack poses = g.pose();
        // Its glow and halo of rays.
        if (stage == Stage.PACK) {
            g.flush();
            Matrix4f m = poses.last().pose();
            float in = Mth.clamp(time / FLY_IN, 0, 1);
            ShowFx.rays(m, pose[0], pose[1], hh * 2.8f, 14, -time * 0.02f, ShowFx.fade(0xFFFFE6A0, 0.55f * in));
            ShowFx.rays(m, pose[0], pose[1], hh * 2.2f, 10, time * 0.013f, ShowFx.fade(0xFFFFC060, 0.4f * in));
            ShowFx.glow(m, pose[0], pose[1], hw * 2.4f, hh * 1.9f, 0, ShowFx.fade(0xFFFFE070, 0.8f * in));
        }
        // Torn open, the body drops away under the cards.
        boolean torn = stage != Stage.PACK && time >= tearAt + RIP;
        if (torn) {
            return;
        }
        drawPackBody(g, pose, hw, hh, 0, 1, time);
    }

    /** The torn pack in front of the cards coming out: its top flying off, its body dropping away. */
    private void drawPackFront(GuiGraphics g, float time) {
        float t = time - tearAt - RIP;
        if (t < 0) {
            return;
        }
        float[] box = packBox();
        float[] pose = packPose(tearAt + RIP);
        float hw = box[2];
        float hh = box[3];
        float alpha = Mth.clamp(1 - t / 22, 0, 1);
        // The top, spinning off up to the right.
        float[] top = {pose[0] + t * 7, pose[1] - t * 8 + t * t * 0.35f, t * 25, t * 9, pose[4]};
        drawPackPiece(g, top, hw, hh, 0, TEAR_LINE, false, true, alpha, t * 14);
        // The body, dropping out of sight.
        float[] body = {pose[0], pose[1] + t * t * 1.4f + t * 2, 0, -t * 2, pose[4]};
        drawPackPiece(g, body, hw, hh, TEAR_LINE, 1, true, false, alpha, 0);
    }

    private void drawPackBody(GuiGraphics g, float[] pose, float hw, float hh, float from, float to, float time) {
        drawPackPiece(g, pose, hw, hh, from, to, false, false, 1, 0);
        // A glare sweeping over the foil wrapper now and then.
        float sweep = (time % 50) / 50f;
        if (sweep < 0.4f && stage == Stage.PACK) {
            PoseStack poses = g.pose();
            poses.pushPose();
            poses.translate(pose[0], pose[1], 2);
            poses.last().pose().mul(perspective(hh * 6));
            poses.mulPose(Axis.YP.rotationDegrees(pose[2]));
            poses.mulPose(Axis.XP.rotationDegrees(pose[3]));
            poses.scale(pose[4], pose[4], 1);
            g.flush();
            float x = Mth.lerp(sweep / 0.4f, -hw * 1.2f, hw * 1.2f);
            g.enableScissor(Math.round(pose[0] - hw * pose[4]), Math.round(pose[1] - hh * pose[4]),
                    Math.round(pose[0] + hw * pose[4]), Math.round(pose[1] + hh * pose[4]));
            ShowFx.glow(poses.last().pose(), x, 0, hw * 0.25f, hh * 1.5f, 0.35f, ShowFx.fade(0xFFFFFFFF, 0.5f));
            g.disableScissor();
            poses.popPose();
        }
    }

    /**
     * Part of the pack, between {@code from} and {@code to} of its height, as a slab with a thickness; a torn edge
     * is jagged.
     */
    private void drawPackPiece(GuiGraphics g, float[] pose, float hw, float hh, float from, float to,
                               boolean tornTop, boolean tornBottom, float alpha, float roll) {
        ProductArt.Art art = ProductArt.get(productCode);
        ResourceLocation texture = art == null ? PACK_ICON : art.full();
        PoseStack poses = g.pose();
        poses.pushPose();
        poses.translate(pose[0], pose[1], 4);
        poses.mulPose(Axis.ZP.rotationDegrees(roll));
        poses.last().pose().mul(perspective(hh * 6));
        poses.mulPose(Axis.YP.rotationDegrees(pose[2]));
        poses.mulPose(Axis.XP.rotationDegrees(pose[3]));
        poses.scale(pose[4], pose[4], pose[4]);
        g.flush();
        Matrix4f m = poses.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // Drawn in order, far side first; the depth of the flattened layers would decide otherwise.
        RenderSystem.disableDepthTest();
        float thickness = hw * 0.07f;
        int layers = 8;
        boolean frontNear = Mth.cos(pose[2] * Mth.DEG_TO_RAD) * Mth.cos(pose[3] * Mth.DEG_TO_RAD) >= 0;
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int k = 0; k <= layers; k++) {
            // Far side first, so the near side ends up on top.
            int layer = frontNear ? k : layers - k;
            float z = -thickness + 2 * thickness * layer / layers;
            int shade = layer == layers ? 0xFFFFFF : layer == 0 ? 0xB8B8B8 : 0x707070;
            int color = (Math.round(alpha * 255) << 24) | shade;
            stripes(b, m, hw, hh, z, from, to, tornTop, tornBottom, color, layer == 0);
        }
        MeshData mesh = b.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        poses.popPose();
    }

    /** The picture between heights {@code from} and {@code to} as columns, so a torn edge can zigzag. */
    private static void stripes(BufferBuilder b, Matrix4f m, float hw, float hh, float z, float from, float to,
                                boolean tornTop, boolean tornBottom, int color, boolean mirrored) {
        int columns = 12;
        for (int c = 0; c < columns; c++) {
            float u0 = c / (float) columns;
            float u1 = (c + 1) / (float) columns;
            float t0 = tornTop ? from + jag(c) : from;
            float t1 = tornTop ? from + jag(c + 1) : from;
            float b0 = tornBottom ? to + jag(c) : to;
            float b1 = tornBottom ? to + jag(c + 1) : to;
            float x0 = -hw + 2 * hw * u0;
            float x1 = -hw + 2 * hw * u1;
            float tu0 = mirrored ? 1 - u0 : u0;
            float tu1 = mirrored ? 1 - u1 : u1;
            b.addVertex(m, x0, -hh + 2 * hh * t0, z).setUv(tu0, t0).setColor(color);
            b.addVertex(m, x0, -hh + 2 * hh * b0, z).setUv(tu0, b0).setColor(color);
            b.addVertex(m, x1, -hh + 2 * hh * b1, z).setUv(tu1, b1).setColor(color);
            b.addVertex(m, x1, -hh + 2 * hh * t1, z).setUv(tu1, t1).setColor(color);
        }
    }

    private static float jag(int column) {
        return column % 2 == 0 ? -0.025f : 0.025f;
    }

    // --- Input ----------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (done()) {
                onClose();
            } else {
                skip();
            }
            return true;
        }
        switch (stage) {
            case PACK -> {
                if (ticks >= FLY_IN / 2) {
                    tear();
                }
            }
            case TEAR -> {
            }
            case FAN -> {
                int i = cardAt(mouseX, mouseY, ticks);
                if (i >= 0 && !slots[i].revealed && slots[i].flipAt < 0) {
                    flip(i, 0);
                } else if (allRevealed() && ticks >= lastRevealAt + FLIP) {
                    stage = Stage.ROWS;
                    rowsAt = ticks;
                }
            }
            case ROWS -> {
                if (done()) {
                    onClose();
                }
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (done()) {
                onClose();
            } else {
                skip();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
