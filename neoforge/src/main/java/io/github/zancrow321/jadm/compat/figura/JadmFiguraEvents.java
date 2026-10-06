package io.github.zancrow321.jadm.compat.figura;

import com.mojang.datafixers.util.Pair;
import org.figuramc.figura.entries.FiguraEvent;
import org.figuramc.figura.entries.annotations.FiguraEventPlugin;
import org.figuramc.figura.lua.api.event.LuaEvent;

import java.util.Collection;
import java.util.List;

/**
 * Duel events for avatar scripts, e.g. {@code events["jadm.summon"]:register(function(card, mine) ... end)}. They fire
 * on your own avatar while you duel:
 * <ul>
 *   <li>{@code DUEL_START(opponentName)} and {@code DUEL_END(won)} ({@code nil} for a draw)</li>
 *   <li>{@code TURN_START(mine, turn)} and {@code PHASE(phaseName, myTurn)}</li>
 *   <li>{@code SUMMON(cardName, mine)} and {@code ACTIVATE(cardName, mine)}; the name is {@code nil} if hidden</li>
 *   <li>{@code ATTACK(mine, direct)} and {@code LP_CHANGE(mine, change, lifePoints)}</li>
 * </ul>
 */
@FiguraEventPlugin
public final class JadmFiguraEvents implements FiguraEvent {
    static final String ID = "jadm";
    private static final List<String> EVENTS = List.of("DUEL_START", "DUEL_END", "TURN_START", "PHASE", "SUMMON",
            "ACTIVATE", "ATTACK", "LP_CHANGE");

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public Collection<Pair<String, LuaEvent>> getEvents() {
        // Called once per avatar, so every avatar gets its own events.
        return EVENTS.stream().map(name -> Pair.of(name, new LuaEvent())).toList();
    }
}
