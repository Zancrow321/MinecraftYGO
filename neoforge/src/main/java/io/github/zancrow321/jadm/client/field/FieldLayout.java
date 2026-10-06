package io.github.zancrow321.jadm.client.field;

import io.github.zancrow321.jadm.engine.protocol.Loc;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;

/**
 * Where each zone sits on the projected field, in field-local blocks: {@code x} runs to player 0's right and
 * {@code z} from player 0's end toward player 1's. The layout follows the real mat: monster row in front of the
 * spell/trap row, Field Zone and Extra Deck on the left, Graveyard and Deck on the right (from each owner's view).
 */
public final class FieldLayout {
    public static final double ZONE_WIDTH = 2.2;
    public static final double ZONE_DEPTH = 2.6;
    public static final double MONSTER_ROW = 1.75;
    public static final double SPELL_ROW = 4.55;
    public static final double CARD_WIDTH = 1.4;
    public static final double CARD_HEIGHT = 2.04;
    /** Half the field's length, used to keep clicks and rendering near the mat. */
    public static final double HALF_LENGTH = SPELL_ROW + ZONE_DEPTH / 2;
    public static final double HALF_WIDTH = 4.5 * ZONE_WIDTH;

    private FieldLayout() {
    }

    /** A zone's centre in field-local coordinates. */
    public record Slot(double x, double z) {
    }

    /** @return the centre of the zone holding {@code loc}, or {@code null} for places not on the mat (hand) */
    public static Slot slot(int controller, int location, int sequence) {
        double side = controller == 0 ? -1 : 1;  // player 0 sits at negative z
        double right = controller == 0 ? 1 : -1; // player 1 sees the field mirrored
        if ((location & ~LOCATION_OVERLAY) == LOCATION_MZONE && sequence > 4) {
            // Extra Monster Zones (Master Rule 4+) sit on the centre line.
            return new Slot(right * (sequence == 5 ? -1 : 1) * ZONE_WIDTH, 0);
        }
        Slot slot = matSlot(controller, location, sequence, side, right);
        // With Extra Monster Zones each half moves out to make room for them.
        return slot == null || shift == 0 ? slot : new Slot(slot.x(), slot.z() + side * shift);
    }

    /** How far each half of the mat moves out from the centre line when the Extra Monster Zones are there. */
    private static final double EMZ_SHIFT = 1.0;
    private static double shift;

    /** Half the field's length for the current rules. */
    public static double halfLength() {
        return HALF_LENGTH + shift;
    }

    private static Slot matSlot(int controller, int location, int sequence, double side, double right) {
        return switch (location & ~LOCATION_OVERLAY) {
            case LOCATION_MZONE -> new Slot(right * (sequence - 2) * ZONE_WIDTH, side * MONSTER_ROW);
            case LOCATION_SZONE -> switch (sequence) {
                case 5 -> new Slot(right * -3 * ZONE_WIDTH, side * MONSTER_ROW); // Field Zone
                case 6, 7 -> new Slot(right * (sequence == 6 ? -4 : 4) * ZONE_WIDTH, side * SPELL_ROW);
                default -> new Slot(right * (sequence - 2) * ZONE_WIDTH, side * SPELL_ROW);
            };
            case LOCATION_GRAVE -> new Slot(right * 3 * ZONE_WIDTH, side * MONSTER_ROW);
            case LOCATION_DECK -> new Slot(right * 3 * ZONE_WIDTH, side * SPELL_ROW);
            case LOCATION_EXTRA -> new Slot(right * -3 * ZONE_WIDTH, side * SPELL_ROW);
            case LOCATION_REMOVED -> new Slot(right * 4 * ZONE_WIDTH, side * MONSTER_ROW);
            default -> null;
        };
    }

    public static Slot slot(Loc loc) {
        return slot(loc.controller(), loc.location(), loc.sequence());
    }

    /** Every zone drawn on the mat, as (controller, location, sequence), for the current duel's rules. */
    public static int[][] ZONES = zones(false, false);
    private static int rules = -1;

    /**
     * Matches the zones to the duel's rules: the Extra Monster Zones (Master Rule 4 and later, shared, so listed
     * once as player 0's) and Master Rule 3's own Pendulum Zones at the outer ends of the spell/trap row.
     */
    public static void use(boolean extraMonsterZones, boolean separatePendulumZones) {
        int key = (extraMonsterZones ? 1 : 0) | (separatePendulumZones ? 2 : 0);
        if (key != rules) {
            rules = key;
            ZONES = zones(extraMonsterZones, separatePendulumZones);
            shift = extraMonsterZones ? EMZ_SHIFT : 0;
        }
    }

    private static int[][] zones(boolean emz, boolean pendulum) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int p = 0; p < 2; p++) {
            for (int s = 0; s < 5; s++) {
                out.add(new int[]{p, LOCATION_MZONE, s});
                out.add(new int[]{p, LOCATION_SZONE, s});
            }
            out.add(new int[]{p, LOCATION_SZONE, 5});
            if (pendulum) {
                out.add(new int[]{p, LOCATION_SZONE, 6});
                out.add(new int[]{p, LOCATION_SZONE, 7});
            }
            out.add(new int[]{p, LOCATION_GRAVE, 0});
            out.add(new int[]{p, LOCATION_DECK, 0});
            out.add(new int[]{p, LOCATION_EXTRA, 0});
            out.add(new int[]{p, LOCATION_REMOVED, 0});
        }
        if (emz) {
            out.add(new int[]{0, LOCATION_MZONE, 5});
            out.add(new int[]{0, LOCATION_MZONE, 6});
        }
        return out.toArray(int[][]::new);
    }

    /**
     * The same Extra Monster Zone as player 0 sees it: player 1's zone 5 is player 0's zone 6 and the other way
     * round. Every other place is returned as it is.
     */
    public static Loc shared(Loc loc) {
        if (loc != null && loc.controller() == 1 && (loc.location() & ~LOCATION_OVERLAY) == LOCATION_MZONE
                && loc.sequence() >= 5) {
            return new Loc(0, loc.location(), 11 - loc.sequence(), loc.position());
        }
        return loc;
    }

    /** @return the zone under a field-local point, or {@code null} */
    public static Loc zoneAt(double x, double z) {
        for (int[] zone : ZONES) {
            Slot s = slot(zone[0], zone[1], zone[2]);
            if (Math.abs(x - s.x()) <= ZONE_WIDTH / 2 - 0.05 && Math.abs(z - s.z()) <= ZONE_DEPTH / 2 - 0.05) {
                return new Loc(zone[0], zone[1], zone[2], 0);
            }
        }
        return null;
    }

    /**
     * Whether two places are the same zone (positions and overlay flags ignored; piles match any sequence, hand
     * cards only themselves).
     */
    public static boolean sameZone(Loc a, Loc b) {
        if (a == null || b == null) {
            return false;
        }
        a = shared(a);
        b = shared(b);
        if (a.controller() != b.controller()) {
            return false;
        }
        int la = a.location() & ~LOCATION_OVERLAY;
        int lb = b.location() & ~LOCATION_OVERLAY;
        if (la != lb) {
            return false;
        }
        return (la != LOCATION_MZONE && la != LOCATION_SZONE && la != LOCATION_HAND) || a.sequence() == b.sequence();
    }
}
