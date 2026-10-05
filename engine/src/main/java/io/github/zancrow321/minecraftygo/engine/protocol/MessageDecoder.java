package io.github.zancrow321.minecraftygo.engine.protocol;

import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage.*;

import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static io.github.zancrow321.minecraftygo.engine.protocol.MessageType.*;

/**
 * Decodes the buffer returned by {@code OCG_DuelGetMessage}.
 *
 * <p>The buffer is a sequence of frames: {@code u32 size} (type byte + payload), {@code u8 type}, payload. All
 * integers are little-endian. Payload layouts follow OCG-Core v11; widths differ between messages (for example
 * sequences are u8 in some lists and u32 in others), so each layout is spelled out below.
 */
public final class MessageDecoder {
    private MessageDecoder() {
    }

    public static List<DuelMessage> decodeAll(byte[] buffer) {
        List<DuelMessage> messages = new ArrayList<>();
        ByteReader frames = new ByteReader(buffer);
        int offset = 0;
        while (frames.remaining() >= 4) {
            int size = frames.i32();
            offset += 4;
            if (size <= 0 || size > frames.remaining()) {
                messages.add(new Malformed(-1, Arrays.copyOfRange(buffer, offset - 4, buffer.length),
                        "bad frame size " + size));
                break;
            }
            int type = Byte.toUnsignedInt(buffer[offset]);
            messages.add(decode(type, Arrays.copyOfRange(buffer, offset + 1, offset + size)));
            frames.skip(size);
            offset += size;
        }
        return messages;
    }

    public static DuelMessage decode(int type, byte[] payload) {
        ByteReader r = new ByteReader(payload);
        DuelMessage message;
        try {
            message = read(type, r);
        } catch (BufferUnderflowException | IndexOutOfBoundsException | IllegalArgumentException e) {
            return new Malformed(type, payload, "payload too short: " + e);
        }
        if (!(message instanceof Unhandled) && r.remaining() != 0) {
            return new Malformed(type, payload, r.remaining() + " unread bytes");
        }
        return message;
    }

    /** Only the team matters; the new hand and Extra Deck are read from the board afterwards. */
    private static DuelMessage tagSwap(ByteReader r) {
        TagSwap swap = new TagSwap(r.u8());
        r.bytes(r.remaining());
        return swap;
    }

    private static DuelMessage read(int type, ByteReader r) {
        return switch (type) {
            case RETRY -> new Retry();
            case HINT -> new Hint(r.u8(), r.u8(), r.u64());
            case WIN -> new Win(r.u8(), r.u8());

            case SELECT_BATTLECMD -> {
                int player = r.u8();
                List<Activatable> chains = list(r, () -> new Activatable(new CardRef(r.i32(), r.loc6()), r.u64(),
                        r.u8()));
                List<Attacker> attackers = list(r, () -> new Attacker(new CardRef(r.i32(), r.loc3()), r.bool()));
                yield new SelectBattleCmd(player, chains, attackers, r.bool(), r.bool());
            }
            case SELECT_IDLECMD -> {
                int player = r.u8();
                List<CardRef> summonable = list(r, () -> new CardRef(r.i32(), r.loc6()));
                List<CardRef> special = list(r, () -> new CardRef(r.i32(), r.loc6()));
                List<CardRef> reposition = list(r, () -> new CardRef(r.i32(), r.loc3()));
                List<CardRef> monsterSet = list(r, () -> new CardRef(r.i32(), r.loc6()));
                List<CardRef> spellSet = list(r, () -> new CardRef(r.i32(), r.loc6()));
                List<Activatable> activatable = list(r, () -> new Activatable(new CardRef(r.i32(), r.loc6()),
                        r.u64(), r.u8()));
                yield new SelectIdleCmd(player, summonable, special, reposition, monsterSet, spellSet, activatable,
                        r.bool(), r.bool(), r.bool());
            }
            case SELECT_EFFECTYN -> new SelectEffectYesNo(r.u8(), new CardRef(r.i32(), r.loc()), r.u64());
            case SELECT_YESNO -> new SelectYesNo(r.u8(), r.u64());
            case SELECT_OPTION -> {
                int player = r.u8();
                int count = r.u8();
                List<Long> options = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    options.add(r.u64());
                }
                yield new SelectOption(player, options);
            }
            case SELECT_CARD -> {
                int player = r.u8();
                boolean cancelable = r.bool();
                int min = r.i32();
                int max = r.i32();
                yield new SelectCard(player, cancelable, min, max, list(r, () -> new CardRef(r.i32(), r.loc())));
            }
            case SELECT_CHAIN -> {
                int player = r.u8();
                int special = r.u8();
                boolean forced = r.bool();
                r.i32(); // hint timing for the chooser
                r.i32(); // hint timing for the opponent
                yield new SelectChain(player, special, forced, list(r, () -> new Activatable(
                        new CardRef(r.i32(), r.loc()), r.u64(), r.u8())));
            }
            case SELECT_PLACE, SELECT_DISFIELD -> new SelectPlace(r.u8(), r.u8(), r.i32(), type == SELECT_DISFIELD);
            case SELECT_POSITION -> new SelectPosition(r.u8(), r.i32(), r.u8());
            case SELECT_TRIBUTE -> {
                int player = r.u8();
                boolean cancelable = r.bool();
                int min = r.i32();
                int max = r.i32();
                yield new SelectTribute(player, cancelable, min, max, list(r, () -> new TributeCandidate(
                        new CardRef(r.i32(), r.loc6()), r.u8())));
            }
            case SORT_CHAIN, SORT_CARD -> {
                int player = r.u8();
                // Location is a full u32 in this message.
                yield new SortCards(player, list(r, () -> {
                    int code = r.i32();
                    int controller = r.u8();
                    int location = r.i32();
                    int sequence = r.i32();
                    return new CardRef(code, new Loc(controller, location, sequence, 0));
                }), type == SORT_CHAIN);
            }
            case SELECT_COUNTER -> {
                int player = r.u8();
                int counterType = r.u16();
                int count = r.u16();
                yield new SelectCounter(player, counterType, count, list(r, () -> new CounterCandidate(
                        new CardRef(r.i32(), r.loc3()), r.u16())));
            }
            case SELECT_SUM -> {
                int player = r.u8();
                boolean atLeast = r.u8() == 1;
                int target = r.i32();
                int min = r.i32();
                int max = r.i32();
                List<SumCandidate> must = list(r, () -> new SumCandidate(new CardRef(r.i32(), r.loc()), r.i32()));
                List<SumCandidate> selectable = list(r, () -> new SumCandidate(new CardRef(r.i32(), r.loc()),
                        r.i32()));
                yield new SelectSum(player, atLeast, target, min, max, must, selectable);
            }
            case SELECT_UNSELECT_CARD -> {
                int player = r.u8();
                boolean finishable = r.bool();
                boolean cancelable = r.bool();
                int min = r.i32();
                int max = r.i32();
                List<CardRef> select = list(r, () -> new CardRef(r.i32(), r.loc()));
                List<CardRef> unselect = list(r, () -> new CardRef(r.i32(), r.loc()));
                yield new SelectUnselectCard(player, finishable, cancelable, min, max, select, unselect);
            }
            case ROCK_PAPER_SCISSORS -> new RockPaperScissors(r.u8());
            case ANNOUNCE_RACE -> new AnnounceRace(r.u8(), r.u8(), r.u64());
            case ANNOUNCE_ATTRIB -> new AnnounceAttribute(r.u8(), r.u8(), r.i32());
            case ANNOUNCE_CARD, ANNOUNCE_NUMBER -> {
                int player = r.u8();
                int count = r.u8();
                List<Long> values = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    values.add(r.u64());
                }
                yield type == ANNOUNCE_CARD ? new AnnounceCard(player, values) : new AnnounceNumber(player, values);
            }

            case CONFIRM_DECKTOP, CONFIRM_CARDS, CONFIRM_EXTRATOP -> {
                int player = r.u8();
                yield new ConfirmCards(type, player, list(r, () -> new CardRef(r.i32(), r.loc6())));
            }
            case SHUFFLE_DECK -> new ShuffleDeck(r.u8());
            case SHUFFLE_HAND, SHUFFLE_EXTRA -> {
                int player = r.u8();
                yield new ShuffleHand(type, player, list(r, r::i32));
            }
            case DECK_TOP -> new DeckTop(r.u8(), r.i32(), r.i32(), r.i32());
            case NEW_TURN -> new NewTurn(r.u8());
            case TAG_SWAP -> tagSwap(r);
            case NEW_PHASE -> new NewPhase(r.u16());
            case MOVE -> new Move(r.i32(), r.loc(), r.loc(), r.i32());
            case POS_CHANGE -> {
                int code = r.i32();
                int controller = r.u8();
                int location = r.u8();
                int sequence = r.u8();
                int previous = r.u8();
                int current = r.u8();
                yield new PositionChange(code, new Loc(controller, location, sequence, current), previous);
            }
            case SET -> new DuelMessage.Set(r.i32(), r.loc());
            case SWAP -> new Swap(new CardRef(r.i32(), r.loc()), new CardRef(r.i32(), r.loc()));
            case FIELD_DISABLED -> new FieldDisabled(r.i32());
            case SUMMONING, SPSUMMONING, FLIPSUMMONING -> new Summoning(type, r.i32(), r.loc());
            case SUMMONED, SPSUMMONED, FLIPSUMMONED -> new Summoned(type);
            case CHAINING -> new Chaining(r.i32(), r.loc(), r.u8(), r.u8(), r.i32(), r.u64(), r.i32());
            case CHAINED, CHAIN_SOLVING, CHAIN_SOLVED, CHAIN_NEGATED, CHAIN_DISABLED -> new ChainEvent(type, r.u8());
            case CHAIN_END -> new ChainEnd();
            case CARD_SELECTED, BECOME_TARGET -> new CardsHighlighted(type, list(r, r::loc));
            case RANDOM_SELECTED -> {
                r.u8(); // player
                yield new CardsHighlighted(type, list(r, r::loc));
            }
            case DRAW -> {
                int player = r.u8();
                int count = r.i32();
                List<Integer> codes = new ArrayList<>(count);
                List<Integer> positions = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    codes.add(r.i32());
                    positions.add(r.i32());
                }
                yield new Draw(player, codes, positions);
            }
            case DAMAGE, RECOVER, LPUPDATE, PAY_LPCOST -> new LifePoints(type, r.u8(), r.i32());
            case EQUIP -> new Equip(r.loc(), r.loc());
            case CARD_TARGET, CANCEL_TARGET -> new CardTarget(type, r.loc(), r.loc());
            case ADD_COUNTER, REMOVE_COUNTER -> {
                int counterType = r.u16();
                Loc loc = r.loc3();
                yield new Counter(type, counterType, loc, r.u16());
            }
            case ATTACK -> new Attack(r.loc(), r.loc());
            case BATTLE -> new Battle(r.loc(), r.i32(), r.i32(), r.bool(), r.loc(), r.i32(), r.i32(), r.bool());
            case ATTACK_DISABLED, DAMAGE_STEP_START, DAMAGE_STEP_END -> new BattleEvent(type);
            case MISSED_EFFECT -> new MissedEffect(r.loc(), r.i32());
            case TOSS_COIN, TOSS_DICE -> {
                int player = r.u8();
                int count = r.u8();
                List<Integer> results = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    results.add(r.u8());
                }
                yield new Toss(type, player, results);
            }
            case HAND_RES -> {
                int hands = r.u8();
                yield new HandResult(hands & 0x3, (hands >> 2) & 0x3);
            }
            default -> new Unhandled(type, r.bytes(r.remaining()));
        };
    }

    /** Reads a {@code u32} count followed by that many entries. */
    private static <T> List<T> list(ByteReader r, Supplier<T> entry) {
        int count = r.i32();
        if (count < 0 || count > r.remaining()) {
            throw new IllegalArgumentException("bad list count " + count);
        }
        List<T> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(entry.get());
        }
        return out;
    }
}
