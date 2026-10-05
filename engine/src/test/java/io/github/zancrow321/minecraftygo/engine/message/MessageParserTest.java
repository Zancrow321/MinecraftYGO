package io.github.zancrow321.minecraftygo.engine.message;

import io.github.zancrow321.minecraftygo.engine.prompt.CardRef;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.wire.LocInfo;
import io.github.zancrow321.minecraftygo.engine.wire.WireFormatException;
import io.github.zancrow321.minecraftygo.engine.wire.WireWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageParserTest {
	private static byte[] frame(int type, WireWriter payload) {
		byte[] body = payload.toByteArray();
		return new WireWriter().i32(body.length + 1).u8(type).bytes(body).toByteArray();
	}

	private static byte[] concat(byte[]... parts) {
		WireWriter out = new WireWriter();
		for (byte[] part : parts) {
			out.bytes(part);
		}
		return out.toByteArray();
	}

	@Test
	void parsesSeveralFramedMessages() {
		byte[] buffer = concat(
				frame(MSG_NEW_TURN, new WireWriter().u8(1)),
				frame(MSG_NEW_PHASE, new WireWriter().u16(PHASE_MAIN1)),
				frame(MSG_WIN, new WireWriter().u8(0).u8(1)),
				frame(MSG_CHAIN_END, new WireWriter()));
		List<CoreMessage> messages = MessageParser.parse(buffer);
		assertThat(messages).containsExactly(
				new Event.NewTurn(1),
				new Event.NewPhase(PHASE_MAIN1),
				new Event.Win(0, 1),
				new Event.ChainEnd());
	}

	@Test
	void parsesIdleCommandWithMixedSequenceWidths() {
		WireWriter w = new WireWriter().u8(0);
		w.i32(1).i32(89631139).u8(0).u8(LOCATION_HAND).i32(2);          // summonable (u32 seq)
		w.i32(0);                                                         // special summonable
		w.i32(1).i32(46986414).u8(0).u8(LOCATION_MZONE).u8(3);          // repositionable (u8 seq)
		w.i32(0).i32(0);                                                  // monster set, spell set
		w.i32(1).i32(55144522).u8(0).u8(LOCATION_HAND).i32(4).i64((55144522L << 20)).u8(0); // activatable
		w.u8(1).u8(1).u8(0);
		Prompt.IdleCommand idle = (Prompt.IdleCommand) MessageParser.parse(frame(MSG_SELECT_IDLECMD, w)).getFirst();
		assertThat(idle.player()).isZero();
		assertThat(idle.summonable()).containsExactly(new CardRef(89631139, 0, LOCATION_HAND, 2, 0));
		assertThat(idle.repositionable()).containsExactly(new CardRef(46986414, 0, LOCATION_MZONE, 3, 0));
		assertThat(idle.activatable()).singleElement().satisfies(o -> {
			assertThat(o.card().code()).isEqualTo(55144522);
			assertThat(o.description()).isEqualTo(55144522L << 20);
		});
		assertThat(idle.canBattlePhase()).isTrue();
		assertThat(idle.canShuffle()).isFalse();
	}

	@Test
	void parsesSelectCardWithLocInfo() {
		WireWriter w = new WireWriter().u8(1).u8(1).i32(1).i32(2).i32(2);
		w.i32(111).locInfo(new LocInfo(1, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK));
		w.i32(222).locInfo(new LocInfo(0, LOCATION_MZONE, 4, POS_FACEDOWN_DEFENSE));
		Prompt.SelectCard select = (Prompt.SelectCard) MessageParser.parse(frame(MSG_SELECT_CARD, w)).getFirst();
		assertThat(select).isEqualTo(new Prompt.SelectCard(1, true, 1, 2, List.of(
				new CardRef(111, 1, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK),
				new CardRef(222, 0, LOCATION_MZONE, 4, POS_FACEDOWN_DEFENSE))));
	}

	@Test
	void rejectsTrailingBytes() {
		assertThatThrownBy(() -> MessageParser.parse(frame(MSG_NEW_TURN, new WireWriter().u8(1).u8(9))))
				.isInstanceOf(WireFormatException.class);
	}

	@Test
	void rejectsTruncatedFrames() {
		byte[] frame = frame(MSG_WIN, new WireWriter().u8(0).u8(1));
		byte[] truncated = java.util.Arrays.copyOf(frame, frame.length - 1);
		assertThatThrownBy(() -> MessageParser.parse(truncated)).isInstanceOf(WireFormatException.class);
	}
}
