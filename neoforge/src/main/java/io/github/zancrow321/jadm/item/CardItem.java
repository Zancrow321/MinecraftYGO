package io.github.zancrow321.jadm.item;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * One trading card. Which card it is lives in the {@code jadm:card} component; cards of the same
 * passcode and rarity stack.
 */
public final class CardItem extends Item {
    /**
     * The card in the slot under the mouse while a big preview of it is on screen, set by the client around drawing a
     * screen; its tooltip then keeps to its name and rarity. Compared by identity.
     */
    public static ItemStack previewed = ItemStack.EMPTY;

    public CardItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(int code, Rarity rarity) {
        ItemStack stack = new ItemStack(JadmItems.CARD.get());
        stack.set(JadmComponents.CARD.get(), new JadmComponents.CardStack(code, rarity.id()));
        return stack;
    }

    public static ItemStack of(int code) {
        return of(code, Rarity.COMMON);
    }

    /** @return the card's passcode, or 0 for a blank card */
    public static int code(ItemStack stack) {
        JadmComponents.CardStack card = stack.get(JadmComponents.CARD.get());
        return card == null ? 0 : card.code();
    }

    public static Rarity rarity(ItemStack stack) {
        JadmComponents.CardStack card = stack.get(JadmComponents.CARD.get());
        try {
            return card == null ? Rarity.COMMON : Rarity.parse(card.rarity());
        } catch (IllegalArgumentException e) {
            return Rarity.COMMON;
        }
    }

    public static ChatFormatting color(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> ChatFormatting.WHITE;
            case RARE -> ChatFormatting.AQUA;
            case SUPER -> ChatFormatting.GOLD;
            case ULTRA -> ChatFormatting.LIGHT_PURPLE;
            case SECRET -> ChatFormatting.RED;
        };
    }

    public static String rarityName(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> "Common";
            case RARE -> "Rare";
            case SUPER -> "Super Rare";
            case ULTRA -> "Ultra Rare";
            case SECRET -> "Secret Rare";
        };
    }

    @Override
    public Component getName(ItemStack stack) {
        int code = code(stack);
        CardInfo card = code == 0 ? null : JadmData.cards().card(code);
        if (card == null) {
            return super.getName(stack);
        }
        return Component.literal(card.name()).withStyle(color(rarity(stack)));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return rarity(stack).ordinal() >= Rarity.SUPER.ordinal();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int code = code(stack);
        CardInfo card = code == 0 ? null : JadmData.cards().card(code);
        if (card == null) {
            return;
        }
        boolean preview = stack == previewed;
        if (!preview) {
            tooltip.add(Component.literal(typeLine(card)).withStyle(ChatFormatting.GRAY));
        }
        Rarity rarity = rarity(stack);
        if (rarity != Rarity.COMMON) {
            tooltip.add(Component.literal(rarityName(rarity)).withStyle(color(rarity)));
        }
        if (preview) {
            return;
        }
        if (flag.hasShiftDown()) {
            for (String line : wrap(card.description(), 48)) {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.literal("Shift for card text").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** "DARK Spellcaster · Level 7 · ATK 2500 / DEF 2100", or "Spell Card" / "Trap Card". */
    public static String typeLine(CardInfo card) {
        if (card.is(OcgConstants.TYPE_SPELL)) {
            return "Spell Card";
        }
        if (card.is(OcgConstants.TYPE_TRAP)) {
            return "Trap Card";
        }
        var data = card.data();
        String attribute = JadmData.text().system(1010 + Integer.numberOfTrailingZeros(Math.max(1, data.attribute())));
        String race = JadmData.text().system(1020 + Long.numberOfTrailingZeros(Math.max(1, data.race())));
        String atk = data.attack() < 0 ? "?" : String.valueOf(data.attack());
        String def = data.defense() < 0 ? "?" : String.valueOf(data.defense());
        StringBuilder line = new StringBuilder(attribute).append(' ').append(race);
        for (int i = 0; i < SUBTYPES.length; i++) {
            if ((data.type() & SUBTYPES[i]) != 0) {
                line.append(" / ").append(SUBTYPE_NAMES[i]);
            }
        }
        if (card.is(OcgConstants.TYPE_LINK)) {
            return line.append(" · LINK-").append(data.level()).append(' ').append(linkArrows(data.linkMarker()))
                    .append(" · ATK ").append(atk).toString();
        }
        line.append(card.is(OcgConstants.TYPE_XYZ) ? " · Rank " : " · Level ").append(data.level());
        if (card.is(OcgConstants.TYPE_PENDULUM)) {
            line.append(" · Scale ").append(data.lscale());
        }
        return line.append(" · ATK ").append(atk).append(" / DEF ").append(def).toString();
    }

    private static final int[] SUBTYPES = {OcgConstants.TYPE_FUSION, OcgConstants.TYPE_RITUAL,
            OcgConstants.TYPE_SYNCHRO, OcgConstants.TYPE_XYZ, OcgConstants.TYPE_PENDULUM, OcgConstants.TYPE_LINK,
            0x1000 /* Tuner */, 0x200 /* Spirit */, 0x400 /* Union */, 0x800 /* Gemini */, 0x200000 /* Flip */,
            0x400000 /* Toon */, OcgConstants.TYPE_NORMAL};
    private static final String[] SUBTYPE_NAMES = {"Fusion", "Ritual", "Synchro", "Xyz", "Pendulum", "Link", "Tuner",
            "Spirit", "Union", "Gemini", "Flip", "Toon", "Normal"};
    /** Link arrows in reading order, with the {@code LINK_MARKER_*} bit each stands for. */
    private static final int[] ARROW_BITS = {0100, 0200, 0400, 0010, 0040, 0001, 0002, 0004};
    private static final String[] ARROWS = {"↖", "↑", "↗", "←", "→", "↙", "↓", "↘"};

    /** A Link monster's arrows, e.g. "←↓→". */
    public static String linkArrows(int markers) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ARROW_BITS.length; i++) {
            if ((markers & ARROW_BITS[i]) != 0) {
                out.append(ARROWS[i]);
            }
        }
        return out.toString();
    }

    static List<String> wrap(String text, int width) {
        List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\\R")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
