package io.github.zancrow321.jadm.progression;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmData;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.engine.data.Products;
import net.minecraft.server.MinecraftServer;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sets a server releases on other days than the real TCG ({@code [progression] releaseDates}): read from the config,
 * changed by {@code /jadm progression date} and sent to clients, which put the sets in the same order.
 */
public final class ReleaseDates {
    private ReleaseDates() {
    }

    /**
     * Reads {@code CODE=YYYY-MM-DD} entries (a set code or product id); entries that name no product or no date are
     * skipped with a warning.
     *
     * @return product id to date, in the order of the entries (a later one for the same product wins)
     */
    static Map<String, LocalDate> parse(List<? extends String> entries) {
        Products products = JadmData.bundledProducts();
        Map<String, LocalDate> dates = new LinkedHashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            Products.Product product = eq < 0 ? null : products.resolve(entry.substring(0, eq));
            LocalDate date = eq < 0 ? null : date(entry.substring(eq + 1));
            if (product == null || date == null) {
                Jadm.LOGGER.warn("Skipping [progression] releaseDates entry \"{}\": expected a set code and a date, "
                        + "e.g. MRD=2002-04-01", entry);
                continue;
            }
            dates.put(product.id(), date);
        }
        return dates;
    }

    static LocalDate date(String text) {
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** As the network sends them: {@code id=YYYY-MM-DD}. */
    public static List<String> encode(Map<String, LocalDate> dates) {
        List<String> out = new ArrayList<>();
        dates.forEach((id, date) -> out.add(id + "=" + date));
        return out;
    }

    public static Map<String, LocalDate> decode(List<String> entries) {
        Map<String, LocalDate> dates = new LinkedHashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            LocalDate date = eq < 0 ? null : date(entry.substring(eq + 1));
            if (date != null) {
                dates.put(entry.substring(0, eq), date);
            }
        }
        return dates;
    }

    /**
     * Puts the config's dates in place and, if the order changed, tells every player online. The world and each
     * player keep the date they had reached: whatever now comes out by then is unlocked, whatever moved past it locked.
     */
    public static void apply(MinecraftServer server) {
        if (!JadmServerConfig.SPEC.isLoaded()) {
            return;
        }
        Map<String, LocalDate> dates = parse(JadmServerConfig.RELEASE_DATES.get());
        if (server != null) {
            ProgressionData data = ProgressionData.get(server);
            Map<String, LocalDate> before = decode(data.releaseDates());
            if (!before.equals(dates)) {
                Products from = JadmData.bundledProducts().withReleaseDates(before);
                Products to = JadmData.bundledProducts().withReleaseDates(dates);
                if (data.world() != null) {
                    data.world(carry(from, to, data.world()));
                }
                for (java.util.UUID player : data.players()) {
                    data.player(player, carry(from, to, data.player(player)));
                }
                data.releaseDates(encode(dates));
            }
        }
        if (JadmData.releaseDates(dates) && server != null) {
            server.getPlayerList().getPlayers().forEach(Progress::sync);
        }
    }

    /** @return the newest product in the new order out by the date the old one came out on */
    private static String carry(Products from, Products to, String id) {
        Products.Product reached = from.get(id);
        if (reached == null) {
            return id;
        }
        String carried = null;
        for (Products.Product p : to.products()) {
            if (p.date().isAfter(reached.date())) {
                break;
            }
            carried = p.id();
        }
        return carried != null ? carried : to.products().get(0).id();
    }

    /**
     * Moves a product's release date in the config, or with {@code null} back to its real one, and applies it.
     *
     * @return the product's new date
     */
    static LocalDate set(MinecraftServer server, Products.Product product, LocalDate date) {
        Products products = JadmData.bundledProducts();
        List<String> entries = new ArrayList<>();
        for (String entry : JadmServerConfig.RELEASE_DATES.get()) {
            int eq = entry.indexOf('=');
            Products.Product named = eq < 0 ? null : products.resolve(entry.substring(0, eq));
            if (named == null || !named.id().equals(product.id())) {
                entries.add(entry);
            }
        }
        if (date != null) {
            // Promos share their code, so only a code that finds this very product stands for it.
            Products.Product byCode = products.resolve(product.code());
            String key = byCode != null && byCode.id().equals(product.id()) ? product.code() : product.id();
            entries.add(key + "=" + date);
        }
        JadmServerConfig.RELEASE_DATES.set(entries);
        JadmServerConfig.SPEC.save();
        apply(server);
        return date != null ? date : products.get(product.id()).date();
    }
}
