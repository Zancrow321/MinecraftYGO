package io.github.zancrow321.minecraftygo.engine.data;

import io.github.zancrow321.minecraftygo.engine.CardData;

import java.util.List;

/**
 * Everything the mod knows about one card: engine stats plus display text.
 *
 * @param strings the card's extra strings (effect prompts), indexed from 0
 */
public record CardInfo(CardData data, String name, String description, List<String> strings) {
    public int code() {
        return data.code();
    }

    public boolean is(int typeFlag) {
        return (data.type() & typeFlag) != 0;
    }
}
