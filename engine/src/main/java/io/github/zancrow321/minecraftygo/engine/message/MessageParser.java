package io.github.zancrow321.minecraftygo.engine.message;

import io.github.zancrow321.minecraftygo.engine.prompt.CardRef;
import io.github.zancrow321.minecraftygo.engine.prompt.ChainOption;
import io.github.zancrow321.minecraftygo.engine.prompt.Prompt;
import io.github.zancrow321.minecraftygo.engine.wire.LocInfo;
import io.github.zancrow321.minecraftygo.engine.wire.WireFormatException;
import io.github.zancrow321.minecraftygo.engine.wire.WireReader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;

/**
 * Splits an {@code OCG_DuelGetMessage} buffer ({@code [u32 length][u8 id][payload]...}) into typed messages.
 * Every parser must consume its payload exactly; anything else is reported as {@link WireFormatException} so format
 * drift between core versions is caught immediately. Messages without a dedicated parser become
 * {@link Event.Unparsed}.
 */
public final class MessageParser {
	private MessageParser() {}

	public static List<CoreMessage> parse(byte[] buffer) {
		List<CoreMessage> messages = new ArrayList<>();
		WireReader reader = new WireReader(buffer);
		while (reader.hasRemaining()) {
			int length = reader.i32();
			if (length < 1 || length > reader.remaining()) {
				throw new WireFormatException("Invalid message length " + length + " at offset " + (reader.position() - 4));
			}
			int start = reader.position();
			int type = reader.u8();
			WireReader payload = new WireReader(buffer, start + 1, length - 1);
			CoreMessage message;
			try {
				message = parseOne(type, payload);
			} catch (WireFormatException e) {
				throw new WireFormatException("Malformed message type " + type + ": " + e.getMessage(), e);
			}
			if (payload.hasRemaining()) {
				throw new WireFormatException("Message type " + type + " has " + payload.remaining() + " unparsed bytes");
			}
			messages.add(message);
			reader.skip(length - 1);
		}
		return messages;
	}

	private static CoreMessage parseOne(int type, WireReader r) {
		return switch (type) {
			case MSG_RETRY -> new Event.Retry();
			case MSG_HINT -> new Event.Hint(r.u8(), r.u8(), r.i64());
			case MSG_WIN -> new Event.Win(r.u8(), r.u8());
			case MSG_NEW_TURN -> new Event.NewTurn(r.u8());
			case MSG_NEW_PHASE -> new Event.NewPhase(r.u16());
			case MSG_HAND_RES -> {
				int packed = r.u8();
				yield new Event.HandResult(packed & 0x3, (packed >> 2) & 0x3);
			}
			case MSG_TAG_SWAP -> {
				int player = r.u8();
				int deck = r.i32();
				int extra = r.i32();
				int extraFaceUp = r.i32();
				int hand = r.i32();
				int top = r.i32();
				List<Event.CodePosition> handCards = list(r, hand, MessageParser::codePosition);
				List<Event.CodePosition> extraCards = list(r, extra, MessageParser::codePosition);
				yield new Event.TagSwap(player, deck, extra, extraFaceUp, top, handCards, extraCards);
			}
			case MSG_DRAW -> {
				int player = r.u8();
				yield new Event.Draw(player, list(r, r.i32(), MessageParser::codePosition));
			}
			case MSG_MOVE -> new Event.Move(r.i32(), r.locInfo(), r.locInfo(), r.i32());
			case MSG_POS_CHANGE -> new Event.PositionChange(r.i32(), r.u8(), r.u8(), r.u8(), r.u8(), r.u8());
			case MSG_SET -> new Event.SetCard(r.i32(), r.locInfo());
			case MSG_SWAP -> new Event.Swap(r.i32(), r.locInfo(), r.i32(), r.locInfo());
			case MSG_SHUFFLE_HAND, MSG_SHUFFLE_EXTRA -> {
				int player = r.u8();
				yield new Event.ShuffleCards(player, type == MSG_SHUFFLE_EXTRA, list(r, r.i32(), WireReader::i32));
			}
			case MSG_SHUFFLE_DECK -> new Event.ShuffleDeck(r.u8());
			case MSG_SHUFFLE_SET_CARD -> {
				int location = r.u8();
				int count = r.u8();
				List<LocInfo> cards = list(r, count, WireReader::locInfo);
				List<LocInfo> overlay = list(r, count, WireReader::locInfo);
				yield new Event.ShuffleSetCards(location, cards, overlay);
			}
			case MSG_REVERSE_DECK -> new Event.ReverseDeck();
			case MSG_DECK_TOP -> new Event.DeckTop(r.u8(), r.i32(), r.i32(), r.i32());
			case MSG_SWAP_GRAVE_DECK -> {
				int player = r.u8();
				int insertPosition = r.i32();
				int size = r.i32();
				yield new Event.SwapGraveDeck(player, insertPosition, r.bytes(size));
			}
			case MSG_REMOVE_CARDS -> new Event.RemoveCards(list(r, r.i32(), WireReader::locInfo));
			case MSG_CONFIRM_DECKTOP, MSG_CONFIRM_EXTRATOP, MSG_CONFIRM_CARDS -> {
				int player = r.u8();
				yield new Event.Confirm(type, player, list(r, r.i32(),
						in -> new Event.CodeLocation(in.i32(), in.u8(), in.u8(), in.i32())));
			}
			case MSG_SUMMONING, MSG_SPSUMMONING, MSG_FLIPSUMMONING -> new Event.Summoning(type, r.i32(), r.locInfo());
			case MSG_SUMMONED, MSG_SPSUMMONED, MSG_FLIPSUMMONED -> new Event.Summoned(type);
			case MSG_CHAINING -> new Event.Chaining(r.i32(), r.locInfo(), r.u8(), r.u8(), r.i32(), r.i64(), r.i32());
			case MSG_CHAINED, MSG_CHAIN_SOLVING, MSG_CHAIN_SOLVED, MSG_CHAIN_NEGATED, MSG_CHAIN_DISABLED ->
					new Event.ChainLink(type, r.u8());
			case MSG_CHAIN_END -> new Event.ChainEnd();
			case MSG_CARD_SELECTED, MSG_BECOME_TARGET -> new Event.CardsSelected(type, list(r, r.i32(), WireReader::locInfo));
			case MSG_RANDOM_SELECTED -> {
				int player = r.u8();
				yield new Event.RandomSelected(player, list(r, r.i32(), WireReader::locInfo));
			}
			case MSG_MISSED_EFFECT -> new Event.MissedEffect(r.locInfo(), r.i32());
			case MSG_DAMAGE, MSG_RECOVER, MSG_PAY_LPCOST, MSG_LPUPDATE -> new Event.LifePoints(type, r.u8(), r.i32());
			case MSG_EQUIP, MSG_CARD_TARGET, MSG_CANCEL_TARGET -> new Event.CardRelation(type, r.locInfo(), r.locInfo());
			case MSG_ADD_COUNTER, MSG_REMOVE_COUNTER -> new Event.Counter(type, r.u16(), r.u8(), r.u8(), r.u8(), r.u16());
			case MSG_FIELD_DISABLED -> new Event.FieldDisabled(r.i32());
			case MSG_ATTACK -> new Event.Attack(r.locInfo(), r.locInfo());
			case MSG_BATTLE -> new Event.Battle(r.locInfo(), r.i32(), r.i32(), r.bool(), r.locInfo(), r.i32(), r.i32(), r.bool());
			case MSG_ATTACK_DISABLED, MSG_DAMAGE_STEP_START, MSG_DAMAGE_STEP_END -> new Event.BattleStep(type);
			case MSG_TOSS_COIN, MSG_TOSS_DICE -> {
				int player = r.u8();
				yield new Event.Toss(type, player, list(r, r.u8(), WireReader::u8));
			}
			case MSG_CARD_HINT -> new Event.CardHint(r.locInfo(), r.u8(), r.i64());
			case MSG_PLAYER_HINT -> new Event.PlayerHint(r.u8(), r.u8(), r.i64());
			case MSG_AI_NAME, MSG_SHOW_HINT -> {
				int length = r.u16();
				String text = new String(r.bytes(length), StandardCharsets.UTF_8);
				r.u8();
				yield new Event.Text(type, text);
			}
			case MSG_MATCH_KILL -> new Event.MatchKill(r.i32());

			case MSG_SELECT_IDLECMD -> parseIdle(r);
			case MSG_SELECT_BATTLECMD -> parseBattle(r);
			case MSG_SELECT_EFFECTYN -> new Prompt.EffectYesNo(r.u8(), cardWithLoc(r), r.i64());
			case MSG_SELECT_YESNO -> new Prompt.YesNo(r.u8(), r.i64());
			case MSG_SELECT_OPTION -> {
				int player = r.u8();
				yield new Prompt.SelectOption(player, longs(r, r.u8()));
			}
			case MSG_SELECT_CARD -> {
				int player = r.u8();
				boolean cancelable = r.bool();
				int min = r.i32();
				int max = r.i32();
				yield new Prompt.SelectCard(player, cancelable, min, max, list(r, r.i32(), MessageParser::cardWithLoc));
			}
			case MSG_SELECT_TRIBUTE -> {
				int player = r.u8();
				boolean cancelable = r.bool();
				int min = r.i32();
				int max = r.i32();
				yield new Prompt.SelectTribute(player, cancelable, min, max, list(r, r.i32(), in -> {
					CardRef card = new CardRef(in.i32(), in.u8(), in.u8(), in.i32(), 0);
					return new Prompt.TributeCandidate(card, in.u8());
				}));
			}
			case MSG_SELECT_SUM -> {
				int player = r.u8();
				boolean atLeast = r.bool();
				int acc = r.i32();
				int min = r.i32();
				int max = r.i32();
				List<Prompt.SumCandidate> must = list(r, r.i32(), MessageParser::sumCandidate);
				List<Prompt.SumCandidate> selectable = list(r, r.i32(), MessageParser::sumCandidate);
				yield new Prompt.SelectSum(player, atLeast, acc, min, max, must, selectable);
			}
			case MSG_SELECT_UNSELECT_CARD -> {
				int player = r.u8();
				boolean finishable = r.bool();
				boolean cancelable = r.bool();
				int min = r.i32();
				int max = r.i32();
				List<CardRef> select = list(r, r.i32(), MessageParser::cardWithLoc);
				List<CardRef> unselect = list(r, r.i32(), MessageParser::cardWithLoc);
				yield new Prompt.SelectUnselectCard(player, finishable, cancelable, min, max, select, unselect);
			}
			case MSG_SELECT_CHAIN -> {
				int player = r.u8();
				int specialCount = r.u8();
				boolean forced = r.bool();
				int hintTiming = r.i32();
				int opponentHintTiming = r.i32();
				List<ChainOption> chains = list(r, r.i32(),
						in -> new ChainOption(cardWithLoc(in), in.i64(), in.u8()));
				yield new Prompt.SelectChain(player, specialCount, forced, hintTiming, opponentHintTiming, chains);
			}
			case MSG_SELECT_PLACE, MSG_SELECT_DISFIELD ->
					new Prompt.SelectPlace(r.u8(), r.u8(), r.i32(), type == MSG_SELECT_DISFIELD);
			case MSG_SELECT_POSITION -> new Prompt.SelectPosition(r.u8(), r.i32(), r.u8());
			case MSG_SELECT_COUNTER -> {
				int player = r.u8();
				int counterType = r.u16();
				int count = r.u16();
				yield new Prompt.SelectCounter(player, counterType, count, list(r, r.i32(), in -> {
					CardRef card = new CardRef(in.i32(), in.u8(), in.u8(), in.u8(), 0);
					return new Prompt.CounterCandidate(card, in.u16());
				}));
			}
			case MSG_SORT_CARD, MSG_SORT_CHAIN -> {
				int player = r.u8();
				yield new Prompt.SortCards(player, type == MSG_SORT_CHAIN,
						list(r, r.i32(), in -> new CardRef(in.i32(), in.u8(), in.i32(), in.i32(), 0)));
			}
			case MSG_ANNOUNCE_RACE -> new Prompt.AnnounceRace(r.u8(), r.u8(), r.i64());
			case MSG_ANNOUNCE_ATTRIB -> new Prompt.AnnounceAttribute(r.u8(), r.u8(), r.i32());
			case MSG_ANNOUNCE_CARD -> {
				int player = r.u8();
				yield new Prompt.AnnounceCard(player, longs(r, r.u8()));
			}
			case MSG_ANNOUNCE_NUMBER -> {
				int player = r.u8();
				yield new Prompt.AnnounceNumber(player, longs(r, r.u8()));
			}
			case MSG_ROCK_PAPER_SCISSORS -> new Prompt.RockPaperScissors(r.u8());
			default -> new Event.Unparsed(type, r.bytes(r.remaining()));
		};
	}

	private static Event.CodePosition codePosition(WireReader r) {
		return new Event.CodePosition(r.i32(), r.i32());
	}

	private static Prompt.IdleCommand parseIdle(WireReader r) {
		int player = r.u8();
		List<CardRef> summonable = list(r, r.i32(), MessageParser::cardWideSeq);
		List<CardRef> special = list(r, r.i32(), MessageParser::cardWideSeq);
		List<CardRef> reposition = list(r, r.i32(), MessageParser::cardNarrowSeq);
		List<CardRef> monsterSet = list(r, r.i32(), MessageParser::cardWideSeq);
		List<CardRef> spellSet = list(r, r.i32(), MessageParser::cardWideSeq);
		List<ChainOption> activate = list(r, r.i32(), in -> new ChainOption(cardWideSeq(in), in.i64(), in.u8()));
		boolean toBattle = r.bool();
		boolean toEnd = r.bool();
		boolean shuffle = r.bool();
		return new Prompt.IdleCommand(player, summonable, special, reposition, monsterSet, spellSet, activate,
				toBattle, toEnd, shuffle);
	}

	private static Prompt.BattleCommand parseBattle(WireReader r) {
		int player = r.u8();
		List<ChainOption> activate = list(r, r.i32(), in -> new ChainOption(cardWideSeq(in), in.i64(), in.u8()));
		List<Prompt.Attacker> attackers = list(r, r.i32(),
				in -> new Prompt.Attacker(cardNarrowSeq(in), in.bool()));
		boolean toMain2 = r.bool();
		boolean toEnd = r.bool();
		return new Prompt.BattleCommand(player, activate, attackers, toMain2, toEnd);
	}

	/** u32 code, u8 controller, u8 location, u32 sequence. */
	private static CardRef cardWideSeq(WireReader r) {
		return new CardRef(r.i32(), r.u8(), r.u8(), r.i32(), 0);
	}

	/** u32 code, u8 controller, u8 location, u8 sequence. */
	private static CardRef cardNarrowSeq(WireReader r) {
		return new CardRef(r.i32(), r.u8(), r.u8(), r.u8(), 0);
	}

	/** u32 code + loc_info. */
	private static CardRef cardWithLoc(WireReader r) {
		int code = r.i32();
		LocInfo loc = r.locInfo();
		return CardRef.of(code, loc);
	}

	private static Prompt.SumCandidate sumCandidate(WireReader r) {
		return new Prompt.SumCandidate(cardWithLoc(r), r.i32());
	}

	private static List<Long> longs(WireReader r, int count) {
		List<Long> values = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			values.add(r.i64());
		}
		return values;
	}

	private static <T> List<T> list(WireReader r, int count, Function<WireReader, T> element) {
		if (count < 0 || count > r.remaining()) {
			throw new WireFormatException("Implausible element count " + count);
		}
		List<T> values = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			values.add(element.apply(r));
		}
		return values;
	}
}
