package io.github.zancrow321.jadm.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;
import io.github.zancrow321.jadm.api.event.DuelEndEvent;
import io.github.zancrow321.jadm.api.event.DuelStartEvent;
import io.github.zancrow321.jadm.api.event.PackOpenEvent;
import io.github.zancrow321.jadm.api.event.RankChangeEvent;
import io.github.zancrow321.jadm.api.event.SetCompleteEvent;
import io.github.zancrow321.jadm.api.event.TournamentEndEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Makes the mod scriptable from KubeJS server scripts: the {@code JadmEvents} events and the {@code Jadm} helpers.
 * KubeJS loads it from {@code kubejs.plugins.txt}, so nothing here is touched without KubeJS.
 *
 * <pre>{@code
 * JadmEvents.duelEnd(event => {
 *   event.winners.forEach(player => {
 *     Jadm.giveDuelPoints(player, 25)
 *     event.addNote(player, '+25 DP from the server')
 *   })
 * })
 * }</pre>
 */
public final class JadmKubeJSPlugin implements KubeJSPlugin {
    static final EventGroup GROUP = EventGroup.of("JadmEvents");
    /** Before a duel starts; {@code event.cancel()} stops it. */
    static final EventHandler DUEL_START = GROUP.server("duelStart", () -> JadmKubeEvents.DuelStart.class)
            .hasResult();
    static final EventHandler DUEL_END = GROUP.server("duelEnd", () -> JadmKubeEvents.DuelEnd.class);
    static final EventHandler PACK_OPENED = GROUP.server("packOpened", () -> JadmKubeEvents.PackOpened.class);
    static final EventHandler TOURNAMENT_END = GROUP.server("tournamentEnd",
            () -> JadmKubeEvents.TournamentEnd.class);
    static final EventHandler RANK_CHANGED = GROUP.server("rankChanged", () -> JadmKubeEvents.RankChanged.class);
    static final EventHandler SET_COMPLETED = GROUP.server("setCompleted", () -> JadmKubeEvents.SetCompleted.class);

    @Override
    public void init() {
        NeoForge.EVENT_BUS.addListener(DuelStartEvent.class, e -> {
            if (DUEL_START.hasListeners()) {
                post(DUEL_START, new JadmKubeEvents.DuelStart(e)).applyCancel(e);
            }
        });
        NeoForge.EVENT_BUS.addListener(DuelEndEvent.class, e -> {
            if (DUEL_END.hasListeners()) {
                post(DUEL_END, new JadmKubeEvents.DuelEnd(e));
            }
        });
        NeoForge.EVENT_BUS.addListener(PackOpenEvent.class, e -> {
            if (PACK_OPENED.hasListeners()) {
                post(PACK_OPENED, new JadmKubeEvents.PackOpened(e));
            }
        });
        NeoForge.EVENT_BUS.addListener(TournamentEndEvent.class, e -> {
            if (TOURNAMENT_END.hasListeners()) {
                post(TOURNAMENT_END, new JadmKubeEvents.TournamentEnd(e));
            }
        });
        NeoForge.EVENT_BUS.addListener(RankChangeEvent.class, e -> {
            if (RANK_CHANGED.hasListeners()) {
                post(RANK_CHANGED, new JadmKubeEvents.RankChanged(e));
            }
        });
        NeoForge.EVENT_BUS.addListener(SetCompleteEvent.class, e -> {
            if (SET_COMPLETED.hasListeners()) {
                post(SET_COMPLETED, new JadmKubeEvents.SetCompleted(e));
            }
        });
    }

    private static dev.latvian.mods.kubejs.event.EventResult post(EventHandler handler, KubeEvent event) {
        return handler.post(ScriptType.SERVER, event);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("Jadm", JadmBindings.class);
    }
}
