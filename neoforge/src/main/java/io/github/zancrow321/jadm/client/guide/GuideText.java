package io.github.zancrow321.jadm.client.guide;

import io.github.zancrow321.jadm.Jadm;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.function.Function;

/**
 * The little markup of a handbook paragraph. Whole paragraphs: {@code ""} is a gap, {@code "# Heading"},
 * {@code "- bullet"}, {@code "---"} starts a new page and {@code "[recipe jadm:binder]"} draws a crafting
 * grid. Inside a paragraph: {@code **bold**}, {@code `/jadm duel bot`} (commands can be clicked to type them in chat)
 * and placeholders: {@code {server:pool.mode}} for a server setting, {@code {client:chooseZone}} for a client setting
 * and {@code {key:duel_log}} for the key bound to an action ({@code key.jadm.duel_log}; a name with a dot is
 * taken as it is, e.g. {@code {key:key.inventory}}). See {@link #names} for the mod's own names.
 */
final class GuideText {
    static final int INK = 0x3B2C1F;
    static final int HEADING = 0x8A1F1F;
    static final int MUTED = 0x7D6A55;
    private static final Style CODE = Style.EMPTY.withColor(TextColor.fromRgb(0x1F3F8F));
    private static final Style VALUE = Style.EMPTY.withColor(TextColor.fromRgb(0x2E6B2E)).withBold(true);

    private GuideText() {
    }

    /**
     * Fills in the mod's names, so the pages follow a rename: {@code {cmd}} is the command root ({@code /jadm}) and
     * {@code {modid}} the mod id, as in {@code {modid}-server.toml}. Also works inside {@code `code`}.
     */
    static String names(String text) {
        return text.replace("{cmd}", "/" + Jadm.COMMAND).replace("{modid}", Jadm.MOD_ID);
    }

    /** @param resolve turns a placeholder such as {@code server:pool.mode} into its value */
    static MutableComponent inline(String text, Function<String, String> resolve) {
        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                flush(out, run, bold);
                bold = !bold;
                i++;
            } else if (c == '`' && text.indexOf('`', i + 1) > i) {
                flush(out, run, bold);
                int end = text.indexOf('`', i + 1);
                out.append(code(text.substring(i + 1, end)));
                i = end;
            } else if (c == '{' && text.indexOf('}', i + 1) > i) {
                flush(out, run, bold);
                int end = text.indexOf('}', i + 1);
                out.append(Component.literal(resolve.apply(text.substring(i + 1, end))).withStyle(VALUE));
                i = end;
            } else {
                run.append(c);
            }
        }
        flush(out, run, bold);
        return out;
    }

    private static void flush(MutableComponent out, StringBuilder run, boolean bold) {
        if (!run.isEmpty()) {
            out.append(Component.literal(run.toString()).withStyle(Style.EMPTY.withBold(bold)));
            run.setLength(0);
        }
    }

    /** A command is clickable: it goes into the chat box, up to its first {@code <argument>} or {@code [option]}. */
    private static Component code(String text) {
        Style style = CODE;
        if (text.startsWith("/")) {
            int cut = text.length();
            for (String mark : new String[]{" <", " ["}) {
                int at = text.indexOf(mark);
                if (at >= 0 && at < cut) {
                    cut = at + 1;
                }
            }
            style = style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, text.substring(0, cut)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("screen.jadm.guide.type_command")));
        }
        return Component.literal(text).withStyle(style);
    }
}
