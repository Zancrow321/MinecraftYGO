package io.github.zancrow321.jadm.client.field;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.JadmClientConfig;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.engine.data.CardPool;
import io.github.zancrow321.jadm.engine.duel.DuelView;
import io.github.zancrow321.jadm.engine.duel.FieldEvent;
import io.github.zancrow321.jadm.engine.protocol.CardState;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * The ace shot: when a deck's boss monster is summoned (marked in the deck box), or in a deck without one a big
 * monster (high level, from the Extra Deck or with high ATK, as set in the client config), the camera leaves its place, circles the monster's model or hologram while it rises, and its name
 * slams in big with its stats between black bars, like a summon in Master Duel. Then the camera flies back and the
 * duel goes on. Only touched on the client thread.
 */
public final class AceCamera {
    /** How long a shot lasts at least, in ticks: flying in, circling, flying back. */
    public static final int TICKS = 46;
    /** How long the camera takes to fly in and to fly back. */
    private static final int BLEND_TICKS = 10;
    /** When the name slams in, from the shot's start (the monster is up by then). */
    private static final int NAME_AT = 13;
    /** How far the camera circles around the monster, from its front left to its front right. */
    private static final double FROM_ANGLE = Math.toRadians(-75);
    private static final double TO_ANGLE = Math.toRadians(45);
    private static final int GOLD = 0xFFD040;
    private static final int EXTRA = TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK;

    private record Shot(int code, Loc loc, String who, int attack, int defense, long start, int ticks) {
    }

    private static final List<Shot> shots = new ArrayList<>();

    private AceCamera() {
    }

    /** Forgets every shot, for a new duel or when the field goes. */
    static void reset() {
        shots.clear();
    }

    /**
     * Whether {@code code} summoned gets a shot: the setting is on and the card is the boss monster of a deck on the
     * summoning team or, if that team marked no boss, big enough by the client settings.
     *
     * @param bosses the boss monsters of the summoning team's decks (original passcodes), empty for none
     */
    public static boolean ace(int code, List<Integer> bosses) {
        if (code == 0 || !JadmClientConfig.ACE_CAMERA.get()) {
            return false;
        }
        CardInfo info = JadmData.cards().card(code);
        if (info == null || !info.is(TYPE_MONSTER) || info.is(TYPE_TOKEN)) {
            return false;
        }
        if (!bosses.isEmpty()) {
            return bosses.contains(JadmData.original(code));
        }
        if (JadmClientConfig.ACE_EXTRA_DECK.get() && (info.data().type() & EXTRA) != 0) {
            return true;
        }
        int level = info.data().level() & 0xFF;
        // Ranks and Link ratings are no Levels; Extra Deck monsters only count by their ATK unless the setting above.
        if (!info.is(TYPE_XYZ) && !info.is(TYPE_LINK) && level >= JadmClientConfig.ACE_MIN_LEVEL.get()) {
            return true;
        }
        int minAttack = JadmClientConfig.ACE_MIN_ATTACK.get();
        return minAttack > 0 && info.data().attack() >= minAttack;
    }

    /**
     * Queues a shot of the summon {@code event} starting with it at field tick {@code start}.
     *
     * @return how long the queue waits before the next event
     */
    static int queue(FieldEvent event, DuelView view, long start) {
        CardState live = FieldRenderer.cardAt(view.board(), event.from());
        CardInfo info = JadmData.cards().card(event.code());
        boolean known = live != null && live.code() == event.code();
        int attack = known ? live.attack() : info.data().attack();
        int defense = known ? live.defense() : info.data().defense();
        String who = view.you() == event.player() && !ClientField.watching() ? "You summon"
                : view.names().get(event.player()) + " summons";
        // A summon effect that plays longer than a shot keeps the camera on the monster until it is over.
        int ticks = Math.max(TICKS, FieldAnimation.duration(event) + 2 * BLEND_TICKS);
        shots.add(new Shot(event.code(), event.from(), who, attack, defense, start, ticks));
        return Math.max(ticks, FieldAnimation.spacing(event));
    }

    private static float now() {
        return ClientField.tick() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static Shot current() {
        if (!ClientField.active()) {
            return null;
        }
        float now = now();
        for (Shot shot : shots) {
            if (now >= shot.start() && now < shot.start() + shot.ticks()) {
                return shot;
            }
        }
        return null;
    }

    /** Whether a shot is playing now: the camera is away and nothing can be clicked. */
    public static boolean playing() {
        return current() != null;
    }

    /** Drops finished shots and plays their sounds; once per client tick. */
    static void tick() {
        long now = ClientField.tick();
        shots.removeIf(s -> now >= s.start() + s.ticks());
        for (Shot shot : shots) {
            long t = now - shot.start();
            if (t == 0) {
                play(SoundEvents.TRIDENT_RIPTIDE_3.value(), 0.85f, 0.8f);
            } else if (t == NAME_AT) {
                play(SoundEvents.TRIDENT_THUNDER.value(), 1.6f, 0.35f);
                play(SoundEvents.TOTEM_USE, 1.4f, 0.25f);
            }
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** How far the camera is with the monster: 0 in its own place, 1 fully on the shot. */
    private static double weight(Shot shot, float t) {
        double in = smooth(t / BLEND_TICKS);
        double out = smooth((shot.ticks() - t) / BLEND_TICKS);
        return Math.min(in, out);
    }

    private static double smooth(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** How tall the monster stands on the field, in blocks at the field's full size. */
    private static double height(int code) {
        CardPool.Model model = JadmData.model(code);
        double tall = model == null ? Holograms.SIZE + 0.3 : model.height() * FieldRenderer.modelScale(model);
        return Mth.clamp(tall, 1.2, 3.4) * ClientField.size();
    }

    /**
     * Where the camera is and what it looks at during a shot, blended with where it would be otherwise.
     *
     * @param position the camera's own place
     * @param target   what it looks at from there
     * @return {position, target}, the same ones when no shot is playing
     */
    public static Vec3[] camera(Vec3 position, Vec3 target) {
        Shot shot = current();
        if (shot == null) {
            return new Vec3[]{position, target};
        }
        float t = now() - shot.start();
        double w = weight(shot, t);
        FieldLayout.Slot s = FieldLayout.slot(shot.loc());
        Vec3 ground = ClientField.toWorld(s.x(), s.z(), 0);
        double tall = height(shot.code());
        // The monster faces the other duelist; the camera circles in front of it.
        Vec3 front = ClientField.direction().scale(shot.loc().controller() == 0 ? 1 : -1);
        double angle = Mth.lerp(smooth(t / shot.ticks()), FROM_ANGLE, TO_ANGLE);
        Vec3 around = new Vec3(front.x * Math.cos(angle) - front.z * Math.sin(angle), 0,
                front.x * Math.sin(angle) + front.z * Math.cos(angle));
        double radius = 1.6 * ClientField.size() + tall * 0.9;
        // Low at first, looking up at the monster, then rising to look it in the eye.
        double up = Mth.lerp(smooth(t / shot.ticks()), tall * 0.2, tall * 0.75) + 0.3;
        if (ClientField.ceiling() > 0) {
            up = Math.min(up, ClientField.ceiling() - 0.4);
        }
        Vec3 eye = ground.add(around.scale(radius)).add(0, up, 0);
        // Aimed a little above its middle, so it stands clear of its name at the bottom of the screen.
        Vec3 look = ground.add(0, tall * 0.45, 0);
        return new Vec3[]{position.lerp(eye, w), target.lerp(look, w)};
    }

    // ---- Drawing ----

    /** Black bars with who summons and the stats, and the monster's name big just above the lower bar. */
    public static void render(GuiGraphics g, Font font, int w, int h) {
        Shot shot = current();
        if (shot == null) {
            return;
        }
        float t = now() - shot.start();
        float end = shot.ticks() - t;
        double bars = Math.min(smooth(t / 6), smooth(end / 6));
        int bar = (int) (h * 0.12 * bars);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (t < NAME_AT) {
            return;
        }
        CardInfo info = JadmData.cards().card(shot.code());
        String name = JadmData.text().cardName(shot.code());
        float p = (t - NAME_AT) / 5;
        int alpha = (int) (255 * Mth.clamp(end / 6, 0, 1));
        if (alpha <= 8) {
            return;
        }
        // The name slams in from big and settles, sliding a little to the left the whole time.
        float fit = Math.min(3f, (w - 30) / (float) Math.max(1, font.width(name)));
        float scale = fit * (1 + 0.6f * (float) Math.pow(Math.max(0, 1 - p), 2));
        int drift = (int) ((t - NAME_AT) * -0.6f);
        int bandHalf = (int) (fit * 6) + 3;
        int cy = h - bar - bandHalf - 4;
        int type = Holograms.typeColor(info);
        int stripe = type == -1 ? Holograms.attributeColor(info == null ? 0 : info.data().attribute()) : type;
        // A band in the colour of the card's frame behind the name, swept in from the left.
        int bandWidth = (int) (w * Math.min(1, p * 1.5f));
        g.fill(0, cy - bandHalf, bandWidth, cy + bandHalf, (alpha * 0x90 / 255) << 24 | 0x101828);
        g.fill(0, cy - bandHalf, bandWidth, cy - bandHalf + 2, alpha << 24 | stripe);
        g.fill(0, cy + bandHalf - 2, bandWidth, cy + bandHalf, alpha << 24 | stripe);
        big(g, font, name, w / 2 + drift, cy, scale, alpha << 24 | GOLD);
        if (bar > 12) {
            g.drawString(font, shot.who(), 12, bar / 2 - 4, alpha << 24 | 0xFFFFFF);
            if (info != null) {
                big(g, font, stats(info, shot), w / 2, h - bar / 2, 1.3f, alpha << 24 | 0xFFFFFF);
            }
        }
    }

    /** "★★★★★★★★  ATK 3000 / DEF 2500", with Rank or Link rating where they apply. */
    private static String stats(CardInfo info, Shot shot) {
        int level = info.data().level() & 0xFF;
        if (info.is(TYPE_LINK)) {
            return "LINK-" + level + "   ATK " + shot.attack();
        }
        String stars = info.is(TYPE_XYZ) ? "Rank " + level : "★".repeat(Math.min(level, 12));
        return stars + "   ATK " + shot.attack() + " / DEF " + shot.defense();
    }

    private static void big(GuiGraphics g, Font font, String text, int cx, int cy, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, -4, color);
        g.pose().popPose();
    }
}
