package io.github.zancrow321.minecraftygo.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings, in {@code config/minecraftygo-client.toml}. */
public final class YgoClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> CARD_ART_URL;
    public static final ModConfigSpec.ConfigValue<String> CARD_ART_CROPPED_URL;
    public static final ModConfigSpec.BooleanValue DOWNLOAD_CARD_ART;
    public static final ModConfigSpec.BooleanValue CHOOSE_ZONE;

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
        CHOOSE_ZONE = builder
                .comment("When you play a card from its menu, pick its zone yourself instead of taking the middle-most"
                        + " free one. Dragging a card onto a zone always puts it there.")
                .define("chooseZone", false);
        SPEC = builder.build();
    }

    private YgoClientConfig() {
    }
}
