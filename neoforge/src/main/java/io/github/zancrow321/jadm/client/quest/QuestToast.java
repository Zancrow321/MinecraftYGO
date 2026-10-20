package io.github.zancrow321.jadm.client.quest;

import io.github.zancrow321.jadm.quest.QuestView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The pop-up in the corner when a quest moves on: its title and progress, or that it is done. */
final class QuestToast implements Toast {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final long SHOW_MS = 5000;
    private static final ItemStack ICON = new ItemStack(Items.WRITABLE_BOOK);
    private static final ItemStack DONE_ICON = new ItemStack(Items.NETHER_STAR);

    private QuestView.Entry entry;
    private boolean changed;
    private long since;

    private QuestToast(QuestView.Entry entry) {
        this.entry = entry;
    }

    /** Pops the quest up, or updates its pop-up if that is still showing. */
    static void show(Minecraft mc, QuestView.Entry entry) {
        ToastComponent toasts = mc.getToasts();
        QuestToast showing = toasts.getToast(QuestToast.class, entry.id);
        if (showing != null) {
            showing.entry = entry;
            showing.changed = true;
        } else {
            toasts.addToast(new QuestToast(entry));
        }
    }

    @Override
    public Object getToken() {
        return entry.id;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        if (changed) {
            since = timeSinceLastVisible;
            changed = false;
        }
        Font font = toasts.getMinecraft().font;
        g.blitSprite(BACKGROUND, 0, 0, width(), height());
        boolean done = entry.done();
        g.renderFakeItem(done ? DONE_ICON : ICON, 8, 8);
        Component head = Component.translatable(done ? "toast.jadm.quest.done"
                : entry.weekly ? "toast.jadm.quest.weekly" : "toast.jadm.quest.daily");
        g.drawString(font, head, 30, 7, done ? 0xFF60E070 : 0xFFFFD040, false);
        if (!done) {
            String count = shortNumber(entry.progress) + "/" + shortNumber(entry.goal);
            g.drawString(font, count, width() - 8 - font.width(count), 7, 0xFFB0B0B0, false);
        }
        g.drawString(font, QuestText.fit(font, QuestText.title(entry).getString(), width() - 38), 30, 17,
                0xFFFFFFFF, false);
        int barX = 30;
        int barW = width() - 38;
        g.fill(barX, 27, barX + barW, 28, 0xFF303040);
        g.fill(barX, 27, barX + (int) (barW * Mth.clamp(entry.progress / (float) Math.max(1, entry.goal), 0, 1)),
                28, done ? 0xFF60E070 : 0xFF5090E0);
        return timeSinceLastVisible - since < SHOW_MS ? Visibility.SHOW : Visibility.HIDE;
    }

    /** 12500 as "12k", so damage counts fit next to the heading. */
    private static String shortNumber(int n) {
        return n >= 10_000 ? n / 1000 + "k" : String.valueOf(n);
    }
}
