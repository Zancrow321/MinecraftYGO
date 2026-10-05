package io.github.zancrow321.minecraftygo.engine.ffi;

/**
 * {@code OCG_NewCardInfo}. {@code duelist} 0 places the card directly; values &gt; 0 add it to the deck lists of an
 * additional tag duelist of {@code team} (only DECK/EXTRA are accepted then).
 */
public record NewCard(int team, int duelist, int code, int controller, int location, int sequence, int position) {}
