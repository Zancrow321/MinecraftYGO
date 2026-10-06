package io.github.zancrow321.jadm.engine.text;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.CardInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns engine ids into English text: card names, effect descriptions and EDOPro's system strings.
 */
public final class DuelText {
    public static final String SYSTEM_STRINGS = "/jadm/system_strings.json";

    private final CardDatabase cards;
    private final Map<Integer, String> system;

    public DuelText(CardDatabase cards, Map<Integer, String> system) {
        this.cards = cards;
        this.system = Map.copyOf(system);
    }

    public static DuelText loadBundled(CardDatabase cards) {
        try (InputStream in = DuelText.class.getResourceAsStream(SYSTEM_STRINGS)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + SYSTEM_STRINGS);
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<Integer, String> system = new HashMap<>();
            json.entrySet().forEach(e -> system.put(Integer.parseInt(e.getKey()), e.getValue().getAsString()));
            return new DuelText(cards, system);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public CardDatabase cards() {
        return cards;
    }

    /** @return the card's name, or "a face-down card" for code 0 */
    public String cardName(int code) {
        return code == 0 ? "a face-down card" : cards.name(code);
    }

    public String system(int id) {
        return system.getOrDefault(id, "#" + id);
    }

    /**
     * Resolves an effect description: {@code (code << 20) | index} names a card's string, anything else is a system
     * string id.
     */
    public String description(long description) {
        if (description == 0) {
            return "Activate";
        }
        int code = (int) (description >>> 20);
        int index = (int) (description & 0xFFFFF);
        if (code == 0) {
            return system(index);
        }
        CardInfo card = cards.card(code);
        if (card != null && index < card.strings().size() && !card.strings().get(index).isEmpty()) {
            return card.strings().get(index);
        }
        return "Effect of " + cardName(code);
    }

    public String phase(int phase) {
        return switch (phase) {
            case 0x01 -> "Draw Phase";
            case 0x02 -> "Standby Phase";
            case 0x04 -> "Main Phase 1";
            case 0x08, 0x10, 0x20, 0x40, 0x80 -> "Battle Phase";
            case 0x100 -> "Main Phase 2";
            case 0x200 -> "End Phase";
            default -> "Phase " + phase;
        };
    }

    public String location(int location) {
        return switch (location & 0x7F) {
            case 0x01 -> "Deck";
            case 0x02 -> "hand";
            case 0x04 -> "Monster Zone";
            case 0x08 -> "Spell & Trap Zone";
            case 0x10 -> "GY";
            case 0x20 -> "banishment";
            case 0x40 -> "Extra Deck";
            default -> "field";
        };
    }
}
