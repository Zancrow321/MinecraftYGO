package io.github.zancrow321.minecraftygo.tournament;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * Everything about what entry fees are paid in: taking a fee, naming an amount and paying one out (as a prize
 * entry, so it waits for players who are offline). Fees are items ({@code entryFeeItem}) for now; a currency kept
 * as a value would only change this class.
 */
final class Fees {
    private Fees() {
    }

    /** "3 emeralds" */
    static String amount(Tournament t, int n) {
        return n + " " + TournamentManager.itemName(t.setting("entryFeeItem"), n);
    }

    /** Takes {@code n} from {@code player}. @return whether they had that much */
    static boolean take(ServerPlayer player, Tournament t, int n) {
        Item item = TournamentManager.item(t.setting("entryFeeItem"));
        if (player.getInventory().countItem(item) < n) {
            return false;
        }
        player.getInventory().clearOrCountMatchingItems(s -> s.is(item), n, player.inventoryMenu.getCraftSlots());
        return true;
    }

    /** The prize entry that pays out {@code n}, for refunds and the pot. */
    static String payout(Tournament t, int n) {
        return t.setting("entryFeeItem") + " " + n;
    }
}
