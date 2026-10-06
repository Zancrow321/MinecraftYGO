package io.github.zancrow321.minecraftygo.engine.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.zancrow321.minecraftygo.engine.DuelLogHandler;
import io.github.zancrow321.minecraftygo.engine.DuelSettings;
import io.github.zancrow321.minecraftygo.engine.OcgConstants;
import io.github.zancrow321.minecraftygo.engine.OcgCore;
import io.github.zancrow321.minecraftygo.engine.OcgCoreTest;
import io.github.zancrow321.minecraftygo.engine.OcgDuel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Loads every bundled card into the engine once and fails on any card whose script reports an error while it is
 * set up, unless it is listed in {@code broken.json} (which keeps it out of the pool). A listed card that loads
 * cleanly again fails the test too, so the list never goes stale.
 */
class ScriptLoadTest {
    /** Cards per duel: a fresh duel now and then keeps one card's global effects from piling up on the others. */
    private static final int BATCH = 500;

    @BeforeAll
    static void requireNatives() {
        OcgCoreTest.requireNatives();
    }

    @Test
    void everyCardScriptLoads() throws Exception {
        CardDatabase cards = CardDatabase.loadBundled();
        BundledScripts scripts = new BundledScripts();
        List<Integer> codes = cards.all().stream().map(CardInfo::code).sorted().toList();
        Map<Integer, String> failures = new TreeMap<>();
        List<String> log = new ArrayList<>();
        DuelLogHandler handler = (type, message) -> {
            if (type == DuelLogHandler.LogType.ERROR) {
                log.add(message);
            }
        };
        for (int from = 0; from < codes.size(); from += BATCH) {
            try (OcgDuel duel = OcgCore.get().createDuel(DuelSettings.standard(new long[]{1, 2, 3, 4},
                    OcgConstants.DUEL_MODE_MR5), cards, scripts, handler)) {
                for (String base : new String[]{"constant.lua", "utility.lua"}) {
                    duel.loadScript(base, Objects.requireNonNull(scripts.read(base), base));
                }
                for (int code : codes.subList(from, Math.min(codes.size(), from + BATCH))) {
                    log.clear();
                    duel.newCard(0, 0, code, 0, OcgConstants.LOCATION_DECK, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
                    if (!log.isEmpty()) {
                        failures.put(code, cards.name(code) + ": " + String.join(" | ", log));
                    }
                }
            }
        }
        TreeSet<Integer> broken = new TreeSet<>();
        try (var in = new InputStreamReader(Objects.requireNonNull(
                ScriptLoadTest.class.getResourceAsStream(CardPool.BROKEN_RESOURCE)), StandardCharsets.UTF_8)) {
            for (JsonElement e : JsonParser.parseReader(in).getAsJsonArray()) {
                broken.add(e.getAsInt());
            }
        }
        failures.forEach((code, message) -> System.out.println("script error " + code + " " + message));
        assertEquals(broken, new TreeSet<>(failures.keySet()),
                "cards whose scripts fail to load must be exactly those in broken.json");
    }
}
