package io.github.zancrow321.jadm.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings, in {@code config/jadm-client.toml}. */
public final class JadmClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> CARD_ART_URL;
    public static final ModConfigSpec.ConfigValue<String> CARD_ART_CROPPED_URL;
    public static final ModConfigSpec.BooleanValue DOWNLOAD_CARD_ART;
    public static final ModConfigSpec.BooleanValue PRODUCT_IMAGES;
    public static final ModConfigSpec.ConfigValue<String> PRODUCT_IMAGE_URL;
    public static final ModConfigSpec.BooleanValue CHOOSE_ZONE;
    public static final ModConfigSpec.BooleanValue SKIP_RESPONSES;
    public static final ModConfigSpec.BooleanValue CARD_PREVIEW;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DOWNLOAD_CARD_ART = builder
                .comment("Download card artwork when a card is first shown. No artwork ships with the mod.")
                .define("downloadCardArt", true);
        CARD_ART_URL = builder
                .comment("Where card artwork comes from. {code} is replaced by the card's passcode.")
                .define("cardArtUrl", "https://images.ygoprodeck.com/images/cards_small/{code}.jpg");
        CARD_ART_CROPPED_URL = builder
                .comment("Where the artwork alone (without the card frame) comes from, for the hologram of a monster "
                        + "without a model. {code} is replaced by the card's passcode.")
                .define("cardArtCroppedUrl", "https://images.ygoprodeck.com/images/cards_cropped/{code}.jpg");
        PRODUCT_IMAGES = builder
                .comment("Show booster packs, structure decks and tins as the real products. Their pictures are "
                        + "downloaded when an item is first shown; none ship with the mod. Off, or while a picture is "
                        + "missing, they keep the mod's own icons.")
                .define("realProductImages", true);
        PRODUCT_IMAGE_URL = builder
                .comment("Where product pictures come from. {code} is replaced by the product's set code, e.g. LOB.")
                .define("productImageUrl", "https://images.ygoprodeck.com/images/sets/{code}.jpg");
        CHOOSE_ZONE = builder
                .comment("When you play a card from its menu, pick its zone yourself instead of taking the middle-most"
                        + " free one. Dragging a card onto a zone always puts it there.")
                .define("chooseZone", false);
        SKIP_RESPONSES = builder
                .comment("In a duel, pass every chance to respond that you don't have to take, instead of asking. "
                        + "Switch it in the duel with the response button, the duel menu or C.")
                .define("skipResponses", false);
        CARD_PREVIEW = builder
                .comment("Show a big picture of the card under the mouse with its text left of the inventory, chests, "
                        + "the binder and the deck box, where there is room for it.")
                .define("cardPreview", true);
        SPEC = builder.build();
    }

    private JadmClientConfig() {
    }
}
