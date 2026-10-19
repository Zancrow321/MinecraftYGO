package io.github.zancrow321.jadm.duel;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.engine.ai.DuelistAi;
import io.github.zancrow321.jadm.engine.ai.Responder;
import io.github.zancrow321.jadm.engine.ai.windbot.AstraExecutor;
import io.github.zancrow321.jadm.engine.ai.windbot.WindBotAi;
import io.github.zancrow321.jadm.engine.data.Deck;

import java.util.Locale;

/**
 * The AI for every seat no person sits in, as the {@code [ai]} server config says: WindBot with one of Astra's play
 * styles, or the older {@link DuelistAi}.
 */
public final class DuelBots {
    private static final AstraExecutor.Style[] STYLES = AstraExecutor.Style.values();

    private DuelBots() {
    }

    private static boolean windBot() {
        return "windbot".equals(JadmServerConfig.AI_ENGINE.get());
    }

    /**
     * @param styleSeed picks the style when the config says "random": the same seed, the same style
     */
    private static AstraExecutor.Style style(String configured, long styleSeed) {
        if ("random".equals(configured)) {
            return STYLES[(int) Math.floorMod(styleSeed, (long) STYLES.length)];
        }
        try {
            return AstraExecutor.Style.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AstraExecutor.Style.BALANCED;
        }
    }

    /**
     * An NPC duelist.
     *
     * @param deck its deck, or {@code null} in a tag duel (where it can't tell its own cards from its partner's)
     */
    public static Responder npc(long seed, long styleSeed, Deck deck) {
        if (!windBot()) {
            return new DuelistAi(seed, JadmData.cards());
        }
        return new WindBotAi(seed, JadmData.cards(), style(JadmServerConfig.AI_NPC_STYLE.get(), styleSeed),
                JadmServerConfig.AI_NPC_MISTAKES.get(), deck != null ? deck.main() : null);
    }

    /** A tournament bot or a bot seat. @param deck its deck, or {@code null} in a tag duel */
    public static Responder bot(long seed, Deck deck) {
        if (!windBot()) {
            return new DuelistAi(seed, JadmData.cards());
        }
        return new WindBotAi(seed, JadmData.cards(), style(JadmServerConfig.AI_BOT_STYLE.get(), seed),
                JadmServerConfig.AI_BOT_MISTAKES.get(), deck != null ? deck.main() : null);
    }

    /** Chooses for a player who ran out of time; it plays as well as it can. */
    public static Responder standIn(long seed, Deck deck) {
        if (!windBot()) {
            return new DuelistAi(seed, JadmData.cards());
        }
        return new WindBotAi(seed, JadmData.cards(), style(JadmServerConfig.AI_BOT_STYLE.get(), seed), 0,
                deck != null ? deck.main() : null);
    }
}
