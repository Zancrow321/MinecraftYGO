package io.github.zancrow321.minecraftygo.engine.testing;

import io.github.zancrow321.minecraftygo.engine.message.CoreMessage;
import io.github.zancrow321.minecraftygo.engine.message.Event;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.query.CardInfo;
import io.github.zancrow321.minecraftygo.engine.query.FieldState;
import io.github.zancrow321.minecraftygo.engine.query.PlayerField;
import io.github.zancrow321.minecraftygo.engine.wire.LocInfo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden-information invariants, stated independently of the production redaction code: given the raw message (or
 * field) and what a viewer received, nothing the viewer may not know must be present.
 */
public final class LeakOracle {
	private LeakOracle() {}

	public static void checkMessage(CoreMessage raw, int viewer, Optional<CoreMessage> redacted) {
		if (raw instanceof Prompt prompt) {
			assertThat(redacted).as("prompt %s for viewer %d", raw, viewer).matches(r -> r.isPresent() == (prompt.player() == viewer));
			return;
		}
		if (redacted.isEmpty()) {
			return;
		}
		CoreMessage seen = redacted.get();
		switch (seen) {
			case Event.Draw d when d.player() != viewer -> assertThat(d.cards())
					.allMatch(c -> c.code() == 0 || (c.position() & POS_FACEUP) != 0, "opponent draws hidden");
			case Event.Move m when m.code() != 0 && m.to().controller() != viewer ->
					assertThat(isPublic(m.to())).as("move %s visible to %d", m, viewer).isTrue();
			case Event.SetCard s when s.location().controller() != viewer ->
					assertThat(s.code()).as("set card visible to %d", viewer).isZero();
			case Event.ShuffleCards s when s.player() != viewer ->
					assertThat(s.codes()).as("shuffled codes").containsOnly(0);
			case Event.PositionChange p when p.controller() != viewer && (p.currentPosition() & POS_FACEDOWN) != 0 ->
					assertThat(p.code()).isZero();
			case Event.Confirm c when c.cards().stream().anyMatch(card -> card.location() == LOCATION_DECK) ->
					assertThat(c.player()).as("deck confirm only for its player").isEqualTo(viewer);
			case Event.Hint h when h.hintType() == HINT_SELECTMSG || h.hintType() == HINT_EVENT ->
					assertThat(h.player()).isEqualTo(viewer);
			case Event.Unparsed u -> throw new AssertionError("unparsed message delivered: " + u);
			default -> {}
		}
	}

	private static boolean isPublic(LocInfo to) {
		if ((to.location() & (LOCATION_GRAVE | LOCATION_OVERLAY)) != 0) {
			return true;
		}
		return (to.location() & (LOCATION_DECK | LOCATION_HAND)) == 0 && (to.position() & POS_FACEUP) != 0;
	}

	public static void checkField(FieldState full, int viewer, FieldState seen) {
		for (int player = 0; player < 2; player++) {
			PlayerField actual = full.player(player);
			PlayerField visible = seen.player(player);
			if (player == viewer) {
				assertThat(visible).isEqualTo(actual);
				continue;
			}
			assertThat(visible.hand()).hasSameSizeAs(actual.hand());
			for (int i = 0; i < actual.hand().size(); i++) {
				if (!actual.hand().get(i).isPublic()) {
					assertThat(visible.hand().get(i).code()).as("opponent hand card").isZero();
				}
			}
			checkZones(actual.monsterZones(), visible.monsterZones());
			checkZones(actual.spellZones(), visible.spellZones());
			for (int i = 0; i < actual.extraDeck().size(); i++) {
				if (!actual.extraDeck().get(i).isPublic()) {
					assertThat(visible.extraDeck().get(i).code()).as("opponent extra deck card").isZero();
				}
			}
			assertThat(visible.graveyard()).isEqualTo(actual.graveyard());
			assertThat(visible.lifePoints()).isEqualTo(actual.lifePoints());
		}
	}

	private static void checkZones(List<CardInfo> actual, List<CardInfo> visible) {
		assertThat(visible).hasSameSizeAs(actual);
		for (int i = 0; i < actual.size(); i++) {
			CardInfo card = actual.get(i);
			assertThat(Objects.isNull(visible.get(i))).isEqualTo(card == null);
			if (card != null && !card.isFaceUp() && !card.isPublic()) {
				assertThat(visible.get(i).code()).as("face-down card").isZero();
				assertThat(visible.get(i).position()).isEqualTo(card.position());
			}
		}
	}
}
