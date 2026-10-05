package io.github.zancrow321.minecraftygo.client;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.client.field.ClientField;
import io.github.zancrow321.minecraftygo.client.field.FieldLayout;
import io.github.zancrow321.minecraftygo.engine.duel.DuelView;
import io.github.zancrow321.minecraftygo.engine.duel.ViewCodec;
import io.github.zancrow321.minecraftygo.engine.text.PromptChoices;
import io.github.zancrow321.minecraftygo.engine.text.PromptView;
import io.github.zancrow321.minecraftygo.network.DuelResponsePayload;
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
    /** Ticked options of the current multi-select prompt, shared by the duel screen and field clicks. */
    private static final List<Integer> selected = new ArrayList<>();

    private ClientDuel() {
    }

    public static void receive(String json) {
        DuelView next;
        try {
            next = ViewCodec.decode(json);
        } catch (RuntimeException e) {
            MinecraftYgo.LOGGER.error("Bad duel view from server", e);
            return;
        }
        if (view == null || view.result() != null) {
            log.clear(); // a new duel
        }
        view = next;
        log.addAll(next.log());
        if (next.result() != null) {
            log.add(next.result());
        }
        while (log.size() > MAX_LOG) {
            log.removeFirst();
        }
        prompt = next.prompt() == null ? null
                : new PromptChoices(YgoData.text()).build(next.prompt(), next.hint());
        selected.clear();
        ClientField.onView(next);

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DuelScreen screen) {
            screen.refresh();
        } else if (prompt != null && mc.screen == null && !AUTOPLAY && !(ClientField.active() && onField(prompt))) {
            // Prompts with nothing to click on the mat (yes/no, positions, options) open the screen straight away.
            mc.setScreen(new DuelScreen());
        }
    }

    /** Whether some of the prompt's options are zones on the mat. */
    private static boolean onField(PromptView prompt) {
        if (prompt.multi() != null) {
            return prompt.multi().locs().stream().anyMatch(l -> l != null && FieldLayout.slot(l) != null);
        }
        return prompt.choices().stream().anyMatch(c -> c.at() != null && FieldLayout.slot(c.at()) != null);
    }

    /** For headless testing: {@code -Dminecraftygo.autoplay=true} answers prompts at random after a pause. */
    private static final boolean AUTOPLAY = Boolean.getBoolean("minecraftygo.autoplay");
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
