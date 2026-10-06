package io.github.zancrow321.minecraftygo.engine.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.zancrow321.minecraftygo.engine.DuelSettings;
import io.github.zancrow321.minecraftygo.engine.OcgCoreTest;
import io.github.zancrow321.minecraftygo.engine.data.Banlist;
import io.github.zancrow321.minecraftygo.engine.data.Banlists;
import io.github.zancrow321.minecraftygo.engine.data.BundledScripts;
import io.github.zancrow321.minecraftygo.engine.data.CardDatabase;
import io.github.zancrow321.minecraftygo.engine.data.CardInfo;
import io.github.zancrow321.minecraftygo.engine.data.CardPool;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.data.DeckBuilder;
import io.github.zancrow321.minecraftygo.engine.data.DeckRules;
import io.github.zancrow321.minecraftygo.engine.data.PoolMode;
import io.github.zancrow321.minecraftygo.engine.data.TournamentDecks;
import io.github.zancrow321.minecraftygo.engine.duel.DuelController;
import io.github.zancrow321.minecraftygo.engine.protocol.DuelMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static io.github.zancrow321.minecraftygo.engine.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * NPC decks: the hand-kept tournament decks, the builder's Extra Deck, and the AI actually summoning from it.
 */
class NpcDecksTest {
    private static final CardDatabase CARDS = CardDatabase.loadBundled();

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @Test
    void everyTournamentDeckCardIsKnownAndTheDeckIsLegalInItsEra() throws Exception {
        TournamentDecks decks = TournamentDecks.loadBundled(CARDS);
        Banlists banlists = Banlists.loadBundled();
        try (var in = new InputStreamReader(Objects.requireNonNull(
                TournamentDecks.class.getResourceAsStream(TournamentDecks.RESOURCE)), StandardCharsets.UTF_8)) {
            List<JsonElement> raw = JsonParser.parseReader(in).getAsJsonArray().asList();
            assertEquals(raw.size(), decks.decks().size());
            for (int i = 0; i < raw.size(); i++) {
                TournamentDecks.Entry entry = decks.decks().get(i);
                assertEquals(count(raw.get(i), "main"), entry.main().size(), entry.id() + ": unknown card name");
                assertEquals(count(raw.get(i), "extra"), entry.extra().size(), entry.id() + ": unknown card name");
                Deck deck = entry.legal(banlists.at(entry.date()), CARDS);
                assertNotNull(deck, entry.id() + " is not legal in its era");
                // Even under today's list it stays playable, padded with copies of what is left.
                assertNotNull(entry.legal(banlists.latest(), CARDS), entry.id() + " under the latest list");
            }
        }
    }

    private static int count(JsonElement deck, String part) {
        int n = 0;
        for (JsonElement e : deck.getAsJsonObject().getAsJsonArray(part)) {
            n += Integer.parseInt(e.getAsString().split(" ", 2)[0]);
        }
        return n;
    }

    @Test
    void builtDecksUseTheNewerExtraDeckTypesAndPreferModels() {
        CardPool pool = CardPool.of(PoolMode.ALL, CARDS);
        Banlist banlist = Banlists.loadBundled().latest();
        int synchro = 0;
        int xyz = 0;
        int link = 0;
        int modeled = 0;
        int monsters = 0;
        for (long seed = 1; seed <= 30; seed++) {
            Deck deck = DeckBuilder.random("npc", seed, CARDS, pool, banlist);
            assertEquals(List.of(), DeckRules.problems(deck, CARDS, banlist), "seed " + seed);
            for (int code : deck.extra()) {
                CardInfo card = CARDS.card(code);
                synchro += card.is(TYPE_SYNCHRO) ? 1 : 0;
                xyz += card.is(TYPE_XYZ) ? 1 : 0;
                link += card.is(TYPE_LINK) ? 1 : 0;
            }
            for (int code : deck.main()) {
                if (CARDS.card(code).is(TYPE_MONSTER)) {
                    monsters++;
                    modeled += pool.models().containsKey(code) ? 1 : 0;
                }
            }
        }
        assertTrue(synchro > 0 && xyz > 0 && link > 0, synchro + " synchro, " + xyz + " xyz, " + link + " link");
        long modeledInPool = pool.monsters().stream().filter(pool.models()::containsKey).count();
        double share = (double) modeledInPool / pool.monsters().size();
        assertTrue((double) modeled / monsters > share * 1.5,
                "modeled monsters " + modeled + "/" + monsters + " vs pool share " + share);
    }

    /** Two AIs with Extra Decks under the modern rules: they finish, and they summon from the Extra Deck. */
    @Test
    void theAiSummonsFromTheExtraDeck() {
        CardPool pool = CardPool.of(PoolMode.ALL, CARDS);
        Banlist banlist = Banlists.loadBundled().latest();
        TournamentDecks tournament = TournamentDecks.loadBundled(CARDS);
        Deck teledad = tournament.decks().stream().filter(d -> d.id().equals("teledad")).findFirst().orElseThrow()
                .legal(banlist, CARDS);
        int extraSummons = 0;
        for (long seed = 1; seed <= 12; seed++) {
            Deck first = seed % 3 == 0 ? teledad : DeckBuilder.random("a", seed, CARDS, pool, banlist);
            Deck second = DeckBuilder.random("b", seed * 7919, CARDS, pool, banlist);
            extraSummons += play(seed, first, second);
        }
        System.out.println("extra deck summons in 12 duels: " + extraSummons);
        assertTrue(extraSummons >= 3, "only " + extraSummons + " extra deck summons");
    }

    private static int play(long seed, Deck first, Deck second) {
        DuelSettings settings = DuelSettings.standard(new long[]{seed, seed * 31, seed * 17, 99}, DUEL_MODE_MR5);
        Responder[] bots = {new DuelistAi(seed, CARDS), new DuelistAi(seed + 1000, CARDS)};
        int extraSummons = 0;
        try (DuelController duel = new DuelController(CARDS, new BundledScripts(), settings, first, second,
                (type, message) -> { })) {
            DuelController.Step step = duel.advance();
            DuelMessage.Prompt last = null;
            int attempt = 0;
            for (int prompts = 0; !step.finished(); prompts++) {
                if (prompts > 20_000) {
                    fail("seed " + seed + ": duel did not finish");
                }
                for (DuelMessage message : step.messages()) {
                    if (message instanceof DuelMessage.Move m && m.from().location() == LOCATION_EXTRA
                            && m.to().location() == LOCATION_MZONE) {
                        extraSummons++;
                    }
                }
                DuelMessage.Prompt prompt = step.prompt();
                boolean retried = step.messages().stream().anyMatch(m -> m instanceof DuelMessage.Retry);
                attempt = retried && prompt == last ? attempt + 1 : 0;
                if (attempt > 200) {
                    fail("seed " + seed + ": AI stuck on " + prompt);
                }
                last = prompt;
                duel.respond(bots[prompt.player()].respond(prompt, duel.board().viewedBy(prompt.player()), attempt));
                step = duel.advance();
            }
        }
        return extraSummons;
    }
}
