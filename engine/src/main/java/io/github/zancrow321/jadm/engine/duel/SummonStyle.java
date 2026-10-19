package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.CardData;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.protocol.MessageType;
import io.github.zancrow321.jadm.engine.protocol.Responses;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * How a monster was Special Summoned, so clients can give each kind its own show. The core doesn't say: a summoned
 * card always moves with plain {@code REASON_SPSUMMON}. But the materials of a Fusion, Synchro, Xyz, Link or Ritual
 * Summon move right before it with that kind's reason, and a Pendulum Summon is the Special Summon command on a
 * card in a Pendulum Zone.
 */
public final class SummonStyle {
    public static final int NONE = 0;
    public static final int FUSION = 1;
    public static final int SYNCHRO = 2;
    public static final int XYZ = 3;
    public static final int RITUAL = 4;
    public static final int LINK = 5;
    /** The first monster of a Pendulum Summon. */
    public static final int PENDULUM = 6;
    /** Every further monster of the same Pendulum Summon: they come down with the first. */
    public static final int PENDULUM_MORE = 7;

    static final int REASON_MATERIAL = 0x8;
    static final int REASON_FUSION = 0x40000;
    static final int REASON_SYNCHRO = 0x80000;
    static final int REASON_RITUAL = 0x100000;
    static final int REASON_XYZ = 0x200000;
    static final int REASON_LINK = 0x10000000;

    private SummonStyle() {
    }

    /** Whether {@code style} is one of the shows (not {@link #NONE} or {@link #PENDULUM_MORE}). */
    public static boolean show(int style) {
        return style >= FUSION && style <= PENDULUM;
    }

    /** Follows a duel's messages and answers, and tags each Special Summon with its style. */
    static final class Tracker {
        private final CardDatabase cards;
        /** The style the last materials were sent for, until a summon uses them. */
        private int materials = NONE;
        /** Monsters of the Pendulum Summon in progress so far, or -1 when none is. */
        private int pendulum = -1;

        Tracker(CardDatabase cards) {
            this.cards = cards;
        }

        /** Called with every answer given to the core. */
        void responded(DuelMessage.Prompt prompt, byte[] response) {
            if (!(prompt instanceof DuelMessage.SelectIdleCmd idle) || response.length < 4) {
                return;
            }
            int value = (response[0] & 0xFF) | (response[1] & 0xFF) << 8 | (response[2] & 0xFF) << 16
                    | (response[3] & 0xFF) << 24;
            int index = value >>> 16;
            boolean pendulumZone = (value & 0xFFFF) == Responses.IDLE_SPECIAL_SUMMON
                    && index < idle.specialSummonable().size()
                    && idle.specialSummonable().get(index).loc().location() == LOCATION_SZONE;
            pendulum = pendulumZone ? 0 : -1;
        }

        /** @return {@code message}, with its style if it is a Special Summon */
        DuelMessage annotate(DuelMessage message) {
            switch (message) {
                case DuelMessage.Move m when (m.reason() & REASON_MATERIAL) != 0 -> {
                    int style = materialStyle(m.reason());
                    if (style != NONE) {
                        materials = style;
                    }
                }
                case DuelMessage.Summoning m when m.type() == MessageType.SPSUMMONING -> {
                    int style = style(m.code());
                    materials = NONE;
                    return style == NONE ? m : m.withStyle(style);
                }
                case DuelMessage.Summoning m -> materials = NONE;
                case DuelMessage.Summoned m when m.type() == MessageType.SPSUMMONED -> pendulum = -1;
                case DuelMessage.NewPhase m -> {
                    materials = NONE;
                    pendulum = -1;
                }
                default -> {
                }
            }
            return message;
        }

        private int style(int code) {
            CardData card = code == 0 ? null : cards.get(code);
            int type = card == null ? 0 : card.type();
            int fits = switch (materials) {
                case FUSION -> type & TYPE_FUSION;
                case SYNCHRO -> type & TYPE_SYNCHRO;
                case XYZ -> type & TYPE_XYZ;
                case LINK -> type & TYPE_LINK;
                case RITUAL -> type & TYPE_RITUAL;
                default -> 0;
            };
            if (fits != 0) {
                return materials;
            }
            if (pendulum >= 0) {
                return pendulum++ == 0 ? PENDULUM : PENDULUM_MORE;
            }
            return NONE;
        }

        private static int materialStyle(int reason) {
            if ((reason & REASON_FUSION) != 0) {
                return FUSION;
            }
            if ((reason & REASON_SYNCHRO) != 0) {
                return SYNCHRO;
            }
            if ((reason & REASON_XYZ) != 0) {
                return XYZ;
            }
            if ((reason & REASON_LINK) != 0) {
                return LINK;
            }
            if ((reason & REASON_RITUAL) != 0) {
                return RITUAL;
            }
            return NONE;
        }
    }
}
