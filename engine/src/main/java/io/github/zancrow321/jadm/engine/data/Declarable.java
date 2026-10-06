package io.github.zancrow321.jadm.engine.data;

import io.github.zancrow321.jadm.engine.CardData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static io.github.zancrow321.jadm.engine.OcgConstants.TYPE_MONSTER;
import static io.github.zancrow321.jadm.engine.OcgConstants.TYPE_TOKEN;

/**
 * Which card names may be declared when a card says "declare a card name": the filter the core sends with the
 * prompt is a small stack program, run here exactly as ocgcore's {@code is_declarable} (playerop.cpp) does.
 */
public final class Declarable {
    private static final long ADD = 0x4000000000000000L;
    private static final long SUB = 0x4000000100000000L;
    private static final long MUL = 0x4000000200000000L;
    private static final long DIV = 0x4000000300000000L;
    private static final long AND = 0x4000000400000000L;
    private static final long OR = 0x4000000500000000L;
    private static final long NEG = 0x4000000600000000L;
    private static final long NOT = 0x4000000700000000L;
    private static final long BAND = 0x4000000800000000L;
    private static final long BOR = 0x4000000900000000L;
    private static final long BNOT = 0x4000001000000000L;
    private static final long BXOR = 0x4000001100000000L;
    private static final long LSHIFT = 0x4000001200000000L;
    private static final long RSHIFT = 0x4000001300000000L;
    private static final long ALLOW_ALIASES = 0x4000001400000000L;
    private static final long ALLOW_TOKENS = 0x4000001500000000L;
    private static final long ISCODE = 0x4000010000000000L;
    private static final long ISSETCARD = 0x4000010100000000L;
    private static final long ISTYPE = 0x4000010200000000L;
    private static final long ISRACE = 0x4000010300000000L;
    private static final long ISATTRIBUTE = 0x4000010400000000L;
    private static final long GETCODE = 0x4000010500000000L;
    private static final long GETTYPE = 0x4000010700000000L;
    private static final long GETRACE = 0x4000010800000000L;
    private static final long GETATTRIBUTE = 0x4000010900000000L;
    /** Cards the core always accepts (card.h). */
    private static final int MARINE_DOLPHIN = 78734254;
    private static final int TWINKLE_MOSS = 13857930;

    private Declarable() {
    }

    /** Whether the core would accept this card's name for a prompt with these opcodes. */
    public static boolean test(CardData cd, List<Long> opcodes) {
        Deque<Long> stack = new ArrayDeque<>();
        boolean alias = false;
        boolean token = false;
        for (long op : opcodes) {
            if (op == ALLOW_ALIASES) {
                alias = true;
            } else if (op == ALLOW_TOKENS) {
                token = true;
            } else if (op == ADD || op == SUB || op == MUL || op == DIV || op == AND || op == OR || op == BAND
                    || op == BOR || op == BXOR || op == LSHIFT || op == RSHIFT) {
                if (stack.size() >= 2) {
                    long rhs = stack.pop();
                    long lhs = stack.pop();
                    stack.push(binary(op, lhs, rhs));
                }
            } else if (op == NEG || op == NOT || op == BNOT) {
                if (!stack.isEmpty()) {
                    long val = stack.pop();
                    stack.push(op == NEG ? -val : op == NOT ? (val == 0 ? 1 : 0) : ~val);
                }
            } else if (op == ISCODE || op == ISTYPE || op == ISRACE || op == ISATTRIBUTE) {
                if (!stack.isEmpty()) {
                    long val = stack.pop();
                    stack.push(op == ISCODE ? (cd.code() == (int) val ? 1L : 0L)
                            : op == ISTYPE ? (cd.type() & val) : op == ISRACE ? (cd.race() & val)
                            : (cd.attribute() & val));
                }
            } else if (op == ISSETCARD) {
                if (!stack.isEmpty()) {
                    int setCode = (int) (long) stack.pop();
                    int type = setCode & 0xfff;
                    int subtype = setCode & 0xf000;
                    boolean found = false;
                    for (int sc : cd.setcodes()) {
                        if ((sc & 0xfff) == type && (sc & 0xf000 & subtype) == subtype) {
                            found = true;
                            break;
                        }
                    }
                    stack.push(found ? 1L : 0L);
                }
            } else if (op == GETCODE) {
                stack.push((long) cd.code());
            } else if (op == GETTYPE) {
                stack.push((long) cd.type() & 0xFFFFFFFFL);
            } else if (op == GETRACE) {
                stack.push(cd.race());
            } else if (op == GETATTRIBUTE) {
                stack.push((long) cd.attribute() & 0xFFFFFFFFL);
            } else {
                stack.push(op);
            }
        }
        if (stack.size() != 1 || stack.peek() == 0) {
            return false;
        }
        return cd.code() == MARINE_DOLPHIN || cd.code() == TWINKLE_MOSS
                || ((alias || cd.alias() == 0) && (token
                || (cd.type() & (TYPE_MONSTER | TYPE_TOKEN)) != (TYPE_MONSTER | TYPE_TOKEN)));
    }

    private static long binary(long op, long lhs, long rhs) {
        if (op == ADD) {
            return lhs + rhs;
        } else if (op == SUB) {
            return lhs - rhs;
        } else if (op == MUL) {
            return lhs * rhs;
        } else if (op == DIV) {
            return rhs == 0 ? 0 : lhs / rhs;
        } else if (op == AND) {
            return lhs != 0 && rhs != 0 ? 1 : 0;
        } else if (op == OR) {
            return lhs != 0 || rhs != 0 ? 1 : 0;
        } else if (op == BAND) {
            return lhs & rhs;
        } else if (op == BOR) {
            return lhs | rhs;
        } else if (op == BXOR) {
            return lhs ^ rhs;
        } else if (op == LSHIFT) {
            return lhs << rhs;
        }
        return lhs >> rhs;
    }
}
