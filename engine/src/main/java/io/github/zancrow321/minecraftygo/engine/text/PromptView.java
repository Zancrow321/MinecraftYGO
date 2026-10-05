package io.github.zancrow321.minecraftygo.engine.text;

import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import java.util.ArrayList;
import java.util.Collections;

import io.github.zancrow321.minecraftygo.engine.protocol.Responses;

import java.util.List;
import java.util.function.Function;

/**
 * A prompt laid out for a UI: a title and either a list of one-click choices or a multi-select with a confirm
 * button. Each choice carries the exact response bytes to send back.
 */
public record PromptView(String title, List<Choice> choices, MultiSelect multi) {
    /** @param at the field or hand place this choice is about (for clicking it in the world), or {@code null} */
    public record Choice(String label, byte[] response, Loc at) {
        public Choice(String label, byte[] response) {
            this(label, response, null);
        }
    }

    /**
     * Pick between {@code min} and {@code max} of {@code options}, then send {@code encode(selectedIndices)}.
     */
    public record MultiSelect(List<String> options, List<Loc> locs, int min, int max,
                              Function<List<Integer>, byte[]> encoder, Choice cancel) {
        public boolean canConfirm(List<Integer> selected) {
            return selected.size() >= min && selected.size() <= max;
        }

        public byte[] encode(List<Integer> selected) {
            return encoder.apply(selected);
        }
    }

    public static PromptView choices(String title, List<Choice> choices) {
        return new PromptView(title, List.copyOf(choices), null);
    }

    /** @param locs where each option is (same order as {@code options}); entries may be {@code null} */
    public static PromptView multi(String title, List<String> options, List<Loc> locs, int min, int max,
                                   Function<List<Integer>, byte[]> encoder, boolean cancelable) {
        Choice cancel = cancelable ? new Choice("Cancel", Responses.cancel()) : null;
        return new PromptView(title, List.of(), new MultiSelect(List.copyOf(options),
                Collections.unmodifiableList(new ArrayList<>(locs)), min, max, encoder, cancel));
    }
}
