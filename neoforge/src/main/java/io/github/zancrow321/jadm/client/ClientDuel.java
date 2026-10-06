package io.github.zancrow321.jadm.client;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.client.duel.DuelStaging;
import io.github.zancrow321.jadm.client.duel.DuelUi;
import io.github.zancrow321.jadm.client.field.ClientField;
import io.github.zancrow321.jadm.compat.figura.FiguraCompat;
import io.github.zancrow321.jadm.engine.duel.DuelView;
import io.github.zancrow321.jadm.engine.duel.FieldEvent;
import io.github.zancrow321.jadm.engine.protocol.Loc;
import io.github.zancrow321.jadm.engine.duel.ViewCodec;
import io.github.zancrow321.jadm.engine.text.PromptChoices;
import io.github.zancrow321.jadm.engine.text.PromptView;
import io.github.zancrow321.jadm.network.DuelResponsePayload;
import io.github.zancrow321.jadm.network.DuelResultPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's copy of its current duel. Only touched on the client thread.
 */
public final class ClientDuel {
    private static final int MAX_LOG = 200;

    private static DuelView view;
    private static PromptView prompt;
    private static final List<String> log = new ArrayList<>();
    /** Ticked options of the current multi-select prompt, shared by the card window and field clicks. */
    private static final List<Integer> selected = new ArrayList<>();
    private static Trigger lastTrigger;

    private ClientDuel() {
    }

    public static void receive(String json) {
        DuelView next;
        try {
            next = ViewCodec.decode(json);
        } catch (RuntimeException e) {
            Jadm.LOGGER.error("Bad duel view from server", e);
            return;
        }
        if (view == null || view.result() != null) {
            log.clear(); // a new duel
            lastTrigger = null;
            DuelUi.reset();
            DuelStaging.reset();
        }
        for (FieldEvent event : next.events()) {
            Trigger t = trigger(event, next); // before the field takes in the new board
            if (t != null) {
                lastTrigger = t;
            }
        }
        boolean onlyTurnOrPhase = next.events().stream()
                .allMatch(e -> e.kind() == FieldEvent.Kind.TURN || e.kind() == FieldEvent.Kind.PHASE);
        if (view != null && onlyTurnOrPhase && (view.board().phase() != next.board().phase()
                || view.board().turn() != next.board().turn())) {
            String whose = next.board().turnPlayer() == next.you() ? "Your " : "Your opponent's ";
            lastTrigger = new Trigger(0, whose + JadmData.text().phase(next.board().phase()));
        }
        DuelView previous = view;
        view = next;
        FiguraCompat.onView(previous, next);
        log.addAll(next.log());
        if (next.result() != null) {
            log.add(next.result());
        }
        while (log.size() > MAX_LOG) {
            log.removeFirst();
        }
        prompt = next.prompt() == null ? null
                : new PromptChoices(JadmData.text()).build(next.prompt(), next.hint());
        selected.clear();
        ClientField.onView(next);
    }

    /**
     * What the duel last did that you might respond to: a card activated, a summon or an attack.
     *
     * @param code the card behind it, or 0 if you can't know
     * @param what a line saying what happened
     */
    public record Trigger(int code, String what) {
    }

    private static Trigger trigger(FieldEvent event, DuelView view) {
        int code = ClientField.actor(event, view);
        String name = code == 0 ? "A card" : JadmData.text().cardName(code);
        String who = event.player() == view.you() ? "You" : "Your opponent";
        return switch (event.kind()) {
            case ACTIVATE -> new Trigger(code, who + (code == 0 ? " activated a card" : " activated " + name));
            case SUMMON -> new Trigger(code, who + (code == 0 ? " summoned a monster" : " summoned " + name));
            case SET -> new Trigger(code, who + " set a card");
            case ATTACK -> new Trigger(code, name + (event.to().location() == 0
                    ? " attacks directly" : " declares an attack"));
            case LEAVE -> new Trigger(code, name + " left the field");
            case POSITION -> new Trigger(code, name + " changed its position");
            default -> null;
        };
    }

    /** A new field arrived: whatever duel was shown before is over for this client. */
    public static void forget() {
        view = null;
        prompt = null;
        selected.clear();
    }

    private static int clockTicks = -1;
    private static long clockAt;

    /** The server's word on how much of your turn time is left (only with a turn time limit). */
    public static void clock(int ticksLeft) {
        clockTicks = ticksLeft;
        clockAt = ClientField.tick();
    }

    /** @return your time left this turn in ticks, counting down, or -1 if there is no limit or nothing to choose */
    public static int clockLeft() {
        long since = ClientField.tick() - clockAt;
        if (clockTicks < 0 || prompt == null || since > 40) {
            return -1;
        }
        return (int) Math.max(0, clockTicks - since);
    }

    public static void result(DuelResultPayload payload) {
        DuelStaging.result(payload);
    }

    /** @return what you'd be responding to, or {@code null} if nothing has happened yet this duel */
    public static Trigger lastTrigger() {
        return lastTrigger;
    }

    /** For headless testing: {@code -Djadm.autoplay=true} answers prompts at random after a pause. */
    private static final boolean AUTOPLAY = Boolean.getBoolean("jadm.autoplay");
    private static final java.util.Random AUTOPLAY_RANDOM = new java.util.Random(1);
    private static int autoplayDelay;

    public static void autoplayTick() {
        if (!AUTOPLAY || prompt == null) {
            autoplayDelay = 0;
            return;
        }
        if (++autoplayDelay < 40) {
            return;
        }
        autoplayDelay = 0;
        Minecraft.getInstance().setScreen(null);
        if (prompt.multi() == null) {
            List<PromptView.Choice> c = prompt.choices();
            answer(c.get(AUTOPLAY_RANDOM.nextInt(4) == 0 ? c.size() - 1 : AUTOPLAY_RANDOM.nextInt(c.size()))
                    .response());
            return;
        }
        PromptView.MultiSelect multi = prompt.multi();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < multi.options().size(); i++) {
            indices.add(i);
        }
        java.util.Collections.shuffle(indices, AUTOPLAY_RANDOM);
        int count = Math.max(multi.min(), Math.min(1, multi.max()));
        answer(multi.encode(indices.subList(0, Math.min(count, indices.size()))));
    }

    public static List<Integer> selected() {
        return selected;
    }

    public static void toggle(int option) {
        if (!selected.remove((Integer) option)) {
            selected.add(option);
        }
    }

    public static DuelView view() {
        return view;
    }

    public static PromptView prompt() {
        return prompt;
    }

    public static List<String> log() {
        return log;
    }

    public static void answer(byte[] response) {
        if (prompt == null) {
            return;
        }
        prompt = null; // wait for the server's next view
        selected.clear();
        PacketDistributor.sendToServer(new DuelResponsePayload(response));
    }
}
