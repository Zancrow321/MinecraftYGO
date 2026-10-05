package io.github.zancrow321.minecraftygo.engine.view;

import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.query.CardInfo;
import io.github.zancrow321.minecraftygo.engine.query.FieldState;
import io.github.zancrow321.minecraftygo.engine.query.PlayerField;
import io.github.zancrow321.minecraftygo.engine.wire.LocInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;

/**
 * Hidden-information rules: what a viewer (core player/team 0 or 1, or {@link #SPECTATOR}) may learn from a core
 * message or field snapshot. This is a whitelist - message types without a rule are delivered to nobody. Rules follow
 * the YGOPro server conventions (prompts only to the prompted player, hidden destinations zero the card code, ...).
 */
public final class Visibility {
	/** A viewer that is not a duelist; sees only public information. */
	public static final int SPECTATOR = -1;

	private Visibility() {}

	public static Optional<CoreMessage> redact(CoreMessage message, int viewer) {
		return Optional.ofNullable(redactOrNull(message, viewer));
	}

	private static CoreMessage redactOrNull(CoreMessage message, int viewer) {
		return switch (message) {
			case Prompt p -> p.player() == viewer ? p : null;
			case Event e -> redactEvent(e, viewer);
			default -> null;
		};
	}

	private static CoreMessage redactEvent(Event event, int viewer) {
		return switch (event) {
			// Retries are handled by the duel runner (the prompt is simply sent again).
			case Event.Retry _ -> null;
			case Event.Unparsed _ -> null;
			case Event.Hint h -> switch (h.hintType()) {
				case HINT_OPSELECTED, HINT_RACE, HINT_ATTRIB, HINT_CODE, HINT_NUMBER, HINT_CARD -> h;
				default -> h.player() == viewer ? h : null;
			};
			case Event.MissedEffect m -> m.location().controller() == viewer ? m : null;
			case Event.Draw d -> d.player() == viewer ? d : new Event.Draw(d.player(),
					d.cards().stream().map(c -> isFaceUp(c.position()) ? c : new Event.CodePosition(0, c.position())).toList());
			case Event.Move m -> m.to().controller() == viewer || !isHiddenDestination(m.to()) ? m
					: new Event.Move(0, m.from(), m.to(), m.reason());
			case Event.PositionChange p -> p.controller() == viewer || isFaceUp(p.currentPosition()) ? p
					: new Event.PositionChange(0, p.controller(), p.location(), p.sequence(), p.previousPosition(),
					p.currentPosition());
			case Event.SetCard s -> s.location().controller() == viewer ? s : new Event.SetCard(0, s.location());
			case Event.Swap s -> new Event.Swap(
					visibleOnField(s.code1(), s.location1(), viewer), s.location1(),
					visibleOnField(s.code2(), s.location2(), viewer), s.location2());
			case Event.ShuffleCards s -> s.player() == viewer ? s
					: new Event.ShuffleCards(s.player(), s.extra(), s.codes().stream().map(_ -> 0).toList());
			case Event.TagSwap t -> t.player() == viewer ? t : new Event.TagSwap(t.player(), t.deckCount(),
					t.extraCount(), t.extraFaceUpCount(), t.deckTopCode(), hideFaceDown(t.hand()), hideFaceDown(t.extra()));
			case Event.Confirm c -> c.messageType() == MSG_CONFIRM_CARDS
					&& c.cards().stream().anyMatch(card -> card.location() == LOCATION_DECK)
					? (c.player() == viewer ? c : null) : c;
			case Event.Summoning s -> s;
			default -> event;
		};
	}

	/**
	 * Destinations where the card is not publicly known: the deck, the hand, or any face-down position - except the
	 * graveyard and Xyz materials, which are always public.
	 */
	public static boolean isHiddenDestination(LocInfo to) {
		if ((to.location() & (LOCATION_GRAVE | LOCATION_OVERLAY)) != 0) {
			return false;
		}
		return (to.location() & (LOCATION_DECK | LOCATION_HAND)) != 0 || !isFaceUp(to.position());
	}

	private static boolean isFaceUp(int position) {
		return (position & POS_FACEUP) != 0;
	}

	private static int visibleOnField(int code, LocInfo location, int viewer) {
		return location.controller() == viewer || isFaceUp(location.position()) ? code : 0;
	}

	private static List<Event.CodePosition> hideFaceDown(List<Event.CodePosition> cards) {
		return cards.stream().map(c -> isFaceUp(c.position()) ? c : new Event.CodePosition(0, c.position())).toList();
	}

	/** The field as {@code viewer} may see it. */
	public static FieldState redact(FieldState state, int viewer) {
		List<PlayerField> players = new ArrayList<>(2);
		for (int player = 0; player < 2; player++) {
			PlayerField field = state.player(player);
			players.add(player == viewer ? field : opponentView(field));
		}
		return new FieldState(players, state.chain());
	}

	private static PlayerField opponentView(PlayerField field) {
		return new PlayerField(field.lifePoints(),
				zones(field.monsterZones()),
				zones(field.spellZones()),
				field.hand().stream().map(Visibility::publicOrHidden).toList(),
				field.graveyard(),
				field.banished().stream().map(c -> c.isFaceUp() ? c : c.hidden()).toList(),
				field.extraDeck().stream().map(Visibility::publicOrHidden).toList(),
				field.deckCount());
	}

	private static List<CardInfo> zones(List<CardInfo> zones) {
		List<CardInfo> result = new ArrayList<>(zones.size());
		for (CardInfo card : zones) {
			result.add(card == null ? null : publicOrHidden(card));
		}
		return result;
	}

	private static CardInfo publicOrHidden(CardInfo card) {
		return card.isPublic() || card.isFaceUp() ? card : card.hidden();
	}
}
