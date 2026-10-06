package io.github.zancrow321.jadm.tournament;

import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * Everything about what entry fees are paid in ({@code entryFeeCurrency}): Duel Points, or an item. Takes a fee,
 * names an amount and pays one out (as a prize entry, so it waits for players who are offline).
 */
final class Fees {
    private Fees() {
    }

    /** "points", or the id of the item fees are paid in */
    static String currency(Tournament t) {
        String c = t.setting("entryFeeCurrency").strip();
        if (c.equals("currency")) {
            return Points.active() ? "points" : JadmServerConfig.SHOP_CURRENCY.get();
        }
        return c;
    }

    static boolean points(Tournament t) {
        return currency(t).equals("points");
    }

    /** "250 DP", "3 emeralds" */
    static String amount(Tournament t, int n) {
        return points(t) ? Points.format(n) : n + " " + TournamentManager.itemName(currency(t), n);
    }

    /** Takes {@code n} from {@code player}. @return whether they had that much */
    static boolean take(ServerPlayer player, Tournament t, int n) {
        if (points(t)) {
            return Points.get(player.server).take(player.server, player.getUUID(), n);
        }
        Item item = TournamentManager.item(currency(t));
        if (player.getInventory().countItem(item) < n) {
            return false;
        }
        player.getInventory().clearOrCountMatchingItems(s -> s.is(item), n, player.inventoryMenu.getCraftSlots());
        return true;
    }

    /** The prize entry that pays out {@code n}, for refunds and the pot. */
    static String payout(Tournament t, int n) {
        return (points(t) ? "points" : currency(t)) + " " + n;
    }
}
