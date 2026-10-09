package io.github.zancrow321.jadm.tournament;

import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.item.BoosterPackItem;
import io.github.zancrow321.jadm.item.CardItem;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prize entries such as {@code "pack 3"}, {@code "pack:LOB 2"}, {@code "card 89631139"}, {@code "points 500"},
 * {@code "xp 100"}, {@code "minecraft:diamond 5"} and {@code "command give {player} minecraft:cake"}. A card can
 * name its rarity after the count ({@code "card 89631139 1 ultra"}).
 */
final class Prizes {
    private Prizes() {
    }

    static boolean valid(String entry) {
        String[] p = entry.strip().split("\\s+", 2);
        if (p[0].isEmpty()) {
            return false;
        }
        String kind = p[0].toLowerCase(Locale.ROOT);
        if (kind.equals("command")) {
            return p.length == 2;
        }
        if (p.length == 2 && !p[1].matches("\\d{1,6}") && !(kind.equals("card") && p[1].matches("\\d+( \\d{1,4}( [a-z]+)?)?"))) {
            return false;
        }
        if (kind.equals("points")) {
            return p.length == 2;
        }
        if (kind.equals("pack") || kind.equals("xp")) {
            return true;
        }
        if (kind.startsWith("pack:")) {
            return kind.length() > 5;
        }
        if (kind.equals("card")) {
            return p.length == 2;
        }
        ResourceLocation id = ResourceLocation.tryParse(p[0]);
        return id != null && BuiltInRegistries.ITEM.containsKey(id);
    }

    /** Hands over one prize entry. @return what the player got, for the chat, or {@code null} if nothing */
    static String give(ServerPlayer player, String entry) {
        String[] p = entry.strip().split("\\s+");
        String kind = p[0].toLowerCase(Locale.ROOT);
        try {
            switch (kind) {
                case "command" -> {
                    String command = entry.strip().substring("command".length()).strip()
                            .replace("{player}", player.getScoreboardName());
                    var server = player.server;
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack()
                            .withSuppressedOutput(), command.startsWith("/") ? command.substring(1) : command);
                    return null;
                }
                case "points" -> {
                    int n = count(p, 1, 0);
                    Points.get(player.server).add(player.server, player.getUUID(), n);
                    return Points.format(n);
                }
                case "xp" -> {
                    int xp = count(p, 1, 0);
                    player.giveExperiencePoints(xp);
                    return xp + " experience";
                }
                case "card" -> {
                    int code = Integer.parseInt(p[1]);
                    int n = count(p, 2, 1);
                    if (JadmData.cards().card(code) == null) {
                        return null;
                    }
                    // A card from a Sealed or Draft pool keeps its rarity.
                    BoosterSets.Rarity rarity = p.length > 3 ? Limited.rarity(p[3]) : BoosterSets.Rarity.COMMON;
                    for (int i = 0; i < n; i++) {
                        hand(player, CardItem.of(code, rarity));
                    }
                    return (n > 1 ? n + "x " : "") + JadmData.text().cardName(code);
                }
                default -> {
                    if (kind.equals("pack") || kind.startsWith("pack:")) {
                        int n = count(p, 1, 1);
                        List<String> names = new ArrayList<>();
                        for (int i = 0; i < n; i++) {
                            BoosterSets.BoosterSet set = kind.equals("pack")
                                    ? BoosterPackItem.randomSet(player.getRandom()) : JadmData.set(p[0].substring(5));
                            if (set == null) {
                                break;
                            }
                            ItemStack pack = BoosterPackItem.of(set.id());
                            names.add(pack.getHoverName().getString());
                            hand(player, pack);
                        }
                        return names.isEmpty() ? null : String.join(", ", names);
                    }
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(p[0]));
                    if (item == Items.AIR) {
                        return null;
                    }
                    int n = count(p, 1, 1);
                    give(player, item, n);
                    return n + "x " + new ItemStack(item).getHoverName().getString();
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void give(ServerPlayer player, Item item, int count) {
        int max = new ItemStack(item).getMaxStackSize();
        for (int left = count; left > 0; left -= max) {
            hand(player, new ItemStack(item, Math.min(max, left)));
        }
    }

    private static int count(String[] parts, int index, int fallback) {
        return parts.length > index ? Integer.parseInt(parts[index]) : fallback;
    }

    static void hand(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
