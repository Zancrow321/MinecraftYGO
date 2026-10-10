package io.github.zancrow321.jadm.clan;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/** How clans appear in chat, and messages to a whole clan. */
public final class ClanText {
    /** The chat colors a clan can pick, by name. */
    public static final List<String> COLORS = List.of("gold", "yellow", "red", "dark_red", "light_purple",
            "dark_purple", "blue", "dark_blue", "aqua", "dark_aqua", "green", "dark_green", "white", "gray",
            "dark_gray");

    private ClanText() {
    }

    public static ChatFormatting color(String name) {
        ChatFormatting f = ChatFormatting.getByName(name);
        return f != null && f.isColor() ? f : ChatFormatting.GOLD;
    }

    /** The color as RGB, for the client. */
    public static int rgb(String name) {
        Integer c = color(name).getColor();
        return c == null ? 0xFFAA00 : c;
    }

    /** "[KC]" in the clan's color. */
    public static MutableComponent tag(String tag, String color) {
        return Component.literal("[" + tag + "]").withStyle(color(color));
    }

    public static MutableComponent tag(Clans.Clan clan) {
        return tag(clan.tag, clan.color);
    }

    /** "[KC] Kaiba Corp" with the tag in the clan's color. */
    public static MutableComponent named(Clans.Clan clan) {
        return tag(clan).append(Component.literal(" " + clan.name).withStyle(ChatFormatting.WHITE));
    }

    /** "[KC] 4 : 2 [XYZ]" */
    public static MutableComponent score(Clans.Clan a, int scoreA, Clans.Clan b, int scoreB) {
        return tag(a).append(Component.literal(" " + scoreA + " : " + scoreB + " ").withStyle(ChatFormatting.WHITE))
                .append(tag(b));
    }

    /** A clickable "[text]" that runs {@code command}. */
    public static MutableComponent button(String text, ChatFormatting color, String command) {
        return Component.literal("[" + text + "]").withStyle(s -> s.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    /** Tells every online member of {@code clan}. */
    public static void tell(MinecraftServer server, Clans.Clan clan, Component text) {
        if (clan == null) {
            return;
        }
        for (UUID id : clan.members.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(text);
            }
        }
    }

    /** Tells the online leader and officers of {@code clan}. */
    public static void tellManagers(MinecraftServer server, Clans.Clan clan, Component text) {
        clan.members.forEach((id, m) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && m.role.manages()) {
                player.sendSystemMessage(text);
            }
        });
    }

    /** "2 days 5 hours", "3 hours 10 minutes" or "4 minutes". */
    /** "1 duelist", "3 duelists". */
    public static String count(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    public static String duration(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        long days = minutes / (60 * 24);
        long hours = minutes / 60 % 24;
        long mins = minutes % 60;
        if (days > 0) {
            return days + (days == 1 ? " day" : " days") + (hours > 0 ? " " + hours + (hours == 1 ? " hour" : " hours")
                    : "");
        }
        if (hours > 0) {
            return hours + (hours == 1 ? " hour" : " hours") + (mins > 0 ? " " + mins + (mins == 1 ? " minute"
                    : " minutes") : "");
        }
        return Math.max(1, mins) + (mins == 1 ? " minute" : " minutes");
    }
}
