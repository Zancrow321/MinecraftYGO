package io.github.zancrow321.jadm.client;

import io.github.zancrow321.jadm.client.collection.BinderScreen;
import io.github.zancrow321.jadm.client.collection.DeckBoxScreen;
import io.github.zancrow321.jadm.client.collection.PackOpenScreen;
import io.github.zancrow321.jadm.client.cosmetics.CosmeticsScreen;
import io.github.zancrow321.jadm.network.CosmeticsPayload;
import io.github.zancrow321.jadm.network.PackOpenedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/**
 * Opens the collection screens. Items call this only on the client, so its screen classes never load on a server.
 */
public final class ClientScreens {
    private ClientScreens() {
    }

    public static void openBinder(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new BinderScreen(hand));
    }

    public static void openDeckBox(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new DeckBoxScreen(hand));
    }

    public static void cosmetics(CosmeticsPayload payload) {
        Minecraft.getInstance().setScreen(new CosmeticsScreen(payload));
    }

    public static void starterChoices(io.github.zancrow321.jadm.network.StarterChoicesPayload payload) {
        Minecraft.getInstance().setScreen(
                new io.github.zancrow321.jadm.client.collection.StarterScreen(payload.choices()));
    }

    public static void guide(io.github.zancrow321.jadm.network.GuidePayload payload) {
        Minecraft.getInstance().setScreen(new io.github.zancrow321.jadm.client.guide.GuideScreen(
                payload.settings()));
    }

    /** The tournament changed (or the player asked to see it): keep it, and open or refresh the window. */
    public static void tournament(io.github.zancrow321.jadm.network.TournamentPayload payload) {
        io.github.zancrow321.jadm.client.tournament.TournamentScreen.receive(payload.open(), payload.json());
    }

    /** Opens the settings of the Arena Core at {@code pos}. */
    public static void arenaCore(net.minecraft.core.BlockPos pos) {
        Minecraft.getInstance().setScreen(new io.github.zancrow321.jadm.client.arena.ArenaCoreScreen(pos));
    }

    /** Opens the admin menu, or refreshes it if it is open. */
    public static void admin(io.github.zancrow321.jadm.network.AdminPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof io.github.zancrow321.jadm.client.collection.AdminScreen screen) {
            screen.update(payload);
        } else if (payload.open()) {
            mc.setScreen(new io.github.zancrow321.jadm.client.collection.AdminScreen(payload));
        }
    }

    public static void packOpened(PackOpenedPayload payload) {
        Minecraft.getInstance().setScreen(new PackOpenScreen(payload.setName(), payload.cards()));
    }
}
