package io.github.zancrow321.jadm.client.collection;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.JadmClientConfig;
import io.github.zancrow321.jadm.client.field.ClientField;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.item.CardItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A big picture of the card under the mouse, with its name, rarity, stats and text, in the free space left of a
 * screen's window: the inventory, chests and every other container, the binder and the deck box. It looks like the
 * duel's card panel. Drawn only where there is room for it; the tooltip by the mouse then keeps to the card's name.
 * A card held in the hand gets one too, in the top left corner while playing.
 */
public final class CardPreview {
    private static final int MIN_WIDTH = 72;
    private static final int MAX_WIDTH = 150;
    /** Space between the preview and the screen's edge, and between the preview and the window. */
    private static final int GAP = 6;
    private static final int GOLD = 0xFFFFD040;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFB0C4D8;
    private static final int PANEL = 0xD0081828;
    private static final int PANEL_EDGE = 0xFF3A6A9A;
    /** The recipe book's width, how far left of the middle it sits while open, and how far its tabs stick out. */
    private static final int RECIPE_BOOK_WIDTH = 147;
    private static final int RECIPE_BOOK_SHIFT = 86;
    private static final int RECIPE_BOOK_TABS = 32;
    private static final int HUD_MAX_WIDTH = 120;
    private static final int HOTBAR_WIDTH = 182;
    /** Room kept free at the bottom of the screen in play: the chat's input line and recent messages. */
    private static final int HUD_BOTTOM = 70;

    private CardPreview() {
    }

    /** Whether a preview fits in the {@code room} pixels left of a window. */
    public static boolean fits(int room) {
        return JadmClientConfig.CARD_PREVIEW.get() && room - 2 * GAP >= MIN_WIDTH;
    }

    /**
     * Before a container screen draws: when a card under the mouse will get its preview, its tooltip is cut down to
     * its name and rarity.
     */
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        ItemStack card = hovered(event.getScreen());
        CardItem.previewed = card != null && fits(room(event.getScreen())) ? card : ItemStack.EMPTY;
    }

    /** After a container screen has drawn: the preview of the card under the mouse, if it fits. */
    public static void afterRender(ScreenEvent.Render.Post event) {
        CardItem.previewed = ItemStack.EMPTY;
        ItemStack card = hovered(event.getScreen());
        if (card != null) {
            render(event.getGuiGraphics(), event.getScreen().getMinecraft().font, CardItem.code(card),
                    CardItem.rarity(card), room(event.getScreen()), event.getScreen().height);
        }
    }

    /** The card in the slot under the mouse, as long as nothing is held on the cursor (else there's no tooltip). */
    private static ItemStack hovered(net.minecraft.client.gui.screens.Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)
                || !container.getMenu().getCarried().isEmpty()) {
            return null;
        }
        Slot slot = container.getSlotUnderMouse();
        ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
        return stack.getItem() instanceof CardItem && CardItem.code(stack) != 0 ? stack : null;
    }

    /** The free space left of a container's window, or left of its recipe book while that is open. */
    private static int room(net.minecraft.client.gui.screens.Screen screen) {
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
        if (screen instanceof RecipeUpdateListener book && book.getRecipeBookComponent().isVisible()) {
            // The same sum the recipe book places itself by; on a narrow screen it covers the window instead.
            boolean narrow = screen.width < 379;
            return Math.min(container.getGuiLeft(),
                    (screen.width - RECIPE_BOOK_WIDTH) / 2 - (narrow ? 0 : RECIPE_BOOK_SHIFT) - RECIPE_BOOK_TABS);
        }
        return container.getGuiLeft();
    }

    /**
     * Draws a card's preview in the {@code room} pixels left of a window, beside it and centred on the screen's
     * height: its picture on top, as big as the width allows, then its text, smaller where it would not fit.
     *
     * @return whether it fitted and was drawn
     */
    public static boolean render(GuiGraphics g, Font font, int code, Rarity rarity, int room, int screenHeight) {
        if (!fits(room)) {
            return false;
        }
        int width = Math.min(MAX_WIDTH, room - 2 * GAP);
        return draw(g, font, code, rarity, room - GAP - width, width, GAP, screenHeight - GAP, true);
    }

    /**
     * While playing with no screen open: the card held in the main hand (or else the off hand) in the top left
     * corner, clear of the hotbar and the chat. Not during a duel, which has its own card panel, nor with the HUD
     * hidden.
     */
    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || ClientField.active()
                || !JadmClientConfig.CARD_PREVIEW.get()) {
            return;
        }
        ItemStack card = mc.player.getMainHandItem();
        if (!(card.getItem() instanceof CardItem)) {
            card = mc.player.getOffhandItem();
        }
        if (!(card.getItem() instanceof CardItem) || CardItem.code(card) == 0) {
            return;
        }
        // Left of the hotbar, and smaller than beside a window so it hides less of the world.
        int width = Math.min(HUD_MAX_WIDTH, (g.guiWidth() - HOTBAR_WIDTH) / 2 - 2 * GAP);
        if (width >= MIN_WIDTH) {
            draw(g, mc.font, CardItem.code(card), CardItem.rarity(card), GAP, width, GAP,
                    g.guiHeight() - HUD_BOTTOM, false);
        }
    }

    /**
     * Draws a card's preview {@code width} wide at {@code x}, between {@code top} and {@code bottom}: centred
     * between them, or at the top.
     */
    private static boolean draw(GuiGraphics g, Font font, int code, Rarity rarity, int x, int width, int top,
                                int bottom, boolean centred) {
        CardInfo info = JadmData.cards().card(code);
        if (info == null) {
            return false;
        }
        int maxHeight = bottom - top;
        if (maxHeight < 60) {
            return false;
        }
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(info.name(), GOLD));
        if (rarity != Rarity.COMMON) {
            Integer color = CardItem.color(rarity).getColor();
            lines.add(new Line(CardItem.rarityName(rarity), 0xFF000000 | (color == null ? 0xFFFFFF : color)));
        }
        String type = CardItem.typeLine(info);
        int stats = type.indexOf(" · ATK");
        if (stats >= 0) {
            lines.add(new Line(type.substring(0, stats), DIM));
            lines.add(new Line(type.substring(stats + 3), DIM));
        } else {
            lines.add(new Line(type, DIM));
        }
        lines.add(new Line(info.description().replace("\r", ""), TEXT));

        // The biggest text size at which everything fits beside the biggest picture; long text makes the picture
        // smaller (down to two fifths of the height) before it is cut off at the bottom at the smallest size.
        int fullArt = Math.round((width - 6) * 391f / 268f);
        int artHeight = 0;
        int textRoom = 0;
        float scale = 0;
        for (int fifths = 3; fifths >= 2 && scale == 0; fifths--) {
            artHeight = Math.min(fullArt, maxHeight * fifths / 5);
            textRoom = maxHeight - artHeight - 9;
            for (float s : new float[] {1f, 0.75f, 0.5f}) {
                if (height(font, lines, (int) ((width - 6) / s)) * s <= textRoom) {
                    scale = s;
                    break;
                }
            }
        }
        if (scale == 0) {
            scale = 0.5f;
        }
        int artWidth = Math.round(artHeight * 268f / 391f);
        int wrap = (int) ((width - 6) / scale);
        int textHeight = Math.min(textRoom, (int) Math.ceil(height(font, lines, wrap) * scale));
        int height = 3 + artHeight + 3 + textHeight + 3;
        int y = centred ? Math.max(top, top + (maxHeight - height) / 2) : top;

        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL_EDGE);
        g.fill(x, y, x + width, y + height, PANEL);
        CardGrid.drawCard(g, font, code, rarity, x + width / 2 - artWidth / 2, y + 3, artWidth, artHeight);
        int ty = y + 3 + artHeight + 3;
        g.pose().pushPose();
        g.pose().translate(x + 3, ty, 0);
        g.pose().scale(scale, scale, 1);
        int ly = 0;
        int max = (int) (textHeight / scale);
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (i == lines.size() - 1) {
                ly += 2;
            }
            for (FormattedCharSequence part : wrap(font, line.text(), wrap)) {
                if (ly + 9 > max) {
                    break;
                }
                g.drawString(font, part, 0, ly, line.color());
                ly += 9;
            }
        }
        g.pose().popPose();
        return true;
    }

    private record Line(String text, int color) {
    }

    /** Text wrapped to {@code wrap}; a rule of dashes (between Pendulum and monster effects) is cut to fit instead. */
    private static List<FormattedCharSequence> wrap(Font font, String text, int wrap) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.length() > 2 && paragraph.chars().allMatch(c -> c == '-')) {
                out.add(Component.literal(font.plainSubstrByWidth(paragraph, wrap)).getVisualOrderText());
            } else {
                out.addAll(font.split(Component.literal(paragraph), wrap));
            }
        }
        return out;
    }

    /** How tall lines of text are when wrapped to {@code wrap}, at full size. */
    private static int height(Font font, List<Line> lines, int wrap) {
        int height = 2;
        for (Line line : lines) {
            height += wrap(font, line.text(), wrap).size() * 9;
        }
        return height;
    }
}
