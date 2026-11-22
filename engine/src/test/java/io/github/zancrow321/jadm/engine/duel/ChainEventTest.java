package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import io.github.zancrow321.jadm.engine.protocol.MessageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The chain's messages become field events, so a client can show the chain links resolving. */
class ChainEventTest {
    @Test
    void activationsCarryTheirChainLink() {
        FieldEvent e = FieldEvent.of(new DuelMessage.Chaining(44095762, new Loc(1, 0x08, 2, 0x1), 1, 0x08, 2, 0, 2),
                0);
        assertEquals(FieldEvent.Kind.ACTIVATE, e.kind());
        assertEquals(44095762, e.code());
        assertEquals(1, e.player());
        assertEquals(2, e.amount());
    }

    @Test
    void resolvingNegatingAndTheEndAreEvents() {
        assertEquals(FieldEvent.Kind.CHAIN_SOLVING,
                FieldEvent.of(new DuelMessage.ChainEvent(MessageType.CHAIN_SOLVING, 2), 0).kind());
        assertEquals(2, FieldEvent.of(new DuelMessage.ChainEvent(MessageType.CHAIN_SOLVING, 2), 0).amount());
        assertEquals(FieldEvent.Kind.CHAIN_SOLVED,
                FieldEvent.of(new DuelMessage.ChainEvent(MessageType.CHAIN_SOLVED, 1), 0).kind());
        assertEquals(FieldEvent.Kind.CHAIN_NEGATED,
                FieldEvent.of(new DuelMessage.ChainEvent(MessageType.CHAIN_NEGATED, 1), 0).kind());
        assertEquals(FieldEvent.Kind.CHAIN_NEGATED,
                FieldEvent.of(new DuelMessage.ChainEvent(MessageType.CHAIN_DISABLED, 1), 0).kind());
        assertEquals(FieldEvent.Kind.CHAIN_END, FieldEvent.of(new DuelMessage.ChainEnd(), 0).kind());
        // Adding a link is told by its activation already.
        assertNull(FieldEvent.of(new DuelMessage.ChainEvent(MessageType.CHAINED, 1), 0));
    }
}
