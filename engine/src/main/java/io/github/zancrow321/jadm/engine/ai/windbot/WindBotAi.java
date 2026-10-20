package io.github.zancrow321.jadm.engine.ai.windbot;

import io.github.zancrow321.jadm.engine.OcgConstants;
import io.github.zancrow321.jadm.engine.ai.DuelistAi;
import io.github.zancrow321.jadm.engine.ai.Responder;
import io.github.zancrow321.jadm.engine.data.CardDatabase;
import io.github.zancrow321.jadm.engine.data.CardInfo;
import io.github.zancrow321.jadm.engine.data.Declarable;
import io.github.zancrow321.jadm.engine.duel.Board;
import io.github.zancrow321.jadm.engine.protocol.CardRef;
import io.github.zancrow321.jadm.engine.protocol.CardState;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage;
import io.github.zancrow321.jadm.engine.protocol.DuelMessage.*;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import io.github.zancrow321.jadm.engine.protocol.MessageType;
import io.github.zancrow321.jadm.engine.protocol.Responses;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * WindBot (IceYGO, MIT) with Astra's executor, for any deck. It keeps WindBot's picture of the duel up to date from
 * the messages its seat may see ({@link #observe}) and from the board at every prompt, turns each prompt into a
 * question for {@link GameAI}, and encodes the answer for the core. Players in the model are local: 0 is this seat's
 * team, 1 the opponent.
 *
 * <p>A seat that is not fed messages (a stand-in for a player who ran out of time) still works from the board alone,
 * just without the chain and summon history. Anything it can't answer, and every retry, goes to {@link DuelistAi}.
 */
public final class WindBotAi implements Responder {
    private static final int STATUS_DISABLED = 0x1;
    private static final int STATUS_PROC_COMPLETE = 0x8;
    private static final int HINT_ZONE = 11;
    /** System strings: "Replay the attack?" and "Attack directly?". */
    private static final long BATTLE_REPLAY = 30;
    private static final long DIRECT_ATTACK = 31;
    /** System string 221: "activate the trigger effect of ...", which WindBot treats as no description. */
    private static final long TRIGGER_EFFECT = 221;

    private final DuelistAi fallback;
    private final Random random;
    private final double mistakes;
    private final Duel duel = new Duel();
    private final GameAI ai;
    private final AstraExecutor executor;

    /** This seat's team (absolute player), -1 until the first message or prompt. */
    private int me = -1;
    /** Whether messages arrive; if not, turns and phases are read from the board. */
    private boolean observing;
    private long selectHint;
    private int boardTurn = -1;
    private int boardPhase = -1;
    private int answered;
    private int fallbacks;
    private int retries;
    private RuntimeException lastError;

    /**
     * @param style    how it plays
     * @param mistakes how often (0-1) it skips an optional response or an attack, as a beginner would
     * @param deck     the passcodes of its Main Deck, so it knows what is left in it; {@code null} in a tag duel or
     *                 when unknown
     */
    public WindBotAi(long seed, CardDatabase cards, AstraExecutor.Style style, double mistakes,
                     Collection<Integer> deck) {
        NamedCards.use(cards);
        this.random = new Random(seed);
        this.mistakes = Math.max(0, Math.min(1, mistakes));
        this.fallback = new DuelistAi(seed ^ 0x5DEECE66DL, cards);
        this.ai = new GameAI(duel, random);
        this.executor = new AstraExecutor(ai, duel, style);
        ai.executor = executor;
        if (deck != null && !deck.isEmpty()) {
            duel.fields[0].setInitialDeck(deck);
        }
    }

    public AstraExecutor.Style style() {
        return executor.style();
    }

    /** Prompts WindBot answered itself (retries not counted). */
    public int answered() {
        return answered;
    }

    /** Prompts it handed to {@link DuelistAi} because it had no answer or failed. */
    public int fallbacks() {
        return fallbacks;
    }

    /** Answers the core rejected, each then retried by {@link DuelistAi}. */
    public int retries() {
        return retries;
    }

    /** The last error that made it hand a prompt over, for diagnostics. */
    public RuntimeException lastError() {
        return lastError;
    }

    // --- Messages ----------------------------------------------------------------------------------------------------

    @Override
    public void observe(int team, DuelMessage message) {
        if (me < 0) {
            me = team;
        }
        if (team != me) {
            return;
        }
        observing = true;
        try {
            handle(message);
        } catch (RuntimeException e) {
            // A model that drifted is put right by the board at the next prompt.
        }
    }

    private int local(int player) {
        if (player != 0 && player != 1) {
            return player;
        }
        return player == me ? 0 : 1;
    }

    private int absolute(int player) {
        return player == 0 ? me : 1 - me;
    }

    private ClientCard cardAt(Loc loc) {
        if (loc == null || loc.isNone()) {
            return null;
        }
        return duel.getCard(local(loc.controller()), loc.location(), loc.sequence(),
                loc.isOverlay() ? loc.position() : 0);
    }

    private void handle(DuelMessage message) {
        switch (message) {
            case NewTurn m -> {
                duel.turn++;
                duel.player = local(m.player());
                ai.onNewTurn();
            }
            case NewPhase m -> {
                duel.phase = m.phase();
                newPhase();
            }
            case Hint m -> hint(m);
            case Draw m -> draw(m);
            case LifePoints m -> {
                ClientField field = duel.fields[local(m.player())];
                switch (m.type()) {
                    case MessageType.DAMAGE, MessageType.PAY_LPCOST ->
                            field.lifePoints = Math.max(0, field.lifePoints - m.amount());
                    case MessageType.RECOVER -> field.lifePoints += m.amount();
                    default -> field.lifePoints = m.amount();
                }
            }
            case Move m -> move(m);
            case Swap m -> swap(m);
            case Attack m -> {
                ClientCard attacker = cardAt(m.attacker());
                if (attacker != null) {
                    ClientCard defender = cardAt(m.target());
                    duel.fields[attacker.controller].battlingMonster = attacker;
                    duel.fields[1 - attacker.controller].battlingMonster = defender;
                    duel.fields[1 - attacker.controller].underAttack = true;
                    ai.onAttack();
                }
            }
            case Battle m -> {
                duel.fields[0].underAttack = false;
                duel.fields[1].underAttack = false;
            }
            case BattleEvent m -> {
                if (m.type() == MessageType.ATTACK_DISABLED) {
                    duel.fields[0].underAttack = false;
                    duel.fields[1].underAttack = false;
                }
            }
            case PositionChange m -> {
                ClientCard card = cardAt(m.loc());
                if (card != null) {
                    int current = m.loc().position();
                    card.position = current;
                    if ((m.previousPosition() & CardPosition.FACE_UP) != 0
                            && (current & CardPosition.FACE_DOWN) != 0) {
                        card.clearEquipRelations();
                        card.clearCardTargets();
                    }
                    ai.onPosChange(card, m.previousPosition(), current);
                }
            }
            case Chaining m -> chaining(m);
            case ChainEvent m -> {
                switch (m.type()) {
                    case MessageType.CHAIN_SOLVING -> duel.solvingChainIndex = m.chainLink();
                    case MessageType.CHAIN_NEGATED, MessageType.CHAIN_DISABLED ->
                            duel.negatedChainIndexList.add(m.chainLink());
                    case MessageType.CHAIN_SOLVED -> ai.onChainSolved(m.chainLink());
                    default -> {
                    }
                }
            }
            case ChainEnd m -> {
                ai.onChainEnd();
                duel.lastChainPlayer = -1;
                duel.lastChainLocation = 0;
                duel.currentChain.clear();
                duel.currentChainInfo.clear();
                duel.chainTargets.clear();
                duel.lastChainTargets.clear();
                duel.chainTargetOnly.clear();
                duel.solvingChainIndex = 0;
                duel.negatedChainIndexList.clear();
                duel.summoningCards.clear();
            }
            case CardsHighlighted m -> {
                if (m.type() == MessageType.BECOME_TARGET) {
                    becomeTarget(m.cards());
                }
            }
            case Equip m -> {
                ClientCard equip = cardAt(m.card());
                ClientCard target = cardAt(m.target());
                if (equip != null && target != null) {
                    equip.setEquipTarget(target);
                }
            }
            case CardTarget m -> {
                ClientCard owner = cardAt(m.card());
                ClientCard target = cardAt(m.target());
                if (owner != null && target != null) {
                    if (m.type() == MessageType.CARD_TARGET) {
                        owner.addCardTarget(target);
                    } else {
                        owner.removeCardTarget(target);
                    }
                }
            }
            case Summoning m -> summoning(m);
            case Summoned m -> {
                if (m.type() == MessageType.SPSUMMONED) {
                    for (ClientCard card : duel.summoningCards) {
                        card.isSpecialSummoned = true;
                        duel.lastSummonedCards.add(card);
                    }
                    ai.onSpSummoned();
                } else {
                    duel.lastSummonedCards.addAll(duel.summoningCards);
                }
                duel.summoningCards.clear();
            }
            case ConfirmCards m -> {
                for (CardRef ref : m.cards()) {
                    ClientCard card = cardAt(ref.loc());
                    if (card != null && ref.code() > 0) {
                        card.setId(ref.code());
                    }
                }
            }
            case ShuffleHand m -> {
                List<ClientCard> hand = duel.fields[local(m.player())].hand;
                if (hand.size() == m.codes().size()) {
                    for (int i = 0; i < hand.size(); i++) {
                        hand.get(i).setId(m.codes().get(i));
                    }
                }
            }
            case ShuffleDeck m -> {
                for (ClientCard card : duel.fields[local(m.player())].deck) {
                    card.setId(0);
                }
            }
            case Unhandled m -> {
                if (m.type() == MessageType.PLAYER_HINT && m.payload().length >= 10) {
                    ByteBuffer b = ByteBuffer.wrap(m.payload()).order(ByteOrder.LITTLE_ENDIAN);
                    int player = Byte.toUnsignedInt(b.get());
                    int hintType = Byte.toUnsignedInt(b.get());
                    executor.onPlayerHint(local(player), hintType, b.getLong());
                }
            }
            default -> {
            }
        }
    }

    private void newPhase() {
        duel.lastSummonPlayer = -1;
        duel.summoningCards.clear();
        duel.lastSummonedCards.clear();
        for (ClientField field : duel.fields) {
            field.battlingMonster = null;
            field.underAttack = false;
        }
        for (ClientCard monster : duel.fields[0].getMonsters()) {
            monster.attacked = false;
        }
        selectHint = 0;
        ai.onNewPhase();
    }

    private void hint(Hint m) {
        switch (m.hintType()) {
            case MessageType.HINT_EVENT -> {
                if (m.data() == 24) { // battling
                    duel.fields[0].underAttack = false;
                    duel.fields[1].underAttack = false;
                }
            }
            case MessageType.HINT_SELECTMSG -> selectHint = m.data();
            case MessageType.HINT_OPSELECTED -> {
                // Options announced for the newest chain link, after activation and before resolution.
                if (duel.solvingChainIndex == 0 && !duel.currentChainInfo.isEmpty()) {
                    duel.currentChainInfo.get(duel.currentChainInfo.size() - 1).announces.add(m.data());
                }
                ai.onReceivingAnnouce(local(m.player()), m.data());
            }
            case HINT_ZONE -> ai.onHintZone(local(m.player()), m.data());
            default -> {
            }
        }
    }

    private void draw(Draw m) {
        int player = local(m.player());
        ClientField field = duel.fields[player];
        for (int code : m.codes()) {
            ClientCard card = field.deck.isEmpty() ? new ClientCard(0, CardLocation.DECK, 0)
                    : field.deck.remove(field.deck.size() - 1);
            duel.addCard(CardLocation.HAND, card, player, -1, 0, code & 0x7fffffff);
        }
        ai.onDraw(player);
    }

    private void move(Move m) {
        Loc from = m.from();
        Loc to = m.to();
        int previousControler = local(from.controller());
        int previousLocation = from.location();
        int currentControler = local(to.controller());
        int currentLocation = to.location();
        int previousMoveLocation = from.isOverlay() ? CardLocation.OVERLAY : previousLocation;
        int currentMoveLocation = to.isOverlay() ? CardLocation.OVERLAY : currentLocation;

        ClientCard card = previousLocation == 0 ? null
                : duel.getCard(previousControler, previousLocation, from.sequence(), from.position());
        if (card != null) {
            card.lastLocation = previousLocation;
            if (previousLocation != currentLocation) {
                card.clearEquipRelations();
                card.clearCardTargets();
            }
        }
        if (from.isOverlay()) {
            // Materials are only ids on their host; detach by index, as a host may hold two of the same card.
            ClientCard host = duel.getCard(previousControler, previousLocation & 0x7f, from.sequence());
            if (host != null && from.position() < host.overlays.size()) {
                host.overlays.remove(from.position());
            }
            if (card == null) {
                card = new ClientCard(m.code(), CardLocation.OVERLAY, 0, 0);
            }
            card.lastLocation = CardLocation.OVERLAY;
            previousLocation = 0;
        } else if (previousLocation != 0) {
            duel.removeCard(previousLocation, card, previousControler, from.sequence());
        }

        if (to.isOverlay()) {
            ClientCard host = duel.getCard(currentControler, currentLocation & 0x7f, to.sequence());
            if (host != null) {
                host.overlays.add(m.code());
            }
            if (card != null) {
                card.setId(m.code());
                card.location = CardLocation.OVERLAY;
                card.sequence = to.position();
                card.position = 0;
                card.controller = currentControler;
            }
        } else if (currentLocation != 0) {
            if (card == null) {
                card = new ClientCard(m.code(), currentLocation, to.sequence(), to.position());
            } else if (previousLocation != currentLocation) {
                card.isSpecialSummoned = false;
            }
            // A hidden move keeps what the bot already knew about the card.
            duel.addCard(currentLocation, card, currentControler, to.sequence(), to.position(),
                    m.code() != 0 ? m.code() : card.id);
        }
        if (card != null) {
            ai.onMove(card, previousControler, previousMoveLocation, currentControler, currentMoveLocation);
        }
    }

    private void swap(Swap m) {
        ClientCard first = cardAt(m.first().loc());
        ClientCard second = cardAt(m.second().loc());
        if (first == null || second == null) {
            return;
        }
        Loc a = m.first().loc();
        Loc b = m.second().loc();
        duel.removeCard(a.location(), first, local(a.controller()), a.sequence());
        duel.removeCard(b.location(), second, local(b.controller()), b.sequence());
        duel.addCard(b.location(), first, local(b.controller()), b.sequence(), first.position,
                m.first().code() != 0 ? m.first().code() : first.id);
        duel.addCard(a.location(), second, local(a.controller()), a.sequence(), second.position,
                m.second().code() != 0 ? m.second().code() : second.id);
    }

    private void chaining(Chaining m) {
        ClientCard card = cardAt(m.loc());
        if (card == null) {
            card = new ClientCard(m.code(), m.loc().location(), m.loc().sequence(), m.loc().position());
            card.controller = local(m.loc().controller());
        }
        if (card.id == 0) {
            card.setId(m.code());
        }
        int player = local(m.triggerController());
        duel.lastChainLocation = m.loc().location();
        ai.onChaining(card, player);
        duel.chainTargetOnly.clear();
        duel.lastSummonPlayer = -1;
        duel.currentChain.add(card);
        duel.currentChainInfo.add(new ChainInfo(card, player, m.description()));
        duel.lastChainPlayer = player;
    }

    private void becomeTarget(List<Loc> targets) {
        duel.lastChainTargets.clear();
        int index = duel.solvingChainIndex > 0 ? duel.solvingChainIndex - 1 : duel.currentChainInfo.size() - 1;
        ChainInfo info = index >= 0 && index < duel.currentChainInfo.size() ? duel.currentChainInfo.get(index) : null;
        for (Loc loc : targets) {
            ClientCard card = loc.isOverlay() ? null : cardAt(loc);
            if (card == null) {
                continue;
            }
            duel.chainTargets.add(card);
            duel.lastChainTargets.add(card);
            duel.chainTargetOnly.add(card);
            if (info != null && !info.targets.contains(card)) {
                info.targets.add(card);
            }
        }
    }

    private void summoning(Summoning m) {
        duel.lastSummonedCards.clear();
        if (m.type() == MessageType.SPSUMMONING) {
            // The materials are chosen once the summon starts.
            ai.cleanSelectMaterials();
        }
        ClientCard card = cardAt(m.loc());
        if (card != null) {
            duel.summoningCards.add(card);
        }
        duel.lastSummonPlayer = local(m.loc().controller());
        if (m.type() == MessageType.SUMMONING) {
            ai.onSummoning();
        } else if (m.type() == MessageType.SPSUMMONING) {
            ai.onSpSummoning();
        }
    }

    // --- The board -------------------------------------------------------------------------------------------------

    /** Puts the model in line with the board: what the bot sees now wins over what it pieced together. */
    private void sync(Board board) {
        duel.isNewRule = board.extraMonsterZones();
        duel.isNewRule2020 = (board.options() & OcgConstants.DUEL_FSX_MMZONE) != 0;
        duel.separatePendulumZones = board.separatePendulumZones();
        if (!observing) {
            if (board.turn() != boardTurn) {
                boardTurn = board.turn();
                boardPhase = -1;
                duel.turn = board.turn();
                duel.player = local(board.turnPlayer());
                ai.onNewTurn();
            }
            if (board.phase() != boardPhase) {
                boardPhase = board.phase();
                duel.phase = board.phase();
                newPhase();
            }
        } else {
            duel.player = local(board.turnPlayer());
            duel.phase = board.phase();
        }
        for (int player = 0; player < 2; player++) {
            Board.Side side = board.side(absolute(player));
            ClientField field = duel.fields[player];
            field.lifePoints = side.lifePoints();
            syncZone(field.monsterZone, side.monsters(), player, CardLocation.MONSTER_ZONE);
            syncZone(field.spellZone, side.spells(), player, CardLocation.SPELL_ZONE);
            syncList(field.hand, side.hand(), player, CardLocation.HAND);
            syncList(field.graveyard, side.graveyard(), player, CardLocation.GRAVE);
            syncList(field.banished, side.banished(), player, CardLocation.REMOVED);
            syncList(field.extraDeck, side.extra(), player, CardLocation.EXTRA);
            while (field.deck.size() > side.deckCount()) {
                field.deck.remove(field.deck.size() - 1);
            }
            while (field.deck.size() < side.deckCount()) {
                field.deck.add(new ClientCard(0, CardLocation.DECK, field.deck.size()));
            }
            for (ClientCard card : field.deck) {
                card.controller = player;
                card.location = CardLocation.DECK;
            }
            Duel.resetSequence(field.deck);
        }
        for (int player = 0; player < 2; player++) {
            Board.Side side = board.side(absolute(player));
            syncRelations(duel.fields[player].monsterZone, side.monsters());
            syncRelations(duel.fields[player].spellZone, side.spells());
        }
    }

    private void syncZone(ClientCard[] zone, List<CardState> states, int player, int location) {
        for (int seq = 0; seq < zone.length; seq++) {
            CardState state = seq < states.size() ? states.get(seq) : null;
            ClientCard card = zone[seq];
            if (state == null) {
                if (card != null) {
                    card.clearEquipRelations();
                    card.clearCardTargets();
                }
                zone[seq] = null;
                continue;
            }
            if (card == null || state.code() != 0 && card.id != 0 && card.id != state.code()) {
                card = new ClientCard(0, location, seq);
                zone[seq] = card;
            }
            apply(card, state, location, seq, player);
        }
    }

    private void syncList(List<ClientCard> cards, List<CardState> states, int player, int location) {
        if (cards.size() == states.size()) {
            for (int i = 0; i < cards.size(); i++) {
                CardState state = states.get(i);
                ClientCard card = cards.get(i);
                if (state.code() != 0 && card.id != 0 && card.id != state.code()) {
                    card = new ClientCard(0, location, i);
                    cards.set(i, card);
                }
                apply(card, state, location, i, player);
            }
            return;
        }
        List<ClientCard> pool = new ArrayList<>(cards);
        cards.clear();
        for (int i = 0; i < states.size(); i++) {
            CardState state = states.get(i);
            ClientCard card = null;
            for (ClientCard candidate : pool) {
                if (state.code() != 0 ? candidate.id == state.code() : candidate.id == 0) {
                    card = candidate;
                    break;
                }
            }
            if (card != null) {
                pool.remove(card);
            } else {
                card = new ClientCard(0, location, i);
            }
            apply(card, state, location, i, player);
            cards.add(card);
        }
    }

    private void apply(ClientCard card, CardState state, int location, int seq, int player) {
        card.location = location;
        card.sequence = seq;
        card.controller = player;
        card.position = state.position();
        card.owner = local(state.owner());
        if (state.code() == 0) {
            return; // hidden: keep what the bot already knew
        }
        card.setId(state.code());
        card.alias = state.alias() != state.code() ? state.alias() : 0;
        card.type = state.type();
        card.level = state.level();
        card.rank = state.rank();
        card.attribute = state.attribute();
        card.race = state.race();
        card.attack = state.attack();
        card.defense = state.defense();
        card.baseAttack = state.baseAttack();
        card.baseDefense = state.baseDefense();
        card.lScale = state.leftScale();
        card.rScale = state.rightScale();
        card.linkCount = state.link();
        card.linkMarker = state.linkMarker();
        card.disabled = (state.status() & STATUS_DISABLED) != 0 ? 1 : 0;
        card.procCompleted = (state.status() & STATUS_PROC_COMPLETE) != 0 ? 1 : 0;
        card.overlays.clear();
        if (state.overlayCodes() != null) {
            card.overlays.addAll(state.overlayCodes());
        }
    }

    private void syncRelations(ClientCard[] zone, List<CardState> states) {
        for (int seq = 0; seq < zone.length && seq < states.size(); seq++) {
            ClientCard card = zone[seq];
            CardState state = states.get(seq);
            if (card == null || state == null || state.code() == 0) {
                continue;
            }
            card.setEquipTarget(cardAt(state.equipTarget()));
            Set<ClientCard> targets = new HashSet<>();
            if (state.targets() != null) {
                for (Loc loc : state.targets()) {
                    ClientCard target = cardAt(loc);
                    if (target != null) {
                        targets.add(target);
                    }
                }
            }
            for (ClientCard old : List.copyOf(card.targetCards)) {
                if (!targets.contains(old)) {
                    card.removeCardTarget(old);
                }
            }
            targets.forEach(card::addCardTarget);
        }
    }

    // --- Prompts ---------------------------------------------------------------------------------------------------

    @Override
    public byte[] respond(Prompt prompt, Board board, int attempt) {
        if (me < 0) {
            me = prompt.player();
        }
        if (attempt > 0) {
            retries++;
            return fallback.respond(prompt, board, attempt - 1);
        }
        try {
            sync(board);
            byte[] answer = answer(prompt);
            if (answer != null) {
                answered++;
                return answer;
            }
        } catch (RuntimeException e) {
            // Fall through to the rule-of-thumb duelist.
            lastError = e;
        }
        fallbacks++;
        return fallback.respond(prompt, board, 0);
    }

    /** The bot's object for a card named in a prompt; a new one if the model has none there. */
    private ClientCard cardOf(CardRef ref, List<ClientCard> taken) {
        Loc loc = ref.loc();
        int player = local(loc.controller());
        ClientCard card;
        if (loc.isOverlay()) {
            card = new ClientCard(ref.code(), CardLocation.OVERLAY, -1);
            ClientCard host = duel.getCard(player, loc.location() & 0x7f, loc.sequence());
            if (host != null) {
                card.ownTargets.add(host);
            }
        } else {
            card = duel.getCard(player, loc.location(), loc.sequence());
            if (card == null || taken.contains(card)) {
                card = new ClientCard(ref.code(), loc.location(), loc.sequence());
            }
        }
        card.controller = player;
        if (ref.code() != 0 && (card.id == 0 || card.location == CardLocation.DECK)) {
            card.setId(ref.code());
        }
        return card;
    }

    private List<ClientCard> cardsOf(List<CardRef> refs) {
        List<ClientCard> cards = new ArrayList<>(refs.size());
        for (CardRef ref : refs) {
            cards.add(cardOf(ref, cards));
        }
        return cards;
    }

    private boolean slip() {
        return mistakes > 0 && random.nextDouble() < mistakes;
    }

    private byte[] answer(Prompt prompt) {
        return switch (prompt) {
            case SelectIdleCmd p -> idle(p);
            case SelectBattleCmd p -> battle(p);
            case SelectCard p -> {
                List<ClientCard> cards = cardsOf(p.cards());
                long hint = selectHint;
                selectHint = 0;
                List<ClientCard> selected = ai.onSelectCard(cards, p.min(), p.max(), hint, p.cancelable());
                yield selection(cards, selected, p.min(), p.max(), p.cancelable());
            }
            case SelectUnselectCard p -> {
                List<ClientCard> cards = cardsOf(p.selectable());
                // Cancelling here would drop the whole selection, so it only finishes one already started.
                boolean cancelable = (p.cancelable() || p.finishable()) && !p.unselectable().isEmpty();
                int min = p.finishable() ? 0 : 1;
                // The hint stays: script helpers ask for one card at a time under the same title.
                List<ClientCard> selected = ai.onSelectCard(cards, min, 1, selectHint, cancelable);
                if (selected == null || selected.isEmpty() || !cards.contains(selected.get(0))) {
                    if (p.finishable() || p.cancelable() || cards.isEmpty()) {
                        yield Responses.cancel();
                    }
                    yield Responses.toggleCard(0);
                }
                yield Responses.toggleCard(cards.indexOf(selected.get(0)));
            }
            case SelectChain p -> chain(p);
            case SelectEffectYesNo p -> {
                ClientCard card = cardOf(p.card(), List.of());
                long desc = p.description() == 0 || p.description() == TRIGGER_EFFECT ? -1 : p.description();
                yield Responses.yesNo(ai.onSelectEffectYn(card, desc));
            }
            case SelectYesNo p -> {
                if (p.description() == BATTLE_REPLAY) {
                    yield Responses.yesNo(ai.onSelectBattleReplay());
                }
                if (p.description() == DIRECT_ATTACK) {
                    yield Responses.yesNo(ai.onSelectBattleDirectAttack());
                }
                yield Responses.yesNo(ai.onSelectYesNo(p.description()));
            }
            case SelectOption p -> Responses.index(ai.onSelectOption(p.options()));
            case SelectPlace p -> place(p);
            case SelectPosition p -> {
                int positions = p.positions();
                if (Integer.bitCount(positions & 0xf) == 1) {
                    yield Responses.position(positions & 0xf);
                }
                List<Integer> list = new ArrayList<>();
                for (int position : new int[]{CardPosition.FACE_UP_ATTACK, CardPosition.FACE_DOWN_ATTACK,
                        CardPosition.FACE_UP_DEFENCE, CardPosition.FACE_DOWN_DEFENCE}) {
                    if ((positions & position) != 0) {
                        list.add(position);
                    }
                }
                yield Responses.position(ai.onSelectPosition(p.code(), list));
            }
            case SelectTribute p -> tribute(p);
            case SelectSum p -> sum(p);
            case SortCards p -> {
                if (p.chain()) {
                    yield Responses.defaultOrder();
                }
                List<ClientCard> cards = cardsOf(p.cards());
                List<ClientCard> sorted = ai.onCardSorting(cards);
                if (sorted.size() != cards.size() || !new HashSet<>(sorted).containsAll(cards)) {
                    yield Responses.defaultOrder();
                }
                List<Integer> order = new ArrayList<>();
                for (ClientCard card : cards) {
                    order.add(sorted.indexOf(card));
                }
                yield Responses.order(order);
            }
            case SelectCounter p -> {
                List<ClientCard> cards = new ArrayList<>();
                List<Integer> counters = new ArrayList<>();
                for (CounterCandidate candidate : p.cards()) {
                    cards.add(cardOf(candidate.card(), cards));
                    counters.add(candidate.counters());
                }
                int[] used = ai.onSelectCounter(p.counterType(), p.count(), cards, counters);
                List<Integer> perCard = new ArrayList<>();
                for (int count : used) {
                    perCard.add(count);
                }
                yield Responses.counters(perCard);
            }
            case RockPaperScissors p -> Responses.hand(ai.onRockPaperScissors());
            case AnnounceRace p -> {
                List<Long> available = new ArrayList<>();
                for (int bit = 0; bit < 64; bit++) {
                    if ((p.available() & (1L << bit)) != 0) {
                        available.add(1L << bit);
                    }
                }
                long mask = 0;
                for (long race : ai.onAnnounceRace(p.count(), available)) {
                    mask |= race;
                }
                yield Responses.race(mask);
            }
            case AnnounceAttribute p -> {
                List<Integer> available = new ArrayList<>();
                for (int bit = 0; bit < 7; bit++) {
                    if ((p.available() & (1 << bit)) != 0) {
                        available.add(1 << bit);
                    }
                }
                int mask = 0;
                for (int attribute : ai.onAnnounceAttrib(p.count(), available)) {
                    mask |= attribute;
                }
                yield Responses.attribute(mask);
            }
            case AnnounceCard p -> {
                List<Integer> available = new ArrayList<>();
                for (CardInfo info : NamedCards.database().all()) {
                    if ((info.data().type() & CardType.TOKEN) != 0
                            || NamedCards.isAltartAlias(info.code(), info.data().alias())) {
                        continue;
                    }
                    if (Declarable.test(info.data(), p.opcodes())) {
                        available.add(info.code());
                    }
                }
                yield available.isEmpty() ? null : Responses.cardCode(ai.onAnnounceCard(available));
            }
            case AnnounceNumber p -> p.values().isEmpty() ? null : Responses.index(ai.onAnnounceNumber(p.values()));
        };
    }

    private byte[] idle(SelectIdleCmd p) {
        MainPhase main = new MainPhase();
        duel.mainPhase = main;
        List<List<CardRef>> groups = List.of(p.summonable(), p.specialSummonable(), p.repositionable(),
                p.monsterSettable(), p.spellSettable());
        List<List<ClientCard>> targets = List.of(main.summonableCards, main.specialSummonableCards,
                main.reposableCards, main.monsterSetableCards, main.spellSetableCards);
        for (int k = 0; k < groups.size(); k++) {
            List<CardRef> refs = groups.get(k);
            for (int i = 0; i < refs.size(); i++) {
                ClientCard card = cardOf(refs.get(i), targets.get(k));
                card.actionIndex[k] = i;
                targets.get(k).add(card);
            }
        }
        for (int i = 0; i < p.activatable().size(); i++) {
            Activatable activatable = p.activatable().get(i);
            ClientCard card = cardOf(activatable.card(), List.of());
            card.actionIndex[5] = i;
            card.actionActivateIndex.put(activatable.description(), i);
            main.activableCards.add(card);
            main.activableDescs.add(activatable.description());
        }
        main.canBattlePhase = p.canBattle();
        main.canEndPhase = p.canEnd();
        MainPhaseAction action = ai.onSelectIdleCmd(main);
        if (action.action == MainPhaseAction.MainAction.TO_END_PHASE && !p.canEnd()) {
            return p.canBattle() ? Responses.command(Responses.IDLE_TO_BATTLE, 0) : null;
        }
        if (action.action == MainPhaseAction.MainAction.TO_BATTLE_PHASE && !p.canBattle()) {
            return p.canEnd() ? Responses.command(Responses.IDLE_TO_END, 0) : null;
        }
        return Responses.command(action.action.ordinal(), action.index);
    }

    private byte[] battle(SelectBattleCmd p) {
        BattlePhase battle = new BattlePhase();
        duel.battlePhase = battle;
        for (int i = 0; i < p.activatable().size(); i++) {
            Activatable activatable = p.activatable().get(i);
            ClientCard card = cardOf(activatable.card(), List.of());
            card.actionIndex[0] = i;
            battle.activableCards.add(card);
            battle.activableDescs.add(activatable.description());
        }
        for (int i = 0; i < p.attackers().size(); i++) {
            Attacker attacker = p.attackers().get(i);
            ClientCard card = cardOf(attacker.card(), battle.attackableCards);
            card.actionIndex[1] = i;
            card.canDirectAttack = attacker.canAttackDirectly();
            card.attacked = false;
            battle.attackableCards.add(card);
        }
        for (ClientCard monster : duel.fields[0].getMonsters()) {
            if (!battle.attackableCards.contains(monster)) {
                monster.attacked = true;
            }
        }
        battle.canMainPhaseTwo = p.canMain2();
        battle.canEndPhase = p.canEnd();
        if (battle.attackableCards.isEmpty() && battle.activableCards.isEmpty() && !p.canMain2() && !p.canEnd()) {
            return null;
        }
        BattlePhaseAction action = ai.onSelectBattleCmd(battle);
        if (action.action == BattlePhaseAction.BattleAction.ATTACK && (p.canMain2() || p.canEnd()) && slip()) {
            // A beginner holds back an attack now and then.
            return Responses.command(p.canMain2() ? Responses.BATTLE_TO_MAIN2 : Responses.BATTLE_TO_END, 0);
        }
        if (action.action == BattlePhaseAction.BattleAction.TO_MAIN_PHASE_TWO && !p.canMain2()
                || action.action == BattlePhaseAction.BattleAction.TO_END_PHASE && !p.canEnd()) {
            return null;
        }
        return Responses.command(action.action.ordinal(), action.index);
    }

    private byte[] chain(SelectChain p) {
        if (p.chains().isEmpty()) {
            return Responses.index(-1);
        }
        if (p.chains().size() == 1 && p.forced()) {
            return Responses.index(0);
        }
        List<ClientCard> cards = new ArrayList<>();
        List<Long> descs = new ArrayList<>();
        List<Boolean> forces = new ArrayList<>();
        for (Activatable activatable : p.chains()) {
            cards.add(cardOf(activatable.card(), List.of()));
            descs.add(activatable.description() == TRIGGER_EFFECT ? 0 : activatable.description());
            forces.add(p.forced());
        }
        int index = ai.onSelectChain(cards, descs, forces, 0);
        if (index >= 0 && !p.forced() && slip()) {
            // A beginner misses a chance to respond now and then.
            return Responses.index(-1);
        }
        if (index < 0 && p.forced()) {
            index = 0;
        }
        return Responses.index(index);
    }

    private byte[] place(SelectPlace p) {
        int available = ~p.blockedZones();
        int count = Math.max(1, p.count());
        long hint = selectHint;
        selectHint = 0;
        int selected = p.disableField() ? ai.onSelectDisfield(hint, p.count(), available)
                : ai.onSelectPlace((int) hint, p.count(), available);
        List<Responses.Zone> zones = new ArrayList<>();
        for (int zone = 0; zone < 32 && zones.size() < count; zone++) {
            if ((selected & (1 << zone)) == 0 || (available & (1 << zone)) == 0) {
                continue;
            }
            int sequence = zone & 0xf;
            if (sequence == 7) {
                continue;
            }
            int player = (zone >> 4) == 0 ? p.player() : 1 - p.player();
            if (sequence < 7) {
                zones.add(new Responses.Zone(player, CardLocation.MONSTER_ZONE, sequence));
            } else {
                zones.add(new Responses.Zone(player, CardLocation.SPELL_ZONE, sequence - 8));
            }
        }
        return zones.size() < count ? null : Responses.zones(zones);
    }

    private byte[] selection(List<ClientCard> cards, List<ClientCard> selected, int min, int max,
                             boolean cancelable) {
        boolean valid = selected != null
                && (selected.size() >= min && selected.size() <= max || cancelable && selected.isEmpty())
                && new HashSet<>(selected).size() == selected.size()
                && selected.stream().allMatch(card -> card != null && cards.contains(card));
        if (!valid) {
            List<ClientCard> ordered = new ArrayList<>();
            if (selected != null) {
                for (ClientCard card : selected) {
                    if (card != null && cards.contains(card) && !ordered.contains(card)) {
                        ordered.add(card);
                    }
                }
            }
            for (ClientCard card : cards) {
                if (!ordered.contains(card)) {
                    ordered.add(card);
                }
            }
            selected = ordered.subList(0, Math.min(Math.min(min, max), ordered.size()));
        }
        if (selected.isEmpty() && cancelable) {
            return Responses.cancel();
        }
        return Responses.cards(indices(cards, selected));
    }

    private static List<Integer> indices(List<ClientCard> cards, List<ClientCard> selected) {
        List<Integer> indices = new ArrayList<>(selected.size());
        for (ClientCard card : selected) {
            indices.add(cards.indexOf(card));
        }
        return indices;
    }

    private byte[] tribute(SelectTribute p) {
        List<ClientCard> cards = new ArrayList<>();
        for (TributeCandidate candidate : p.cards()) {
            ClientCard card = cardOf(candidate.card(), cards);
            card.opParam1 = 1;
            card.opParam2 = candidate.releaseParam();
            cards.add(card);
        }
        long hint = selectHint;
        selectHint = 0;
        List<ClientCard> selected = ai.onSelectTribute(cards, p.min(), p.max(), hint, p.cancelable());
        boolean valid = selected != null && (p.cancelable() && selected.isEmpty()
                || new HashSet<>(selected).size() == selected.size() && cards.containsAll(selected)
                && ai.isValidTributeSelection(selected, p.min(), p.max()));
        if (!valid) {
            selected = ai.findTributeSelection(cards, p.min(), p.max());
            if (selected == null) {
                return null;
            }
        }
        if (selected.isEmpty() && p.cancelable()) {
            return Responses.cancel();
        }
        return Responses.cards(indices(cards, selected));
    }

    private byte[] sum(SelectSum p) {
        List<ClientCard> mandatory = new ArrayList<>();
        List<ClientCard> cards = new ArrayList<>();
        List<ClientCard> taken = new ArrayList<>();
        for (int group = 0; group < 2; group++) {
            List<SumCandidate> candidates = group == 0 ? p.mustSelect() : p.selectable();
            for (int i = 0; i < candidates.size(); i++) {
                SumCandidate candidate = candidates.get(i);
                ClientCard card = cardOf(candidate.card(), taken);
                taken.add(card);
                card.selectSeq = i;
                int param = candidate.param();
                int first = param & 0xffff;
                int second = (param >>> 16) & 0xffff;
                if ((second & 0x8000) != 0) {
                    first = param & 0x7fffffff;
                    second = 0;
                }
                if (second > 0 && first > second) {
                    card.opParam1 = second;
                    card.opParam2 = first;
                } else {
                    card.opParam1 = first;
                    card.opParam2 = second;
                }
                (group == 0 ? mandatory : cards).add(card);
            }
        }
        int max = p.max() <= 0 ? 99 : p.max();
        long hint = selectHint;
        selectHint = 0;
        List<ClientCard> selected = ai.onSelectSum(cards, mandatory, p.target(), p.min(), max, hint, !p.atLeast());
        return Responses.cards(indices(cards, selected));
    }
}
