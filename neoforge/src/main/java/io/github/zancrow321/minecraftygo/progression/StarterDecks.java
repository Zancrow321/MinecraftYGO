package io.github.zancrow321.minecraftygo.progression;

import io.github.zancrow321.minecraftygo.YgoData;
import io.github.zancrow321.minecraftygo.engine.data.Banlist;
import io.github.zancrow321.minecraftygo.engine.data.CardInfo;
import io.github.zancrow321.minecraftygo.engine.data.Deck;
import io.github.zancrow321.minecraftygo.engine.data.DeckRules;
import io.github.zancrow321.minecraftygo.engine.data.PoolMode;
import io.github.zancrow321.minecraftygo.engine.data.Products;
import io.github.zancrow321.minecraftygo.item.DeckBoxItem;
import io.github.zancrow321.minecraftygo.item.YgoComponents;
import io.github.zancrow321.minecraftygo.item.YgoItems;
import io.github.zancrow321.minecraftygo.network.StarterChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A new player's first deck: Yugi's or Kaiba's starter deck, or any starter or structure deck that is out. Offered
 * once per player, on their first login or with {@code /ygo starter}.
 */
public final class StarterDecks {
    private static final Pattern DECK = Pattern.compile("starter deck|structure deck");
    /** The two bundled starters stand for these products. */
    private static final List<String> BUNDLED_CODES = List.of("SDY", "SDK");

    private StarterDecks() {
    }

    /** The decks a player can pick from, in release order. */
    static List<StarterChoicesPayload.Choice> choices(ServerPlayer player) {
        List<StarterChoicesPayload.Choice> choices = new ArrayList<>();
        Deck yugi = Deck.bundled("starter_yugi");
        Deck kaiba = Deck.bundled("starter_kaiba");
        choices.add(new StarterChoicesPayload.Choice("starter_yugi", "Starter Deck: Yugi", yugi.main().size(),
                46986414));
        choices.add(new StarterChoicesPayload.Choice("starter_kaiba", "Starter Deck: Kaiba", kaiba.main().size(),
                89631139));
        if (YgoData.poolMode() == PoolMode.MODELED) {
            return choices;
        }
        List<Products.Product> products = YgoData.products().products();
        int last = YgoData.poolMode() == PoolMode.PROGRESSION ? YgoData.step(player) : products.size() - 1;
        for (int i = 0; i <= last && i < products.size(); i++) {
            Products.Product p = products.get(i);
            if (p.kind() == Products.Kind.DECK && DECK.matcher(p.name().toLowerCase(Locale.ROOT)).find()
                    && !BUNDLED_CODES.contains(p.code())) {
                Deck deck = deckOf(p, YgoData.banlist(player));
                if (deck.main().size() >= DeckRules.MAIN_MIN && deck.main().size() <= DeckRules.MAIN_MAX) {
                    choices.add(new StarterChoicesPayload.Choice(p.id(), p.name(), deck.main().size()
                            + deck.extra().size(), cover(p)));
                }
            }
        }
        return choices;
    }

    /**
     * A product's cards as a deck: extra deck monsters in the extra deck (at most 15), the rest in the main. The card
     * lists name each card once, while a real structure deck holds copies, so a main deck under 40 is filled up with
     * second and third copies, commons first.
     *
     * @param banlist if not {@code null}, forbidden cards are left out and copies stay within their limit
     */
    public static Deck deckOf(Products.Product product, Banlist banlist) {
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> commons = new ArrayList<>();
        List<Integer> others = new ArrayList<>();
        for (Products.Printing printing : product.cards()) {
            CardInfo card = YgoData.cards().card(printing.code());
            if (card == null || banlist != null && banlist.limit(printing.code()) == 0) {
                continue;
            }
            if (DeckRules.isExtra(card)) {
                if (extra.size() < DeckRules.EXTRA_MAX) {
                    extra.add(printing.code());
                }
            } else {
                main.add(printing.code());
                (printing.rarity().equalsIgnoreCase("Common") ? commons : others).add(printing.code());
            }
        }
        List<Integer> fill = new ArrayList<>(commons);
        fill.addAll(others);
        for (int copy = 2; copy <= 3 && !fill.isEmpty(); copy++) {
            for (int code : fill) {
                if (main.size() >= DeckRules.MAIN_MIN) {
                    break;
                }
                if (banlist == null || banlist.limit(code) >= copy) {
                    main.add(code);
                }
            }
        }
        main.sort(null);
        return new Deck(product.name(), main, extra, List.of());
    }

    /** The product's first card printed as ultra rare or better, else its first card. */
    private static int cover(Products.Product product) {
        for (Products.Printing p : product.cards()) {
            String r = p.rarity().toLowerCase(Locale.ROOT);
            if ((r.contains("ultra") || r.contains("secret")) && YgoData.cards().card(p.code()) != null) {
                return p.code();
            }
        }
        return product.cards().isEmpty() ? 0 : product.cards().get(0).code();
    }

    /** Sends the choice to a player who hasn't picked yet. */
    public static void offer(ServerPlayer player) {
        if (ProgressionData.get(player.server).hasStarter(player.getUUID())) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new StarterChoicesPayload(choices(player)));
    }

    /** {@code /ygo starter}: the choice again, or why not. */
    public static int command(ServerPlayer player) {
        if (ProgressionData.get(player.server).hasStarter(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.minecraftygo.starter.taken"));
            return 0;
        }
        offer(player);
        return 1;
    }

    /** The player picked a deck: a deck box with it, once. */
    public static void pick(ServerPlayer player, String id) {
        ProgressionData data = ProgressionData.get(player.server);
        if (data.hasStarter(player.getUUID())) {
            return;
        }
        StarterChoicesPayload.Choice choice = choices(player).stream().filter(c -> c.id().equals(id)).findFirst()
                .orElse(null);
        if (choice == null) {
            return;
        }
        ItemStack box;
        if (id.startsWith("starter_")) {
            box = id.equals("starter_yugi") ? YgoItems.starterYugi() : YgoItems.starterKaiba();
        } else {
            Deck deck = deckOf(YgoData.products().get(id), YgoData.banlist(player));
            box = new ItemStack(YgoItems.DECK_BOX.get());
            box.set(YgoComponents.DECK.get(), new YgoComponents.DeckList(deck.main(), deck.extra()));
            box.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(choice.name()));
        }
        data.starter(player.getUUID());
        // Checked before the box goes into the inventory, which empties the stack it is handed.
        List<String> problems = DeckBoxItem.problems(box, player, YgoData.banlist(player));
        if (!player.getInventory().add(box)) {
            player.drop(box, false);
        }
        player.sendSystemMessage(Component.translatable("message.minecraftygo.starter.given", choice.name())
                .withStyle(ChatFormatting.GREEN));
        if (!problems.isEmpty()) {
            player.sendSystemMessage(Component.literal(problems.get(0)).withStyle(ChatFormatting.GRAY));
        }
    }
}
