package io.github.zancrow321.minecraftygo.duel;

import io.github.zancrow321.minecraftygo.YgoServerConfig;
import io.github.zancrow321.minecraftygo.engine.data.BoosterSets;
import io.github.zancrow321.minecraftygo.item.BoosterPackItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Hands out what the server config says a won or lost duel brings. */
final class DuelRewards {
    private DuelRewards() {
    }

    /** @return one line per thing given, for the result screen, e.g. "Won Booster Pack: Metal Raiders" */
    static List<String> give(ServerPlayer player, YgoServerConfig.Rewards rewards, boolean won) {
        List<String> lines = new ArrayList<>();
        String verb = won ? "Won " : "Got ";
        int packs = (won ? rewards.winPacks : rewards.lossPacks).get();
        for (int i = 0; i < packs; i++) {
            BoosterSets.BoosterSet set = BoosterPackItem.randomSet(player.getRandom());
            if (set == null) {
                break;
            }
            ItemStack pack = BoosterPackItem.of(set.id());
            lines.add(verb + pack.getHoverName().getString());
            hand(player, pack);
        }
        int emeralds = (won ? rewards.winEmeralds : rewards.lossEmeralds).get();
        if (emeralds > 0) {
            lines.add(verb + emeralds + (emeralds == 1 ? " emerald" : " emeralds"));
            for (int left = emeralds; left > 0; left -= 64) {
                hand(player, new ItemStack(Items.EMERALD, Math.min(64, left)));
            }
        }
        int xp = (won ? rewards.winXp : rewards.lossXp).get();
        if (xp > 0) {
            player.giveExperiencePoints(xp);
            lines.add(verb + xp + " experience");
        }
        return lines;
    }

    private static void hand(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
