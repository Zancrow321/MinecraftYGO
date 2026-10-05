package io.github.zancrow321.minecraftygo.engine.prompt;

import io.github.zancrow321.minecraftygo.engine.prompt.PromptResponse.IdleActionType;
import io.github.zancrow321.minecraftygo.engine.wire.WireWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseEncoderTest {
	private static final CardRef CARD = new CardRef(1, 0, LOCATION_HAND, 0, 0);

	@Test
	void idleActionPacksIndexAndType() {
		Prompt.IdleCommand idle = new Prompt.IdleCommand(0, List.of(CARD, CARD, CARD), List.of(), List.of(), List.of(),
				List.of(), List.of(), true, true, false);
		byte[] bytes = ResponseEncoder.encode(idle, new PromptResponse.IdleAction(IdleActionType.SUMMON, 2));
		assertThat(bytes).containsExactly(new WireWriter().i32((2 << 16)).toByteArray());
		assertThat(ResponseEncoder.encode(idle, new PromptResponse.IdleAction(IdleActionType.TO_END_PHASE, 0)))
				.containsExactly(new WireWriter().i32(7).toByteArray());
		assertThatThrownBy(() -> ResponseEncoder.encode(idle, new PromptResponse.IdleAction(IdleActionType.SHUFFLE_HAND, 0)))
				.isInstanceOf(InvalidResponseException.class);
	}

	@Test
	void cardSelectionUsesMode0() {
		Prompt.SelectCard select = new Prompt.SelectCard(0, false, 1, 2, List.of(CARD, CARD, CARD));
		assertThat(ResponseEncoder.encode(select, new PromptResponse.Cards(List.of(2, 0))))
				.containsExactly(new WireWriter().i32(0).i32(2).i32(2).i32(0).toByteArray());
		assertThatThrownBy(() -> ResponseEncoder.encode(select, new PromptResponse.Cards(List.of(0, 0))))
				.isInstanceOf(InvalidResponseException.class);
		assertThatThrownBy(() -> ResponseEncoder.encode(select, new PromptResponse.Cancel()))
				.isInstanceOf(InvalidResponseException.class);
	}

	@Test
	void placeChecksAvailability() {
		// own monster zones 0-4 occupied except 2; everything else unavailable
		int unavailable = ~(1 << 2);
		Prompt.SelectPlace place = new Prompt.SelectPlace(1, 1, unavailable, false);
		assertThat(ResponseEncoder.encode(place, new PromptResponse.Places(List.of(new PromptResponse.Zone(1, LOCATION_MZONE, 2)))))
				.containsExactly(1, LOCATION_MZONE, 2);
		assertThatThrownBy(() -> ResponseEncoder.encode(place,
				new PromptResponse.Places(List.of(new PromptResponse.Zone(0, LOCATION_MZONE, 2)))))
				.isInstanceOf(InvalidResponseException.class);
	}

	@Test
	void sortDefaultIsMinusOneByte() {
		Prompt.SortCards sort = new Prompt.SortCards(0, false, List.of(CARD, CARD));
		assertThat(ResponseEncoder.encode(sort, new PromptResponse.Cancel())).containsExactly(0xFF);
		assertThat(ResponseEncoder.encode(sort, new PromptResponse.Order(List.of(1, 0)))).containsExactly(1, 0);
	}

	@Test
	void exactSumUsesBothParamValues() {
		// levels 4 and 2, and a card that counts as 1 or 6 -> only {2, 1or6} reaches exactly 8 (via the high value)
		Prompt.SelectSum sum = new Prompt.SelectSum(0, false, 8, 1, 3, List.of(), List.of(
				new Prompt.SumCandidate(CARD, 4), new Prompt.SumCandidate(CARD, 2), new Prompt.SumCandidate(CARD, 1 | (6 << 16))));
		assertThat(ResponseEncoder.encode(sum, new PromptResponse.Cards(List.of(2, 1)))).hasSize(16);
		assertThatThrownBy(() -> ResponseEncoder.encode(sum, new PromptResponse.Cards(List.of(0, 1))))
				.isInstanceOf(InvalidResponseException.class);
		assertThatThrownBy(() -> ResponseEncoder.encode(sum, new PromptResponse.Cards(List.of(0, 2))))
				.isInstanceOf(InvalidResponseException.class);
	}

	@Test
	void announceMaskMustMatchCount() {
		Prompt.AnnounceAttribute attribute = new Prompt.AnnounceAttribute(0, 1, ATTRIBUTE_ALL);
		assertThat(ResponseEncoder.encode(attribute, new PromptResponse.Mask(ATTRIBUTE_DARK))).hasSize(4);
		assertThatThrownBy(() -> ResponseEncoder.encode(attribute, new PromptResponse.Mask(ATTRIBUTE_DARK | ATTRIBUTE_LIGHT)))
				.isInstanceOf(InvalidResponseException.class);
	}
}
