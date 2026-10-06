package io.github.zancrow321.minecraftygo.engine.text;

import io.github.zancrow321.minecraftygo.engine.protocol.Loc;
import io.github.zancrow321.minecraftygo.engine.protocol.Responses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * A prompt laid out for a UI: a title and either a list of one-click choices or a multi-select with a confirm
 * button. Each choice carries the exact response bytes to send back.
 *
 * @param card the card the whole prompt is about (a "use this effect?" question, a position to pick), or 0
 */
public record PromptView(String title, List<Choice> choices, MultiSelect multi, int card) {
    /** What a choice does, so a UI can put it on the right card, button or menu. */
    public enum Kind {
        SUMMON, SPECIAL_SUMMON, SET_MONSTER, SET_SPELL, ACTIVATE, REPOSITION, ATTACK, TO_BATTLE, TO_MAIN2, TO_END,
        CHAIN, PASS, PLACE, OTHER
    }

    /**
     * @param at     the field or hand place this choice is about (for clicking it in the world), or {@code null}
     * @param kind   what the choice does
     * @param action a short label for a menu on the card itself ("Normal Summon", "Activate: ..."); the card's
     *               name is left out
     * @param code   the card the choice is about, or 0
     */
    public record Choice(String label, byte[] response, Loc at, Kind kind, String action, int code) {
        public Choice(String label, byte[] response) {
            this(label, response, null);
        }

        public Choice(String label, byte[] response, Loc at) {
            this(label, response, at, Kind.OTHER, label, 0);
        }
    }

    /**
     * Pick between {@code min} and {@code max} of {@code options}, then send {@code encode(selectedIndices)}.
     *
     * @param codes the card behind each option, or 0 (same order as {@code options})
     */
    public record MultiSelect(List<String> options, List<Loc> locs, List<Integer> codes, int min, int max,
                              Function<List<Integer>, byte[]> encoder, Choice cancel) {
        public boolean canConfirm(List<Integer> selected) {
            return selected.size() >= min && selected.size() <= max;
        }

        public byte[] encode(List<Integer> selected) {
            return encoder.apply(selected);
        }
    }

    public static PromptView choices(String title, List<Choice> choices) {
        return choices(title, choices, 0);
    }

    public static PromptView choices(String title, List<Choice> choices, int card) {
        return new PromptView(title, List.copyOf(choices), null, card);
    }

    /** @param locs where each option is (same order as {@code options}); entries may be {@code null} */
    public static PromptView multi(String title, List<String> options, List<Loc> locs, int min, int max,
                                   Function<List<Integer>, byte[]> encoder, boolean cancelable) {
        return multi(title, options, locs, Collections.nCopies(options.size(), 0), min, max, encoder, cancelable);
    }

    public static PromptView multi(String title, List<String> options, List<Loc> locs, List<Integer> codes, int min,
                                   int max, Function<List<Integer>, byte[]> encoder, boolean cancelable) {
        Choice cancel = cancelable ? new Choice("Cancel", Responses.cancel()) : null;
        return new PromptView(title, List.of(), new MultiSelect(List.copyOf(options),
                Collections.unmodifiableList(new ArrayList<>(locs)), List.copyOf(codes), min, max, encoder, cancel),
                0);
    }
}
