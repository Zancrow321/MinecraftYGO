package io.github.zancrow321.jadm.tournament;

import io.github.zancrow321.jadm.engine.tournament.Draft;

import java.util.ArrayList;
import java.util.List;

/**
 * What the draft and deck building window shows one duelist of a Sealed or Draft tournament: sent as JSON whenever
 * it changes for them.
 */
public final class LimitedView {
    public String tournament;
    /** When the tournament opened, so the window knows a new one from the last */
    public long id;
    /** "sealed" or "draft" */
    public String mode;
    /** "draft" or "build" */
    public String phase;
    /** Until the pick or the deck building ends */
    public int secondsLeft;
    /** Duelists still picking from their pack, or still building */
    public int waiting;
    public int minimum;

    // Draft
    public int round;
    public int rounds;
    public int pick;
    public String setName = "";
    /** The pack in front of you */
    public List<Draft.Card> pack = new ArrayList<>();
    /** You took a card from it already and wait for the others */
    public boolean chosen;
    /** Who gets the pack next */
    public String passTo = "";

    // Building
    public List<Draft.Card> pool = new ArrayList<>();
    public List<Integer> main = new ArrayList<>();
    public List<Integer> extra = new ArrayList<>();
    public boolean built;
    public List<String> problems = new ArrayList<>();
    /** Sealed: the packs you opened and their sets, for the window to reveal them once */
    public List<List<Draft.Card>> packs = new ArrayList<>();
    public List<String> packNames = new ArrayList<>();
}
