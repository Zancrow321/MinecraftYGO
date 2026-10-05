package io.github.zancrow321.minecraftygo.engine.text;

import io.github.zancrow321.minecraftygo.engine.protocol.Responses;

import java.util.List;
import java.util.function.Function;

/**
 * A prompt laid out for a UI: a title and either a list of one-click choices or a multi-select with a confirm
 * button. Each choice carries the exact response bytes to send back.
 */
public record PromptView(String title, List<Choice> choices, MultiSelect multi) {
    public record Choice(String label, byte[] response) {
    }

    /**
     * Pick between {@code min} and {@code max} of {@code options}, then send {@code encode(selectedIndices)}.
     */
    public record MultiSelect(List<String> options, int min, int max, Function<List<Integer>, byte[]> encoder,
                              Choice cancel) {
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

    public static PromptView multi(String title, List<String> options, int min, int max,
                                   Function<List<Integer>, byte[]> encoder, boolean cancelable) {
        Choice cancel = cancelable ? new Choice("Cancel", Responses.cancel()) : null;
        return new PromptView(title, List.of(), new MultiSelect(List.copyOf(options), min, max, encoder, cancel));
    }
}
