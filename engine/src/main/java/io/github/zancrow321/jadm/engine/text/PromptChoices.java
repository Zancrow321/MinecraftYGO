package io.github.zancrow321.jadm.engine.text;

import io.github.zancrow321.jadm.engine.data.Declarable;

import io.github.zancrow321.jadm.engine.protocol.CardRef;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.*;
import io.github.zancrow321.jadm.engine.protocol.Responses;
import io.github.zancrow321.jadm.engine.text.PromptView.Choice;
import io.github.zancrow321.jadm.engine.text.PromptView.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds a {@link PromptView} for any prompt, so the client UI can be generic.
 */
public final class PromptChoices {
    private final DuelText text;

    public PromptChoices(DuelText text) {
        this.text = text;
    }

    /**
     * @param hint the latest SELECTMSG hint for this prompt (a description id), or 0
     */
    public PromptView build(Prompt prompt, long hint) {
        String hinted = hint != 0 ? text.description(hint) : null;
        return switch (prompt) {
            case SelectIdleCmd p -> idle(p);
            case SelectBattleCmd p -> battle(p);
            case SelectEffectYesNo p -> PromptView.choices(effectQuestion(p), yesNo(), p.card().code());
            case SelectYesNo p -> PromptView.choices(text.description(p.description()), yesNo());
            case SelectOption p -> {
                List<Choice> choices = new ArrayList<>();
                for (int i = 0; i < p.options().size(); i++) {
                    choices.add(new Choice(text.description(p.options().get(i)), Responses.index(i)));
                }
                yield PromptView.choices(or(hinted, "Select an option"), choices);
            }
            case SelectCard p -> PromptView.multi(or(hinted, "Select " + range(p.min(), p.max()) + " card(s)"),
                    cardLabels(p.cards(), p.player()), locs(p.cards()), codes(p.cards()), p.min(), p.max(),
                    Responses::cards, p.cancelable());
            case SelectTribute p -> {
                List<CardRef> cards = p.cards().stream().map(TributeCandidate::card).toList();
                yield PromptView.multi(or(hinted, "Select monsters to Tribute"), cardLabels(cards, p.player()),
                        locs(cards), codes(cards), 0, p.max(), Responses::cards, p.cancelable());
            }
            case SelectSum p -> {
                List<CardRef> cards = p.selectable().stream().map(SumCandidate::card).toList();
                yield PromptView.multi(or(hinted, "Select cards totalling " + p.target()),
                        cardLabels(cards, p.player()), locs(cards), codes(cards), 0, sumMax(p), Responses::cards,
                        false);
            }
            case SelectChain p -> chain(p);
            // The core's hint for a zone choice is the card being placed, not a text id.
            case SelectPlace p -> {
                int placed = hint != 0 && text.cards().card((int) hint) != null ? (int) hint : 0;
                yield place(p, placed != 0 ? "Choose a zone for " + text.cardName(placed) : hinted, placed);
            }
            case SelectPosition p -> {
                List<Choice> choices = new ArrayList<>();
                addPosition(choices, p.positions(), 0x1, "Face-up Attack");
                addPosition(choices, p.positions(), 0x2, "Face-down Attack");
                addPosition(choices, p.positions(), 0x4, "Face-up Defense");
                addPosition(choices, p.positions(), 0x8, "Face-down Defense");
                yield PromptView.choices("Choose a position for " + text.cardName(p.code()), choices, p.code());
            }
            case SelectUnselectCard p -> {
                List<Choice> choices = new ArrayList<>();
                for (int i = 0; i < p.selectable().size(); i++) {
                    CardRef card = p.selectable().get(i);
                    choices.add(new Choice(cardLabel(card, p.player()), Responses.toggleCard(i), card.loc(),
                            Kind.OTHER, text.cardName(card.code()), card.code()));
                }
                for (int i = 0; i < p.unselectable().size(); i++) {
                    CardRef card = p.unselectable().get(i);
                    choices.add(new Choice("Unselect " + cardLabel(card, p.player()),
                            Responses.toggleCard(p.selectable().size() + i), card.loc(), Kind.UNSELECT,
                            "Unselect", card.code()));
                }
                if (p.finishable() || p.cancelable()) {
                    choices.add(new Choice(p.finishable() ? "Done" : "Cancel", Responses.cancel()));
                }
                yield PromptView.choices(or(hinted, "Select a card"), choices);
            }
            case SortCards p -> PromptView.choices("Order the cards", List.of(
                    new Choice("Keep the current order", Responses.defaultOrder())));
            case SelectCounter p -> PromptView.choices("Remove " + p.count() + " counter(s)", List.of(
                    new Choice("Remove automatically", autoCounters(p))));
            case RockPaperScissors p -> PromptView.choices("Rock, paper, scissors!", List.of(
                    new Choice("Rock", Responses.hand(2)), new Choice("Paper", Responses.hand(3)),
                    new Choice("Scissors", Responses.hand(1))));
            case AnnounceRace p -> bits("Declare " + p.count() + " Type(s)", p.available(), p.count(), true);
            case AnnounceAttribute p -> bits("Declare " + p.count() + " Attribute(s)", p.available(), p.count(),
                    false);
            case AnnounceCard p -> {
                List<Choice> choices = new ArrayList<>();
                text.cards().all().stream().filter(c -> Declarable.test(c.data(), p.opcodes()))
                        .sorted((a, b) -> a.name().compareTo(b.name()))
                        .forEach(c -> choices.add(new Choice(c.name(), Responses.cardCode(c.code()), null,
                                Kind.OTHER, c.name(), c.code())));
                yield PromptView.choices("Declare a card name", choices);
            }
            case AnnounceNumber p -> {
                List<Choice> choices = new ArrayList<>();
                for (int i = 0; i < p.values().size(); i++) {
                    choices.add(new Choice(String.valueOf(p.values().get(i)), Responses.index(i)));
                }
                yield PromptView.choices(or(hinted, "Declare a number"), choices);
            }
        };
    }

    /**
     * "Use the effect of X?" with the effect's own text. The core's generic texts ("Activate the Trigger Effect of
     * "%ls" from [%ls]?") name the card and where it is in place of their blanks; they then say it all themselves.
     */
    private String effectQuestion(SelectEffectYesNo p) {
        String name = text.cardName(p.card().code());
        String effect = text.description(p.description());
        if (!effect.contains("%ls")) {
            return "Use the effect of " + name + "? " + effect;
        }
        effect = effect.replaceFirst("%ls", java.util.regex.Matcher.quoteReplacement(name));
        return effect.replace("%ls", text.location(p.card().loc().location()));
    }

    /**
     * How many cards a sum prompt allows. "At least" sums (Ritual tributes) have no count limit: the core sends 0 and
     * only checks that the total reaches the target without a card to spare.
     */
    private static int sumMax(SelectSum p) {
        return p.atLeast() ? p.selectable().size() : Math.max(0, p.max() - p.mustSelect().size());
    }

    private PromptView idle(SelectIdleCmd p) {
        List<Choice> c = new ArrayList<>();
        add(c, p.summonable(), "Normal Summon", Responses.IDLE_SUMMON, Kind.SUMMON);
        add(c, p.specialSummonable(), "Special Summon", Responses.IDLE_SPECIAL_SUMMON, Kind.SPECIAL_SUMMON);
        add(c, p.monsterSettable(), "Set", Responses.IDLE_SET_MONSTER, Kind.SET_MONSTER);
        add(c, p.spellSettable(), "Set", Responses.IDLE_SET_SPELL, Kind.SET_SPELL);
        activate(c, p.activatable(), "Activate", Responses.IDLE_ACTIVATE, Kind.ACTIVATE);
        add(c, p.repositionable(), "Change position", Responses.IDLE_REPOSITION, Kind.REPOSITION);
        if (p.canBattle()) {
            c.add(new Choice("Go to Battle Phase", Responses.command(Responses.IDLE_TO_BATTLE, 0), null,
                    Kind.TO_BATTLE, "Battle Phase", 0));
        }
        if (p.canEnd()) {
            c.add(new Choice("End turn", Responses.command(Responses.IDLE_TO_END, 0), null, Kind.TO_END, "End turn",
                    0));
        }
        return PromptView.choices("Main Phase: choose an action", c);
    }

    private PromptView battle(SelectBattleCmd p) {
        List<Choice> c = new ArrayList<>();
        for (int i = 0; i < p.attackers().size(); i++) {
            Attacker a = p.attackers().get(i);
            String action = a.canAttackDirectly() ? "Attack (directly)" : "Attack";
            c.add(new Choice("Attack with " + text.cardName(a.card().code())
                    + (a.canAttackDirectly() ? " (can attack directly)" : ""),
                    Responses.command(Responses.BATTLE_ATTACK, i), a.card().loc(), Kind.ATTACK, action,
                    a.card().code()));
        }
        activate(c, p.activatable(), "Activate", Responses.BATTLE_ACTIVATE, Kind.ACTIVATE);
        if (p.canMain2()) {
            c.add(new Choice("Go to Main Phase 2", Responses.command(Responses.BATTLE_TO_MAIN2, 0), null,
                    Kind.TO_MAIN2, "Main Phase 2", 0));
        }
        if (p.canEnd()) {
            c.add(new Choice("End turn", Responses.command(Responses.BATTLE_TO_END, 0), null, Kind.TO_END,
                    "End turn", 0));
        }
        return PromptView.choices("Battle Phase: choose an action", c);
    }

    private PromptView chain(SelectChain p) {
        List<Choice> c = new ArrayList<>();
        for (int i = 0; i < p.chains().size(); i++) {
            Activatable a = p.chains().get(i);
            c.add(new Choice("Chain " + text.cardName(a.card().code()) + ": " + text.description(a.description()),
                    Responses.index(i), a.card().loc(), Kind.CHAIN, text.description(a.description()),
                    a.card().code()));
        }
        if (!p.forced()) {
            c.add(new Choice("Don't respond", Responses.index(-1), null, Kind.PASS, "Don't respond", 0));
        }
        return PromptView.choices("Respond with a card effect?", c);
    }

    /** @param card the card being placed, or 0 */
    private PromptView place(SelectPlace p, String hinted, int card) {
        List<String> labels = new ArrayList<>();
        List<Responses.Zone> zones = new ArrayList<>();
        for (int bit = 0; bit < 32; bit++) {
            if ((p.blockedZones() & (1 << bit)) != 0) {
                continue;
            }
            int local = bit % 16;
            boolean own = bit < 16;
            int player = own ? p.player() : 1 - p.player();
            String side = own ? "Your " : "Opponent's ";
            if (local <= 4) {
                zones.add(new Responses.Zone(player, 0x04, local));
                labels.add(side + "Monster Zone " + (local + 1));
            } else if (local <= 6) {
                zones.add(new Responses.Zone(player, 0x04, local));
                labels.add(side + "Extra Monster Zone " + (local - 4));
            } else if (local >= 8 && local <= 12) {
                zones.add(new Responses.Zone(player, 0x08, local - 8));
                labels.add(side + "Spell & Trap Zone " + (local - 7));
            } else if (local == 13) {
                zones.add(new Responses.Zone(player, 0x08, 5));
                labels.add(side + "Field Zone");
            } else if (local >= 14) {
                zones.add(new Responses.Zone(player, 0x08, local - 8));
                labels.add(side + "Pendulum Zone " + (local - 13));
            }
        }
        String title = or(hinted, p.disableField() ? "Choose zone(s) to disable" : "Choose a zone");
        if (p.count() == 1) {
            List<Choice> choices = new ArrayList<>();
            for (int i = 0; i < zones.size(); i++) {
                Responses.Zone z = zones.get(i);
                choices.add(new Choice(labels.get(i), Responses.zones(List.of(z)),
                        new Loc(z.player(), z.location(), z.sequence(), 0), Kind.PLACE, labels.get(i), 0));
            }
            return PromptView.choices(title, choices, card);
        }
        return PromptView.multi(title, labels,
                zones.stream().map(z -> new Loc(z.player(), z.location(), z.sequence(), 0)).toList(), p.count(),
                p.count(), selected -> Responses.zones(selected.stream().map(zones::get).toList()), false);
    }

    private PromptView bits(String title, long available, int count, boolean race) {
        List<String> labels = new ArrayList<>();
        List<Long> bits = new ArrayList<>();
        for (int bit = 0; bit < 64; bit++) {
            if ((available & (1L << bit)) != 0) {
                bits.add(1L << bit);
                labels.add((race ? "Type #" : "Attribute #") + (bit + 1));
            }
        }
        return PromptView.multi(title, labels, Collections.nCopies(labels.size(), null), count, count, selected -> {
            long mask = 0;
            for (int i : selected) {
                mask |= bits.get(i);
            }
            return race ? Responses.race(mask) : Responses.attribute((int) mask);
        }, false);
    }

    private void add(List<Choice> choices, List<CardRef> cards, String verb, int type, Kind kind) {
        for (int i = 0; i < cards.size(); i++) {
            CardRef card = cards.get(i);
            String label = (kind == Kind.REPOSITION ? "Change position of " : verb + " ") + text.cardName(card.code());
            choices.add(new Choice(label, Responses.command(type, i), card.loc(), kind, verb, card.code()));
        }
    }

    private void activate(List<Choice> choices, List<Activatable> cards, String verb, int type, Kind kind) {
        for (int i = 0; i < cards.size(); i++) {
            Activatable a = cards.get(i);
            String effect = text.description(a.description());
            choices.add(new Choice(verb + " " + text.cardName(a.card().code()) + ": " + effect,
                    Responses.command(type, i), a.card().loc(), kind,
                    effect.equals(verb) ? verb : verb + ": " + effect, a.card().code()));
        }
    }

    private static List<Integer> codes(List<CardRef> cards) {
        return cards.stream().map(CardRef::code).toList();
    }

    private static List<Loc> locs(List<CardRef> cards) {
        return cards.stream().map(CardRef::loc).toList();
    }

    private List<String> cardLabels(List<CardRef> cards, int viewer) {
        return cards.stream().map(c -> cardLabel(c, viewer)).toList();
    }

    private String cardLabel(CardRef card, int viewer) {
        if (card.loc().location() == 0) {
            return text.cardName(card.code());
        }
        String side = card.loc().controller() == viewer ? "your " : "opponent's ";
        return text.cardName(card.code()) + " (" + side + text.location(card.loc().location()) + ")";
    }

    private static byte[] autoCounters(SelectCounter p) {
        List<Integer> remove = new ArrayList<>();
        int left = p.count();
        for (CounterCandidate c : p.cards()) {
            int take = Math.min(left, c.counters());
            remove.add(take);
            left -= take;
        }
        return Responses.counters(remove);
    }

    private static void addPosition(List<Choice> choices, int mask, int position, String label) {
        if ((mask & position) != 0) {
            choices.add(new Choice(label, Responses.position(position)));
        }
    }

    private static List<Choice> yesNo() {
        return List.of(new Choice("Yes", Responses.yesNo(true)), new Choice("No", Responses.yesNo(false)));
    }

    private static String range(int min, int max) {
        return min == max ? String.valueOf(min) : min + "-" + max;
    }

    private static String or(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
