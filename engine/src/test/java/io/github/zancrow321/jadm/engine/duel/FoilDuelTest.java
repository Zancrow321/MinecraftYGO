package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.DuelLogHandler;
import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.Ruleset;
import io.github.zancrow321.jadm.engine.ai.RandomResponder;
import io.github.zancrow321.jadm.engine.data.BoosterSets.Rarity;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.protocol.CardState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.github.zancrow321.jadm.engine.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** The foil copies in a deck keep their rarity on the board, wherever the card goes, and only those copies. */
class FoilDuelTest {
    private static final CardDatabase CARDS = CardDatabase.loadBundled();

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    /** A deck whose every copy is a foil of the given rarity. */
    private static Deck allFoil(Deck deck, Rarity rarity) {
        Map<Integer, List<Rarity>> foils = new HashMap<>();
        Stream.concat(deck.main().stream(), deck.extra().stream())
                .forEach(code -> foils.computeIfAbsent(code, c -> new ArrayList<>()).add(rarity));
        return new Deck(deck.name(), deck.main(), deck.extra(), deck.side(), foils);
    }

    private static DuelSettings settings(long seed) {
        return DuelSettings.standard(new long[]{seed, seed * 31, seed * 17, 99}, Ruleset.MODERN.flags());
    }

    @Test
    void foilDeckShowsItsRarityAndTheOtherDoesNot() {
        DuelLogHandler log = (type, message) -> { };
        try (DuelController duel = new DuelController(CARDS, new BundledScripts(), settings(5),
                allFoil(Deck.bundled("starter_yugi"), Rarity.STARLIGHT), Deck.bundled("starter_kaiba"), log)) {
            duel.advance();
            Board board = ViewCodec.decode(ViewCodec.encode(new DuelView(0, List.of("a", "b"), duel.board(),
                    List.of(), List.of(), null, 0, null, null))).board();
            assertEquals(5, board.side(0).hand().size());
            for (CardState card : board.side(0).hand()) {
                assertEquals(Rarity.STARLIGHT, card.rarity(), "foil in hand: " + card.code());
            }
            for (CardState card : board.side(1).hand()) {
                assertEquals(Rarity.COMMON, card.rarity());
            }
            for (CardState card : duel.query(0, LOCATION_EXTRA)) {
                assertEquals(Rarity.STARLIGHT, card.rarity());
            }
        }
    }

    @Test
    void onlyTheFoilCopiesOfACardShine() {
        // Three copies of the first card, one of them a Ghost Rare.
        List<Integer> main = new ArrayList<>(Deck.bundled("starter_yugi").main());
        int code = main.get(0);
        main.set(1, code);
        main.set(2, code);
        long total = main.stream().filter(c -> c == code).count();
        Deck deck = new Deck("one foil", main, List.of(), List.of(),
                Map.of(code, List.of(Rarity.GHOST)));
        DuelLogHandler log = (type, message) -> { };
        try (DuelController duel = new DuelController(CARDS, new BundledScripts(), settings(9), deck,
                Deck.bundled("starter_kaiba"), log)) {
            duel.advance();
            List<CardState> all = new ArrayList<>(duel.query(0, LOCATION_DECK));
            all.addAll(duel.query(0, LOCATION_HAND));
            long ghosts = all.stream().filter(c -> c.rarity() == Rarity.GHOST).count();
            long plain = all.stream().filter(c -> c.code() == code && c.rarity() == Rarity.COMMON).count();
            assertEquals(1, ghosts);
            assertEquals(total - 1, plain);
            assertTrue(all.stream().filter(c -> c.rarity() == Rarity.GHOST).allMatch(c -> c.code() == code));
        }
    }

    /** A tag partner's foils show once they take over, and the first duelist's stay. */
    @Test
    void tagPartnerFoilsShowWhenTheyTakeOver() {
        List<List<Deck>> teams = List.of(
                List.of(Deck.bundled("starter_yugi"), allFoil(Deck.bundled("starter_kaiba"), Rarity.SECRET)),
                List.of(Deck.bundled("starter_kaiba"), Deck.bundled("starter_yugi")));
        DuelLogHandler log = (type, message) -> { };
        RandomResponder bot = new RandomResponder(4, CARDS);
        try (DuelController duel = new DuelController(CARDS, new BundledScripts(), settings(13), teams, log)) {
            DuelController.Step step = duel.advance();
            for (int prompts = 0; prompts < 5000 && !step.finished(); prompts++) {
                if (duel.activeDuelist(0) == 1) {
                    long secret = Stream.of(duel.query(0, LOCATION_HAND), duel.query(0, LOCATION_DECK))
                            .flatMap(List::stream).filter(c -> c.rarity() == Rarity.SECRET).count();
                    assertTrue(secret > 20, "the partner's cards are foils: " + secret);
                    return;
                }
                // Before the partner takes over, team 0 has no foils.
                assertTrue(duel.query(0, LOCATION_HAND).stream().allMatch(c -> c.rarity() == Rarity.COMMON));
                duel.respond(bot.respond(step.prompt(), duel.board(), 0));
                step = duel.advance();
            }
            fail("the partner never took over");
        }
    }

    @Test
    void hiddenCardsGiveNoRarityAway() {
        DuelLogHandler log = (type, message) -> { };
        try (DuelController duel = new DuelController(CARDS, new BundledScripts(), settings(5),
                allFoil(Deck.bundled("starter_yugi"), Rarity.STARLIGHT), Deck.bundled("starter_kaiba"), log)) {
            duel.advance();
            for (CardState card : duel.board().viewedBy(1).side(0).hand()) {
                assertEquals(0, card.cover());
            }
        }
    }
}
