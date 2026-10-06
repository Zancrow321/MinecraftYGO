package io.github.zancrow321.jadm.engine;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Smoke tests against the real native library. Run {@code native/build.sh} first; CI builds it for every platform
 * and fails if it is missing, while local runs without it are skipped.
 */
public class OcgCoreTest {
    private static final long[] SEED = {1, 2, 3, 4};
    private static final int TYPE_MONSTER = 0x1;
    private static final int TYPE_NORMAL = 0x10;
    private static final int TEST_CARD = 89631139; // Blue-Eyes White Dragon

    @BeforeAll
    public static void requireNatives() {
        NativeLoader.Platform platform = NativeLoader.Platform.current();
        boolean bundled = OcgCoreTest.class.getResource(
                "/natives/" + platform.directory() + "/" + platform.fileName()) != null;
        boolean inCi = System.getenv("CI") != null;
        assumeTrue(bundled || inCi || System.getProperty(NativeLoader.OVERRIDE_PROPERTY) != null,
                "ocgcore not built for " + platform.directory() + "; run native/build.sh");
    }

    @Test
    void loadsSupportedVersion() {
        assertEquals(OcgCore.SUPPORTED_MAJOR_VERSION, OcgCore.get().version().major());
    }

    @Test
    void rejectsZeroSeed() {
        DuelCreationException e = assertThrows(DuelCreationException.class, () -> OcgCore.get().createDuel(
                DuelSettings.standard(new long[4], OcgConstants.DUEL_MODE_MR1), code -> null, name -> null,
                (type, message) -> { }));
        assertEquals(DuelCreationException.Status.NULL_RNG_SEED, e.status());
    }

    @Test
    void runsDuelUntilFirstPlayerChoice() {
        List<Integer> requestedCards = new ArrayList<>();
        CardDataProvider cards = code -> {
            requestedCards.add(code);
            return new CardData(code, 0, new int[]{0x0dd}, TYPE_MONSTER | TYPE_NORMAL, 8, 0x10, 0x2000, 3000, 2500,
                    0, 0, 0);
        };

        try (OcgDuel duel = OcgCore.get().createDuel(DuelSettings.standard(SEED, OcgConstants.DUEL_MODE_MR1), cards,
                name -> null, (type, message) -> { })) {
            for (int team = 0; team < 2; team++) {
                for (int i = 0; i < 40; i++) {
                    duel.newCard(team, 0, TEST_CARD, team, OcgConstants.LOCATION_DECK, 0,
                            OcgConstants.POS_FACEDOWN_DEFENSE);
                }
            }
            assertEquals(40, duel.queryCount(0, OcgConstants.LOCATION_DECK));

            duel.start();
            OcgDuel.Status status;
            int steps = 0;
            boolean sawMessages = false;
            do {
                status = duel.process();
                sawMessages |= duel.getMessage().length > 0;
            } while (status == OcgDuel.Status.CONTINUE && ++steps < 1000);

            assertEquals(OcgDuel.Status.AWAITING, status, "duel should stop to ask a player for a choice");
            assertNotEquals(0, steps);
            assertFalse(requestedCards.isEmpty(), "core should have read card data");
            // Master Rule 1 draws on the first turn too: 5 opening cards + 1.
            assertEquals(6, duel.queryCount(0, OcgConstants.LOCATION_HAND), "opening hand plus first draw");
            assertEquals(5, duel.queryCount(1, OcgConstants.LOCATION_HAND), "opening hand");
            assertTrue(sawMessages, "core should have produced messages");
        }
    }
}
