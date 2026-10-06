package io.github.zancrow321.jadm.compat.figura;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.duel.Board;
import io.github.zancrow321.jadm.engine.duel.DuelView;
import io.github.zancrow321.jadm.engine.duel.FieldEvent;
import net.minecraft.client.Minecraft;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.lua.api.event.EventsAPI;

/**
 * Turns the local player's duel views into {@code jadm} events on their own avatar. Avatars sync what they do to
 * other players with pings, as usual in Figura. Only public information goes out: life points, summons and
 * activations of revealed cards, attacks, turns and the result, never the hand.
 */
final class FiguraBridge {
    private FiguraBridge() {
    }

    /**
     * Figura 0.1.5 on NeoForge doesn't look for {@code @FiguraEventPlugin} classes (only API, permission, screen and
     * vanilla part plugins), so the events are handed to it directly. If a later Figura finds them as well, the
     * second registration just replaces the first.
     */
    static void registerEvents() {
        EventsAPI.initEntryPoints(java.util.Set.of(new JadmFiguraEvents()));
    }

    static void onView(DuelView previous, DuelView next) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Avatar avatar = AvatarManager.getAvatarForPlayer(mc.player.getUUID());
        if (avatar == null || !avatar.loaded || avatar.luaRuntime == null) {
            return;
        }
        int me = next.you();
        int opp = 1 - me;
        boolean fresh = previous == null || previous.result() != null;
        if (fresh && next.result() == null) {
            fire(avatar, "DUEL_START", next.names().get(opp));
        }
        Board board = next.board();
        if (!fresh && previous.board().turn() != board.turn()) {
            fire(avatar, "TURN_START", board.turnPlayer() == me, board.turn());
        }
        if (!fresh && previous.board().phase() != board.phase()) {
            fire(avatar, "PHASE", JadmData.text().phase(board.phase()), board.turnPlayer() == me);
        }
        for (FieldEvent e : next.events()) {
            boolean mine = e.player() == me;
            String card = e.code() == 0 ? null : JadmData.text().cardName(e.code());
            switch (e.kind()) {
                case SUMMON -> fire(avatar, "SUMMON", card, mine);
                case ACTIVATE -> fire(avatar, "ACTIVATE", card, mine);
                case ATTACK -> fire(avatar, "ATTACK", mine, e.to().location() == 0);
                case DAMAGE -> fire(avatar, "LP_CHANGE", mine, -e.amount(), board.side(e.player()).lifePoints());
                case RECOVER -> fire(avatar, "LP_CHANGE", mine, e.amount(), board.side(e.player()).lifePoints());
                default -> {
                }
            }
        }
        if (next.result() != null && (previous == null || previous.result() == null)) {
            String result = next.result();
            fire(avatar, "DUEL_END", result.startsWith("You win") ? Boolean.TRUE
                    : result.startsWith("You lose") ? Boolean.FALSE : null);
        }
    }

    private static void fire(Avatar avatar, String event, Object... args) {
        try {
            avatar.run(JadmFiguraEvents.ID.toUpperCase() + "." + event, avatar.tick, args);
        } catch (RuntimeException e) {
            Jadm.LOGGER.debug("Figura event {} failed", event, e);
        }
    }
}
