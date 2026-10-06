package io.github.zancrow321.jadm.client.guide;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.client.JadmClientConfig;
import io.github.zancrow321.jadm.item.JadmItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The handbook as an open book: a title page, the contents (click a chapter to go there) and the chapters, laid out
 * two pages at a time from {@link GuideContent}. Arrow keys, the mouse wheel or the page buttons turn pages; the line
 * at the top of a page leads back to the contents. The book reopens where it was last left.
 */
public final class GuideScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Jadm.MOD_ID, "textures/gui/guide_book.png");
    private static final int W = 380;
    private static final int H = 224;
    private static final int TEXTURE_W = 512;
    private static final int TEXTURE_H = 256;
    private static final int[] PAGE_X = {22, 206};
    private static final int TEXT_W = 152;
    private static final int HEADER_Y = 13;
    private static final int TOP = 27;
    private static final int PAGE_H = 172;
    private static final int LINE = 9;
    private static final int BULLET = 8;

    private static int lastSpread;

    private final Map<String, String> serverSettings;
    private final Map<String, String> clientSettings = new HashMap<>();
    private GuideContent content;
    private final List<Page> pages = new ArrayList<>();
    /** The page each chapter starts on. */
    private final List<Integer> chapterPages = new ArrayList<>();
    private int spread;
    private int left;
    private int top;
    private PageButton back;
    private PageButton forward;
    /** What can be hovered or clicked, as drawn in the last frame. */
    private final List<Hit> hits = new ArrayList<>();

    public GuideScreen(Map<String, String> serverSettings) {
        super(Component.translatable("item.jadm.guide_book"));
        this.serverSettings = serverSettings;
        collect(JadmClientConfig.SPEC.getValues(), "", clientSettings);
    }

    private static void collect(UnmodifiableConfig config, String prefix, Map<String, String> into) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            Object value = entry.getRawValue();
            if (value instanceof UnmodifiableConfig section) {
                collect(section, prefix + entry.getKey() + ".", into);
            } else if (value instanceof ModConfigSpec.ConfigValue<?> setting) {
                into.put(prefix + entry.getKey(), String.valueOf(setting.get()));
            }
        }
    }

    // ---- Layout ----------------------------------------------------------------------------------------------------

    private sealed interface Element permits Text, Gap, Title, ChapterHead, TocRow, Recipe, Break {
        int height();
    }

    private record Text(FormattedCharSequence line, int indent, boolean bullet, boolean heading) implements Element {
        @Override
        public int height() {
            return LINE;
        }
    }

    private record Gap(int height) implements Element {
    }

    private record Title() implements Element {
        @Override
        public int height() {
            return 78;
        }
    }

    private record ChapterHead(int chapter) implements Element {
        @Override
        public int height() {
            return 24;
        }
    }

    private record TocRow(int chapter) implements Element {
        @Override
        public int height() {
            return 13;
        }
    }

    private record Recipe(ResourceLocation id) implements Element {
        @Override
        public int height() {
            return 60;
        }
    }

    private record Break() implements Element {
        @Override
        public int height() {
            return 0;
        }
    }

    /** @param chapter the chapter shown, -1 for the title page and -2 for the contents */
    private record Page(List<Element> elements, int chapter, boolean chapterStart) {
    }

    private record Hit(int x, int y, int w, int h, Runnable click, Component tooltip, ItemStack item,
                       FormattedCharSequence line) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(2, (height - H) / 2);
        content = GuideContent.load();
        layout();
        spread = Math.min(lastSpread, (pages.size() - 1) / 2);
        back = addRenderableWidget(new PageButton(left + 16, top + 200, false, b -> turn(-1), true));
        forward = addRenderableWidget(new PageButton(left + W - 16 - 23, top + 200, true, b -> turn(1), true));
        updateButtons();
    }

    private void layout() {
        pages.clear();
        chapterPages.clear();
        List<Element> intro = new ArrayList<>();
        intro.add(new Title());
        paragraphs(content.intro(), intro);
        paginate(intro, -1);
        List<Element> toc = new ArrayList<>();
        for (int i = 0; i < content.chapters().size(); i++) {
            toc.add(new TocRow(i));
        }
        paginate(toc, -2);
        for (int i = 0; i < content.chapters().size(); i++) {
            chapterPages.add(pages.size());
            List<Element> elements = new ArrayList<>();
            elements.add(new ChapterHead(i));
            paragraphs(content.chapters().get(i).text(), elements);
            paginate(elements, i);
        }
    }

    private void paragraphs(List<String> paragraphs, List<Element> into) {
        for (String raw : paragraphs) {
            String paragraph = GuideText.names(raw);
            if (paragraph.isBlank()) {
                into.add(new Gap(5));
            } else if (paragraph.equals("---")) {
                into.add(new Break());
            } else if (paragraph.startsWith("[recipe ") && paragraph.endsWith("]")) {
                ResourceLocation id = ResourceLocation.tryParse(paragraph.substring(8, paragraph.length() - 1).trim());
                if (id != null) {
                    into.add(new Recipe(id));
                }
            } else if (paragraph.startsWith("# ")) {
                into.add(new Gap(4));
                Component heading = GuideText.inline(paragraph.substring(2), this::resolve)
                        .withStyle(s -> s.withBold(true).withColor(GuideText.HEADING));
                for (FormattedCharSequence line : font.split(heading, TEXT_W)) {
                    into.add(new Text(line, 0, false, true));
                }
            } else if (paragraph.startsWith("- ")) {
                boolean first = true;
                for (FormattedCharSequence line : font.split(GuideText.inline(paragraph.substring(2),
                        this::resolve), TEXT_W - BULLET)) {
                    into.add(new Text(line, BULLET, first, false));
                    first = false;
                }
            } else {
                for (FormattedCharSequence line : font.split(GuideText.inline(paragraph, this::resolve), TEXT_W)) {
                    into.add(new Text(line, 0, false, false));
                }
            }
        }
    }

    /** Fills pages with the elements: gaps never start a page and a heading never ends one. */
    private void paginate(List<Element> elements, int chapter) {
        List<Element> page = new ArrayList<>();
        int used = 0;
        boolean start = true;
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            int need = e.height();
            if (e instanceof Text t && t.heading() && i + 1 < elements.size()) {
                need += elements.get(i + 1).height();
            }
            if (e instanceof Break || used + need > PAGE_H && !page.isEmpty()) {
                pages.add(new Page(page, chapter, start));
                page = new ArrayList<>();
                used = 0;
                start = false;
            }
            if (e instanceof Break || e instanceof Gap && page.isEmpty()) {
                continue;
            }
            page.add(e);
            used += e.height();
        }
        pages.add(new Page(page, chapter, start));
    }

    private String resolve(String placeholder) {
        int colon = placeholder.indexOf(':');
        String kind = colon < 0 ? "" : placeholder.substring(0, colon);
        String key = placeholder.substring(colon + 1);
        return switch (kind) {
            case "server" -> serverSettings.getOrDefault(key, "?");
            case "client" -> clientSettings.getOrDefault(key, "?");
            case "key" -> {
                String name = key.contains(".") ? key : "key." + Jadm.MOD_ID + "." + key;
                for (KeyMapping mapping : minecraft.options.keyMappings) {
                    if (mapping.getName().equals(name)) {
                        yield mapping.getTranslatedKeyMessage().getString();
                    }
                }
                yield "?";
            }
            default -> "{" + placeholder + "}";
        };
    }

    // ---- Navigation ------------------------------------------------------------------------------------------------

    private int spreads() {
        return (pages.size() + 1) / 2;
    }

    private void turn(int by) {
        goTo(spread + by);
    }

    private void goTo(int to) {
        spread = Math.max(0, Math.min(spreads() - 1, to));
        lastSpread = spread;
        updateButtons();
    }

    private void goToContents() {
        int contents = 0;
        while (contents < pages.size() && pages.get(contents).chapter() != -2) {
            contents++;
        }
        goTo(contents / 2);
    }

    private void updateButtons() {
        back.visible = spread > 0;
        forward.visible = spread < spreads() - 1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> turn(-1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> turn(1);
            case GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_BACKSPACE -> goToContents();
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            turn(scrollY > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : hits) {
                if (hit.contains(mouseX, mouseY)) {
                    if (hit.click() != null) {
                        hit.click().run();
                        return true;
                    }
                    Style style = hit.line() == null ? null
                            : font.getSplitter().componentStyleAtWidth(hit.line(), (int) mouseX - hit.x());
                    if (style != null && style.getClickEvent() != null
                            && style.getClickEvent().getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                        minecraft.setScreen(new ChatScreen(style.getClickEvent().getValue()));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---- Drawing ---------------------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(TEXTURE, left, top, 0, 0, W, H, TEXTURE_W, TEXTURE_H);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        hits.clear();
        for (int side = 0; side < 2; side++) {
            int index = spread * 2 + side;
            if (index < pages.size()) {
                drawPage(graphics, pages.get(index), index, left + PAGE_X[side], mouseX, mouseY);
            }
        }
        for (Hit hit : hits) {
            if (!hit.contains(mouseX, mouseY)) {
                continue;
            }
            if (hit.item() != null) {
                graphics.renderTooltip(font, hit.item(), mouseX, mouseY);
            } else if (hit.tooltip() != null) {
                graphics.renderTooltip(font, hit.tooltip(), mouseX, mouseY);
            } else if (hit.line() != null) {
                graphics.renderComponentHoverEffect(font,
                        font.getSplitter().componentStyleAtWidth(hit.line(), mouseX - hit.x()), mouseX, mouseY);
            }
        }
    }

    private void drawPage(GuiGraphics graphics, Page page, int index, int x, int mouseX, int mouseY) {
        if (page.chapter() != -1) {
            Component header = page.chapter() == -2 || page.chapterStart()
                    ? Component.translatable("screen.jadm.guide.contents")
                    : Component.literal(content.chapters().get(page.chapter()).title());
            Component line = Component.literal("≡ ").append(header);
            int w = font.width(line);
            boolean hover = page.chapter() != -2 && mouseX >= x && mouseX < x + w && mouseY >= top + HEADER_Y - 1
                    && mouseY < top + HEADER_Y + LINE;
            graphics.drawString(font, hover ? line.copy().withStyle(s -> s.withUnderlined(true)) : line, x,
                    top + HEADER_Y, GuideText.MUTED, false);
            if (page.chapter() != -2) {
                hits.add(new Hit(x, top + HEADER_Y - 1, w, LINE + 1, this::goToContents,
                        Component.translatable("screen.jadm.guide.back_to_contents"), null, null));
            }
            String number = String.valueOf(index + 1);
            graphics.drawString(font, number, x + (TEXT_W - font.width(number)) / 2, top + 204, GuideText.MUTED,
                    false);
        }
        int y = top + TOP;
        for (Element e : page.elements()) {
            draw(graphics, e, x, y, mouseX, mouseY);
            y += e.height();
        }
    }

    private void draw(GuiGraphics graphics, Element element, int x, int y, int mouseX, int mouseY) {
        switch (element) {
            case Text t -> {
                if (t.bullet()) {
                    graphics.drawString(font, "•", x + 1, y, GuideText.INK, false);
                }
                graphics.drawString(font, t.line(), x + t.indent(), y, GuideText.INK, false);
                hits.add(new Hit(x + t.indent(), y, font.width(t.line()), LINE, null, null, null, t.line()));
            }
            case Title t -> {
                graphics.pose().pushPose();
                graphics.pose().translate(x + TEXT_W / 2f - 16, y + 4, 0);
                graphics.pose().scale(2, 2, 1);
                graphics.renderItem(new ItemStack(JadmItems.GUIDE_BOOK.get()), 0, 0);
                graphics.pose().popPose();
                Component title = Component.literal(content.title()).withStyle(s -> s.withBold(true));
                centered(graphics, title, x, y + 42, Math.min(1.5f, (float) TEXT_W / font.width(title)),
                        GuideText.HEADING);
                for (FormattedCharSequence line : font.split(FormattedText.of(content.subtitle()), TEXT_W)) {
                    graphics.drawString(font, line, x + (TEXT_W - font.width(line)) / 2, y + 58, GuideText.MUTED,
                            false);
                    y += LINE;
                }
            }
            case ChapterHead h -> {
                GuideContent.Chapter chapter = content.chapters().get(h.chapter());
                graphics.renderItem(chapter.icon(), x, y);
                for (FormattedCharSequence line : font.split(Component.literal(chapter.title())
                        .withStyle(s -> s.withBold(true)), TEXT_W - 20).subList(0, 1)) {
                    graphics.drawString(font, line, x + 20, y + 4, GuideText.HEADING, false);
                }
                graphics.fill(x, y + 18, x + TEXT_W, y + 19, 0x60000000 | GuideText.HEADING);
            }
            case TocRow r -> {
                GuideContent.Chapter chapter = content.chapters().get(r.chapter());
                boolean hover = mouseX >= x && mouseX < x + TEXT_W && mouseY >= y && mouseY < y + 13;
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(0.75f, 0.75f, 1);
                graphics.renderItem(chapter.icon(), 0, 0);
                graphics.pose().popPose();
                Component title = Component.literal(chapter.title());
                if (hover) {
                    title = title.copy().withStyle(s -> s.withUnderlined(true));
                }
                graphics.drawString(font, font.substrByWidth(title, TEXT_W - 34).getString(), x + 16, y + 2,
                        hover ? GuideText.HEADING : GuideText.INK, false);
                String number = String.valueOf(chapterPages.get(r.chapter()) + 1);
                graphics.drawString(font, number, x + TEXT_W - font.width(number), y + 2, GuideText.MUTED, false);
                int page = chapterPages.get(r.chapter());
                hits.add(new Hit(x, y, TEXT_W, 13, () -> goTo(page / 2), null, null, null));
            }
            case Recipe r -> drawRecipe(graphics, r.id(), x, y);
            case Gap g -> {
            }
            case Break b -> {
            }
        }
    }

    private void centered(GuiGraphics graphics, Component text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x + TEXT_W / 2f, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    /** A crafting grid: ingredients that accept several items cycle through them. */
    private void drawRecipe(GuiGraphics graphics, ResourceLocation id, int x, int y) {
        RecipeHolder<?> holder = minecraft.level == null ? null
                : minecraft.level.getRecipeManager().byKey(id).orElse(null);
        if (holder == null) {
            graphics.drawString(font, "? " + id, x, y, GuideText.MUTED, false);
            return;
        }
        List<Ingredient> ingredients = holder.value().getIngredients();
        int columns = holder.value() instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        int gridX = x + 10;
        int gridY = y + 2;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int sx = gridX + column * 18;
                int sy = gridY + row * 18;
                slot(graphics, sx, sy);
                int i = row * columns + column;
                if (column < columns && i < ingredients.size()) {
                    ItemStack[] items = ingredients.get(i).getItems();
                    if (items.length > 0) {
                        item(graphics, items[(int) (System.currentTimeMillis() / 1000 % items.length)], sx, sy);
                    }
                }
            }
        }
        int arrowX = gridX + 3 * 18 + 6;
        graphics.drawString(font, "→", arrowX + 2, gridY + 23, GuideText.INK, false);
        int resultX = arrowX + 18;
        slot(graphics, resultX, gridY + 18);
        item(graphics, holder.value().getResultItem(minecraft.level.registryAccess()), resultX, gridY + 18);
    }

    private void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B7355);
        graphics.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFF6E0);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFFD9C7A3);
    }

    private void item(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.renderItem(stack, x + 1, y + 1);
        graphics.renderItemDecorations(font, stack, x + 1, y + 1);
        hits.add(new Hit(x, y, 18, 18, null, null, stack, null));
    }
}
