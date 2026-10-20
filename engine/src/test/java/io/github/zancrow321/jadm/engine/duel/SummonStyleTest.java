package io.github.zancrow321.jadm.engine.duel;

import io.github.zancrow321.jadm.engine.DuelSettings;
import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.OcgCoreTest;
import io.github.zancrow321.jadm.engine.data.BundledScripts;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.Deck;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.text.DuelText;
import io.github.zancrow321.jadm.engine.text.PromptChoices;
import io.github.zancrow321.jadm.engine.text.PromptView;
import io.github.zancrow321.jadm.engine.protocol.MessageType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plays each kind of summon from a set-up hand and field and checks the style the host tags it with: the core sends a
 * plain Special Summon for all of them.
 */
class SummonStyleTest {
    record Scenario(String name, String lua, String action, List<Integer> prefer, List<Integer> styles) {
    }

    static final List<Scenario> SCENARIOS = List.of(
            new Scenario("fusion", """
                    Debug.AddCard(24094653,0,0,LOCATION_HAND,0,POS_FACEDOWN)
                    Debug.AddCard(46986414,0,0,LOCATION_HAND,0,POS_FACEDOWN)
                    Debug.AddCard(78193831,0,0,LOCATION_HAND,0,POS_FACEDOWN)
                    Debug.AddCard(98502113,0,0,LOCATION_EXTRA,0,POS_FACEDOWN)
                    """, "Activate Polymerization", List.of(98502113, 46986414, 78193831), List.of(SummonStyle.FUSION)),
            new Scenario("synchro", """
                    Debug.AddCard(63977008,0,0,LOCATION_MZONE,0,POS_FACEUP_ATTACK)
                    Debug.AddCard(9365703,0,0,LOCATION_MZONE,1,POS_FACEUP_ATTACK)
                    Debug.AddCard(60800381,0,0,LOCATION_EXTRA,0,POS_FACEDOWN)
                    """, "Special Summon Junk Warrior", List.of(63977008, 9365703), List.of(SummonStyle.SYNCHRO)),
            new Scenario("xyz", """
                    Debug.AddCard(69247929,0,0,LOCATION_MZONE,0,POS_FACEUP_ATTACK)
                    Debug.AddCard(69247929,0,0,LOCATION_MZONE,1,POS_FACEUP_ATTACK)
                    Debug.AddCard(71594310,0,0,LOCATION_EXTRA,0,POS_FACEDOWN)
                    """, "Special Summon Gem-Knight Pearl", List.of(69247929), List.of(SummonStyle.XYZ)),
            new Scenario("ritual", """
                    Debug.AddCard(41426869,0,0,LOCATION_HAND,0,POS_FACEDOWN)
                    Debug.AddCard(64631466,0,0,LOCATION_HAND,0,POS_FACEDOWN)
                    Debug.AddCard(69247929,0,0,LOCATION_MZONE,1,POS_FACEUP_ATTACK)
                    """, "Activate Black Illusion Ritual", List.of(64631466, 69247929), List.of(SummonStyle.RITUAL)),
            new Scenario("link", """
                    Debug.AddCard(69247929,0,0,LOCATION_MZONE,1,POS_FACEUP_ATTACK)
                    Debug.AddCard(98978921,0,0,LOCATION_EXTRA,0,POS_FACEDOWN)
                    """, "Special Summon Link Spider", List.of(69247929), List.of(SummonStyle.LINK)),
            new Scenario("pendulum", """
                    Debug.AddCard(94415058,0,0,LOCATION_PZONE,0,POS_FACEUP)
                    Debug.AddCard(20409757,0,0,LOCATION_PZONE,1,POS_FACEUP)
                    Debug.AddCard(69247929,0,0,LOCATION_HAND,0,POS_FACEDOWN)
                    Debug.AddCard(69247929,0,0,LOCATION_HAND,0,POS_FACEDOWN)
                    """, "Special Summon Stargazer", List.of(69247929),
                    List.of(SummonStyle.PENDULUM, SummonStyle.PENDULUM_MORE)),
            new Scenario("reborn", """
                    Debug.AddCard(83764718,0,0,LOCATION_HAND,0,POS_FACEDOWN)
                    Debug.AddCard(46986414,0,0,LOCATION_GRAVE,0,POS_FACEUP)
                    """, "Activate Monster Reborn", List.of(46986414), List.of(SummonStyle.NONE)));

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @AfterAll
    static void clearSetup() {
        System.clearProperty("jadm.duelSetup");
    }

    @Test
    void eachSummonGetsItsStyle() throws Exception {
        for (Scenario s : SCENARIOS) {
            assertEquals(s.styles(), run(s), s.name());
        }
    }

    /** Move reasons as the core sends them for the materials in the scenarios above, and for other moves. */
    @Test
    void materialsNameTheirSummon() {
        assertEquals(SummonStyle.FUSION, SummonStyle.material(0x40048));
        assertEquals(SummonStyle.SYNCHRO, SummonStyle.material(0x80008));
        assertEquals(SummonStyle.XYZ, SummonStyle.material(0x200008));
        assertEquals(SummonStyle.RITUAL, SummonStyle.material(0x10004A));
        assertEquals(SummonStyle.LINK, SummonStyle.material(0x10000008));
        assertEquals(SummonStyle.NONE, SummonStyle.material(0x400)); // a spell going to the Graveyard after use
        assertEquals(SummonStyle.NONE, SummonStyle.material(0x800)); // the summoned monster itself
    }

    /** Plays {@code s}'s action on the first Main Phase and returns the styles of the Special Summons it made. */
    static List<Integer> run(Scenario s) throws Exception {
        List<Integer> styles = new ArrayList<>();
        Path setup = Files.createTempFile("setup", ".lua");
        Files.writeString(setup, s.lua());
        System.setProperty("jadm.duelSetup", setup.toString());
        CardDatabase cards = CardDatabase.loadBundled();
        DuelText text = DuelText.loadBundled(cards);
        PromptChoices choices = new PromptChoices(text);
        boolean done = false;
        try (DuelController duel = new DuelController(cards, new BundledScripts(),
                DuelSettings.standard(new long[]{1, 2, 3, 4}, OcgConstants.DUEL_MODE_MR5),
                Deck.bundled("starter_yugi"), Deck.bundled("starter_kaiba"), (t, m) -> { })) {
            for (int i = 0; i < 40; i++) {
                DuelController.Step step = duel.advance();
                for (DuelMessage m : step.messages()) {
                    if (m instanceof DuelMessage.Summoning summoning && summoning.type() == MessageType.SPSUMMONING) {
                        styles.add(summoning.style());
                    }
                }
                if (step.prompt() == null) {
                    break;
                }
                PromptView view = choices.build(step.prompt(), 0);
                if (view.choices() != null && view.title().startsWith("Main Phase")) {
                    if (done) {
                        break;
                    }
                    PromptView.Choice pick = view.choices().stream().filter(c -> c.label().startsWith(s.action()))
                            .findFirst().orElse(null);
                    if (pick == null) {
                        fail(s.name() + ": no " + s.action() + " in "
                                + view.choices().stream().map(PromptView.Choice::label).toList());
                    }
                    done = true;
                    duel.respond(pick.response());
                    continue;
                }
                byte[] r;
                if (view.multi() != null) {
                    List<Integer> idx = new ArrayList<>();
                    List<Integer> codes = view.multi().codes();
                    for (int k = 0; k < view.multi().options().size() && idx.size() < Math.max(1, view.multi().min());
                         k++) {
                        if (codes != null && s.prefer().contains(codes.get(k))) {
                            idx.add(k);
                        }
                    }
                    for (int k = 0; idx.size() < Math.max(1, view.multi().min()); k++) {
                        if (!idx.contains(k)) {
                            idx.add(k);
                        }
                    }
                    r = view.multi().encode(idx);
                } else {
                    boolean unselect = view.choices().stream()
                            .filter(c -> c.label().startsWith("Unselect")).count() >= 2;
                    PromptView.Choice pick = view.choices().stream()
                            .filter(c -> c.label().equals("Don't respond") || (unselect && c.label().equals("Done")))
                            .findFirst().orElse(view.choices().stream()
                                    .filter(c -> !c.label().startsWith("Unselect") && s.prefer().contains(c.code()))
                                    .findFirst().orElse(view.choices().get(0)));
                    r = pick.response();
                }
                duel.respond(r);
            }
        }
        return styles;
    }
}
