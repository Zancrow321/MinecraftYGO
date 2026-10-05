package io.github.zancrow321.minecraftygo.client.cosmetics;

import io.github.zancrow321.minecraftygo.cosmetics.Cosmetics;
import io.github.zancrow321.minecraftygo.item.YgoComponents;
import io.github.zancrow321.minecraftygo.item.YgoItems;
import io.github.zancrow321.minecraftygo.network.CosmeticsPayload;
import io.github.zancrow321.minecraftygo.network.SelectCosmeticPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Pick a disk skin and a card sleeve. Locked ones show what unlocks them. The server applies a pick and sends the
 * screen back refreshed.
 */
public final class CosmeticsScreen extends Screen {
    private static final int GOLD = 0xFFFFD040;
    private static final int DIM = 0xFFB0C4D8;
    private static final int PANEL = 0xC0081828;
    private static final int TILE = 44;
    private static final int GAP = 8;
    private static final int SLEEVE_W = 30;
    private static final int SLEEVE_H = 44;

    private final CosmeticsPayload state;

    private record Tile(boolean skin, Cosmetics.Cosmetic cosmetic, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Tile> tiles = new ArrayList<>();

    public CosmeticsScreen(CosmeticsPayload state) {
        super(Component.literal("Cosmetics"));
        this.state = state;
    }

    @Override
    protected void init() {
        tiles.clear();
        int top = height / 2 - 70;
        layout(true, Cosmetics.SKINS, top + 26, TILE, TILE);
        layout(false, Cosmetics.SLEEVES, top + 104, SLEEVE_W + 14, SLEEVE_H + 4);
    }

    private void layout(boolean skin, List<Cosmetics.Cosmetic> list, int y, int w, int h) {
        int total = list.size() * (w + GAP) - GAP;
        int x = width / 2 - total / 2;
        for (Cosmetics.Cosmetic c : list) {
            tiles.add(new Tile(skin, c, x, y, w, h));
            x += w + GAP;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int top = height / 2 - 70;
        g.fill(width / 2 - 190, top - 26, width / 2 + 190, top + 176, PANEL);
        g.drawCenteredString(font, title, width / 2, top - 18, GOLD);
        var data = state.data();
        g.drawCenteredString(font, "Duels won " + data.wins() + "  ·  NPCs beaten " + data.npcWins()
                + "  ·  Packs opened " + data.packs(), width / 2, top - 6, DIM);
        g.drawString(font, "Disk skin" + (state.hasDisk() ? "" : "  (wear or hold your Duel Disk to change it)"),
                width / 2 - 182, top + 14, 0xFFFFFFFF);
        g.drawString(font, "Card sleeves", width / 2 - 182, top + 92, 0xFFFFFFFF);

        Tile hovered = null;
        for (Tile t : tiles) {
            boolean unlocked = t.cosmetic().unlocked(data);
            boolean selected = t.skin() ? t.cosmetic().id().equals(state.skin())
                    : t.cosmetic().id().equals(Cosmetics.sleeve(data.sleeve()).id());
            boolean over = t.contains(mouseX, mouseY);
            if (over) {
                hovered = t;
            }
            int border = selected ? GOLD : over && unlocked ? 0xFFFFFFFF : 0xFF34485C;
            g.fill(t.x() - 1, t.y() - 1, t.x() + t.w() + 1, t.y() + t.h() + 1, border);
            g.fill(t.x(), t.y(), t.x() + t.w(), t.y() + t.h(), 0xFF0C1C2C);
            if (t.skin()) {
                ItemStack disk = new ItemStack(YgoItems.DUEL_DISK.get());
                disk.set(YgoComponents.DISK_SKIN.get(), t.cosmetic().id());
                g.pose().pushPose();
                g.pose().translate(t.x() + 4, t.y() + 4, 0);
                g.pose().scale(2.25f, 2.25f, 1);
                g.renderFakeItem(disk, 0, 0);
                g.pose().popPose();
            } else {
                g.blit(Cosmetics.sleeveTexture(t.cosmetic().id()), t.x() + 7, t.y() + 2, SLEEVE_W, SLEEVE_H, 0, 0,
                        68, 100, 68, 100);
            }
            if (!unlocked) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(t.x(), t.y(), t.x() + t.w(), t.y() + t.h(), 0xB0000000);
                g.drawCenteredString(font, "Locked", t.x() + t.w() / 2, t.y() + t.h() / 2 - 4, DIM);
                g.pose().popPose();
            }
        }
        if (hovered != null) {
            Cosmetics.Cosmetic c = hovered.cosmetic();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(c.name()));
            if (!c.unlocked(data)) {
                lines.add(Component.literal(c.requirement() + " (" + Math.min(data.stat(c.stat()), c.needed()) + "/"
                        + c.needed() + ")").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Tile t : tiles) {
            if (t.contains(mouseX, mouseY) && t.cosmetic().unlocked(state.data()) && (!t.skin() || state.hasDisk())) {
                PacketDistributor.sendToServer(new SelectCosmeticPayload(t.skin(), t.cosmetic().id()));
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.2f);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
