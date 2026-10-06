package io.github.zancrow321.jadm.guide;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.item.JadmItems;
import io.github.zancrow321.jadm.network.GuidePayload;
import io.github.zancrow321.jadm.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * The Duelist's Handbook: given once on a player's first join, opened with the item or {@code /jadm guide}. Its pages
 * live on the client ({@code assets/jadm/guide/<language>.json}); the server only sends its current settings
 * so the book can show them.
 */
public final class GuideBook {
    private GuideBook() {
    }

    /** Opens the book for the player, with the server's settings filled in. */
    public static int open(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new GuidePayload(settings()));
        return 1;
    }

    /** Gives the book to a player who joins for the first time (or hasn't had one since the book came out). */
    public static void onLogin(ServerPlayer player) {
        if (!JadmServerConfig.GUIDE_ON_FIRST_JOIN.get() || !ProgressionData.get(player.server)
                .giveGuide(player.getUUID())) {
            return;
        }
        ItemStack book = new ItemStack(JadmItems.GUIDE_BOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.sendSystemMessage(Component.translatable("message.jadm.guide.given",
                        "/" + Jadm.COMMAND + " guide")
                .withStyle(ChatFormatting.GOLD));
    }

    /** Every server setting by its path in the config file, such as {@code pool.mode}. */
    static Map<String, String> settings() {
        Map<String, String> values = new HashMap<>();
        collect(JadmServerConfig.SPEC.getValues(), "", values);
        return values;
    }

    private static void collect(UnmodifiableConfig config, String prefix, Map<String, String> into) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String path = prefix + entry.getKey();
            Object value = entry.getRawValue();
            if (value instanceof UnmodifiableConfig section) {
                collect(section, path + ".", into);
            } else if (value instanceof ModConfigSpec.ConfigValue<?> setting) {
                try {
                    into.put(path, String.valueOf(setting.get()));
                } catch (IllegalStateException notLoaded) {
                    into.put(path, String.valueOf(setting.getDefault()));
                }
            }
        }
    }
}
