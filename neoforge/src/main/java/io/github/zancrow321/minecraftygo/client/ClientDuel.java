package io.github.zancrow321.minecraftygo.client;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.github.zancrow321.minecraftygo.YgoData;
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

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DuelScreen screen) {
            screen.refresh();
        } else if (prompt != null && mc.screen == null) {
            mc.setScreen(new DuelScreen());
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
        PacketDistributor.sendToServer(new DuelResponsePayload(response));
    }
}
