package io.github.zancrow321.minecraftygo.engine.prompt;

import io.github.zancrow321.minecraftygo.engine.data.CardData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static io.github.zancrow321.minecraftygo.engine.constants.OcgConstants.*;

/**
 * Evaluates the postfix {@code OPCODE_*} program of {@code MSG_ANNOUNCE_CARD} against a card; a port of
 * {@code is_declarable} in ygopro-core {@code playerop.cpp}.
 */
public final class AnnounceFilter {
	private static final int CARD_MARINE_DOLPHIN = 78734254;
	private static final int CARD_TWINKLE_MOSS = 13857930;

	private AnnounceFilter() {}

	public static boolean isDeclarable(CardData card, List<Long> opcodes) {
		Deque<Long> stack = new ArrayDeque<>();
		boolean allowAliases = false;
		boolean allowTokens = false;
		for (long opcode : opcodes) {
			if (opcode == OPCODE_ADD) binary(stack, (a, b) -> a + b);
			else if (opcode == OPCODE_SUB) binary(stack, (a, b) -> a - b);
			else if (opcode == OPCODE_MUL) binary(stack, (a, b) -> a * b);
			else if (opcode == OPCODE_DIV) binary(stack, (a, b) -> b == 0 ? 0 : a / b);
			else if (opcode == OPCODE_AND) binary(stack, (a, b) -> (a != 0 && b != 0) ? 1 : 0);
			else if (opcode == OPCODE_OR) binary(stack, (a, b) -> (a != 0 || b != 0) ? 1 : 0);
			else if (opcode == OPCODE_NEG) unary(stack, a -> -a);
			else if (opcode == OPCODE_NOT) unary(stack, a -> a == 0 ? 1 : 0);
			else if (opcode == OPCODE_BAND) binary(stack, (a, b) -> a & b);
			else if (opcode == OPCODE_BOR) binary(stack, (a, b) -> a | b);
			else if (opcode == OPCODE_BXOR) binary(stack, (a, b) -> a ^ b);
			else if (opcode == OPCODE_BNOT) unary(stack, a -> ~a);
			else if (opcode == OPCODE_LSHIFT) binary(stack, (a, b) -> a << b);
			else if (opcode == OPCODE_RSHIFT) binary(stack, (a, b) -> a >> b);
			else if (opcode == OPCODE_ISCODE) unary(stack, a -> card.code() == (int) a ? 1 : 0);
			else if (opcode == OPCODE_ISTYPE) unary(stack, a -> card.type() & a);
			else if (opcode == OPCODE_ISRACE) unary(stack, a -> card.race() & a);
			else if (opcode == OPCODE_ISATTRIBUTE) unary(stack, a -> card.attribute() & a);
			else if (opcode == OPCODE_GETCODE) stack.push((long) card.code());
			else if (opcode == OPCODE_GETTYPE) stack.push(Integer.toUnsignedLong(card.type()));
			else if (opcode == OPCODE_GETRACE) stack.push(card.race());
			else if (opcode == OPCODE_GETATTRIBUTE) stack.push(Integer.toUnsignedLong(card.attribute()));
			else if (opcode == OPCODE_ISSETCARD) {
				if (!stack.isEmpty()) {
					int setCode = (int) (long) stack.pop();
					int setType = setCode & 0xFFF;
					int setSubType = setCode & 0xF000;
					boolean matches = false;
					for (int sc : card.setcodes()) {
						if ((sc & 0xFFF) == setType && (sc & 0xF000 & setSubType) == setSubType) {
							matches = true;
							break;
						}
					}
					stack.push(matches ? 1L : 0L);
				}
			} else if (opcode == OPCODE_ALLOW_ALIASES) allowAliases = true;
			else if (opcode == OPCODE_ALLOW_TOKENS) allowTokens = true;
			else stack.push(opcode);
		}
		if (stack.size() != 1 || stack.peek() == 0) {
			return false;
		}
		boolean isMonsterToken = (card.type() & (TYPE_MONSTER | TYPE_TOKEN)) == (TYPE_MONSTER | TYPE_TOKEN);
		return card.code() == CARD_MARINE_DOLPHIN || card.code() == CARD_TWINKLE_MOSS
				|| ((allowAliases || card.alias() == 0) && (allowTokens || !isMonsterToken));
	}

	private interface LongBinary {
		long apply(long a, long b);
	}

	private interface LongUnary {
		long apply(long a);
	}

	private static void binary(Deque<Long> stack, LongBinary op) {
		if (stack.size() >= 2) {
			long rhs = stack.pop();
			long lhs = stack.pop();
			stack.push(op.apply(lhs, rhs));
		}
	}

	private static void unary(Deque<Long> stack, LongUnary op) {
		if (!stack.isEmpty()) {
			stack.push(op.apply(stack.pop()));
		}
	}
}
