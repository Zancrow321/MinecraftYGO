package io.github.zancrow321.jadm.display;

import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.item.JadmItems;
import net.minecraft.world.item.ItemStack;

/** The four collector displays: what they hold and how many. */
public enum DisplayKind {
    /** A card behind glass in a frame on the wall, or lying on a table. */
    CARD_FRAME("card_frame", 1, true, true),
    /** A glass case with nine cards in three rows, on the wall or lying on a table. */
    SHOWCASE("card_showcase", 9, true, true),
    /** A card in a toploader, standing on a little stand. */
    TOPLOADER_STAND("toploader_stand", 1, true, false),
    /** An open display box with three sealed products: booster packs, structure decks or tins. */
    PACK_DISPLAY("pack_display", 3, false, false);

    private final String id;
    private final int slots;
    private final boolean cards;
    private final boolean wall;

    DisplayKind(String id, int slots, boolean cards, boolean wall) {
        this.id = id;
        this.slots = slots;
        this.cards = cards;
        this.wall = wall;
    }

    public String id() {
        return id;
    }

    public int slots() {
        return slots;
    }

    /** Whether it can hang on a wall; the others always stand on the floor. */
    public boolean wall() {
        return wall;
    }

    /** Whether a stack may go in: a card with a passcode, or a sealed product. */
    public boolean accepts(ItemStack stack) {
        if (cards) {
            return stack.is(JadmItems.CARD.get()) && CardItem.code(stack) != 0;
        }
        return stack.is(JadmItems.BOOSTER_PACK.get()) || stack.is(JadmItems.STRUCTURE_DECK.get())
                || stack.is(JadmItems.TIN.get());
    }
}
