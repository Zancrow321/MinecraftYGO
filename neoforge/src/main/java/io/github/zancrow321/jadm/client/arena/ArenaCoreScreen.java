package io.github.zancrow321.jadm.client.arena;

import io.github.zancrow321.jadm.arena.ArenaCoreBlockEntity;
import io.github.zancrow321.jadm.arena.BuiltArena;
import io.github.zancrow321.jadm.network.ArenaCoreSettingsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings of an Arena Core, and what it found when it measured the arena around it: whether the arena can be
 * used (and what is missing), how big the field comes out, the room over it and how far the podiums go up. While it
 * is open the field's outline shows over the floor.
 */
public final class ArenaCoreScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 214;
    private static final int GOLD = 0xFFFFD040;
    private static final int TEXT = 0xFFE0E8F0;
    private static final int DIM = 0xFF8FA4B8;

    private final BlockPos pos;
    private int size;
    private int lift;
    private boolean outline;
    private int left;
    private int top;
    private long lastPreview;

    public ArenaCoreScreen(BlockPos pos) {
        super(Component.translatable("block.jadm.duel_dome_core"));
        this.pos = pos;
        ArenaCoreBlockEntity core = core();
        if (core != null) {
            size = core.sizeSetting();
            lift = core.liftSetting();
            outline = core.outline();
        }
    }

    private ArenaCoreBlockEntity core() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof ArenaCoreBlockEntity core ? core : null;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int y = top + HEIGHT - 50;
        int w = (WIDTH - 24) / 3;
        addRenderableWidget(Button.builder(sizeLabel(), b -> {
            int i = 0;
            while (i < BuiltArena.SIZES.length && BuiltArena.SIZES[i] != size) {
                i++;
            }
            size = BuiltArena.SIZES[(i + (hasShiftDown() ? BuiltArena.SIZES.length - 1 : 1))
                    % BuiltArena.SIZES.length];
            b.setMessage(sizeLabel());
            send(false);
        }).bounds(left + 8, y, w, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("screen.jadm.arena_core.size.hint"))).build());
        addRenderableWidget(Button.builder(liftLabel(), b -> {
            lift = (lift + (hasShiftDown() ? BuiltArena.MAX_LIFT : 1)) % (BuiltArena.MAX_LIFT + 1);
            b.setMessage(liftLabel());
            send(false);
        }).bounds(left + 12 + w, y, w, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("screen.jadm.arena_core.lift.hint"))).build());
        addRenderableWidget(Button.builder(lookLabel(), b -> {
            outline = !outline;
            b.setMessage(lookLabel());
            send(false);
        }).bounds(left + 16 + 2 * w, y, w, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("screen.jadm.arena_core.look.hint"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.jadm.arena_core.measure"),
                b -> send(true)).bounds(left + 8, y + 24, (WIDTH - 20) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + WIDTH / 2 + 2, y + 24, (WIDTH - 20) / 2, 20).build());
    }

    private Component sizeLabel() {
        return Component.translatable("screen.jadm.arena_core.size", size == 0
                ? Component.translatable("screen.jadm.arena_core.size.auto") : Component.literal(size + " %"));
    }

    private Component liftLabel() {
        return Component.translatable("screen.jadm.arena_core.lift", lift);
    }

    private Component lookLabel() {
        return Component.translatable(outline ? "screen.jadm.arena_core.look.outline"
                : "screen.jadm.arena_core.look.mat");
    }

    private void send(boolean preview) {
        PacketDistributor.sendToServer(new ArenaCoreSettingsPayload(pos, size, lift, outline, preview));
    }

    @Override
    public void tick() {
        ArenaCoreBlockEntity core = core();
        if (core == null || minecraft == null || minecraft.player == null
                || minecraft.player.distanceToSqr(pos.getCenter()) > 64) {
            onClose();
            return;
        }
        // Keep the outline up for as long as the screen is open.
        long now = core.getLevel().getGameTime();
        if (core.previewUntil() - now < 60 && now - lastPreview > 40) {
            lastPreview = now;
            send(true);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0xE0101826);
        g.fill(left, top, left + WIDTH, top + 1, 0xFF38E0FF);
        g.fill(left + 8, top + 22, left + WIDTH - 8, top + 23, 0x8038E0FF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + 8, top + 8, GOLD);
        ArenaCoreBlockEntity core = core();
        BuiltArena.Survey survey = core == null ? null : core.survey();
        int y = top + 30;
        if (survey == null) {
            g.drawString(font, Component.translatable("screen.jadm.arena_core.measuring"), left + 8, y, DIM);
            return;
        }
        Component status = Component.translatable(survey.ok() ? "screen.jadm.arena_core.ready"
                : "screen.jadm.arena_core.not_ready");
        for (FormattedCharSequence part : font.split(status, WIDTH - 16)) {
            g.drawString(font, part, left + 8, y, survey.ok() ? 0xFF60FF90 : 0xFFFF6060);
            y += 11;
        }
        y += 3;
        List<Component> lines = new ArrayList<>();
        for (String problem : survey.problems()) {
            lines.add(Component.literal("✖ ").append(Component.translatable(
                    "screen.jadm.arena_core.problem." + problem)).withColor(0xFFFF9090));
        }
        int plus = survey.plus().size();
        int minus = survey.minus().size();
        lines.add(Component.translatable("screen.jadm.arena_core.podiums", plus, minus,
                Component.translatable(plus == 2 && minus == 2 ? "screen.jadm.arena_core.podiums.tag"
                        : "screen.jadm.arena_core.podiums.single")).withColor(TEXT));
        if (survey.ok()) {
            lines.add(Component.translatable("screen.jadm.arena_core.field", Math.round(survey.size() * 100),
                    Math.round(Math.min(survey.fits(), 3) * 100)).withColor(TEXT));
            lines.add((survey.ceiling() > 0 ? Component.translatable("screen.jadm.arena_core.ceiling",
                    (int) survey.ceiling()) : Component.translatable("screen.jadm.arena_core.sky")).withColor(TEXT));
            lines.add((survey.lift() > 0 ? Component.translatable("screen.jadm.arena_core.lifts", survey.lift())
                    : Component.translatable("screen.jadm.arena_core.no_lift")).withColor(TEXT));
        }
        for (String note : survey.notes()) {
            lines.add(Component.literal("• ").append(Component.translatable(
                    "screen.jadm.arena_core.note." + note)).withColor(DIM));
        }
        if (survey.problems().contains("no_podiums") || survey.problems().contains("one_side")) {
            lines.add(Component.translatable("screen.jadm.arena_core.how").withColor(DIM));
        }
        int bottom = top + HEIGHT - 56;
        for (Component line : lines) {
            for (FormattedCharSequence part : font.split(line, WIDTH - 16)) {
                if (y + 9 > bottom) {
                    return;
                }
                g.drawString(font, part, left + 8, y, TEXT);
                y += 11;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
